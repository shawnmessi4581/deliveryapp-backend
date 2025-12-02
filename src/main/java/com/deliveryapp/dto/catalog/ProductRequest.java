package com.deliveryapp.dto.catalog;


import lombok.Data;

@Data
public class ProductRequest {
    private String name;
    private String description;
    private Double basePrice;
    private Long storeId;
    private Long categoryId;
    private Long subCategoryId; // Optional
    private Boolean isAvailable;
}