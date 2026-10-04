package com.deliveryapp.entity;

import com.deliveryapp.enums.OfferType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents a promotional offer that can be attached to a specific product.
 *
 * <h3>Offer mechanics (driven by {@link OfferType})</h3>
 * <ul>
 *   <li><b>BUY_X_GET_Y_FREE</b>        – buy {@code buyQuantity}, get {@code getQuantity} free.</li>
 *   <li><b>BUY_X_GET_Y_PERCENT_OFF</b> – buy {@code buyQuantity}, get {@code getQuantity}
 *       at {@code discountPercent} % off.</li>
 *   <li><b>BUY_X_GET_Y_FIXED_OFF</b>   – buy {@code buyQuantity}, get {@code getQuantity}
 *       with {@code discountValue} SYP off each rewarded unit.</li>
 * </ul>
 *
 * <h3>Ownership & funding</h3>
 * <ul>
 *   <li>{@code store} is the store whose product carries the offer.</li>
 *   <li>{@code fundedByStore} decides who pays: the store (deducted from its payout) or the platform.
 *       Vendor offers are always paid by their store; an admin can choose either.</li>
 *   <li>Vendors can only manage offers they created ({@code createdBy}); admin offers are read-only
 *       for them, even when their store pays.</li>
 * </ul>
 */
@Entity
@Table(name = "promotional_offers")
@Data
public class PromotionalOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offer_id")
    private Long offerId;

    // ── Core metadata ────────────────────────────────────────────────────────

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ── Offer type & parameters ──────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferType offerType;

    /**
     * Number of units the customer must purchase to trigger the offer.
     * Example: "Buy <b>1</b> Get 1 Free" → {@code buyQuantity = 1}.
     */
    @Column(nullable = false)
    private Integer buyQuantity;

    /**
     * Number of units the customer receives at the discounted / free price.
     * Example: "Buy 1 Get <b>1</b> Free" → {@code getQuantity = 1}.
     */
    @Column(nullable = false)
    private Integer getQuantity;

    /**
     * Percentage discount applied to the rewarded unit(s).
     * Only meaningful when {@code offerType = BUY_X_GET_Y_PERCENT_OFF}.
     * Range: 0–100.
     */
    private Double discountPercent;

    /**
     * Fixed SYP amount discounted from each rewarded unit.
     * Only meaningful when {@code offerType = BUY_X_GET_Y_FIXED_OFF}.
     */
    private Double discountValue;

    // ── Scope: which product does this offer apply to ─────────────────────────

    /**
     * The product this offer is linked to. Required — an offer must target a specific product.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * The store of the offer's product (the one that pays when {@code fundedByStore} is true).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    /**
     * Who pays the discount: true = the store (deducted from its payout), false = the platform.
     * Vendor offers are always true; admins choose (default false).
     * Null on offers created before this field existed — those fall back to who created them.
     */
    @Column(name = "funded_by_store")
    private Boolean fundedByStore;

    // ── Scheduling & lifecycle ───────────────────────────────────────────────

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean isActive = true;

    // ── Audit ────────────────────────────────────────────────────────────────

    /** ID of the User (admin or vendor) who created the offer. */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
