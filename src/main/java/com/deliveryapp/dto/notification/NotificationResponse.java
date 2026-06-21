package com.deliveryapp.dto.notification;

import com.deliveryapp.dto.catalog.ProductResponse;
import com.deliveryapp.dto.catalog.StoreResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Long notificationId;
    private String title;
    private String message;
    private String imageUrl;
    private String type; // e.g., "PROMO", "ORDER_UPDATE"

    // --- LINKING FIELDS ---
    private String referenceType;
    private Long referenceId;
    private String externalUrl;

    // --- RICH DATA (Like Banners) ---
    private StoreResponse store; // Populated if referenceType == "store"
    private ProductResponse product; // Populated if referenceType == "product"

    private Boolean isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}