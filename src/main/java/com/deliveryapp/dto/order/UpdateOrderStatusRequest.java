package com.deliveryapp.dto.order;

import com.deliveryapp.enums.OrderStatus;
import lombok.Data;

@Data
public class UpdateOrderStatusRequest {
    private Long userId; // The user performing the action (Admin/Driver)
    private OrderStatus newStatus;
}
