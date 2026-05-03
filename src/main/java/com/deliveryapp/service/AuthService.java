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
                throw new DuplicateResourceException("رقم الهاتف مسجل مسبقاً.");
            }
            // Unverified leftover — clean up so they can try again
            otpVerificationRepository.deleteByPhoneNumber(request.getPhoneNumber());
            userRepository.delete(existingUser);
        });

        User user = new User();
        user.setName(request.getName());
        user.setPhoneNumber(request.getPhoneNumber());
        // user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.CUSTOMER);
        user.setIsActive(false); // ← Inactive until phone is verified
        userRepository.save(user);

        sendOtp(request.getPhoneNumber(), user);

        log.info("New user registered (unverified): [...{}]",
                request.getPhoneNumber().substring(
                        Math.max(0, request.getPhoneNumber().length() - 4)));

        return "تم التسجيل بنجاح. يرجى التحقق من رقم هاتفك باستخدام رمز التحقق المرسل.";
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
                .orElseThrow(() -> new InvalidDataException("رمز التحقق غير صالح أو منتهي الصلاحية."));

        validateOtp(dbOtp, otpCode);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود."));

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
                        "لم يتم العثور على حساب بهذا الرقم."));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new InvalidDataException(
                    "غير موثق: رقم الهاتف غير موثق. يرجى إكمال عملية التحقق.");
        }

        // 1. Authenticate with Try/Catch for better error handling
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getPhoneNumber(),
                            request.getPassword()));
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            // This catches wrong passwords
            throw new InvalidDataException("كلمة المرور غير صحيحة. يرجى المحاولة مرة أخرى.");
        } catch (Exception e) {
            // Catch any other auth-related errors (e.g. locked accounts in Spring Security)
            throw new InvalidDataException("فشل المصادقة: " + e.getMessage());
        }

        // 2. Update Last Login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // 3. Generate Token and Response
        String token = tokenService.generateToken(authentication, user.getUserId());
        UserResponse userResponse = userMapper.toUserResponse(user);

        return new AuthResponse(token, userResponse);
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود."));
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

        return "إذا كان هذا الرقم مسجلاً، فقد تم إرسال رمز التحقق.";
    }

    /**
     * Step 2: Verify OTP and update password.
     */
    @Transactional
    public String resetPassword(String phoneNumber, String otpCode, String newPassword) {
        OtpVerification dbOtp = otpVerificationRepository
                .findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new InvalidDataException("رمز التحقق غير صالح أو منتهي الصلاحية."));

        validateOtp(dbOtp, otpCode);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        otpVerificationRepository.delete(dbOtp);

        log.info("Password reset successful: [...{}]",
                phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));

        return "تم تغيير كلمة المرور بنجاح. يمكنك الآن تسجيل الدخول.";
    }

    /**
     * Universal Resend OTP method.
     * Works for both inactive (registering) and active (password reset) users.
     * Includes a 1-minute cooldown to prevent SMS spam / abuse.
     */
    @Transactional
    public String resendOtp(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("لم يتم العثور على حساب بهذا الرقم."));

        // Anti-Spam / Cooldown Check: Look for the most recent OTP
        otpVerificationRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .ifPresent(lastOtp -> {
                    // If the last OTP was created less than 1 minute ago, block the request
                    if (lastOtp.getCreatedAt().plusMinutes(1).isAfter(LocalDateTime.now())) {
                        throw new InvalidDataException("يرجى الانتظار دقيقة واحدة على الأقل قبل طلب رمز جديد.");
                    }
                });

        // Reuse your existing helper method (clears old OTPs, generates new, sends SMS)
        sendOtp(phoneNumber, user);

        log.info("OTP resent to: [...{}]",
                phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));

        return "تم إرسال رمز تحقق جديد إلى هاتفك.";
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
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        otp.setIsVerified(false);
        otp.setUser(user);
        otpVerificationRepository.save(otp);

        // ⚠️ TEMP - remove before production
        log.warn("DEV OTP for {}: {}", phoneNumber, otpCode);

        smsService.sendSms(phoneNumber, otpCode);
    }

    /**
     * Validates an OTP record: checks lock, expiry, and code match.
     * Increments attempt counter on each wrong guess.
     */
    private void validateOtp(OtpVerification dbOtp, String submittedCode) {
        if (dbOtp.isLocked()) {
            throw new InvalidDataException(
                    "محاولات خاطئة كثيرة. يرجى طلب رمز جديد.");
        }
        if (dbOtp.isExpired()) {
            throw new InvalidDataException("انتهت صلاحية رمز التحقق. يرجى طلب رمز جديد.");
        }
        if (!dbOtp.getOtpCode().equals(submittedCode)) {
            dbOtp.incrementAttempts();
            otpVerificationRepository.save(dbOtp);
            int remaining = OtpVerification.MAX_ATTEMPTS - dbOtp.getAttemptCount();
            if (remaining <= 0) {
                throw new InvalidDataException(
                        "محاولات خاطئة كثيرة. يرجى طلب رمز جديد.");
            }
            throw new InvalidDataException(
                    "رمز غير صحيح. تبقى " + remaining + " محاولة (محاولات).");
        }
    }

    private String generateOtp() {
        String otp;
        do {
            int num = secureRandom.nextInt(900_000) + 100_000; // 100000–999999, always 6 digits
            otp = String.valueOf(num);
        } while (otp.length() != 6);
        return otp;
    }
}