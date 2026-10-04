package com.deliveryapp.dto.order;

import lombok.Data;
import java.util.List;

@Data
public class CouponCheckRequest {
    private String code;
    private Long userId;
    private Long storeId; // No longer needed: coupon scope is checked against the items' stores
    private List<OrderItemRequest> items; // We need items to calculate the subtotal

    // Optional: lets FREE_DELIVERY coupons report the delivery amount they waive
    private Long addressId;
}
