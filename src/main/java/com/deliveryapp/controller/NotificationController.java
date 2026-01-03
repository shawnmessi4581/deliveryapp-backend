package com.deliveryapp.controller;

import com.deliveryapp.dto.notification.NotificationResponse;
import com.deliveryapp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 1. Get My Notifications
    @GetMapping("/{userId}")
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(@PathVariable Long userId) {
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

    // 4. Send Notification (Updated for Multipart/File Upload)
    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> sendNotification(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String topic,
            @RequestParam String title,
            @RequestParam String message,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long referenceId,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        if (userId != null) {
            // Send to Specific User
            notificationService.sendNotification(
                    userId, title, message, image, type, referenceId
            );
            return ResponseEntity.ok("Sent to User " + userId);
        }
        else if (topic != null && !topic.isEmpty()) {
            // Send to Topic
            notificationService.sendGlobalNotification(
                    topic, title, message, image, type, referenceId
            );
            return ResponseEntity.ok("Sent to Topic: " + topic);
        }

        return ResponseEntity.badRequest().body("Please provide either userId or topic");
    }
}