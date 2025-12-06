package com.deliveryapp.dto.catalog;


import lombok.Data;

@Data
public class SubCategoryResponse {
    private Long subCategoryId;
    private String name;
    private Long parentCategoryId;
    private String icon;
    private Integer displayOrder;
}