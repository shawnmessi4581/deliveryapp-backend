package com.deliveryapp.dto.user;

import com.deliveryapp.entity.UserAddress;
import lombok.Data;
import java.util.List;

@Data
public class UserProfileResponse {
    private Long userId;
    private String name;
    private String email;
    private String phoneNumber;
    private String profileImage; // Full URL

    // Primary Profile Address (Stored on User table)
    private String primaryAddress;

    // Saved Address List (Home, Work, etc.)
    private List<UserAddress> savedAddresses;

    // Driver Specifics (Optional)
    private String userType;
    private String vehicleType;
    private Double rating;
}