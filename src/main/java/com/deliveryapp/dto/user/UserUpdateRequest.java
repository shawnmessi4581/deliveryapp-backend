package com.deliveryapp.dto.user;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String name;
    private String email;

    // Address Fields
    private String address;
    private Double latitude;
    private Double longitude;
}