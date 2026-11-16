package com.deliveryapp.dto.order;

import com.deliveryapp.enums.OrderStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VendorOrderResponse {
    private Long orderId;
    private String orderNumber;
    private OrderStatus status;
    private LocalDateTime createdAt;

    // Minimal Customer Info
    private String customerName;

    // Notes
    private String orderNote;

    // Vendor's Specific Financials
    private Double storeSubtotal; // Total of ONLY their items (full price)
    private Double storeDiscountAmount; // Discounts the store pays for (its own offers / flash sale)
    private Double commissionAmount; // App's cut (on the amount after store discounts)
    private Double storePayout; // What the driver pays the store in cash

    // ── Breakdown of the numbers above ───────────────────────────────────────
    private Double commissionPercentage; // e.g. 10.0 for 10%
    /** Part of storeDiscountAmount from the store's own offers (store prices). */
    private Double storeOfferDiscountAmount;
    /** Part of storeDiscountAmount from the store's own flash-sale coupon. */
    private Double storeCouponDiscountAmount;

    // ── Coupon used on the order (null when none) ────────────────────────────
    /** Null when no coupon was used, or the coupon was deleted later (its flash sale was removed). */
    private String couponCode;
    private String couponTitle;
    /** PERCENTAGE / FIXED_AMOUNT / FREE_DELIVERY. */
    private String couponDiscountType;
    /**
     * The coupon's value as set up: 10 = 10% for PERCENTAGE, an SYP amount for FIXED_AMOUNT.
     * Null for FREE_DELIVERY, no coupon, or a coupon deleted since.
     */
    private Double couponDiscountValue;
    /** What the customer saved with the coupon on the whole order (customer prices); 0 when no coupon. */
    private Double couponDiscountAmount;
    /** True = this store pays the coupon (its own flash sale); false = the app pays it, or no coupon. */
    private Boolean couponFundedByStore;

    /** The offers applied to this store's items, so lines can show the offer's name. */
    private List<OrderOfferResponse> offers;

    // ONLY the items that belong to this specific store
    private List<OrderItemResponse> items;
}