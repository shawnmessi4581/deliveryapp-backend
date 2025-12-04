package com.deliveryapp.dto.user;

import lombok.Data;

@Data
public class AddressRequest {
    private String label;
    private String addressLine;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;
}