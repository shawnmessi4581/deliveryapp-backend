package com.deliveryapp.dto.user;

import lombok.Data;

@Data
public class CreateDriverRequest {
    private String name;
    private String phoneNumber; // Used for Login
    private String password;    // Initial Password
    private String email;

    // Driver Specifics
    private String vehicleType;   // e.g., "Bike", "Car", "Scooter"
    private String vehicleNumber; // License Plate
}