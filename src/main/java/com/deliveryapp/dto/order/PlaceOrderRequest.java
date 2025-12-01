package com.deliveryapp.dto.order;

import lombok.Data;

@Data
public class PlaceOrderRequest {
    private Long userId;
    private String deliveryAddress;
    private Double deliveryLatitude;
    private Double deliveryLongitude;
    private String notes;
}