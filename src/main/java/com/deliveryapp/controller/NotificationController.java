package com.deliveryapp.controller;

import com.deliveryapp.dto.notification.NotificationRequest;
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
        return ResponseEntity.ok("تم تمييز الإشعار كمقروء");
    }

    // 4. Send Notification (Updated for Multipart/File Upload & Target Group)
    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> sendNotification(
            @ModelAttribute NotificationRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        if (request.getUserId() != null) {
            // Send to Specific User
            notificationService.sendNotification(
                    request.getUserId(),
                    request.getTitle(),
                    request.getMessage(),
                    image,
                    request.getType(),
                    request.getReferenceType(),
                    request.getReferenceId(),
                    request.getExternalUrl());
            return ResponseEntity.ok("تم الإرسال للمستخدم " + request.getUserId());
        } else if (request.getTargetGroup() != null && !request.getTargetGroup().isEmpty()) {
            // Send to Group (e.g. "CUSTOMER", "DRIVER", "all_users")
            // This loops through all users in that group, saves to DB, and sends Multicast
            notificationService.sendGroupNotification(
                    request.getTargetGroup(),
                    request.getTitle(),
                    request.getMessage(),
                    image,
                    request.getType(),
                    request.getReferenceType(),
                    request.getReferenceId(),
                    request.getExternalUrl());
            return ResponseEntity.ok("تم الإرسال للمجموعة المستهدفة: " + request.getTargetGroup());
        }

        return ResponseEntity.badRequest().body("يرجى توفير إما userId أو targetGroup");
    }
}