package com.deliveryapp.dto.order;

import com.deliveryapp.entity.Color;

import lombok.Data;

@Data
public class OrderItemResponse {
    private String productName;
    private String variantDetails;
    private Integer quantity;
    private Double unitPrice;
    private Double totalPrice;
    private String notes;
    private Color selectedColor;
}