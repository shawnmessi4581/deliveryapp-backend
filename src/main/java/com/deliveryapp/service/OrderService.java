package com.deliveryapp.service;

import com.deliveryapp.dto.order.*;
import com.deliveryapp.entity.*;
import com.deliveryapp.enums.DriverOrderStatus;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import com.deliveryapp.util.DistanceUtil;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
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
    private final StoreRepository storeRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final ColorRepository colorRepository;

    private final CouponService couponService;
    private final NotificationService notificationService;
    private final PricingService pricingService;

    private final DistanceUtil distanceUtil;
    private final UrlUtil urlUtil;

    @Transactional
    public Order placeOrder(Long userId, Long addressId, String instruction, String couponCode,
            List<OrderItemRequest> itemsRequest) {

        if (itemsRequest == null || itemsRequest.isEmpty())
            throw new InvalidDataException("لم يتم تحديد أي عناصر.");
        if (addressId == null)
            throw new InvalidDataException("عنوان التوصيل مطلوب.");

        UserAddress userAddress = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود برقم: " + addressId));

        if (!userAddress.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("العنوان لا يخص هذا المستخدم");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود"));

        Order order = new Order();
        order.setUser(user);
        order.setOrderNumber(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.PENDING);

        List<OrderItem> orderItems = new ArrayList<>();
        Set<Store> uniqueStores = new HashSet<>();
        double subtotal = 0.0;

        for (OrderItemRequest itemReq : itemsRequest) {
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
        order.setSelectedInstruction(instruction);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 5. CALCULATE BEST ROUTE FEE (ROUND UP TO INTEGER)
        double maxFeePerKm = uniqueStores.stream()
                .mapToDouble(s -> s.getDeliveryFeeKM() != null ? s.getDeliveryFeeKM() : 0.0)
                .max().orElse(0.0);

        double totalDistanceKm = calculateOptimizedDistance(
                new ArrayList<>(uniqueStores),
                userAddress.getLatitude(),
                userAddress.getLongitude());

        double rawDeliveryFee = totalDistanceKm * maxFeePerKm;
        double deliveryFee = Math.ceil(rawDeliveryFee); // 🟢 CEIL FIX: Round UP to nearest integer

        // 6. Handle Coupon Logic
        double discountAmount = 0.0;
        Coupon validCoupon = null;

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            Store primaryStore = uniqueStores.iterator().next();
            validCoupon = couponService.validateCouponForOrder(couponCode, userId, orderItems, primaryStore);
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
            couponService.recordUsage(validCoupon, userId, savedOrder.getOrderId(), discountAmount);
        }

        logStatusChange(savedOrder, null, OrderStatus.PENDING, "تم استلام الطلب");

        try {
            notificationService.notifyStaffOfNewOrder(savedOrder.getOrderNumber(), savedOrder.getOrderId());
        } catch (Exception e) {
            System.err.println("Failed to notify staff: " + e.getMessage());
        }

        return savedOrder;
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));

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

        // 🟢 NOTIFY CUSTOMER WHEN CONFIRMED
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
        }

        return savedOrder;
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));

        // 1. Verify Ownership
        if (!order.getUser().getUserId().equals(userId)) {
            throw new InvalidDataException("ليس لديك إذن لإلغاء هذا الطلب.");
        }

        // 2. Verify Status
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidDataException("يمكنك فقط إلغاء الطلب عندما يكون قيد الانتظار.");
        }

        // 3. Reverse Coupon Usage
        if (order.getCouponId() != null) {
            couponUsageRepository.deleteByOrderId(orderId);
            try {
                Coupon coupon = couponService.getCouponById(order.getCouponId());
                if (coupon != null) {
                    coupon.setCurrentUsageCount(Math.max(0, coupon.getCurrentUsageCount() - 1));
                }
            } catch (ResourceNotFoundException e) {
                // Ignore
            }
        }

        // 🚨 NEW: Notify Staff BEFORE deleting the order so we have the orderNumber
        try {
            notificationService.notifyStaffOfCancelledOrder(order.getOrderNumber(), orderId);
        } catch (Exception e) {
            System.err.println("Failed to notify staff of cancellation: " + e.getMessage());
        }

        // 4. Delete History and Order
        historyRepository.deleteByOrderOrderId(orderId);
        orderRepository.delete(order);
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

    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserUserIdOrderByCreatedAtDesc(userId);
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId));
    }

    public List<Order> getAdminOrders(OrderStatus status, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

            if (status != null) {
                return orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, startDateTime,
                        endDateTime);
            } else {
                return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDateTime, endDateTime);
            }
        }
        if (status != null) {
            return orderRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void deleteOrder(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("الطلب غير موجود برقم: " + orderId);
        }
        historyRepository.deleteByOrderOrderId(orderId);
        couponUsageRepository.deleteByOrderId(orderId);
        orderRepository.deleteById(orderId);
    }

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

        return savedOrder;
    }

    // 2. ADD NEW METHOD FOR DRIVER RESPONSE
    @Transactional
    public Order driverRespondToOrder(Long orderId, Long driverId, boolean isAccepted) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("الطلب غير موجود"));

        if (order.getDriver() == null || !order.getDriver().getUserId().equals(driverId)) {
            throw new InvalidDataException("ليس لديك إذن للوصول إلى هذا الطلب.");
        }

        if (isAccepted) {
            order.setDriverOrderStatus(DriverOrderStatus.ACCEPTED);
            // Optional: You can auto-update the main status to PREPARING here if you want
            notificationService.notifyAllStaff(
                    "تم قبول الطلب! ✅",
                    "السائق " + order.getDriver().getName() + " وافق على توصيل الطلب رقم " + order.getOrderNumber(),
                    "DRIVER_ACCEPTED",
                    orderId);
        } else {
            order.setDriverOrderStatus(DriverOrderStatus.REJECTED);

            // Notify Admins that the driver rejected the order!
            notificationService.notifyAllStaff(
                    "تم رفض الطلب! 🚨",
                    "السائق " + order.getDriver().getName() + " رفض توصيل الطلب رقم " + order.getOrderNumber(),
                    "DRIVER_REJECTED",
                    orderId);
        }

        return orderRepository.save(order);
    }

    public List<Order> getDriverOrders(Long driverId, Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) {
            List<OrderStatus> activeStatuses = Arrays.asList(
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.OUT_FOR_DELIVERY);
            return orderRepository.findByDriverUserIdAndStatusInOrderByCreatedAtDesc(driverId, activeStatuses);
        } else {
            return orderRepository.findByDriverUserIdOrderByCreatedAtDesc(driverId);
        }
    }

    public DeliveryFeeResponse calculateDeliveryFee(Long storeId, Long addressId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

        UserAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود"));

        double distance = distanceUtil.calculateDistance(
                address.getLatitude(), address.getLongitude(),
                store.getLatitude(), store.getLongitude());

        Double feePerKm = store.getDeliveryFeeKM() != null ? store.getDeliveryFeeKM() : 0.0;

        // 🟢 FIX: Ensure calculation preview also rounds up (Ceil)
        double deliveryFee = Math.ceil(distance * feePerKm);

        return new DeliveryFeeResponse(deliveryFee, store.getEstimatedDeliveryTime());
    }

    public CouponCheckResponse verifyCoupon(CouponCheckRequest request) {
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

        List<OrderItem> tempItems = new ArrayList<>();
        double subtotal = 0.0;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود"));

            double price = pricingService.getFinalPriceInSYP(product);
            if (itemReq.getVariantId() != null) {
                ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("النوع غير موجود"));
                price += pricingService.getVariantFinalPriceInSYP(variant);
            }

            OrderItem tempItem = new OrderItem();
            tempItem.setProduct(product);
            tempItem.setQuantity(itemReq.getQuantity());
            tempItem.setUnitPrice(price);
            tempItem.setTotalPrice(price * itemReq.getQuantity());

            tempItems.add(tempItem);
            subtotal += tempItem.getTotalPrice();
        }

        Coupon coupon = couponService.validateCouponForOrder(
                request.getCode(),
                request.getUserId(),
                tempItems,
                store);

        double discount = couponService.calculateDiscount(coupon, subtotal, 0.0);

        return new CouponCheckResponse(
                coupon.getCouponId(),
                coupon.getCode(),
                discount,
                "تم تطبيق القسيمة بنجاح",
                coupon.getDiscountType().name());
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

    public DeliveryFeeResponse calculateMultiStoreFee(List<Long> storeIds, Long addressId) {
        if (storeIds == null || storeIds.isEmpty()) {
            throw new InvalidDataException("لم يتم توفير متاجر لحساب الرسوم");
        }

        List<Store> stores = storeRepository.findAllById(storeIds);
        if (stores.isEmpty()) {
            throw new ResourceNotFoundException("لم يتم العثور على متاجر صالحة");
        }

        UserAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود"));

        double maxFeePerKm = stores.stream()
                .mapToDouble(s -> s.getDeliveryFeeKM() != null ? s.getDeliveryFeeKM() : 0.0)
                .max().orElse(0.0);

        double totalDistanceKm = calculateOptimizedDistance(stores, address.getLatitude(), address.getLongitude());

        // 🟢 FIX: Ensure multi-store preview also rounds up (Ceil)
        double deliveryFee = Math.ceil(totalDistanceKm * maxFeePerKm);

        String estimatedTime = stores.get(0).getEstimatedDeliveryTime();

        return new DeliveryFeeResponse(deliveryFee, estimatedTime);
    }

    private double calculateOptimizedDistance(List<Store> stores, double userLat, double userLng) {
        if (stores.isEmpty())
            return 0.0;

        List<Store> unvisited = new ArrayList<>(stores);
        double currentLat = userLat;
        double currentLng = userLng;
        double totalDistance = 0.0;

        while (!unvisited.isEmpty()) {
            Store closestStore = null;
            double minDist = Double.MAX_VALUE;

            for (Store s : unvisited) {
                if (s.getLatitude() == null || s.getLongitude() == null)
                    continue;

                double d = distanceUtil.calculateDistance(currentLat, currentLng, s.getLatitude(), s.getLongitude());
                if (d < minDist) {
                    minDist = d;
                    closestStore = s;
                }
            }

            if (closestStore != null) {
                totalDistance += minDist;
                currentLat = closestStore.getLatitude();
                currentLng = closestStore.getLongitude();
                unvisited.remove(closestStore);
            } else {
                break;
            }
        }

        return totalDistance;
    }
}