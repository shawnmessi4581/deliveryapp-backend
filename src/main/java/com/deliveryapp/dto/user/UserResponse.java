package com.deliveryapp.dto.user;

import com.deliveryapp.enums.UserType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Long userId;
    private String name;
    private String phoneNumber;
    private String email;
    private String profileImage;
    private UserType userType; // Enum: CUSTOMER, DRIVER, ADMIN

    // Address Info
    private String address;
    private Double latitude;
    private Double longitude;

    // Driver Specific
    private String vehicleType;
    private String vehicleNumber;
    private Boolean isAvailable;
    private Double rating;
    private Integer totalDeliveries;

    // Status
    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
}