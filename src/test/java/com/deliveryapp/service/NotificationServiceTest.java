package com.deliveryapp.service;

import com.deliveryapp.entity.Notification;
import com.deliveryapp.mapper.notification.NotificationMapper;
import com.deliveryapp.repository.NotificationRepository;
import com.deliveryapp.repository.UserRepository;
import com.deliveryapp.util.UrlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FCMService fcmService;

    @Mock
    private UrlUtil urlUtil;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void markAllAsRead_shouldMarkAllUnreadNotificationsForUser() {
        Notification first = mock(Notification.class);
        Notification second = mock(Notification.class);

        when(notificationRepository.findByUserUserIdAndIsReadFalse(10L))
                .thenReturn(List.of(first, second));

        notificationService.markAllAsRead(10L);

        verify(notificationRepository).findByUserUserIdAndIsReadFalse(10L);
        verify(first).setIsRead(true);
        verify(second).setIsRead(true);
        verify(notificationRepository).saveAll(List.of(first, second));
    }
}