package com.deliveryapp.dto.catalog;

import lombok.Data;
import java.util.List;

@Data
public class ProductResponse {
    private Long productId;
    private String name;
    private String description;
    private String imageUrl;
    private boolean isAvailable;
    private Boolean isTrending;
    private Integer displayOrder;

    // --- PRICING ---
    private Double calculatedPrice; // Always the final price in SYP (Offer or Regular)

    // 🟢 NEW: Allow frontend to see USD data
    private Boolean isUsd; // True if the product is priced in USD
    private Double usdPrice; // The raw USD price (e.g., 5.0)

    // --- OFFERS ---
    private Boolean hasOffer;
    private Double originalPrice; // Original SYP price for strikethrough
    private Integer discountPercentage;

    // 🟢 NEW: Original USD price for strikethrough (if you want to show it)
    private Double originalUsdPrice;

    // --- RELATIONSHIPS ---
    private StoreResponse store;
    private Long categoryId;
    private String categoryName;
    private Long subCategoryId;
    private String subCategoryName;
    private Long storeCategoryId;
    private String storeCategoryName;

    private List<String> images;
    private List<ColorResponse> colors;
    private List<ProductVariantResponse> variants;
}