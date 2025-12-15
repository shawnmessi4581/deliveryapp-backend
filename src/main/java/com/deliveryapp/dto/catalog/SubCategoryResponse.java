package com.deliveryapp.dto.catalog;


import lombok.Data;

@Data
public class SubCategoryResponse {
    private Long subCategoryId;
    private String name;
    private String imageUrl;        // Added
    private Boolean isActive;       // Added for Admin Toggle
    private Long parentCategoryId;
    private String parentCategoryName; // Added for Admin Table Display
}