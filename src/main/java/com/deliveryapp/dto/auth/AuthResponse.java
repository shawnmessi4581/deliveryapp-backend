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
     * JWT access token (24 hours by default). Put this in Authorization: Bearer
     * <token>.
     */
    private String accessToken;

    /**
     * Long-lived refresh token; the session ends only after 365 days without a refresh.
     * Store securely on the client (e.g. Flutter: flutter_secure_storage).
     * Send to POST /api/auth/refresh to get a new access token.
     * Rotates on every use — the previous token keeps working until the new one is used.
     */
    private String refreshToken;

    /** Full user details. */
    private UserResponse user;

    // ── Convenience constructors ──────────────────────────────────────────────

}