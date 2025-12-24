package com.deliveryapp.dto.order;


import lombok.Data;
import java.util.List;

@Data
public class PlaceOrderRequest {
    private Long userId;
    private Long addressId;
    private String instruction;
    private String couponCode;

    // NEW: The Frontend sends the cart items here
    private List<OrderItemRequest> items;
}