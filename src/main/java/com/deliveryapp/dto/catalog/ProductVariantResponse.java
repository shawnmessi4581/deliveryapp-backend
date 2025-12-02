package com.deliveryapp.dto.catalog;


import lombok.Data;

@Data
public class ProductVariantResponse {
    private Long variantId;
    private String variantName; // e.g., "Large", "Extra Cheese"
    private Double priceAdjustment;
}