package com.deliveryapp.service;

import com.deliveryapp.dto.notification.NotificationResponse;
import com.deliveryapp.entity.Notification;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.NotificationRepository;
import com.deliveryapp.repository.UserRepository;
import com.deliveryapp.util.UrlUtil; // 1. Import UrlUtil
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;
    private final UrlUtil urlUtil; // 2. Inject UrlUtil

    // --- GETTERS (Updated to return DTOs with Full URLs) ---
    public List<NotificationResponse> getUserNotifications(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserUserIdOrderByCreatedAtDesc(userId);

        return notifications.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    // --- SEND LOGIC ---
    @Transactional
    public void sendNotification(Long userId, String title, String message, String imageUrl, String type, Long referenceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. Save to Database (We store the relative path to keep DB clean)
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setImageUrl(imageUrl); // e.g., "/uploads/..."
        notification.setType(type);
        notification.setReferenceType(type);
        notification.setReferenceId(referenceId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);

        // 2. Send Push Notification via Firebase
        // CRITICAL: We must convert to FULL URL here, otherwise FCM/Phone won't load it
        String fullImageUrl = urlUtil.getFullUrl(imageUrl);

        fcmService.sendToToken(
                user.getFcmToken(),
                title,
                message,
                fullImageUrl, // Send Full URL to Firebase
                type,
                String.valueOf(referenceId)
        );
    }

    @Transactional
    public void sendGlobalNotification(String topic, String title, String message, String imageUrl, String type, Long referenceId) {
        // Convert to Full URL for Topic messages too
        String fullImageUrl = urlUtil.getFullUrl(imageUrl);

        fcmService.sendToTopic(
                topic,
                title,
                message,
                fullImageUrl, // Send Full URL
                type,
                String.valueOf(referenceId)
        );
    }

    // --- MAPPER HELPER ---
    private NotificationResponse mapToResponse(Notification notification) {
        NotificationResponse dto = new NotificationResponse();
        dto.setNotificationId(notification.getNotificationId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());

        // Convert DB Relative path to Full URL for API Response
        dto.setImageUrl(urlUtil.getFullUrl(notification.getImageUrl()));

        dto.setType(notification.getType());
        dto.setReferenceType(notification.getReferenceType());
        dto.setReferenceId(notification.getReferenceId());
        dto.setIsRead(notification.getIsRead());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }
}