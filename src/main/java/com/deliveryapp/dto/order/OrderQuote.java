package com.deliveryapp.dto.order;

import com.deliveryapp.entity.Coupon;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Store;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Result of pricing a cart — produced by
 * {@link com.deliveryapp.service.OrderCalculationService#quote}, the single pricing engine used by
 * checkout and by every preview endpoint, so what the customer sees is what they are charged.
 *
 * <p>totalAmount = subtotal − offerDiscountAmount − (item coupon discount) + deliveryFee.
 * For a FREE_DELIVERY coupon the saving is already taken out of {@code deliveryFee}, and
 * {@code couponDiscountAmount} reports how much delivery was waived.
 */
@Data
public class OrderQuote {

    /** Priced lines with offers applied. Not yet attached to an Order. */
    private List<OrderItem> items = new ArrayList<>();

    /** Distinct stores, in cart order. */
    private List<Store> stores = new ArrayList<>();

    /** Sum of line totals at full price. */
    private double subtotal;

    /** Sum of promotional-offer discounts across all lines. */
    private double offerDiscountAmount;

    /** Stores whose delivery is free (store free delivery / threshold reached). */
    private Set<Long> freeDeliveryStoreIds = new LinkedHashSet<>();

    /** What the customer pays for delivery, after store rules and any FREE_DELIVERY coupon. */
    private double deliveryFee;

    /** The validated coupon, or null when no code was given. */
    private Coupon coupon;

    /** How much the coupon saves (items, or waived delivery for FREE_DELIVERY). */
    private double couponDiscountAmount;

    /** Store paying for the coupon discount (vendor flash sale), null = platform. */
    private Long couponFundedByStoreId;

    /** What that store pays for the coupon, at its own prices (null when the platform pays). */
    private Double couponStoreDiscountAmount;

    private double totalAmount;

    /** A valid coupon that saves nothing (e.g. free-delivery code when delivery is already free) is not consumed. */
    public boolean isCouponApplied() {
        return coupon != null && couponDiscountAmount > 0;
    }
}
