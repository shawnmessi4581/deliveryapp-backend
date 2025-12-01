package com.deliveryapp.dto.cart;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CartItemResponse {
    private Long cartItemId;
    private Long productId;
    private String productName;
    private String variantName; // e.g., "Large, Spicy"
    private Integer quantity;
    private Double unitPrice;
    private Double totalPrice;
    private String notes;
}