package com.deliveryapp.dto.auth;

import lombok.Data;

@Data
public class VerifyAccountRequest {
    private String phoneNumber;
    private String otp;
}