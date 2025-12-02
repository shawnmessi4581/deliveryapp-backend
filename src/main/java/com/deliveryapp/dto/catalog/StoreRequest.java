package com.deliveryapp.dto.catalog;

import lombok.Data;

@Data
public class StoreRequest {
    private String name;
    private String description;
    private String phone;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double deliveryFeeKM;
    private Double minimumOrder;
    private String estimatedDeliveryTime; // e.g., "30-45 min"
    private Long categoryId;
    private Long subCategoryId;
}