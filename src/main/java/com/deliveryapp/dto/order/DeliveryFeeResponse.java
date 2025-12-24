package com.deliveryapp.dto.order;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeliveryFeeResponse {
    private Double deliveryFee;
    private String estimatedTime;
}