package com.deliveryapp.service;

import com.deliveryapp.dto.notification.NotificationResponse;
import com.deliveryapp.entity.Notification;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.NotificationRepository;
import com.deliveryapp.repository.UserRepository;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;
    private final UrlUtil urlUtil;
    private final FileStorageService fileStorageService; // 1. Injected Storage Service

    // --- GETTERS ---
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

    // --- SEND LOGIC (Updated for File Upload) ---
    @Transactional
    public void sendNotification(Long userId, String title, String message, MultipartFile imageFile, String type, Long referenceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 2. Handle Image Upload
        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            // Stores in /uploads/notifications/filename.jpg
            imageUrl = fileStorageService.storeFile(imageFile, "notifications");
        }

        // 3. Save to Database (Store relative path)
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setImageUrl(imageUrl);
        notification.setType(type);
        notification.setReferenceType(type);
        notification.setReferenceId(referenceId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);

        // 4. Send to Firebase (Convert to Full URL)
        String fullImageUrl = urlUtil.getFullUrl(imageUrl);

        fcmService.sendToToken(
                user.getFcmToken(),
                title,
                message,
                fullImageUrl,
                type,
                String.valueOf(referenceId)
        );
    }

    @Transactional
    public void sendGlobalNotification(String topic, String title, String message, MultipartFile imageFile, String type, Long referenceId) {
        // Handle Image Upload
        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = fileStorageService.storeFile(imageFile, "notifications");
        }

        // Convert to Full URL for Firebase
        String fullImageUrl = urlUtil.getFullUrl(imageUrl);

        fcmService.sendToTopic(
                topic,
                title,
                message,
                fullImageUrl,
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