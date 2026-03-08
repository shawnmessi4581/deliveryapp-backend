package com.deliveryapp.dto.user;

import lombok.Data;

@Data
public class AddressResponse {
    private Long addressId;
    private String addressLine;
    private String label;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;
}