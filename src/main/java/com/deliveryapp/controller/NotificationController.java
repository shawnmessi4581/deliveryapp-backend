package com.deliveryapp.controller;

import com.deliveryapp.entity.Notification;
import com.deliveryapp.service.NotificationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 1. Get My Notifications
    @GetMapping("/{userId}")
    public ResponseEntity<List<Notification>> getUserNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId));
    }

    // 2. Get Unread Count
    @GetMapping("/{userId}/unread")
    public ResponseEntity<Long> getUnreadCount(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    // 3. Mark as Read
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<String> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok("Notification marked as read");
    }

    // 4. Send Notification (For Admin Dashboard or Testing)
    @PostMapping("/send")
    public ResponseEntity<String> sendNotification(@RequestBody SendNotificationRequest request) {
        notificationService.sendNotification(
                request.getUserId(),
                request.getTitle(),
                request.getMessage(),
                request.getImageUrl(),
                request.getType(),
                request.getReferenceId()
        );
        return ResponseEntity.ok("Notification sent successfully");
    }

    // Internal DTO for the Send Endpoint
    @Data
    public static class SendNotificationRequest {
        private Long userId;
        private String title;
        private String message;
        private String imageUrl; // Optional
        private String type;     // e.g. "ORDER", "PROMO"
        private Long referenceId;
    }
}