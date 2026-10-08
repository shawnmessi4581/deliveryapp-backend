package com.deliveryapp.service;

import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.repository.OrderRepository;
import com.deliveryapp.repository.OrderStatusHistoryRepository;
import com.deliveryapp.repository.UserRepository;
import com.deliveryapp.util.UrlUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private TelegramService telegramService;

    @Mock
    private OrderCalculationService calculationService;

    @Mock
    private OrderWebSocketService webSocketService;

    @Mock
    private UrlUtil urlUtil;

    @InjectMocks
    private OrderService orderService;

    @Test
    void updateOrderStatus_toOutForDelivery_shouldNotifyCustomerWithDriverName() {
        Order order = orderWithStatus(OrderStatus.CONFIRMED);
        User driver = user(30L, "سامر", UserType.DRIVER);
        order.setDriver(driver);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(30L)).thenReturn(Optional.of(driver));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateOrderStatus(1L, OrderStatus.OUT_FOR_DELIVERY, 30L);

        verify(notificationService).sendNotification(
                eq(20L),
                eq("طلبك في الطريق! 🛵"),
                eq("طلبك رقم ABC123 في الطريق إليك مع السائق سامر."),
                isNull(),
                eq("ORDER_OUT_FOR_DELIVERY"),
                eq("order"),
                eq(1L),
                isNull());
    }

    @Test
    void updateOrderStatus_toOutForDeliveryWithoutDriver_shouldNotifyCustomer() {
        Order order = orderWithStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L, "Admin", UserType.ADMIN)));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateOrderStatus(1L, OrderStatus.OUT_FOR_DELIVERY, 99L);

        verify(notificationService).sendNotification(
                eq(20L),
                anyString(),
                eq("طلبك رقم ABC123 في الطريق إليك."),
                isNull(),
                eq("ORDER_OUT_FOR_DELIVERY"),
                eq("order"),
                eq(1L),
                isNull());
    }

    @Test
    void updateOrderStatus_alreadyOutForDelivery_shouldNotNotifyAgain() {
        Order order = orderWithStatus(OrderStatus.OUT_FOR_DELIVERY);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L, "Admin", UserType.ADMIN)));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateOrderStatus(1L, OrderStatus.OUT_FOR_DELIVERY, 99L);

        verify(notificationService, never()).sendNotification(
                anyLong(), anyString(), anyString(), any(), anyString(), anyString(), anyLong(), any());
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = new Order();
        order.setOrderId(1L);
        order.setOrderNumber("ABC123");
        order.setStatus(status);
        order.setUser(user(20L, "Customer", UserType.CUSTOMER));
        return order;
    }

    private User user(Long id, String name, UserType type) {
        User user = new User();
        user.setUserId(id);
        user.setName(name);
        user.setUserType(type);
        return user;
    }
}
