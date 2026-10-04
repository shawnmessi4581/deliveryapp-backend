package com.deliveryapp.repository;

import com.deliveryapp.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository  extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);
    boolean existsByCode(String code);

    // Customers copy/paste codes, so checkout lookups ignore case
    Optional<Coupon> findByCodeIgnoreCase(String code);

    /**
     * Atomically takes one usage slot. Returns 0 when totalUsageLimit is already reached.
     * The UPDATE row-locks the coupon until the transaction commits, so concurrent checkouts
     * with the same code (flash sales) can never exceed the limit.
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.currentUsageCount = COALESCE(c.currentUsageCount, 0) + 1 " +
           "WHERE c.couponId = :couponId " +
           "  AND (c.totalUsageLimit IS NULL OR COALESCE(c.currentUsageCount, 0) < c.totalUsageLimit)")
    int claimUsage(@Param("couponId") Long couponId);

    /** Atomically gives one usage slot back (order cancelled / deleted). */
    @Modifying
    @Query("UPDATE Coupon c SET c.currentUsageCount = c.currentUsageCount - 1 " +
           "WHERE c.couponId = :couponId AND c.currentUsageCount > 0")
    int releaseUsage(@Param("couponId") Long couponId);
}
