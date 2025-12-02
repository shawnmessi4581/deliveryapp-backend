package com.deliveryapp.dto.catalog;


import lombok.Data;
import java.util.List;

@Data
public class ProductResponse {
    private Long productId;
    private String name;
    private String description;
    private Double basePrice;
    private String imageUrl;
    private boolean isAvailable;
    private Long storeId;
    private List<ProductVariantResponse> variants;
}