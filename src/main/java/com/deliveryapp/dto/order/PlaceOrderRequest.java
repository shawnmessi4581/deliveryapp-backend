package com.deliveryapp.dto.order;

import lombok.Data;

@Data
public class PlaceOrderRequest {
    private Long userId;
    private Long addressId;      // CHANGED: Use ID instead of raw text
    private String instruction;  // NEW: Selected instruction text or ID
    private String couponCode;   // NEW: Optional coupon
}