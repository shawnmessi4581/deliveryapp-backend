package com.deliveryapp.controller;

import com.deliveryapp.dto.auth.AuthResponse;
import com.deliveryapp.dto.auth.LoginRequest;
import com.deliveryapp.dto.auth.RefreshTokenRequest;
import com.deliveryapp.dto.auth.ResendOtpRequest;
import com.deliveryapp.dto.auth.SignupRequest;
import com.deliveryapp.dto.user.ForgotPasswordRequest;
import com.deliveryapp.dto.user.ResetPasswordRequest;
import com.deliveryapp.dto.auth.VerifyAccountRequest;
import com.deliveryapp.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ─── Registration ──────────────────────────────────────────────────────────

    /** Step 1: Create account (inactive) and send OTP. */
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * Step 2: Verify OTP, activate account, return token pair.
     *
     * Flutter should persist both accessToken and refreshToken in
     * flutter_secure_storage,
     * then navigate to the home screen.
     */
    @PostMapping("/verify-account")
    public ResponseEntity<AuthResponse> verifyAccount(
            @RequestBody VerifyAccountRequest request,
            @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo) {
        return ResponseEntity.ok(authService.verifyAccount(
                request.getPhoneNumber(),
                request.getOtp(),
                deviceInfo));
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates user and returns an access token + refresh token.
     *
     * Response body:
     * {
     * "accessToken": "eyJ...", // JWT, 15 min, use in Authorization: Bearer header
     * "refreshToken": "abc...", // Opaque, 30 days, store in flutter_secure_storage
     * "user": { ... }
     * }
     *
     * Returns HTTP 400 with "غير موثق: ..." if account isn't verified,
     * so Flutter can redirect to the verify screen.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo) {
        return ResponseEntity.ok(authService.login(request, deviceInfo));
    }

    // ─── Token Refresh ─────────────────────────────────────────────────────────

    /**
     * Exchanges a valid refresh token for a new access token + refresh token pair.
     *
     * Implements rotation: the submitted refresh token is immediately revoked
     * and a new one is issued. If a stolen/reused token is detected, all sessions
     * for that user are invalidated automatically.
     *
     * Flutter flow:
     * 1. API call returns 401 (access token expired)
     * 2. Read refreshToken from flutter_secure_storage
     * 3. POST /api/auth/refresh with { "refreshToken": "..." }
     * 4. Save new accessToken + refreshToken back to storage
     * 5. Retry the original request
     * 6. If /refresh returns 401/400 → force logout, navigate to login screen
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    /**
     * Revokes ALL refresh tokens for the user (logs out from every device).
     * The access token will still work until it expires (15 min max) —
     * this is acceptable for stateless JWTs.
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        authService.logout(userId);
        return ResponseEntity.ok("تم تسجيل الخروج بنجاح");
    }

    // ─── Password Reset ────────────────────────────────────────────────────────

    /** Step 1: Send OTP to phone. */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.initiatePasswordReset(request.getPhoneNumber()));
    }

    /** Step 2: Verify OTP and set new password. All sessions are revoked. */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(
                request.getPhoneNumber(),
                request.getOtp(),
                request.getNewPassword()));
    }

    // ─── OTP ───────────────────────────────────────────────────────────────────

    @PostMapping("/resend-otp")
    public ResponseEntity<String> resendOtp(@RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(authService.resendOtp(request.getPhoneNumber()));
    }
}