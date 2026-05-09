package com.deliveryapp.dto.auth;

import com.deliveryapp.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    /**
     * Short-lived JWT access token (15 minutes). Put this in Authorization: Bearer
     * <token>.
     */
    private String accessToken;

    /**
     * Long-lived refresh token (30 days).
     * Store securely on the client (e.g. Flutter: flutter_secure_storage).
     * Send to POST /api/auth/refresh to get a new access token.
     * Rotates on every use — old token is invalidated immediately.
     */
    private String refreshToken;

    /** Full user details. */
    private UserResponse user;

    // ── Convenience constructors ──────────────────────────────────────────────

}