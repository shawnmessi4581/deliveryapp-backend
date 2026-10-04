package com.deliveryapp.dto.order;

import lombok.Data;

/**
 * What one store is owed for an order. Computed server-side by
 * {@link com.deliveryapp.service.StorePayoutService} so Telegram, the vendor app and the
 * driver/admin apps all show the same numbers.
 *
 * <p>storePayout = (itemsSubtotal − storeDiscountAmount) − commissionAmount
 */
@Data
public class StorePayoutResponse {
    private Long storeId;
    private String storeName;

    /** The store's items at its own full price (before the admin's markup). */
    private Double itemsSubtotal;

    /** The same items at the price the customer paid (markup included, before discounts). */
    private Double customerItemsSubtotal;

    /** Discounts the store pays for (its own offers and flash-sale coupon), at its own prices. */
    private Double storeDiscountAmount;

    private Double commissionPercentage;

    /** Commission on the amount after store-paid discounts. */
    private Double commissionAmount;

    /** What the driver hands the store (rounded up to a whole SYP). */
    private Double storePayout;
}
