package com.deliveryapp.dto.websocket;

import com.deliveryapp.dto.user.DriverLocationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverWebSocketEvent {
    private String eventType; // STATUS_UPDATE, LOCATION_UPDATE
    private Long driverId;
    private DriverLocationResponse driver;
}
