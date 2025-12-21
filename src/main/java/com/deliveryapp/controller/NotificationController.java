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

    @PostMapping("/send")
    public ResponseEntity<String> sendNotification(@RequestBody SendNotificationRequest request) {

        // CASE 1: Send to Specific User
        if (request.getUserId() != null) {
            notificationService.sendNotification(
                    request.getUserId(),
                    request.getTitle(),
                    request.getMessage(),
                    request.getImageUrl(),
                    request.getType(),
                    request.getReferenceId()
            );
            return ResponseEntity.ok("Sent to User " + request.getUserId());
        }

        // CASE 2: Send to Topic
        else if (request.getTopic() != null && !request.getTopic().isEmpty()) {
            notificationService.sendGlobalNotification(
                    request.getTopic(),
                    request.getTitle(),
                    request.getMessage(),
                    request.getImageUrl(),
                    request.getType(),
                    request.getReferenceId()
            );
            return ResponseEntity.ok("Sent to Topic: " + request.getTopic());
        }

        return ResponseEntity.badRequest().body("Please provide either userId or topic");
    }

    // Updated DTO
    @Data
    public static class SendNotificationRequest {
        private Long userId;     // Optional (For single user)
        private String topic;    // Optional (For group, e.g., "all_users")
        private String title;
        private String message;
        private String imageUrl;
        private String type;
        private Long referenceId;
    }
}