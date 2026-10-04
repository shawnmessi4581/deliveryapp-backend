package com.deliveryapp.dto.order;

import lombok.Data;

import java.util.List;

/**
 * Checkout preview returned by POST /api/orders/quote. Same engine and same numbers as
 * POST /api/orders/place, without creating the order.
 */
@Data
public class OrderQuoteResponse {
    private Double subtotal;
    private Double offerDiscountAmount;

    /** What the customer pays for delivery (FREE_DELIVERY coupon already applied). */
    private Double deliveryFee;

    /** Stores whose delivery is free (store free delivery or threshold reached). */
    private List<Long> freeDeliveryStoreIds;

    // Coupon (null / 0 when no coupon is applied)
    private Long couponId;
    private String couponDiscountType;
    private Double discountAmount;

    private Double totalAmount;

    /** Lines in the same order as the request, with any offer applied. */
    private List<OrderItemResponse> items;
}
