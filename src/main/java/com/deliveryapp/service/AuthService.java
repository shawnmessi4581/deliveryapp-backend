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
import org.springframework.security.authentication.BadCredentialsException;
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
     * Step 1: Create inactive account and send OTP.
     * If phone exists but is still unverified (abandoned signup), clean up and
     * allow retry.
     */
    @Transactional
    public String register(SignupRequest request) {
        userRepository.findByPhoneNumber(request.getPhoneNumber()).ifPresent(existing -> {
            if (Boolean.TRUE.equals(existing.getIsActive())) {
                throw new DuplicateResourceException("رقم الهاتف مسجل مسبقاً.");
            }
            // Unverified leftover — wipe it so they can start fresh
            tokenService.revokeAllTokensForUser(existing.getUserId());
            otpVerificationRepository.deleteByPhoneNumber(request.getPhoneNumber());
            userRepository.delete(existing);
        });

        User user = new User();
        user.setName(request.getName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.CUSTOMER);
        user.setIsActive(false);
        userRepository.save(user);

        sendOtp(request.getPhoneNumber(), user);

        log.info("New user registered (unverified): [...{}]", tail(request.getPhoneNumber()));
        return "تم التسجيل بنجاح. يرجى التحقق من رقم هاتفك باستخدام رمز التحقق المرسل.";
    }

    // ─── Account Verification ──────────────────────────────────────────────────

    /**
     * Step 2: Verify OTP, activate account, return access + refresh tokens.
     */
    @Transactional
    public AuthResponse verifyAccount(String phoneNumber, String otpCode, String deviceInfo) {
        OtpVerification dbOtp = otpVerificationRepository
                .findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new InvalidDataException("رمز التحقق غير صالح أو منتهي الصلاحية."));

        validateOtp(dbOtp, otpCode);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود."));

        user.setIsActive(true);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        otpVerificationRepository.delete(dbOtp);

        log.info("Account verified and activated: [...{}]", tail(phoneNumber));

        String accessToken = tokenService.generateAccessTokenForUser(user);
        String refreshToken = tokenService.createRefreshToken(user, deviceInfo);
        UserResponse userResponse = userMapper.toUserResponse(user);

        return new AuthResponse(accessToken, refreshToken, userResponse);
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates the user and returns both access and refresh tokens.
     *
     * @param deviceInfo optional device header (e.g. "Flutter/Android/Pixel 7")
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo) {
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new ResourceNotFoundException("لم يتم العثور على حساب بهذا الرقم."));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new InvalidDataException("غير موثق: رقم الهاتف غير موثق. يرجى إكمال عملية التحقق.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getPhoneNumber(),
                            request.getPassword()));
        } catch (BadCredentialsException e) {
            throw new InvalidDataException("كلمة المرور غير صحيحة. يرجى المحاولة مرة أخرى.");
        } catch (Exception e) {
            throw new InvalidDataException("فشل المصادقة: " + e.getMessage());
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = tokenService.generateAccessToken(authentication, user.getUserId());
        String refreshToken = tokenService.createRefreshToken(user, deviceInfo);
        UserResponse userResponse = userMapper.toUserResponse(user);

        log.info("User logged in: [...{}]", tail(request.getPhoneNumber()));
        return new AuthResponse(accessToken, refreshToken, userResponse);
    }

    // ─── Token Refresh ─────────────────────────────────────────────────────────

    /**
     * Validates and rotates a refresh token, returning a fresh access + refresh
     * token pair.
     *
     * Implements refresh token rotation:
     * - Old refresh token is revoked immediately
     * - New refresh token is issued in the same family
     * - If a revoked token is reused, the entire family is nuked (theft response)
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        TokenService.RotationResult result = tokenService.rotateRefreshToken(rawRefreshToken);

        User user = result.user();
        String newAccessToken = tokenService.generateAccessTokenForUser(user);
        UserResponse userResponse = userMapper.toUserResponse(user);

        return new AuthResponse(newAccessToken, result.newRawRefreshToken(), userResponse);
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    /**
     * Revokes all refresh tokens for the user (logout from all devices)
     * and clears the FCM token.
     */
    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود."));

        tokenService.revokeAllTokensForUser(userId);
        user.setFcmToken(null);
        userRepository.save(user);

        log.info("User logged out (all sessions revoked): {}", userId);
    }

    // ─── Password Reset ────────────────────────────────────────────────────────

    @Transactional
    public String initiatePasswordReset(String phoneNumber) {
        userRepository.findByPhoneNumber(phoneNumber).ifPresent(user -> {
            if (Boolean.FALSE.equals(user.getIsActive()))
                return;
            sendOtp(phoneNumber, user);
            log.info("Password reset OTP sent: [...{}]", tail(phoneNumber));
        });
        return "إذا كان هذا الرقم مسجلاً، فقد تم إرسال رمز التحقق.";
    }

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

        // Revoke all active sessions — password changed, re-login required
        tokenService.revokeAllTokensForUser(user.getUserId());

        otpVerificationRepository.delete(dbOtp);

        log.info("Password reset successful: [...{}]", tail(phoneNumber));
        return "تم تغيير كلمة المرور بنجاح. يرجى تسجيل الدخول مرة أخرى.";
    }

    @Transactional
    public String resendOtp(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("لم يتم العثور على حساب بهذا الرقم."));

        otpVerificationRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .ifPresent(lastOtp -> {
                    if (lastOtp.getCreatedAt().plusMinutes(1).isAfter(LocalDateTime.now())) {
                        throw new InvalidDataException("يرجى الانتظار دقيقة واحدة على الأقل قبل طلب رمز جديد.");
                    }
                });

        sendOtp(phoneNumber, user);

        log.info("OTP resent to: [...{}]", tail(phoneNumber));
        return "تم إرسال رمز تحقق جديد إلى هاتفك.";
    }

    // ─── Shared Helpers ────────────────────────────────────────────────────────

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

        // ⚠️ TEMP — remove before production
        log.warn("DEV OTP for {}: {}", phoneNumber, otpCode);

        smsService.sendSms(phoneNumber, otpCode);
    }

    private void validateOtp(OtpVerification dbOtp, String submittedCode) {
        if (dbOtp.isLocked()) {
            throw new InvalidDataException("محاولات خاطئة كثيرة. يرجى طلب رمز جديد.");
        }
        if (dbOtp.isExpired()) {
            throw new InvalidDataException("انتهت صلاحية رمز التحقق. يرجى طلب رمز جديد.");
        }
        if (!dbOtp.getOtpCode().equals(submittedCode)) {
            dbOtp.incrementAttempts();
            otpVerificationRepository.save(dbOtp);
            int remaining = OtpVerification.MAX_ATTEMPTS - dbOtp.getAttemptCount();
            if (remaining <= 0) {
                throw new InvalidDataException("محاولات خاطئة كثيرة. يرجى طلب رمز جديد.");
            }
            throw new InvalidDataException("رمز غير صحيح. تبقى " + remaining + " محاولة (محاولات).");
        }
    }

    private String generateOtp() {
        String otp;
        do {
            int num = secureRandom.nextInt(900_000) + 100_000;
            otp = String.valueOf(num);
        } while (otp.length() != 6);
        return otp;
    }

    /** Returns last 4 chars of phone for safe logging. */
    private String tail(String phoneNumber) {
        return phoneNumber.substring(Math.max(0, phoneNumber.length() - 4));
    }
}