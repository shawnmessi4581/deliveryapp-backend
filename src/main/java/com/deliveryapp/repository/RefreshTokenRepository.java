package com.deliveryapp.repository;

import com.deliveryapp.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Same as findByTokenHash, but the row stays locked until the transaction ends,
     * so concurrent refreshes with the same token are handled one after another.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Revoke every token in a family (logout of one device session). */
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
}
