package com.deliveryapp.dto.user;

import com.deliveryapp.enums.UserType;
import lombok.Data;

@Data
public class UserResponse {
    private Long userId;
    private String name;
    private String email;
    private String phoneNumber;
    private UserType userType;
    private String profileImage;
    private Boolean isActive;

    // Driver Specifics
    private String vehicleType;
    private String vehicleNumber;
    private Boolean isAvailable;
    private Double rating;           // <--- Check this
    private Integer totalDeliveries; // <--- Check this
    private Double currentLocationLat;
    private Double currentLocationLng;

    // Address Info (Optional for driver list context, but part of user model)
    private String address;
}