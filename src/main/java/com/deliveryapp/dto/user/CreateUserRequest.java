package com.deliveryapp.dto.user;

import com.deliveryapp.enums.UserType;
import lombok.Data;

@Data
public class CreateUserRequest {
    private String name;
    private String phoneNumber;
    private String email;
    private String password;
    private UserType role; // ADMIN or EMPLOYEE
}