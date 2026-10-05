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
     * "accessToken": "eyJ...", // JWT, 24 h, use in Authorization: Bearer header
     * "refreshToken": "abc...", // Opaque, store in flutter_secure_storage; ends after 365 days unused
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
     * Exchanges a refresh token for a new access token + refresh token pair.
     *
     * Rotation: a new refresh token is issued every time. Retrying with the previous
     * refresh token (lost response, app killed, parallel refreshes) returns the same
     * new token again, so retries never log the user out. No access token is needed;
     * an expired one sent along in the Authorization header is ignored.
     *
     * Flutter flow:
     * 1. API call returns 401 (access token expired)
     * 2. Read refreshToken from flutter_secure_storage
     * 3. POST /api/auth/refresh with { "refreshToken": "..." }
     * 4. Save new accessToken + refreshToken back to storage
     * 5. Retry the original request
     * 6. Only if /refresh returns 400 with "code": "SESSION_EXPIRED" → logout, navigate to login screen.
     *    Timeouts, no connection and 5xx errors are temporary: keep the tokens and retry later.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    /**
     * Logs out this device only: revokes the refresh token of the session the
     * access token belongs to. The user's other devices stay logged in.
     * The access token itself still works until it expires — acceptable for stateless JWTs.
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        authService.logout(userId, jwt.getClaimAsString("sid"));
        return ResponseEntity.ok("تم تسجيل الخروج بنجاح");
    }

    /** Revokes ALL refresh tokens for the user (logs out from every device). */
    @PostMapping("/logout-all")
    public ResponseEntity<String> logoutAllDevices(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        authService.logoutAllDevices(userId);
        return ResponseEntity.ok("تم تسجيل الخروج من جميع الأجهزة بنجاح");
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