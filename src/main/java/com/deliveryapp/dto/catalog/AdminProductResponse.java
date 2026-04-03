package com.deliveryapp.dto.catalog;

import lombok.Data;

@Data
public class AdminProductResponse extends ProductResponse {
    // Inherits all public fields (like name, images, calculatedPrice)

    // --- ADD THE RAW DATA ONLY ADMINS SHOULD SEE ---
    private Double basePrice;
    private Double usdPrice;
    private Boolean isUsd;
}