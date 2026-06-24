package com.deliveryapp.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverWebSocketEvent {
    private String eventType; // LOCATION_UPDATE, STATUS_UPDATE
    private Long driverId;
    private Object payload;
}
