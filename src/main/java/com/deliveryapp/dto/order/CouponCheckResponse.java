package com.deliveryapp.dto.order;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CouponCheckResponse {
    private Long couponId;
    private String code;
    private Double discountAmount;
    private String message;
    // NEW: Tell the frontend the type of discount
    private String discountType; // "PERCENTAGE", "FIXED_AMOUNT", "FREE_DELIVERY"
}