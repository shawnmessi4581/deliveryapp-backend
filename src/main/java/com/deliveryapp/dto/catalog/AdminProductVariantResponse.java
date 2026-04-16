package com.deliveryapp.dto.catalog;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminProductVariantResponse extends ProductVariantResponse {
    // This is the RAW price stored in the DB (can be USD or SYP)
    // The Admin needs to see this!
    private Double priceAdjustment;
}