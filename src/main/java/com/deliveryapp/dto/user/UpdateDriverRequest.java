package com.deliveryapp.dto.user;

import lombok.Data;

@Data
public class UpdateDriverRequest {
    private String name;
    private String phoneNumber;
    private String email;

    // Driver Specifics
    private String vehicleType;
    private String vehicleNumber;

    // Admin Controls
    private Boolean isActive;

    // Note: We don't include password here. If admin wants to change password,
    // it should be a separate "Reset Password" flow for security.
}