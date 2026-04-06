package com.deliveryapp.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DriverLocationResponse {
    private Long driverId;
    private Double latitude;
    private Double longitude;
    private Boolean isAvailable;
    private String vehicleType;
    private String vehicleNumber;
}