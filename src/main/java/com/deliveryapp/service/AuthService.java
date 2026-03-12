package com.deliveryapp.service;

import com.deliveryapp.dto.auth.AuthResponse;
import com.deliveryapp.dto.auth.LoginRequest;
import com.deliveryapp.dto.auth.SignupRequest;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.OtpVerification;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.mapper.user.UserMapper;
import com.deliveryapp.repository.OtpVerificationRepository;
import com.deliveryapp.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final OtpVerificationRepository otpVerificationRepository;
    private final UserMapper userMapper;
    private final SmsService smsService;

    // SecureRandom is thread-safe — safe to reuse as a field
    private final SecureRandom secureRandom = new SecureRandom();

    // ─── Registration & Login ──────────────────────────────────────────────────

    public AuthResponse register(SignupRequest request) {
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new DuplicateResourceException("Phone number already exists");
        }

        User user = new User();
        user.setName(request.getName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.CUSTOMER);
        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);

        return login(new LoginRequest(request.getPhoneNumber(), request.getPassword()));
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getPhoneNumber(),
                        request.getPassword()));

        User user = userRepository.findByPhoneNumber(request.getPhoneNumber()).orElseThrow();
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = tokenService.generateToken(authentication, user.getUserId());
        UserResponse userResponse = userMapper.toUserResponse(user);

        return new AuthResponse(token, userResponse);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setFcmToken(null);
        userRepository.save(user);
    }

    // ─── Password Reset ────────────────────────────────────────────────────────

    /**
     * Step 1: Generate a 6-digit OTP and send it via SMS.
     *
     * Security: returns the SAME message whether the phone exists or not,
     * so attackers cannot enumerate valid phone numbers.
     */
    @Transactional
    public String initiatePasswordReset(String phoneNumber) {
        userRepository.findByPhoneNumber(phoneNumber).ifPresent(user -> {
            String otpCode = generateOtp();

            // Clear any existing OTPs for this number before saving the new one
            otpVerificationRepository.deleteByPhoneNumber(phoneNumber);

            OtpVerification otp = new OtpVerification();
            otp.setPhoneNumber(phoneNumber);
            otp.setOtpCode(otpCode);
            otp.setExpiresAt(LocalDateTime.now().plusMinutes(10));
            otp.setIsVerified(false);
            otp.setUser(user);
            otpVerificationRepository.save(otp);

            // Async — BMS latency won't block this HTTP response
            String smsBody = "Your verification code is: " + otpCode
                    + ". Valid for 10 minutes. Do not share it with anyone.";
            smsService.sendSms(phoneNumber, smsBody);

            log.info("OTP initiated for [...{}]",
                    phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));
        });

        // Always the same response — never reveal whether the number is registered
        return "If this number is registered, an OTP has been sent.";
    }

    /**
     * Step 2: Verify OTP and reset the password.
     *
     * Security:
     * - Locked after 3 wrong attempts (brute-force protection)
     * - OTP deleted immediately on success (no replay attacks)
     */
    @Transactional
    public String resetPassword(String phoneNumber, String otpCode, String newPassword) {
        OtpVerification dbOtp = otpVerificationRepository
                .findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new InvalidDataException("Invalid or expired OTP"));

        // Check brute-force lock
        if (dbOtp.isLocked()) {
            throw new InvalidDataException(
                    "Too many incorrect attempts. Please request a new OTP.");
        }

        // Check expiry
        if (dbOtp.isExpired()) {
            throw new InvalidDataException("OTP has expired. Please request a new one.");
        }

        // Check code — increment counter on every failure
        if (!dbOtp.getOtpCode().equals(otpCode)) {
            dbOtp.incrementAttempts();
            otpVerificationRepository.save(dbOtp);

            int remaining = OtpVerification.MAX_ATTEMPTS - dbOtp.getAttemptCount();
            if (remaining <= 0) {
                throw new InvalidDataException(
                        "Too many incorrect attempts. Please request a new OTP.");
            }
            throw new InvalidDataException(
                    "Incorrect OTP. " + remaining + " attempt(s) remaining.");
        }

        // ✅ OTP is valid — update the password
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Delete the used OTP — cannot be reused
        otpVerificationRepository.delete(dbOtp);

        log.info("Password reset successful for [...{}]",
                phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));

        return "Password changed successfully. You can now log in.";
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Generates a zero-padded 6-digit OTP using a cryptographically secure RNG.
     * Range: 000000 – 999999 (1,000,000 combinations vs 10,000 for 4-digit)
     */
    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}