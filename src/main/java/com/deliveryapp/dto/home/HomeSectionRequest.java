package com.deliveryapp.dto.home;

import lombok.Data;

/**
 * Admin edit of a home section, sent as a multipart form (the icon PNG goes in the "icon" part).
 * Fields left out keep their current value.
 */
@Data
public class HomeSectionRequest {

    private String title;
    private Integer displayOrder;
    private Boolean isActive;

    /** Items shown on the home, 1–50. Only for sections with "show all"; ignored for the others. */
    private Integer itemLimit;

    /** Under-price section only (ignored for the others): the most a product may cost the customer, in SYP. */
    private Double maxPrice;

    /** True removes the current icon. Ignored when a new icon is uploaded. */
    private Boolean removeIcon;
}
