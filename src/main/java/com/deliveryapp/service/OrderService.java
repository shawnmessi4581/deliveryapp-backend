package com.deliveryapp.service;

import com.deliveryapp.dto.order.*;
import com.deliveryapp.entity.*;
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

    private final CouponService couponService;
    private final NotificationService notificationService;

    private final DistanceUtil distanceUtil;
    private final UrlUtil urlUtil;

    // =================================================================================
    // PLACE ORDER LOGIC
    // =================================================================================
    @Transactional
    public Order placeOrder(Long userId, Long addressId, String instruction, String couponCode,
            List<OrderItemRequest> itemsRequest) {

        // 1. Basic Validation
        if (itemsRequest == null || itemsRequest.isEmpty())
            throw new InvalidDataException("No items provided.");
        if (addressId == null)
            throw new InvalidDataException("Delivery Address is required.");

        UserAddress userAddress = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!userAddress.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Address does not belong to this user");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 2. Process Items & Identify Stores
        Order order = new Order();
        order.setUser(user);
        order.setOrderNumber(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.PENDING);

        List<OrderItem> orderItems = new ArrayList<>();
        Set<Store> uniqueStores = new HashSet<>(); // Use Set to avoid duplicates
        double subtotal = 0.0;

        for (OrderItemRequest itemReq : itemsRequest) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            Store store = product.getStore();

            // Check Store Hours
            if (!isStoreOpen(store)) {
                throw new InvalidDataException("Store '" + store.getName() + "' is currently closed.");
            }

            uniqueStores.add(store);

            // Build Item
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setQuantity(itemReq.getQuantity());
            orderItem.setNotes(itemReq.getNotes());

            double price = product.getBasePrice();
            if (itemReq.getVariantId() != null) {
                ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
                if (!variant.getProduct().getProductId().equals(product.getProductId())) {
                    throw new InvalidDataException("Variant invalid for product");
                }
                orderItem.setVariant(variant);
                orderItem.setVariantDetails(variant.getVariantValue());
                price += variant.getPriceAdjustment();
            }

            orderItem.setUnitPrice(price);
            orderItem.setTotalPrice(price * itemReq.getQuantity());

            subtotal += orderItem.getTotalPrice();
            orderItems.add(orderItem);
        }

        // 3. Set Relationships
        // Convert Set to List for the Entity
        order.setStores(new ArrayList<>(uniqueStores));
        order.setOrderItems(orderItems);
        order.setSubtotal(subtotal);

        // 4. Set Location
        order.setDeliveryAddress(userAddress.getAddressLine());
        order.setDeliveryLatitude(userAddress.getLatitude());
        order.setDeliveryLongitude(userAddress.getLongitude());
        order.setSelectedInstruction(instruction);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // ============================================================
        // 5. CALCULATE BEST ROUTE FEE
        // ============================================================

        // A. Find highest fee per KM among all stores involved
        double maxFeePerKm = uniqueStores.stream()
                .mapToDouble(s -> s.getDeliveryFeeKM() != null ? s.getDeliveryFeeKM() : 0.0)
                .max().orElse(0.0);

        // B. Calculate Optimized Total Distance (Store A -> Store B -> User)
        double totalDistanceKm = calculateOptimizedDistance(
                new ArrayList<>(uniqueStores),
                userAddress.getLatitude(),
                userAddress.getLongitude());

        // C. Final Fee
        double deliveryFee = Math.round(totalDistanceKm * maxFeePerKm * 100.0) / 100.0;
        order.setDeliveryFee(deliveryFee);

        // ============================================================

        // 6. Handle Coupon
        double discountAmount = 0.0;
        Coupon validCoupon = null;

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            // Pass the first store for basic validation context
            Store primaryStore = uniqueStores.iterator().next();
            validCoupon = couponService.validateCouponForOrder(couponCode, userId, orderItems, primaryStore);
            discountAmount = couponService.calculateDiscount(validCoupon, subtotal, deliveryFee);

            order.setCouponId(validCoupon.getCouponId());
            order.setDiscountAmount(discountAmount);
        }

        // 7. Total & Save
        double finalTotal = (subtotal + deliveryFee) - discountAmount;
        order.setTotalAmount(Math.max(finalTotal, 0.0));

        Order savedOrder = orderRepository.save(order);

        if (validCoupon != null) {
            couponService.recordUsage(validCoupon, userId, savedOrder.getOrderId(), discountAmount);
        }

        logStatusChange(savedOrder, null, OrderStatus.PENDING, "Order Placed");

        return savedOrder;
    }
    // =================================================================================
    // ORDER MANAGEMENT
    // =================================================================================

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());

        // Handle Delivery Completion (Increment Driver Stats)
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
        logStatusChange(savedOrder, oldStatus, newStatus, "Status updated by user " + userId);

        return savedOrder;
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

    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserUserIdOrderByCreatedAtDesc(userId);
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }

    // ADMIN: Filtered List
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
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        historyRepository.deleteByOrderOrderId(orderId);
        couponUsageRepository.deleteByOrderId(orderId);

        orderRepository.deleteById(orderId);
    }

    // =================================================================================
    // DRIVER & ASSIGNMENT
    // =================================================================================

    @Transactional
    public Order assignDriver(Long orderId, Long driverId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        if (driver.getUserType() != UserType.DRIVER) {
            throw new InvalidDataException("The selected user is not a Driver");
        }

        order.setDriver(driver);

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }

        Order savedOrder = orderRepository.save(order);

        // Notify Driver
        try {
            notificationService.sendNotification(
                    driverId,
                    "New Order Assigned",
                    "You have been assigned to Order #" + order.getOrderNumber(),
                    null,
                    "DRIVER_ASSIGNMENT",
                    orderId);
        } catch (Exception e) {
            System.err.println("Failed to notify driver: " + e.getMessage());
        }

        return savedOrder;
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

    // =================================================================================
    // FEES & COUPONS (PRE-CALCULATION)
    // =================================================================================

    public DeliveryFeeResponse calculateDeliveryFee(Long storeId, Long addressId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        UserAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        double distance = distanceUtil.calculateDistance(
                address.getLatitude(), address.getLongitude(),
                store.getLatitude(), store.getLongitude());

        Double feePerKm = store.getDeliveryFeeKM() != null ? store.getDeliveryFeeKM() : 0.0;
        double deliveryFee = distance * feePerKm;
        deliveryFee = Math.round(deliveryFee * 100.0) / 100.0;

        return new DeliveryFeeResponse(deliveryFee, store.getEstimatedDeliveryTime());
    }

    public CouponCheckResponse verifyCoupon(CouponCheckRequest request) {
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        List<OrderItem> tempItems = new ArrayList<>();
        double subtotal = 0.0;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            double price = product.getBasePrice();
            if (itemReq.getVariantId() != null) {
                ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
                price += variant.getPriceAdjustment();
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

        // Assuming delivery fee is needed for Free Shipping logic, but simple calc for
        // now
        double discount = couponService.calculateDiscount(coupon, subtotal, 0.0);

        return new CouponCheckResponse(
                coupon.getCouponId(),
                coupon.getCode(),
                discount,
                "Coupon Applied Successfully");
    }

    // =================================================================================
    // TRACKING
    // =================================================================================

    public OrderTrackingResponse trackOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderTrackingResponse response = new OrderTrackingResponse();
        response.setOrderId(order.getOrderId());
        response.setStatus(order.getStatus());

        // if (order.getStore() != null) {
        // response.setEstimatedTime(order.getStore().getEstimatedDeliveryTime());
        // }

        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        if (order.getDriver() != null) {
            User driver = order.getDriver();
            response.setDriverId(driver.getUserId());
            response.setDriverName(driver.getName());
            response.setDriverPhone(driver.getPhoneNumber());

            // Full URL for Driver Image (as requested)
            response.setDriverImage(urlUtil.getFullUrl(driver.getProfileImage()));

            response.setDriverVehicle(driver.getVehicleNumber());
            response.setDriverLatitude(driver.getCurrentLocationLat());
            response.setDriverLongitude(driver.getCurrentLocationLng());
        }

        return response;
    }

    // =================================================================================
    // HELPERS
    // =================================================================================

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

    // =================================================================================
    // MULTI-STORE FEE CALCULATION
    // =================================================================================

    public DeliveryFeeResponse calculateMultiStoreFee(List<Long> storeIds, Long addressId) {
        if (storeIds == null || storeIds.isEmpty()) {
            throw new InvalidDataException("No stores provided for fee calculation");
        }

        // 1. Fetch Stores
        List<Store> stores = storeRepository.findAllById(storeIds);
        if (stores.isEmpty()) {
            throw new ResourceNotFoundException("No valid stores found");
        }

        // 2. Fetch User Address
        UserAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        // 3. Find Max Fee Per KM among selected stores
        double maxFeePerKm = stores.stream()
                .mapToDouble(s -> s.getDeliveryFeeKM() != null ? s.getDeliveryFeeKM() : 0.0)
                .max().orElse(0.0);

        // 4. Calculate Optimized Distance (Best Route)
        double totalDistanceKm = calculateOptimizedDistance(stores, address.getLatitude(), address.getLongitude());

        // 5. Calculate Final Fee
        double deliveryFee = Math.round(totalDistanceKm * maxFeePerKm * 100.0) / 100.0;

        // Estimated Time: Pick the longest estimate (simple logic)
        String estimatedTime = stores.get(0).getEstimatedDeliveryTime();

        return new DeliveryFeeResponse(deliveryFee, estimatedTime);
    }

    // =================================================================================
    // ALGORITHM: NEAREST NEIGHBOR (Route Optimization)
    // =================================================================================
    // Logic: User -> Closest Store -> Next Closest Store...
    // We work backward from the user's location to find the total travel path.
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
                break; // Should happen only if coordinates are missing
            }
        }

        return totalDistance;
    }
}