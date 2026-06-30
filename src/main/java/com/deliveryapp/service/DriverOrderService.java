package com.deliveryapp.service;

import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.DriverOrderStatus;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.OrderRepository;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DriverOrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final OrderWebSocketService webSocketService;

    @Transactional
    public Order assignDriver(Long orderId, Long driverId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));

        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("السائق غير موجود برقم: " + driverId));

        if (driver.getUserType() != UserType.DRIVER) {
            throw new InvalidDataException("المستخدم المحدد ليس سائقاً");
        }

        order.setDriver(driver);
        order.setDriverOrderStatus(DriverOrderStatus.PENDING);

        if (order.getStatus() == OrderStatus.PENDING) {
            try {
                notificationService.sendNotification(
                        order.getUser().getUserId(),
                        "تم تأكيد طلبك! ✅",
                        "طلبك رقم " + order.getOrderNumber() + " قيد التجهيز الآن.",
                        null,
                        "ORDER_UPDATE",
                        "order",
                        order.getOrderId(),
                        null);
            } catch (Exception e) {
                System.err.println("Failed to notify customer: " + e.getMessage());
            }
            order.setStatus(OrderStatus.CONFIRMED);
        }

        Order savedOrder = orderRepository.save(order);

        try {
            notificationService.sendNotification(
                    driverId,
                    "تم تعيين طلب جديد 🛵",
                    "تم تعيينك للطلب رقم " + order.getOrderNumber(),
                    null,
                    "DRIVER_ASSIGNMENT",
                    "order",
                    orderId,
                    null);
        } catch (Exception e) {
            System.err.println("Failed to notify driver: " + e.getMessage());
        }

        webSocketService.broadcastOrderUpdated(savedOrder);
        return savedOrder;
    }

    @Transactional
    public Order driverRespondToOrder(Long orderId, Long driverId, boolean isAccepted) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود"));

        if (order.getDriver() == null || !order.getDriver().getUserId().equals(driverId)) {
            throw new InvalidDataException("ليس لديك إذن للوصول إلى هذا الطلب.");
        }

        if (isAccepted) {
            order.setDriverOrderStatus(DriverOrderStatus.ACCEPTED);
            notificationService.notifyAllStaff(
                    "تم قبول الطلب! ✅",
                    "السائق " + order.getDriver().getName() + " وافق على توصيل الطلب رقم " + order.getOrderNumber(),
                    "DRIVER_ACCEPTED",
                    orderId);
        } else {
            order.setDriverOrderStatus(DriverOrderStatus.REJECTED);
            notificationService.notifyAllStaff(
                    "تم رفض الطلب! 🚨",
                    "السائق " + order.getDriver().getName() + " رفض توصيل الطلب رقم " + order.getOrderNumber(),
                    "DRIVER_REJECTED",
                    orderId);
        }

        Order savedOrder = orderRepository.save(order);
        webSocketService.broadcastOrderUpdated(savedOrder);
        return savedOrder;
    }

    public Page<Order> getDriverOrders(Long driverId, Boolean activeOnly, String orderNumber, Pageable pageable) {
        if (orderNumber != null && !orderNumber.trim().isEmpty()) {
            return orderRepository.findByDriverUserIdAndOrderNumberContainingIgnoreCaseOrderByCreatedAtDesc(driverId,
                    orderNumber, pageable);
        }

        if (Boolean.TRUE.equals(activeOnly)) {
            List<OrderStatus> activeStatuses = Arrays.asList(
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.OUT_FOR_DELIVERY);
            return orderRepository.findByDriverUserIdAndStatusInOrderByCreatedAtDesc(driverId, activeStatuses,
                    pageable);
        } else {
            return orderRepository.findByDriverUserIdOrderByCreatedAtDesc(driverId, pageable);
        }
    }
}