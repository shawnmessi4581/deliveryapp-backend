package com.deliveryapp.dto.order;

import lombok.Data;

@Data
public class OrderItemResponse {
    private String productName;
    private String variantDetails;
    private Integer quantity;
    private Double unitPrice;
    private Double totalPrice;
    private String notes;
}