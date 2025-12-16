package com.deliveryapp.dto.order;


import lombok.Data;

@Data
public class OrderCustomerResponse {
    private Long userId;
    private String name;
    private String phoneNumber;
    private String profileAddress; // The user's saved address (might be different from delivery)
}