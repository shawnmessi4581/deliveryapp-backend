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

    /** Scheduled cleanup — delete expired or revoked tokens older than 7 days. */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true OR rt.expiresAt < :cutoff")
    void deleteExpiredOrRevoked(@Param("cutoff") LocalDateTime cutoff);

    /** Check if a revoked token in this family exists (reuse detection). */
    boolean existsByFamilyIdAndRevoked(String familyId, boolean revoked);
}