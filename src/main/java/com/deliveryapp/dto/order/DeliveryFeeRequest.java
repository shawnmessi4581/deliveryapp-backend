package com.deliveryapp.dto.order;

import java.util.List;

import lombok.Data;

@Data
public class DeliveryFeeRequest {
    private List<Long> storeIds; // Changed from single storeId
    private Long addressId;
}