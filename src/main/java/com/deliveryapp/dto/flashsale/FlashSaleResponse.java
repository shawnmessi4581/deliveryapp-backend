package com.deliveryapp.dto.flashsale;

import com.deliveryapp.entity.Coupon;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Public-safe response DTO for a {@link com.deliveryapp.entity.FlashSale}.
 *
 * <p>Returned to all callers — public customers, admin, and vendor.
 * Contains everything the frontend needs to render a flash sale card with a live countdown timer.
 */
@Data
public class FlashSaleResponse {

    private Long flashSaleId;
    private String title;
    private String description;
    private String bannerImage;

    // ── The coupon code customers copy ───────────────────────────────────────

    /**
     * The coupon code the customer copies and pastes at checkout.
     * Always included regardless of auth status so customers can easily use it.
     */
    private String couponCode;

    // ── Discount info ────────────────────────────────────────────────────────

    private Coupon.DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;

    // ── Scope ────────────────────────────────────────────────────────────────

    private Coupon.ApplicableTo applicableTo;
    private Long applicableId;

    // ── Store info ───────────────────────────────────────────────────────────

    /** Store ID, or null if this is an admin/global flash sale. */
    private Long storeId;
    private String storeName;

    // ── Usage stats ──────────────────────────────────────────────────────────

    private Integer totalUsageLimit;
    private Integer maxUsagePerUser;

    /**
     * How many times the backing coupon has been used so far.
     * Lets the frontend show "X / Y uses remaining".
     */
    private Integer currentUsageCount;

    // ── Timer / scheduling ───────────────────────────────────────────────────

    private LocalDateTime startDate;

    /**
     * The timestamp the frontend countdown timer counts down to.
     * Format: ISO-8601 — frontend calculates remaining seconds from current time.
     */
    private LocalDateTime countdownEndsAt;

    /**
     * Remaining seconds until {@code countdownEndsAt}.
     * Computed server-side as a convenience — eliminates client clock-skew issues.
     * Will be 0 if the sale has already ended.
     */
    private Long remainingSeconds;

    // ── Status ───────────────────────────────────────────────────────────────

    private Boolean isActive;

    /**
     * Whether this flash sale is currently live (active AND within time window).
     * Convenience boolean for the UI.
     */
    private Boolean isLive;

    // ── Audit ────────────────────────────────────────────────────────────────

    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
