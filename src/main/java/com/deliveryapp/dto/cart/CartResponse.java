package com.deliveryapp.dto.cart;

import lombok.Data;
import java.util.List;

@Data
public class CartResponse {
    private Long cartId;
    private Long storeId;
    private String storeName;
    private Double totalEstimatedPrice;
    private List<CartItemResponse> items;
}