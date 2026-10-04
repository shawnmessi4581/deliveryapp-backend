package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// currentUsageCount is changed only through atomic UPDATE queries (CouponRepository.claimUsage /
// releaseUsage). @DynamicUpdate keeps unrelated saves (admin edits, flash sale sync/expiry) from
// writing back a stale counter.
@Entity
@Table(name = "coupons")
@Data
@DynamicUpdate
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long couponId;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Enumerated(EnumType.STRING)
    private ApplicableTo applicableTo = ApplicableTo.ALL;

    private Long applicableId;

    private Boolean isFirstOrderOnly = false;

    private Integer maxUsagePerUser = 1;

    private Integer totalUsageLimit;

    private Integer currentUsageCount = 0;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    private Boolean isActive = true;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum DiscountType {
        PERCENTAGE,
        FIXED_AMOUNT,
        FREE_DELIVERY
    }

    public enum ApplicableTo {
        ALL,
        STORE,
        CATEGORY,
        SUBCATEGORY,
        PRODUCT
    }
}