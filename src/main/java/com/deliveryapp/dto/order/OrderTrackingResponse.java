package com.deliveryapp.dto.order;


import com.deliveryapp.enums.OrderStatus;
import lombok.Data;


@Data
public class OrderTrackingResponse {
    private Long orderId;
    private OrderStatus status;
    private String estimatedTime;

    // Driver Info (Current Location)
    private Long driverId;
    private String driverName;
    private String driverPhone;
    private String driverImage;
    private String driverVehicle;
    private Double driverLatitude;
    private Double driverLongitude;

    // --- User/Destination Info (Static) ---
    private String deliveryAddress;   // e.g. "123 Main St, Apt 4"
    private Double deliveryLatitude;  // e.g. 30.0444
    private Double deliveryLongitude; // e.g. 31.2357
}