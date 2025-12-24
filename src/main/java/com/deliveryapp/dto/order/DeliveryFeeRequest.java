package com.deliveryapp.dto.order;

import lombok.Data;

@Data
public class DeliveryFeeRequest {
    private Long storeId;
    private Long addressId;
}