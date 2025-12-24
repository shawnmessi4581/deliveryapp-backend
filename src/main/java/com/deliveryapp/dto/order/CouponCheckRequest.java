package com.deliveryapp.dto.order;

import lombok.Data;
import java.util.List;

@Data
public class CouponCheckRequest {
    private String code;
    private Long userId;
    private Long storeId;
    private List<OrderItemRequest> items; // We need items to calculate the subtotal
}