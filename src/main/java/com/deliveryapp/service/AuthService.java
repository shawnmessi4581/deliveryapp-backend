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

    private final SecureRandom secureRandom = new SecureRandom();

    // ─── Registration ──────────────────────────────────────────────────────────

    /**
     * Step 1 of registration.
     *
     * Creates the user with isActive = FALSE and sends an OTP.
     * No token is returned — the user cannot log in until they verify their number.
     *
     * If the phone already exists but is still unverified (e.g. user never
     * finished),
     * we delete the old record and let them re-register cleanly.
     */
    @Transactional
    public String register(SignupRequest request) {
        userRepository.findByPhoneNumber(request.getPhoneNumber()).ifPresent(existingUser -> {
            if (Boolean.TRUE.equals(existingUser.getIsActive())) {
                throw new DuplicateResourceException("Phone number already registered.");
            }
            // Unverified leftover — clean up so they can try again
            otpVerificationRepository.deleteByPhoneNumber(request.getPhoneNumber());
            userRepository.delete(existingUser);
        });

        User user = new User();
        user.setName(request.getName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.CUSTOMER);
        user.setIsActive(false); // ← Inactive until phone is verified
        userRepository.save(user);

        sendOtp(request.getPhoneNumber(), user);

        log.info("New user registered (unverified): [...{}]",
                request.getPhoneNumber().substring(
                        Math.max(0, request.getPhoneNumber().length() - 4)));

        return "Registration successful. Please verify your phone number with the OTP we sent.";
    }

    // ─── Account Verification (after registration) ─────────────────────────────

    /**
     * Step 2 of registration.
     *
     * Verifies the OTP sent during register(), activates the account,
     * and returns a token + user so the app can log in immediately.
     */
    @Transactional
    public AuthResponse verifyAccount(String phoneNumber, String otpCode) {
        OtpVerification dbOtp = otpVerificationRepository
                .findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new InvalidDataException("Invalid or expired OTP."));

        validateOtp(dbOtp, otpCode);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        // Activate the account
        user.setIsActive(true);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        otpVerificationRepository.delete(dbOtp);

        log.info("Account verified and activated: [...{}]",
                phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));

        // Generate token directly — we can't call authenticationManager here
        // because the password is already hashed and we don't have the raw value.
        // TokenService only needs the userId, so this is safe.
        String token = tokenService.generateTokenForUserId(user.getUserId(), user.getPhoneNumber());
        UserResponse userResponse = userMapper.toUserResponse(user);

        return new AuthResponse(token, userResponse);
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    /**
     * Checks active status before authenticating, so Flutter gets a specific
     * error message it can use to redirect to the verify screen.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account found with this phone number."));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new InvalidDataException(
                    "UNVERIFIED: Phone number not verified. Please complete OTP verification.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getPhoneNumber(),
                        request.getPassword()));

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = tokenService.generateToken(authentication, user.getUserId());
        UserResponse userResponse = userMapper.toUserResponse(user);

        return new AuthResponse(token, userResponse);
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.setFcmToken(null);
        userRepository.save(user);
    }

    // ─── Password Reset ────────────────────────────────────────────────────────

    /**
     * Step 1: Generate OTP and send via SMS.
     * Always returns the same message to prevent phone number enumeration.
     * Unverified accounts are silently skipped — they should finish registration
     * instead.
     */
    @Transactional
    public String initiatePasswordReset(String phoneNumber) {
        userRepository.findByPhoneNumber(phoneNumber).ifPresent(user -> {
            if (Boolean.FALSE.equals(user.getIsActive()))
                return;
            sendOtp(phoneNumber, user);
            log.info("Password reset OTP sent: [...{}]",
                    phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));
        });

        return "If this number is registered, an OTP has been sent.";
    }

    /**
     * Step 2: Verify OTP and update password.
     */
    @Transactional
    public String resetPassword(String phoneNumber, String otpCode, String newPassword) {
        OtpVerification dbOtp = otpVerificationRepository
                .findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new InvalidDataException("Invalid or expired OTP."));

        validateOtp(dbOtp, otpCode);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        otpVerificationRepository.delete(dbOtp);

        log.info("Password reset successful: [...{}]",
                phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));

        return "Password changed successfully. You can now log in.";
    }

    // ─── Shared Helpers ────────────────────────────────────────────────────────

    /**
     * Clears old OTPs for the number, saves a fresh one, and fires the SMS async.
     */
    private void sendOtp(String phoneNumber, User user) {
        otpVerificationRepository.deleteByPhoneNumber(phoneNumber);

        String otpCode = generateOtp();

        OtpVerification otp = new OtpVerification();
        otp.setPhoneNumber(phoneNumber);
        otp.setOtpCode(otpCode);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        otp.setIsVerified(false);
        otp.setUser(user);
        otpVerificationRepository.save(otp);

        String smsBody = "Your verification code is: " + otpCode
                + ". Valid for 10 minutes. Do not share it with anyone.";
        smsService.sendSms(phoneNumber, smsBody);
    }

    /**
     * Validates an OTP record: checks lock, expiry, and code match.
     * Increments attempt counter on each wrong guess.
     */
    private void validateOtp(OtpVerification dbOtp, String submittedCode) {
        if (dbOtp.isLocked()) {
            throw new InvalidDataException(
                    "Too many incorrect attempts. Please request a new OTP.");
        }
        if (dbOtp.isExpired()) {
            throw new InvalidDataException("OTP has expired. Please request a new one.");
        }
        if (!dbOtp.getOtpCode().equals(submittedCode)) {
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
    }

    /** 6-digit zero-padded OTP — 1,000,000 combinations. */
    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}