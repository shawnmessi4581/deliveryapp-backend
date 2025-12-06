package com.deliveryapp.dto.auth;



import com.deliveryapp.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private UserResponse user; // The full user details object
}