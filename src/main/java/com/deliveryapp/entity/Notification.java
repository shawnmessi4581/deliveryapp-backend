package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String title;
    private String message;

    // NEW: Optional Image URL
    private String imageUrl;

    private String type; // e.g., "ORDER_UPDATE", "PROMO"
    private String referenceType; // e.g., "order"
    private Long referenceId;     // e.g., Order ID
    private Boolean isRead;
    private LocalDateTime createdAt;
}