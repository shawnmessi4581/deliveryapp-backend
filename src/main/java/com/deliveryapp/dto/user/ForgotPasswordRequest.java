package com.deliveryapp.dto.user;


import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String phoneNumber;
}