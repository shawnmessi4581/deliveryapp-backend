package com.deliveryapp.dto.offer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A reward the cart has earned but the customer hasn't added yet — returned by the checkout quote so
 * the app can ask the customer to choose it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableRewardResponse {
    private Long offerId;
    private String offerTitle;
    private String summary;
    /** How many reward units the customer can still add. */
    private Integer rewardQuantity;
    /** Products the customer can choose the reward from (details: GET /api/offers/{offerId}). */
    private List<Long> rewardProductIds;
}
