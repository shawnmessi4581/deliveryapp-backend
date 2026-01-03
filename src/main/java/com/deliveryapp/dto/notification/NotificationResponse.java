package com.deliveryapp.dto.notification;


import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Long notificationId;
    private String title;
    private String message;
    private String imageUrl; // This will contain the Full URL
    private String type;
    private String referenceType;
    private Long referenceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}