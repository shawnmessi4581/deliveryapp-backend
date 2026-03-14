package com.deliveryapp.dto.catalog;

import lombok.Data;

@Data
public class SubCategoryRequest {
    private String name;
    private Long categoryId;
    private Boolean isActive;
    private Integer displayOrder;
}