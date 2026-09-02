package com.deliveryapp.repository;

import com.deliveryapp.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke every token in a family (theft response). */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.familyId = :familyId")
    void revokeFamily(@Param("familyId") String familyId);

    /** Revoke all tokens for a user (logout-all / password change). */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.userId = :userId")
    void revokeAllForUser(@Param("userId") Long userId);

    /**
     * Scheduled cleanup.
     * Revoked tokens are only deleted when they are also old (createdAt < cutoff),
     * avoiding a race where a just-rotated token is deleted before the client confirms.
     * Truly expired (expiresAt < now) tokens are always cleaned regardless.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE (rt.revoked = true AND rt.createdAt < :cutoff) OR rt.expiresAt < :cutoff")
    void deleteExpiredOrRevoked(@Param("cutoff") LocalDateTime cutoff);

    /** Check if a revoked token in this family exists (reuse detection). */
    boolean existsByFamilyIdAndRevoked(String familyId, boolean revoked);

    /**
     * Grace-window: find the active successor token issued for this family
     * after a rotation. Used when a client retries with an already-rotated token.
     */
    Optional<RefreshToken> findTopByFamilyIdAndRevokedFalseOrderByCreatedAtDesc(String familyId);
}