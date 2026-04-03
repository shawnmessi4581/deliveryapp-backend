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
}