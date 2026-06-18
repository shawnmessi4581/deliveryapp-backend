package com.deliveryapp.dto.websocket;

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
    private Object order; // Could be null for DELETED
}
