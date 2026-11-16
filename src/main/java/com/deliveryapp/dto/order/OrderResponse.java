package com.deliveryapp.dto.order;

import com.deliveryapp.dto.catalog.StoreResponse; // Import the Store DTO
import com.deliveryapp.dto.report.ProfitBreakdownResponse;
import com.deliveryapp.enums.DriverOrderStatus;
import com.deliveryapp.enums.OrderStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {
    private Long orderId;
    private String orderNumber;

    private List<StoreResponse> stores;

    // Existing Store Info (Kept as requested)
    private Long storeId;
    private String storeName;

    // Driver Info (can be null if not yet assigned)
    private Long driverId;
    private String driverName;
    private String driverPhone;
    private DriverOrderStatus driverOrderStatus;

    // Delivery Info
    private String deliveryAddress;
    private Double deliveryLatitude;
    private Double deliveryLongitude;
    // 🟢 NEW: Add Instructions and Notes
    private String selectedInstruction; // e.g. "Leave at door"
    private String orderNote; // e.g. "Call me when you arrive"

    // Status & Dates
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;

    // Financials
    private Double subtotal;
    private Double deliveryFee;
    private Double totalAmount;

    // Route debugging details for admin dashboard
    private Double maxMinimumDeliveryFee;
    private Double totalDistanceKm;
    private List<DeliveryFeeResponse.RouteSegmentResponse> routeSegments;

    // Items
    private List<OrderItemResponse> items;

    // Customer details
    private OrderCustomerResponse customerDetails;

    // FIELDS FOR COUPONS
    private Double discountAmount; // How much was saved via coupon
    private Long couponId; // Which coupon was used (optional)

    /** Null when no coupon was used, or the coupon was deleted later (e.g. its flash sale was removed). */
    private String couponCode;
    private String couponTitle;
    /** PERCENTAGE / FIXED_AMOUNT / FREE_DELIVERY. */
    private String couponDiscountType;
    /** 10 = 10% for PERCENTAGE, an SYP amount for FIXED_AMOUNT; null for FREE_DELIVERY or no coupon. */
    private Double couponDiscountValue;

    // Who pays the coupon — staff and driver only (null in customer responses)
    /** True = a store pays it (taken from its payout); false = the app pays it, or no coupon. */
    private Boolean couponFundedByStore;
    private Long couponFundedByStoreId;
    private String couponFundedByStoreName;
    /** What the paying store pays, at its own prices (0 when the app pays). The rest of discountAmount is the app's. */
    private Double couponStoreDiscountAmount;

    /** The offers applied in this order, with their store; items point to them through appliedOfferId. */
    private List<OrderOfferResponse> offers;

    // FIELDS FOR PROMOTIONAL OFFERS
    /** Total SYP saved through promotional offers (e.g. Buy 1 Get 1 Free). */
    private Double offerDiscountAmount;

    /**
     * What each store is owed (store-paid discounts and commission already applied).
     * Drivers/admins should use this instead of recomputing from item totals.
     */
    private List<StorePayoutResponse> storePayouts;

    /** The platform's profit on this order — admin only (null for everyone else). */
    private ProfitBreakdownResponse profit;

}