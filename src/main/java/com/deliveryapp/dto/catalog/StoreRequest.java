package com.deliveryapp.dto.catalog;

import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;

@Data
public class StoreRequest {
    private String name;
    private String description;
    private String phone;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double deliveryFeeKM;
    private Double minimumOrder;
    private String estimatedDeliveryTime; // e.g., "30-45 min"
    private Long categoryId;
    private Long subCategoryId;
    private Boolean isBusy;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime openingTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime closingTime;
}