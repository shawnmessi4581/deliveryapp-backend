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
}