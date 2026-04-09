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

    // --- STORE INFO FOR DRIVER PAYOUT CALCULATIONS ---
    private Long storeId;
    private String storeName;
    private Double storeCommissionPercentage; // e.g., 10.0 for 10%
}