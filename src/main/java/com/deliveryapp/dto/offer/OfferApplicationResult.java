package com.deliveryapp.dto.offer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Carries the result of applying a single promotional offer to an order line-item.
 * Produced by {@link com.deliveryapp.service.PromotionalOfferService#applyOfferToItem}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OfferApplicationResult {

    /** ID of the offer that was applied. */
    private Long offerId;

    /** Title of the applied offer (for display / receipts). */
    private String offerTitle;

    /**
     * Total SYP discount amount awarded for this line-item.
     * 0.0 if no offer applied.
     */
    private Double discountAmount;

    /**
     * Human-readable description of what the customer is getting.
     * E.g. "احصل على وحدة مجانية" or "خصم 50% على الوحدة الثانية".
     */
    private String rewardDescription;

    /** Convenience factory: no offer applied. */
    public static OfferApplicationResult none() {
        return new OfferApplicationResult(null, null, 0.0, null);
    }
}
