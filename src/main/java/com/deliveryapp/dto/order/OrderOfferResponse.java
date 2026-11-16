package com.deliveryapp.dto.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An offer applied to an order's lines; items point to it through appliedOfferId (or rewardOfferId).
 * Used in the vendor's order (its own lines) and the admin/staff order (all lines).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderOfferResponse {

    private Long offerId;

    /** Null when the offer was deleted after the order. */
    private String title;

    /** True = the store pays this offer's discount; false = the app pays it. Null in customer responses. */
    private Boolean fundedByStore;

    /** The store whose products carry the offer. */
    private Long storeId;
    private String storeName;
}
