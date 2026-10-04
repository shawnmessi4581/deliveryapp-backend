package com.deliveryapp.dto.order;

import java.util.List;

import lombok.Data;

@Data
public class DeliveryFeeRequest {
    private List<Long> storeIds; // Changed from single storeId
    private Long addressId;

    // Optional: the cart lines. When sent, stores are taken from the items and each store's
    // free-delivery threshold is checked against its own subtotal (after offers).
    private List<OrderItemRequest> items;
}
