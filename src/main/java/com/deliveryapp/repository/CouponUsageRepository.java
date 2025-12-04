package com.deliveryapp.repository;

import com.deliveryapp.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository  extends JpaRepository<CouponUsage, Long> {
    Integer countByCouponId(Long couponId);
    Integer countByCouponIdAndUserId(Long couponId, Long userId);
    // Check if first order
    boolean existsByUserId(Long userId);

}
