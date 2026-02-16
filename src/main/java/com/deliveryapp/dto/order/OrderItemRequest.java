package com.deliveryapp.dto.order;

import lombok.Data;

@Data
public class OrderItemRequest {
    private Long productId;
    private Long variantId; // Optional
    private Integer quantity;
    private String notes;
    private String selectedColor; // Optional/Nullable

}