package com.deliveryapp.dto.catalog;

import lombok.Data;

@Data
public class CategoryRequest {
    private String name;
    private Boolean isActive;
    private Integer displayOrder;
}