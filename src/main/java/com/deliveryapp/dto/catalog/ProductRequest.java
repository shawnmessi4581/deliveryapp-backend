package com.deliveryapp.dto.catalog;

import java.util.List;

import lombok.Data;

@Data
public class ProductRequest {
    private String name;
    private String description;
    private Long storeId;
    private Long categoryId;
    private Long subCategoryId; // Optional
    private Boolean isAvailable;
    private List<Long> colorIds; // e.g. [1, 5, 8]
    private Boolean isTrending;
    private Integer displayOrder;
    private Double basePrice; // SYP
    private Double usdPrice; // USD
    private Boolean isUsd;
    private Long storeCategoryId;
    //
    private Boolean hasOffer;
    private Double offerBasePrice;
    private Double offerUsdPrice;
}