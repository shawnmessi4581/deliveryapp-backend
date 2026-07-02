package com.deliveryapp.service;

import com.deliveryapp.dto.order.*;
import com.deliveryapp.entity.*;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import com.deliveryapp.util.MathUtil;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserAddressRepository addressRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final ColorRepository colorRepository;

    private final CouponService couponService;
    private final NotificationService notificationService;
    private final PricingService pricingService;
    private final TelegramService telegramService;
    private final OrderCalculationService calculationService; // 🟢 Inject new calculation service
    private final OrderWebSocketService webSocketService;

    private final UrlUtil urlUtil;
    private final MathUtil mathUtil;

    @Transactional
    public Order placeOrder(PlaceOrderRequest request) {

        if (request.getItems() == null || request.getItems().isEmpty())
            throw new InvalidDataException("لم يتم تحديد أي عناصر.");
        if (request.getAddressId() == null)
            throw new InvalidDataException("عنوان التوصيل مطلوب.");

        UserAddress userAddress = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود برقم: " + request.getAddressId()));

        if (!userAddress.getUser().getUserId().equals(request.getUserId())) {
            throw new ResourceNotFoundException("العنوان لا يخص هذا المستخدم");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود"));

        Order order = new Order();
        order.setUser(user);
        order.setOrderNumber(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.PENDING);

        List<OrderItem> orderItems = new ArrayList<>();
        Set<Store> uniqueStores = new HashSet<>();
        double subtotal = 0.0;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود: " + itemReq.getProductId()));

            Store store = product.getStore();

            if (!isStoreOpen(store)) {
                throw new InvalidDataException("المتجر '" + store.getName() + "' مغلق حالياً.");
            }

            uniqueStores.add(store);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setQuantity(itemReq.getQuantity());
            orderItem.setNotes(itemReq.getNotes());

            if (itemReq.getColorId() != null) {
                Color color = colorRepository.findById(itemReq.getColorId())
                        .orElseThrow(() -> new ResourceNotFoundException("اللون غير موجود"));

                boolean isValidColor = product.getColors().stream()
                        .anyMatch(c -> c.getColorId().equals(color.getColorId()));

                if (!isValidColor)
                    throw new InvalidDataException("اللون غير متوفر لهذا المنتج");
                orderItem.setSelectedColor(color);
            }

            double price = pricingService.getFinalPriceInSYP(product);
            if (itemReq.getVariantId() != null && itemReq.getVariantId() != 0) {
                ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("النوع غير موجود: " + itemReq.getVariantId()));

                if (!variant.getProduct().getProductId().equals(product.getProductId())) {
                    throw new InvalidDataException("هذا النوع لا ينتمي لهذا المنتج");
                }

                orderItem.setVariant(variant);
                orderItem.setVariantDetails(variant.getVariantValue());
                price += pricingService.getVariantFinalPriceInSYP(variant);
            }

            orderItem.setUnitPrice(price);
            orderItem.setTotalPrice(price * itemReq.getQuantity());

            subtotal += orderItem.getTotalPrice();
            orderItems.add(orderItem);
        }

        order.setStores(new ArrayList<>(uniqueStores));
        order.setOrderItems(orderItems);
        order.setSubtotal(subtotal);

        order.setDeliveryAddress(userAddress.getAddressLine());
        order.setDeliveryLatitude(userAddress.getLatitude());
        order.setDeliveryLongitude(userAddress.getLongitude());
        order.setSelectedInstruction(request.getInstruction());
        order.setOrderNote(request.getOrderNote());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 5. CALCULATE BEST ROUTE FEE
        double maxFeePerKm = uniqueStores.stream()
                .mapToDouble(s -> s.getDeliveryFeeKM() != null ? s.getDeliveryFeeKM() : 0.0)
                .max().orElse(0.0);

        double maxMinimumDeliveryFee = uniqueStores.stream()
                .mapToDouble(s -> s.getMinimumDeliveryFee() != null ? s.getMinimumDeliveryFee() : 0.0)
                .max().orElse(0.0);

        double totalDistanceKm = calculationService.calculateOptimizedDistance(
                new ArrayList<>(uniqueStores),
                userAddress.getLatitude(),
                userAddress.getLongitude());

        double rawDeliveryFee = totalDistanceKm * maxFeePerKm;

        double deliveryFee = mathUtil.roundUpToNearestTen(rawDeliveryFee);

        if (deliveryFee < maxMinimumDeliveryFee) {
            deliveryFee = maxMinimumDeliveryFee;
        }

        // 6. Handle Coupon Logic
        double discountAmount = 0.0;
        Coupon validCoupon = null;

        if (request.getCouponCode() != null && !request.getCouponCode().trim().isEmpty()) {
            Store primaryStore = uniqueStores.iterator().next();
            validCoupon = couponService.validateCouponForOrder(request.getCouponCode(), request.getUserId(), orderItems,
                    primaryStore);
            discountAmount = couponService.calculateDiscount(validCoupon, subtotal, deliveryFee);

            if (validCoupon.getDiscountType() == Coupon.DiscountType.FREE_DELIVERY) {
                discountAmount = deliveryFee;
                deliveryFee = 0.0;
            }

            order.setCouponId(validCoupon.getCouponId());
            order.setDiscountAmount(discountAmount);
        }

        order.setDeliveryFee(deliveryFee);

        // 7. Final Total
        double finalTotal;
        if (validCoupon != null && validCoupon.getDiscountType() == Coupon.DiscountType.FREE_DELIVERY) {
            finalTotal = subtotal;
        } else {
            finalTotal = (subtotal + deliveryFee) - discountAmount;
        }

        order.setTotalAmount(Math.max(finalTotal, 0.0));

        Order savedOrder = orderRepository.save(order);

        if (validCoupon != null) {
            couponService.recordUsage(validCoupon, request.getUserId(), savedOrder.getOrderId(), discountAmount);
        }

        logStatusChange(savedOrder, null, OrderStatus.PENDING, "تم استلام الطلب");

        try {
            notificationService.notifyStaffOfNewOrder(savedOrder.getOrderNumber(), savedOrder.getOrderId());
        } catch (Exception e) {
            System.err.println("Failed to notify staff: " + e.getMessage());
        }

        try {
            telegramService.notifyAllStoresOfOrder(savedOrder);
        } catch (Exception e) {
            System.err.println("Failed to send Telegram store notifications: " + e.getMessage());
        }

        webSocketService.broadcastOrderCreated(savedOrder);

        return savedOrder;
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));

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
                System.err.println("Failed to notify customer: " + e.getMessage());
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
                System.err.println("Failed to notify customer: " + e.getMessage());
            }
        }

        webSocketService.broadcastOrderUpdated(savedOrder);
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
            couponUsageRepository.deleteByOrderId(orderId);
            try {
                Coupon coupon = couponService.getCouponById(order.getCouponId());
                if (coupon != null) {
                    coupon.setCurrentUsageCount(Math.max(0, coupon.getCurrentUsageCount() - 1));
                }
            } catch (ResourceNotFoundException e) {
            }
        }

        try {
            notificationService.notifyStaffOfCancelledOrder(order.getOrderNumber(), orderId);
        } catch (Exception e) {
            System.err.println("Failed to notify staff of cancellation: " + e.getMessage());
        }

        List<Long> storeIds = order.getStores().stream().map(Store::getStoreId)
                .collect(java.util.stream.Collectors.toList());

        historyRepository.deleteByOrderOrderId(orderId);
        orderRepository.delete(order);

        webSocketService.broadcastOrderDeleted(orderId, storeIds);
    }

    @Transactional
    public void deleteOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));

        List<Long> storeIds = order.getStores().stream().map(Store::getStoreId)
                .collect(java.util.stream.Collectors.toList());

        historyRepository.deleteByOrderOrderId(orderId);
        couponUsageRepository.deleteByOrderId(orderId);
        orderRepository.deleteById(orderId);

        webSocketService.broadcastOrderDeleted(orderId, storeIds);
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

    // 🟢 UPDATED: Vendor gets their orders (Active vs History)
    public Page<Order> getVendorOrders(Long storeId, Boolean activeOnly, Pageable pageable) {
        if (Boolean.TRUE.equals(activeOnly)) {
            // For vendors, "Active" means they have to do something with it, or it hasn't
            // been delivered yet.
            List<OrderStatus> activeStatuses = Arrays.asList(
                    OrderStatus.PENDING,
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.OUT_FOR_DELIVERY);
            return orderRepository.findByStores_StoreIdAndStatusInOrderByCreatedAtDesc(storeId, activeStatuses,
                    pageable);
        } else {
            // Return everything (including DELIVERED and CANCELLED)
            return orderRepository.findByStores_StoreIdOrderByCreatedAtDesc(storeId, pageable);
        }
    }

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