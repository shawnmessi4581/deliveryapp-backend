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
import org.springframework.scheduling.annotation.Async;
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
    private final FileStorageService fileStorageService;

    // =================================================================================
    // GETTERS & STATUS
    // =================================================================================

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

    // =================================================================================
    // SEND LOGIC
    // =================================================================================

    // 1. SEND TO SINGLE USER
    @Transactional
    public void sendNotification(Long userId, String title, String message, MultipartFile imageFile, String type,
            Long referenceId) {
        System.out.println("🔔 Attempting to send notification to User ID: " + userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Save Image
        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = fileStorageService.storeFile(imageFile, "notifications");
        }

        // Save to DB
        Notification notification = createNotificationObj(user, title, message, imageUrl, type, referenceId);
        notificationRepository.save(notification);
        System.out.println("💾 Saved notification to DB for User ID: " + userId);

        // Send FCM (Convert to Full URL first)
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

    // 2. SEND TO GROUP (Replaces old "Topic" logic)
    @Async // Run in background for speed
    @Transactional
    public void sendGroupNotification(String targetGroup, String title, String message, MultipartFile imageFile,
            String type, Long referenceId) {
        System.out.println("📢 Attempting to send GLOBAL notification to Group: " + targetGroup);

        // Handle Image Upload
        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = fileStorageService.storeFile(imageFile, "notifications");
            System.out.println("📸 Image saved at: " + imageUrl);
        }

        // Determine Target Users based on Group
        List<User> targetUsers;
        if ("all_users".equalsIgnoreCase(targetGroup)) {
            System.out.println("👥 Fetching ALL active users from DB...");
            targetUsers = userRepository.findAll();
        } else {
            try {
                UserType targetType = UserType.valueOf(targetGroup.toUpperCase());
                System.out.println("👥 Fetching users of type: " + targetType);
                targetUsers = userRepository.findByUserType(targetType);
            } catch (IllegalArgumentException e) {
                System.err.println("❌ Invalid target group provided: " + targetGroup);
                return;
            }
        }

        if (targetUsers.isEmpty()) {
            System.out.println("⚠️ No users found for group: " + targetGroup + ". Aborting send.");
            return;
        }

        System.out.println("✅ Found " + targetUsers.size() + " users. Preparing notifications...");

        // Save DB Notifications and Collect Tokens
        List<Notification> notificationsToSave = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        for (User user : targetUsers) {
            notificationsToSave.add(createNotificationObj(user, title, message, imageUrl, type, referenceId));
            if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                tokens.add(user.getFcmToken());
            }
        }

        // Batch Save to DB
        notificationRepository.saveAll(notificationsToSave);
        System.out.println("💾 Saved " + notificationsToSave.size() + " notifications to DB.");

        // Send Multicast to Firebase
        String fullImageUrl = urlUtil.getFullUrl(imageUrl);
        System.out.println("🚀 Dispatching Multicast to " + tokens.size() + " devices.");
        fcmService.sendToManyTokens(tokens, title, message, fullImageUrl, type, String.valueOf(referenceId));
    }

    // 3. AUTO-NOTIFY STAFF ON NEW ORDER
    @Async // Run in background so the user's order placement is instant
    @Transactional
    public void notifyStaffOfNewOrder(String orderNumber, Long orderId) {
        System.out.println("🔔 Notifying staff of new Order #" + orderNumber);

        String title = "New Order Received! 🛒";
        String message = "Order #" + orderNumber + " has been placed and requires confirmation.";

        // Find Admins and Employees
        List<User> staff = new ArrayList<>();
        staff.addAll(userRepository.findByUserType(UserType.ADMIN));
        staff.addAll(userRepository.findByUserType(UserType.EMPLOYEE));

        if (staff.isEmpty())
            return;

        List<Notification> dbNotifications = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        for (User user : staff) {
            dbNotifications.add(createNotificationObj(user, title, message, null, "NEW_ORDER", orderId));
            if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                tokens.add(user.getFcmToken());
            }
        }

        notificationRepository.saveAll(dbNotifications);
        fcmService.sendToManyTokens(tokens, title, message, null, "NEW_ORDER", String.valueOf(orderId));
    }

    // =================================================================================
    // HELPERS & MAPPERS
    // =================================================================================

    private Notification createNotificationObj(User user, String title, String msg, String img, String type,
            Long refId) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(msg);
        n.setImageUrl(img); // Save relative path in DB
        n.setType(type);
        n.setReferenceType(type);
        n.setReferenceId(refId);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

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