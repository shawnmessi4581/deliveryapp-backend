package com.deliveryapp.dto.auth;

import lombok.Data;

@Data
public class ResendOtpRequest {
    private String phoneNumber;
}