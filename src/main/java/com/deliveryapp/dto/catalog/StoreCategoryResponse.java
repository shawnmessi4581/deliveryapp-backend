package com.deliveryapp.dto.catalog;

import lombok.Data;

@Data
public class StoreCategoryResponse {
    private Long storeCategoryId;
    private Long storeId;
    private String name;
    private Boolean isActive;
    private Integer displayOrder;
}