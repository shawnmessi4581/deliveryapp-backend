package com.deliveryapp.service;

import com.deliveryapp.dto.notification.NotificationResponse;
import com.deliveryapp.entity.Notification;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.NotificationRepository;
import com.deliveryapp.repository.UserRepository;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    // --- 1. SEND TO SINGLE USER ---
    @Transactional
    public void sendNotification(Long userId, String title, String message, MultipartFile imageFile, String type,
            Long referenceId) {
        System.out.println("🔔 Attempting to send notification to User ID: " + userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = fileStorageService.storeFile(imageFile, "notifications");
        }

        // Save to DB
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
        System.out.println("💾 Saved notification to DB for User ID: " + userId);

        // Send FCM
        String fullImageUrl = urlUtil.getFullUrl(imageUrl);
        System.out.println("🚀 Sending FCM to token: " + user.getFcmToken());

        fcmService.sendToToken(
                user.getFcmToken(),
                title,
                message,
                fullImageUrl,
                type,
                String.valueOf(referenceId));
    }

    // --- 2. SEND TO TOPIC (LOOP & SAVE TO DB) ---
    @Transactional
    public void sendGlobalNotification(String topic, String title, String message, MultipartFile imageFile, String type,
            Long referenceId) {
        System.out.println("📢 Attempting to send GLOBAL notification to Topic: " + topic);

        // 1. Handle Image Upload
        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = fileStorageService.storeFile(imageFile, "notifications");
            System.out.println("📸 Image saved at: " + imageUrl);
        }

        // 2. Determine Target Users based on Topic
        List<User> targetUsers;
        if ("all_users".equalsIgnoreCase(topic)) {
            System.out.println("👥 Fetching ALL active users from DB...");
            targetUsers = userRepository.findAll(); // Or findByIsActiveTrue()
        } else {
            try {
                // Topic matches UserType ENUM (CUSTOMER, DRIVER, ADMIN)
                UserType targetType = UserType.valueOf(topic.toUpperCase());
                System.out.println("👥 Fetching users of type: " + targetType);
                targetUsers = userRepository.findByUserType(targetType);
            } catch (IllegalArgumentException e) {
                System.err.println("❌ Invalid topic name provided: " + topic);
                throw new IllegalArgumentException("Invalid topic: " + topic);
            }
        }

        if (targetUsers.isEmpty()) {
            System.out.println("⚠️ No users found for topic: " + topic + ". Aborting send.");
            return;
        }

        System.out.println("✅ Found " + targetUsers.size() + " users. Saving notifications to DB...");

        // 3. Save a Database Notification for EVERY user in the target group
        List<Notification> notificationsToSave = new ArrayList<>();
        for (User user : targetUsers) {
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
            notificationsToSave.add(notification);
        }
        notificationRepository.saveAll(notificationsToSave);
        System.out.println("💾 Saved " + notificationsToSave.size() + " notifications to DB.");

        // 4. Send to Firebase (Topic)
        // We let Firebase handle the mass delivery using the Topic string.
        String fullImageUrl = urlUtil.getFullUrl(imageUrl);
        System.out.println("🚀 Dispatching to Firebase Topic: " + topic);

        fcmService.sendToTopic(
                topic,
                title,
                message,
                fullImageUrl,
                type,
                String.valueOf(referenceId));
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