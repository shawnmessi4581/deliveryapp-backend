package com.deliveryapp.dto.catalog;

import lombok.Data;

@Data
public class CategoryResponse {
    private Long categoryId;
    private String name;
    private String imageUrl;
    private boolean isActive;
}