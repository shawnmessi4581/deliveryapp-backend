package com.deliveryapp.dto.auth;



import lombok.Data;

@Data
public class SignupRequest {
    private String name; // Matches User entity 'name'
    private String phoneNumber;
    private String email; // Optional?
    private String password;
}