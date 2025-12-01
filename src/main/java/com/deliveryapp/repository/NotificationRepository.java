package com.deliveryapp.repository;

import com.deliveryapp.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Get user notifications, newest first
    List<Notification> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    // Count unread notifications
    long countByUserUserIdAndIsReadFalse(Long userId);
}