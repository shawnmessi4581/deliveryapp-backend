package com.deliveryapp.dto.websocket;

import com.deliveryapp.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderWebSocketEvent {
    private String eventType; // CREATED, UPDATED, DELETED
    private Long orderId;
    private Order order; // Could be null for DELETED
}
