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

    // --- SECURITY RULE: ONLY EXPOSE THE CALCULATED SYP PRICE ---
    private Double calculatedPrice; // e.g. 135000.0 (No raw USD or Base prices exposed)

    private StoreResponse store;
    private Long categoryId;
    private String categoryName;
    private Long subCategoryId;
    private String subCategoryName;
    private List<String> images;
    private List<ColorResponse> colors;
    private List<ProductVariantResponse> variants;
    private Long storeCategoryId;
    private String storeCategoryName;
    // 🟢 NEW: For the Frontend UI
    private Boolean hasOffer;
    private Double originalPrice; // The crossed-out price (e.g., 5000)
    private Integer discountPercentage; // E.g., 20 (for a "20% OFF" badge)
}