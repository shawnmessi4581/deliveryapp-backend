package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token_hash", columnList = "token_hash"),
        @Index(name = "idx_refresh_token_user", columnList = "user_id"),
        @Index(name = "idx_refresh_token_family", columnList = "family_id")
})
@Data
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SHA-256 hash of the raw token sent to the client.
     * We never store the raw value — only the hash.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * The login session (one device) this token belongs to — the "sid" claim of its access tokens.
     * Logout revokes every token of the session.
     */
    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** True = the session was ended (logout, password reset), or the token was replaced by rotation. */
    @Column(nullable = false)
    private boolean revoked = false;

    /**
     * Set when this token was replaced by a newer one, back when refresh tokens rotated on every use.
     * Null for tokens that were never replaced (all tokens issued since).
     */
    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    /** Optional — useful for "manage sessions" screen. */
    @Column(length = 100)
    private String deviceInfo;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}