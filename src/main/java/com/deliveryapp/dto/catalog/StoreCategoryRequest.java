package com.deliveryapp.dto.catalog;

import lombok.Data;

@Data
public class StoreCategoryRequest {
    private String name;
    private Long storeId; // Which store this section belongs to
    private Boolean isActive;
    private Integer displayOrder;
}