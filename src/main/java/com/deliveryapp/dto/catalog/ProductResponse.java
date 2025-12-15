package com.deliveryapp.dto.catalog;

import lombok.Data;
import java.util.List;

@Data
public class ProductResponse {
    private Long productId;
    private String name;
    private String description;
    private Double basePrice;
    private String imageUrl;
    private boolean isAvailable; // Crucial for toggling availability

    // Relationships (IDs and Names for Admin Display)
    private Long storeId;
    private String storeName;

    private Long categoryId;
    private String categoryName;

    private Long subCategoryId;
    private String subCategoryName;

    private List<ProductVariantResponse> variants;
}