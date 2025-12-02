package com.deliveryapp.dto.catalog;


import lombok.Data;

@Data
public class StoreResponse {
    private Long storeId;
    private String name;
    private String description;

    // Images
    private String logo;
    private String coverImage;

    // Contact & Location
    private String address;
    private Double latitude;
    private Double longitude;

    // Stats & Info
    private Double rating;
    private Integer totalOrders;
    private String estimatedDeliveryTime;

    // Financials
    private Double deliveryFeeKM;
    private Double minimumOrder;

    // NEW: The calculated fee based on user location
    private Double predictedDeliveryFee;
}