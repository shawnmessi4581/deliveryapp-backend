package com.deliveryapp.dto.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An offer applied to a vendor's order lines; items point to it through appliedOfferId. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendorOrderOfferResponse {

    private Long offerId;

    /** Null when the offer was deleted after the order. */
    private String title;

    /** True = the store pays this offer's discount; false = the app pays it. */
    private Boolean fundedByStore;
}
