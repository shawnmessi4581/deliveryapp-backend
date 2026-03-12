package com.deliveryapp.controller;

import com.deliveryapp.dto.auth.AuthResponse;
import com.deliveryapp.dto.auth.LoginRequest;
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

    /** Step 1 of registration — creates account (inactive) and sends OTP. */
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * Step 2 of registration — verifies OTP, activates account, returns token.
     * Flutter should navigate to the home screen after this succeeds.
     */
    @PostMapping("/verify-account")
    public ResponseEntity<AuthResponse> verifyAccount(@RequestBody VerifyAccountRequest request) {
        return ResponseEntity.ok(authService.verifyAccount(
                request.getPhoneNumber(),
                request.getOtp()));
    }

    /**
     * Standard login.
     * Returns HTTP 400 with message "UNVERIFIED: ..." if account isn't verified
     * yet,
     * so Flutter can catch it and redirect to the verify screen.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        authService.logout(userId);
        return ResponseEntity.ok("Logged out successfully");
    }

    /** Step 1 of password reset — sends OTP to phone. */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.initiatePasswordReset(request.getPhoneNumber()));
    }

    /** Step 2 of password reset — verifies OTP and updates password. */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(
                request.getPhoneNumber(),
                request.getOtp(),
                request.getNewPassword()));
    }
}