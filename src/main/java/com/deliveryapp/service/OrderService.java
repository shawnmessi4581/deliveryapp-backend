package com.deliveryapp.service;

import com.deliveryapp.dto.order.*;
import com.deliveryapp.entity.*;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import com.deliveryapp.util.DistanceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final DistanceUtil distanceUtil;
    private  final  UserRepository userRepository;
    private final ProductRepository productRepository; // Added
    private final ProductVariantRepository variantRepository; // Added
    // NEW DEPENDENCIES
    private final UserAddressRepository addressRepository;
    private final StoreRepository storeRepository;

    private final CouponService couponService;
    private  final  NotificationService notificationService;
    @Transactional
    public Order placeOrder(Long userId, Long addressId, String instruction, String couponCode, List<OrderItemRequest> itemsRequest) {

        // 1. Validate Input
        if (itemsRequest == null || itemsRequest.isEmpty()) {
            throw new InvalidDataException("Cannot place order. No items provided.");
        }

        // 2. Resolve Address
        if (addressId == null) throw new InvalidDataException("Delivery Address is required.");

        UserAddress userAddress = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + addressId));

        if(!userAddress.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Address does not belong to this user");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 3. Process Items & Validate Store Consistency
        Order order = new Order();
        order.setUser(user);
        order.setOrderNumber(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.PENDING);

        Store orderStore = null; // We will determine the store from the first item
        double subtotal = 0.0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest itemReq : itemsRequest) {
            // Fetch Product from DB to get real price (Security)
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.getProductId()));

            // Store Consistency Check
            if (orderStore == null) {
                orderStore = product.getStore(); // Set the store based on first item
            } else if (!orderStore.getStoreId().equals(product.getStore().getStoreId())) {
                throw new InvalidDataException("All items must be from the same store. Please clear cart and try again.");
            }

            // Build Order Item
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setQuantity(itemReq.getQuantity());
            orderItem.setNotes(itemReq.getNotes());

            // Price Calculation
            double price = product.getBasePrice();

            // Handle Variant
            if (itemReq.getVariantId() != null) {
                ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + itemReq.getVariantId()));

                // Ensure variant belongs to product
                if(!variant.getProduct().getProductId().equals(product.getProductId())) {
                    throw new InvalidDataException("Variant does not belong to this product");
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

        order.setStore(orderStore);
        order.setOrderItems(orderItems);
        order.setSubtotal(subtotal);

        // 4. Set Location
        order.setDeliveryAddress(userAddress.getAddressLine());
        order.setDeliveryLatitude(userAddress.getLatitude());
        order.setDeliveryLongitude(userAddress.getLongitude());
        order.setSelectedInstruction(instruction);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 5. Calculate Delivery Fee
        double deliveryFee = 0.0;
        if (orderStore.getLatitude() != null && orderStore.getLongitude() != null &&
                userAddress.getLatitude() != null && userAddress.getLongitude() != null) {

            double distanceInKm = distanceUtil.calculateDistance(
                    userAddress.getLatitude(), userAddress.getLongitude(),
                    orderStore.getLatitude(), orderStore.getLongitude()
            );

            Double feePerKm = orderStore.getDeliveryFeeKM();
            if(feePerKm == null) feePerKm = 0.0;

            deliveryFee = distanceInKm * feePerKm;
            deliveryFee = Math.round(deliveryFee * 100.0) / 100.0;
        }
        order.setDeliveryFee(deliveryFee);

        // 6. Handle Coupon Logic
        double discountAmount = 0.0;
        Coupon validCoupon = null;

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            // Note: You need to update `couponService.validateCoupon` to accept List<OrderItem> instead of Cart
            // For now, I will assume you refactor that or temporarily comment it out.
            // validCoupon = couponService.validateCoupon(couponCode, userId, orderItems, orderStore);
            // discountAmount = couponService.calculateDiscount(validCoupon, subtotal, deliveryFee);

            // Temporary simple check if you haven't refactored coupon service yet:
            validCoupon = couponService.validateCouponForOrder(couponCode, userId, orderItems, orderStore);
            discountAmount = couponService.calculateDiscount(validCoupon, subtotal, deliveryFee);

            order.setCouponId(validCoupon.getCouponId());
            order.setDiscountAmount(discountAmount);
        }

        // 7. Final Total
        double finalTotal = (subtotal + deliveryFee) - discountAmount;
        order.setTotalAmount(Math.max(finalTotal, 0.0));

        // 8. Save
        Order savedOrder = orderRepository.save(order);

        // 9. Coupon Usage
        if(validCoupon != null) {
            couponService.recordUsage(validCoupon, userId, savedOrder.getOrderId(), discountAmount);
        }

        // 10. No need to clear cartService anymore since it's local

        return savedOrder;
    }


    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());

        if(newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
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

    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserUserIdOrderByCreatedAtDesc(userId);
    }

    // --- ADMIN: Get All Orders with Filters ---
    public List<Order> getAdminOrders(OrderStatus status, LocalDate startDate, LocalDate endDate) {

        // 1. If Date Range is provided
        if (startDate != null && endDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay(); // 00:00:00
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59); // 23:59:59

            if (status != null) {
                // Filter by Status AND Date
                return orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, startDateTime, endDateTime);
            } else {
                // Filter by Date only
                return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDateTime, endDateTime);
            }
        }

        // 2. If only Status is provided
        if (status != null) {
            return orderRepository.findByStatusOrderByCreatedAtDesc(status);
        }

        // 3. No filters (Return all)
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    // --- ADMIN: Delete Order ---
    @Transactional
    public void deleteOrder(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        // Because Order has CascadeType.ALL on OrderItems, those will be deleted automatically.
        // However, we must ensure OrderStatusHistory is also handled if it's not cascaded.
        // Assuming your DB or Entity setup handles cascade, otherwise:
        // historyRepository.deleteByOrderOrderId(orderId);

        orderRepository.deleteById(orderId);
    }
    // Get Single Order (Admin or User)
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }

    @Transactional
    public Order assignDriver(Long orderId, Long driverId) {
        // 1. Fetch Order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        // 2. Fetch Driver
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        // 3. Validate User is actually a Driver
        if (driver.getUserType() != UserType.DRIVER) {
            throw new InvalidDataException("The selected user is not a Driver");
        }

        // 4. Assign
        order.setDriver(driver);

        // 5. Auto-update status to CONFIRMED (or PREPARING) if it was just PENDING
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }

        Order savedOrder = orderRepository.save(order);

        // 6. Notify Driver (Optional but recommended)
        try {
            notificationService.sendNotification(
                    driverId,
                    "New Order Assigned",
                    "You have been assigned to Order #" + order.getOrderNumber(),
                    null,
                    "DRIVER_ASSIGNMENT",
                    orderId
            );
        } catch (Exception e) {
            // Ignore notification errors to ensure transaction completes
            System.err.println("Failed to notify driver: " + e.getMessage());
        }

        return savedOrder;
    }

    public DeliveryFeeResponse calculateDeliveryFee(Long storeId, Long addressId) {
        // 1. Fetch Store & Address
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        UserAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        // 2. Calculate Distance
        double distance = distanceUtil.calculateDistance(
                address.getLatitude(), address.getLongitude(),
                store.getLatitude(), store.getLongitude()
        );

        // 3. Calculate Fee
        Double feePerKm = store.getDeliveryFeeKM() != null ? store.getDeliveryFeeKM() : 0.0;
        double deliveryFee = distance * feePerKm;
        deliveryFee = Math.round(deliveryFee * 100.0) / 100.0; // Round to 2 decimals

        return new DeliveryFeeResponse(deliveryFee, store.getEstimatedDeliveryTime());
    }
    public CouponCheckResponse verifyCoupon(CouponCheckRequest request) {
        // 1. Fetch Store
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        // 2. Build Temporary Items & Calculate Subtotal (Securely)
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

        // 3. Call CouponService to Validate
        Coupon coupon = couponService.validateCouponForOrder(
                request.getCode(),
                request.getUserId(),
                tempItems,
                store
        );

        // 4. Calculate Discount
        // Note: Delivery Fee is needed for "FREE_DELIVERY" coupons.
        // Ideally we verify the address here too, but for simplicity let's assume 0 fee
        // or require addressId in CouponCheckRequest if you support Free Shipping coupons.
        double deliveryFee = 0.0;
        double discount = couponService.calculateDiscount(coupon, subtotal, deliveryFee);

        return new CouponCheckResponse(
                coupon.getCouponId(),
                coupon.getCode(),
                discount,
                "Coupon Applied Successfully"
        );
    }
    // ... inside OrderService ...

    public OrderTrackingResponse trackOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderTrackingResponse response = new OrderTrackingResponse();

        // Basic Info
        response.setOrderId(order.getOrderId());
        response.setStatus(order.getStatus());

        if (order.getStore() != null) {
            response.setEstimatedTime(order.getStore().getEstimatedDeliveryTime());
        }

        // --- User Location / Destination ---
        // These are saved in the Order entity at the time of placement
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        // --- Driver Info ---
        if (order.getDriver() != null) {
            User driver = order.getDriver();
            response.setDriverId(driver.getUserId());
            response.setDriverName(driver.getName());
            response.setDriverPhone(driver.getPhoneNumber());
            response.setDriverVehicle(driver.getVehicleNumber());

            // Driver's Real-time Location
            response.setDriverLatitude(driver.getCurrentLocationLat());
            response.setDriverLongitude(driver.getCurrentLocationLng());
        }

        return response;
    }
    // GET DRIVER ORDERS
    public List<Order> getDriverOrders(Long driverId, Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) {
            // Active = CONFIRMED, PREPARING, OUT_FOR_DELIVERY
            List<OrderStatus> activeStatuses = Arrays.asList(
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.OUT_FOR_DELIVERY
            );
            return orderRepository.findByDriverUserIdAndStatusInOrderByCreatedAtDesc(driverId, activeStatuses);
        } else {
            // Return All (History)
            return orderRepository.findByDriverUserIdOrderByCreatedAtDesc(driverId);
        }
    }
}