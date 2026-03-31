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
                .orElseThrow(() -> new ResourceNotFoundException("الإشعار غير موجود برقم: " + notificationId));

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    // 1. SEND TO SINGLE USER
    @Transactional
    public void sendNotification(Long userId, String title, String message, MultipartFile imageFile, String type,
            String referenceType, Long referenceId, String externalUrl) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود"));

        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = fileStorageService.storeFile(imageFile, "notifications");
        }

        Notification notification = createNotificationObj(user, title, message, imageUrl, type, referenceType,
                referenceId, externalUrl);
        notificationRepository.save(notification);

        String fullImageUrl = urlUtil.getFullUrl(imageUrl);

        if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
            fcmService.sendToToken(user.getFcmToken(), title, message, fullImageUrl, type, String.valueOf(referenceId));
        }
    }

    // 2. SEND TO GROUP
    @Async
    @Transactional
    public void sendGroupNotification(String targetGroup, String title, String message, MultipartFile imageFile,
            String type, String referenceType, Long referenceId, String externalUrl) {

        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = fileStorageService.storeFile(imageFile, "notifications");
        }

        List<User> targetUsers;
        if ("all_users".equalsIgnoreCase(targetGroup)) {
            targetUsers = userRepository.findAll();
        } else {
            try {
                UserType targetType = UserType.valueOf(targetGroup.toUpperCase());
                targetUsers = userRepository.findByUserType(targetType);
            } catch (IllegalArgumentException e) {
                System.err.println("❌ Invalid target group provided: " + targetGroup);
                return;
            }
        }

        if (targetUsers.isEmpty())
            return;

        List<Notification> notificationsToSave = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        for (User user : targetUsers) {
            notificationsToSave.add(createNotificationObj(user, title, message, imageUrl, type, referenceType,
                    referenceId, externalUrl));
            if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                tokens.add(user.getFcmToken());
            }
        }

        notificationRepository.saveAll(notificationsToSave);
        String fullImageUrl = urlUtil.getFullUrl(imageUrl);
        fcmService.sendToManyTokens(tokens, title, message, fullImageUrl, type, String.valueOf(referenceId));
    }

    // 3. AUTO-NOTIFY STAFF ON NEW ORDER
    @Async
    @Transactional
    public void notifyStaffOfNewOrder(String orderNumber, Long orderId) {
        String title = "طلب جديد! 🛒";
        String message = "تم استلام الطلب رقم " + orderNumber + " وهو بانتظار التأكيد.";

        List<User> staff = new ArrayList<>();
        staff.addAll(userRepository.findByUserType(UserType.ADMIN));
        staff.addAll(userRepository.findByUserType(UserType.EMPLOYEE));

        if (staff.isEmpty())
            return;

        List<Notification> dbNotifications = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        for (User user : staff) {
            dbNotifications.add(createNotificationObj(user, title, message, null, "NEW_ORDER", "order", orderId, null));
            if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                tokens.add(user.getFcmToken());
            }
        }

        notificationRepository.saveAll(dbNotifications);
        fcmService.sendToManyTokens(tokens, title, message, null, "NEW_ORDER", String.valueOf(orderId));
    }

    private Notification createNotificationObj(User user, String title, String msg, String img, String type,
            String refType, Long refId, String extUrl) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(msg);
        n.setImageUrl(img);
        n.setType(type);
        n.setReferenceType(refType);
        n.setReferenceId(refId);
        n.setExternalUrl(extUrl); // Save external link if present
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    private NotificationResponse mapToResponse(Notification notification) {
        NotificationResponse dto = new NotificationResponse();
        dto.setNotificationId(notification.getNotificationId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setImageUrl(urlUtil.getFullUrl(notification.getImageUrl()));
        dto.setType(notification.getType());

        dto.setReferenceType(notification.getReferenceType());
        dto.setReferenceId(notification.getReferenceId());
        dto.setExternalUrl(notification.getExternalUrl());

        dto.setIsRead(notification.getIsRead());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }
}