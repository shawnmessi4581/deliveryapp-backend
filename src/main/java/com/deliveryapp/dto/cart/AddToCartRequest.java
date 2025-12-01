package com.deliveryapp.dto.cart;

import lombok.Data;

@Data
public class AddToCartRequest {
    private Long userId; // In a secured app, this would come from the JWT token
    private Long productId;
    private Long variantId; // Can be null
    private Integer quantity;
    private String notes;
}