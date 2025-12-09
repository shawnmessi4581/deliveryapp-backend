package com.deliveryapp.dto.user;


import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String phoneNumber;
    private String otp; // The code the user received
    private String newPassword;
}