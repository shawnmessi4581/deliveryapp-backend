package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A Flash Sale is a time-limited promotional event that the customer participates in
 * by copying a generated coupon code and applying it at checkout.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Admin or Vendor creates a FlashSale → service auto-generates a unique {@code couponCode}
 *       and persists a backing {@link Coupon} entity with matching parameters.</li>
 *   <li>Customer sees the flash sale on the app (title, discount, timer, code).</li>
 *   <li>Customer copies the code and pastes it at checkout — the existing coupon engine handles the rest.</li>
 *   <li>When the flash sale expires or is deleted, the backing Coupon is deactivated / deleted.</li>
 * </ol>
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>{@code store = null}  → Admin-created, can be scoped to ALL / CATEGORY / PRODUCT via applicableTo.</li>
 *   <li>{@code store != null} → Vendor-created, always scoped to that store (applicableTo = STORE).</li>
 * </ul>
 */
@Entity
@Table(name = "flash_sales")
@Data
public class FlashSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flash_sale_id")
    private Long flashSaleId;

    // ── Core display ─────────────────────────────────────────────────────────

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Optional banner / thumbnail image URL for the flash sale card. */
    private String bannerImage;

    // ── Coupon parameters (used when auto-creating the backing coupon) ────────

    /**
     * The generated unique code customers copy at checkout.
     * Format: {@code FLASH-XXXXXXXX} (auto-generated on creation).
     */
    @Column(unique = true, nullable = false, length = 50)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Coupon.DiscountType discountType;   // PERCENTAGE | FIXED_AMOUNT

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** Optional: minimum order amount to be eligible. */
    @Column(precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    /** Optional: cap on the maximum discount (for PERCENTAGE type). */
    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    // ── Scope ────────────────────────────────────────────────────────────────

    /**
     * Determines what the discount applies to.
     * Vendor flash sales are always {@code STORE} with the vendor's store ID.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Coupon.ApplicableTo applicableTo = Coupon.ApplicableTo.ALL;

    /**
     * The specific entity ID for STORE / CATEGORY / SUBCATEGORY / PRODUCT scope.
     * Null when applicableTo = ALL.
     */
    private Long applicableId;

    /** The store that owns this flash sale. Null if created by an Admin. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    // ── Usage limits ─────────────────────────────────────────────────────────

    /** Maximum times the coupon can be used globally across all customers. Null = unlimited. */
    private Integer totalUsageLimit;

    /** Maximum times a single customer can use this coupon. Default 1. */
    @Column(nullable = false, columnDefinition = "integer default 1")
    private Integer maxUsagePerUser = 1;

    // ── Scheduling / countdown ───────────────────────────────────────────────

    /** When the flash sale becomes visible and the coupon becomes active. */
    @Column(nullable = false)
    private LocalDateTime startDate;

    /**
     * When the flash sale ends — the countdown timer on the frontend counts down to THIS.
     * The backing Coupon's {@code endDate} is set to this value.
     */
    @Column(nullable = false)
    private LocalDateTime countdownEndsAt;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean isActive = true;

    // ── Backing coupon reference ─────────────────────────────────────────────

    /**
     * The ID of the auto-generated {@link Coupon} entity.
     * Stored as a plain Long (not a FK join) to avoid circular cascade issues.
     * Used for cleanup when the flash sale is deleted.
     */
    @Column(name = "backing_coupon_id")
    private Long backingCouponId;

    // ── Audit ────────────────────────────────────────────────────────────────

    /** ID of the User (Admin or Vendor) who created this flash sale. */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
