package com.deliveryapp.dto.notification;

import lombok.Data;

@Data
public class NotificationRequest {
    private Long userId; // Optional: Send to specific user
    private String targetGroup; // Optional: Send to group (e.g., CUSTOMER, all_users)
    private String title;
    private String message;
    private String type; // e.g., PROMO, GENERAL
    private String referenceType; // e.g., store, product, order
    private Long referenceId; // e.g., 5 (ID of the store/product)
    private String externalUrl; // e.g., https://google.com
}