package com.deliveryapp.service;

import com.deliveryapp.entity.Notification;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.NotificationRepository;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService; // Inject FCM Service

    // --- GETTERS ---
    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByUserUserIdOrderByCreatedAtDesc(userId);
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

    // --- SEND LOGIC (Call this from OrderService, etc.) ---
    @Transactional
    public void sendNotification(Long userId, String title, String message, String imageUrl, String type, Long referenceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. Save to Database (So user can see it in 'Inbox' later)
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setImageUrl(imageUrl);
        notification.setType(type);
        notification.setReferenceType(type); // Usually same as type or generalized
        notification.setReferenceId(referenceId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);

        // 2. Send Push Notification via Firebase
        fcmService.sendToToken(
                user.getFcmToken(), // Ensure User entity has getFcmToken()
                title,
                message,
                imageUrl,
                type,
                String.valueOf(referenceId)
        );
    }
    // NEW: Send to Topic (No DB save needed usually, or save as system log)
    @Transactional
    public void sendGlobalNotification(String topic, String title, String message, String imageUrl, String type, Long referenceId) {
        fcmService.sendToTopic(
                topic,
                title,
                message,
                imageUrl,
                type,
                String.valueOf(referenceId)
        );
    }
}