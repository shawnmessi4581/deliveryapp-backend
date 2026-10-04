package com.deliveryapp.dto.order;

import com.deliveryapp.entity.Color;
import lombok.Data;

@Data
public class OrderItemResponse {
    private Long productId;
    private Long variantId;
    private String productName;
    private String variantDetails;
    private Integer quantity;
    private Double unitPrice;  // customer price (markup included)
    private Double totalPrice;
    private String notes;

    // 💹 Store's own price — admin, driver & vendor only (null in customer responses)
    private Double storeUnitPrice;
    private Double storeTotalPrice;
    private Color selectedColor;

    // --- STORE INFO FOR DRIVER PAYOUT CALCULATIONS ---
    private Long storeId;
    private String storeName;
    private Double storeCommissionPercentage; // e.g., 10.0 for 10%

    // --- PROMOTIONAL OFFER INFO ---
    /** ID of the applied promotional offer, or null if none. */
    private Long appliedOfferId;

    /** Total SYP discount awarded by the promotional offer for this line-item. */
    private Double offerDiscountAmount;

    /** True when the store (not the platform) pays this offer discount. */
    private Boolean offerFundedByStore;

    /** The offer discount at the store's own price (what the store pays when offerFundedByStore). */
    private Double storeOfferDiscountAmount;
}
