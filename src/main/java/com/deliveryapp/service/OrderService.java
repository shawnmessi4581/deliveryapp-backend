package com.deliveryapp.service;

import com.deliveryapp.dto.order.*;
import com.deliveryapp.entity.*;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;

    private final CouponService couponService;
    private final NotificationService notificationService;
    private final TelegramService telegramService;
    private final OrderCalculationService calculationService;
    private final OrderWebSocketService webSocketService;

    private final UrlUtil urlUtil;

    // =================================================================================
    // PLACE ORDER LOGIC
    // =================================================================================
    @Transactional
    public Order placeOrder(PlaceOrderRequest request) {

        if (request.getItems() == null || request.getItems().isEmpty())
            throw new InvalidDataException("لم يتم تحديد أي عناصر.");
        if (request.getAddressId() == null)
            throw new InvalidDataException("عنوان التوصيل مطلوب.");

        UserAddress userAddress = calculationService.findUserAddress(request.getAddressId(), request.getUserId());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود"));

        // 1. Price the lines (variants, offers) — same engine as the /quote preview
        List<OrderItem> orderItems = calculationService.priceItems(request.getItems());

        for (OrderItem item : orderItems) {
            Store store = item.getProduct().getStore();
            if (!isStoreOpen(store)) {
                throw new InvalidDataException("المتجر '" + store.getName() + "' مغلق حالياً.");
            }
        }

        // 2. Delivery fee (store free-delivery rules), coupon / flash sale, total
        OrderQuote quote = calculationService.quote(orderItems, request.getUserId(), request.getCouponCode(),
                userAddress.getLatitude(), userAddress.getLongitude());

        Order order = new Order();
        order.setUser(user);
        order.setOrderNumber(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.PENDING);

        orderItems.forEach(item -> item.setOrder(order));
        order.setStores(new ArrayList<>(quote.getStores()));
        order.setOrderItems(orderItems);

        order.setDeliveryAddress(userAddress.getAddressLine());
        order.setDeliveryLatitude(userAddress.getLatitude());
        order.setDeliveryLongitude(userAddress.getLongitude());
        order.setSelectedInstruction(request.getInstruction());
        order.setOrderNote(request.getOrderNote());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 3. Financials (total = subtotal − offers − coupon + delivery)
        order.setSubtotal(quote.getSubtotal());
        order.setOfferDiscountAmount(quote.getOfferDiscountAmount());
        order.setDeliveryFee(quote.getDeliveryFee());
        if (quote.isCouponApplied()) {
            order.setCouponId(quote.getCoupon().getCouponId());
            order.setDiscountAmount(quote.getCouponDiscountAmount());
            order.setCouponFundedByStoreId(quote.getCouponFundedByStoreId());
            order.setCouponStoreDiscountAmount(quote.getCouponStoreDiscountAmount());
        }
        order.setTotalAmount(quote.getTotalAmount());

        Order savedOrder = orderRepository.save(order);

        // Takes the usage slot atomically — throws (rolling back the order) if a flash sale just sold out
        if (quote.isCouponApplied()) {
            couponService.recordUsage(quote.getCoupon(), request.getUserId(), savedOrder.getOrderId(),
                    quote.getCouponDiscountAmount());
        }

        logStatusChange(savedOrder, null, OrderStatus.PENDING, "تم استلام الطلب");

        // --- NOTIFICATIONS ---

        // 1. Notify Admins and Employees
        try {
            notificationService.notifyStaffOfNewOrder(savedOrder.getOrderNumber(), savedOrder.getOrderId(), savedOrder.getUser().getName());
        } catch (Exception e) {
            System.err.println("Failed to notify staff: " + e.getMessage());
        }

        // 2. 🟢 NEW: Notify Vendors (Store Owners)
        try {
            // Find all users who are VENDORS
            List<User> allVendors = userRepository.findByUserType(UserType.VENDOR);

            for (Store store : quote.getStores()) {
                // Find vendors whose managedStoreId matches this store
                List<User> storeVendors = allVendors.stream()
                        .filter(v -> v.getManagedStore() != null
                                && v.getManagedStore().getStoreId().equals(store.getStoreId()))
                        .collect(Collectors.toList());

                for (User vendor : storeVendors) {
                    notificationService.sendNotification(
                            vendor.getUserId(),
                            "طلب جديد! 🍽️",
                            "لقد تلقيت طلباً جديداً (رقم " + savedOrder.getOrderNumber() + ")",
                            null,
                            "NEW_ORDER",
                            "order",
                            savedOrder.getOrderId(),
                            null);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to notify vendors: " + e.getMessage());
        }

        // 3. Telegram Notifications
        try {
            telegramService.notifyAllStoresOfOrder(savedOrder);
        } catch (Exception e) {
            System.err.println("Failed to send Telegram store notifications: " + e.getMessage());
        }

        // 4. WebSocket Broadcast
        try {
            webSocketService.broadcastOrderCreated(savedOrder);
        } catch (Exception e) {
            System.err.println("Failed to broadcast order creation via websocket: " + e.getMessage());
        }

        return savedOrder;
    }

    // =================================================================================
    // ORDER MANAGEMENT
    // =================================================================================
    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));

        // SECURITY CHECK
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getUserType() == UserType.DRIVER) {
            if (order.getDriver() == null || !order.getDriver().getUserId().equals(userId)) {
                throw new InvalidDataException("لا يمكنك تعديل حالة طلب غير مسند إليك.");
            }
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());

        if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());

            if (oldStatus != OrderStatus.DELIVERED && order.getDriver() != null) {
                User driver = order.getDriver();
                int currentCount = driver.getTotalDeliveries() != null ? driver.getTotalDeliveries() : 0;
                driver.setTotalDeliveries(currentCount + 1);
                userRepository.save(driver);
            }
        }

        Order savedOrder = orderRepository.save(order);
        logStatusChange(savedOrder, oldStatus, newStatus, "تم تحديث حالة الطلب بواسطة " + userId);

        // NOTIFY CUSTOMER
        if (newStatus == OrderStatus.CONFIRMED && oldStatus == OrderStatus.PENDING) {
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
            }
        } else if (newStatus == OrderStatus.OUT_FOR_DELIVERY && oldStatus != OrderStatus.OUT_FOR_DELIVERY) {
            String message = order.getDriver() != null
                    ? "طلبك رقم " + order.getOrderNumber() + " في الطريق إليك مع السائق " + order.getDriver().getName() + "."
                    : "طلبك رقم " + order.getOrderNumber() + " في الطريق إليك.";
            try {
                notificationService.sendNotification(
                        order.getUser().getUserId(),
                        "طلبك في الطريق! 🛵",
                        message,
                        null,
                        "ORDER_OUT_FOR_DELIVERY",
                        "order",
                        order.getOrderId(),
                        null);
            } catch (Exception e) {
                System.err.println("Failed to notify customer of out-for-delivery: " + e.getMessage());
            }
        } else if (newStatus == OrderStatus.DELIVERED && oldStatus != OrderStatus.DELIVERED) {
            try {
                notificationService.sendNotification(
                        order.getUser().getUserId(),
                        "تم التوصيل بنجاح! 🎉",
                        "شكراً لاستخدامك تطبيقنا",
                        null,
                        "ORDER_DELIVERED",
                        "order",
                        order.getOrderId(),
                        null);
            } catch (Exception e) {
            }
        }

        try {
            webSocketService.broadcastOrderUpdated(savedOrder);
        } catch (Exception e) {
            System.err.println("Failed to broadcast order update via websocket: " + e.getMessage());
        }

        return savedOrder;
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));

        if (!order.getUser().getUserId().equals(userId)) {
            throw new InvalidDataException("ليس لديك إذن لإلغاء هذا الطلب.");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidDataException("يمكنك فقط إلغاء الطلب عندما يكون قيد الانتظار.");
        }

        if (order.getCouponId() != null) {
            couponService.releaseUsageForOrder(orderId, order.getCouponId());
        }

        try {
            notificationService.notifyStaffOfCancelledOrder(order.getOrderNumber(), orderId, order.getUser().getName());
        } catch (Exception e) {
            System.err.println("Failed to notify staff of cancellation: " + e.getMessage());
        }

        List<Long> storeIds = order.getStores().stream().map(Store::getStoreId).collect(Collectors.toList());

        historyRepository.deleteByOrderOrderId(orderId);
        orderRepository.delete(order);

        try {
            webSocketService.broadcastOrderDeleted(orderId, storeIds);
        } catch (Exception e) {
            System.err.println("Failed to broadcast order cancellation via websocket: " + e.getMessage());
        }
    }

    private void logStatusChange(Order order, OrderStatus oldS, OrderStatus newS, String notes) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldS);
        history.setNewStatus(newS);
        history.setNotes(notes);
        history.setCreatedAt(LocalDateTime.now());
        historyRepository.save(history);
    }

    // =================================================================================
    // GETTERS & UTILS
    // =================================================================================

    public Page<Order> getUserOrders(Long userId, String orderNumber, Pageable pageable) {
        if (orderNumber != null && !orderNumber.trim().isEmpty()) {
            return orderRepository.findByUserUserIdAndOrderNumberContainingIgnoreCaseOrderByCreatedAtDesc(userId,
                    orderNumber, pageable);
        }
        return orderRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));
    }

    public Page<Order> getAdminOrders(String orderNumber, OrderStatus status, LocalDate startDate, LocalDate endDate,
            Pageable pageable) {
        if (orderNumber != null && !orderNumber.trim().isEmpty()) {
            if (startDate != null && endDate != null && status != null)
                return orderRepository
                        .findByOrderNumberContainingIgnoreCaseAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
                                orderNumber, status, startDate.atStartOfDay(), endDate.atTime(23, 59, 59), pageable);
            else if (startDate != null && endDate != null)
                return orderRepository.findByOrderNumberContainingIgnoreCaseAndCreatedAtBetweenOrderByCreatedAtDesc(
                        orderNumber, startDate.atStartOfDay(), endDate.atTime(23, 59, 59), pageable);
            else if (status != null)
                return orderRepository.findByOrderNumberContainingIgnoreCaseAndStatusOrderByCreatedAtDesc(orderNumber,
                        status, pageable);
            else
                return orderRepository.findByOrderNumberContainingIgnoreCaseOrderByCreatedAtDesc(orderNumber, pageable);
        }
        if (startDate != null && endDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
            if (status != null)
                return orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, startDateTime,
                        endDateTime, pageable);
            else
                return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDateTime, endDateTime, pageable);
        }
        if (status != null)
            return orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);

        return orderRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public void deleteOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));

        List<Long> storeIds = order.getStores().stream().map(Store::getStoreId).collect(Collectors.toList());

        historyRepository.deleteByOrderOrderId(orderId);
        // Gives the slot back too, so a flash sale's "X / Y used" stays in step with its usage rows
        couponService.releaseUsageForOrder(orderId, order.getCouponId());
        orderRepository.deleteById(orderId);

        try {
            webSocketService.broadcastOrderDeleted(orderId, storeIds);
        } catch (Exception e) {
            System.err.println("Failed to broadcast order deletion via websocket: " + e.getMessage());
        }
    }

    public Page<Order> getVendorOrders(Long storeId, Boolean activeOnly, Pageable pageable) {
        if (Boolean.TRUE.equals(activeOnly)) {
            List<OrderStatus> activeStatuses = Arrays.asList(
                    OrderStatus.PENDING,
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.OUT_FOR_DELIVERY);
            return orderRepository.findByStores_StoreIdAndStatusInOrderByCreatedAtDesc(storeId, activeStatuses,
                    pageable);
        } else {
            return orderRepository.findByStores_StoreIdOrderByCreatedAtDesc(storeId, pageable);
        }
    }

    // =================================================================================
    // TRACKING & HELPERS
    // =================================================================================

    public OrderTrackingResponse trackOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));

        OrderTrackingResponse response = new OrderTrackingResponse();
        response.setOrderId(order.getOrderId());
        response.setStatus(order.getStatus());

        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        if (order.getDriver() != null) {
            User driver = order.getDriver();
            response.setDriverId(driver.getUserId());
            response.setDriverName(driver.getName());
            response.setDriverPhone(driver.getPhoneNumber());

            response.setDriverImage(urlUtil.getFullUrl(driver.getProfileImage()));

            response.setDriverVehicle(driver.getVehicleNumber());
            response.setDriverLatitude(driver.getCurrentLocationLat());
            response.setDriverLongitude(driver.getCurrentLocationLng());
        }

        return response;
    }

    private boolean isStoreOpen(Store store) {
        if (store.getOpeningTime() == null || store.getClosingTime() == null)
            return true;

        java.time.LocalTime now = java.time.LocalTime.now();

        if (store.getClosingTime().isBefore(store.getOpeningTime())) {
            return now.isAfter(store.getOpeningTime()) || now.isBefore(store.getClosingTime());
        } else {
            return now.isAfter(store.getOpeningTime()) && now.isBefore(store.getClosingTime());
        }
    }
}