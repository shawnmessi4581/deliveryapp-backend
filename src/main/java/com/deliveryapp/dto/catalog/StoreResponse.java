package com.deliveryapp.dto.catalog;

import lombok.Data;

@Data
public class StoreResponse {
    private Long storeId;
    private String name;
    private String description;

    // Images
    private String logo;
    private String coverImage;

    // Contact & Location
    private String phone;       // Added
    private String address;
    private Double latitude;
    private Double longitude;

    // Stats & Info
    private Boolean isActive;   // Added (Crucial for Admin)
    private Double rating;
    private Integer totalOrders;
    private String estimatedDeliveryTime;

    // Financials
    private Double deliveryFeeKM;
    private Double minimumOrder;

    // Relationships (Added for Admin Forms)
    private Long categoryId;
    private String categoryName;
    private Long subCategoryId;
    private String subCategoryName;

    // User Context (Calculated fee)
    private Double predictedDeliveryFee;
}