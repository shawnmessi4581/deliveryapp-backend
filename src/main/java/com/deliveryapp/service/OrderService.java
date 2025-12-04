package com.deliveryapp.service;

import com.deliveryapp.entity.*;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import com.deliveryapp.util.DistanceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final OrderStatusHistoryRepository historyRepository;
    private final DistanceUtil distanceUtil;

    // NEW DEPENDENCIES
    private final UserAddressRepository addressRepository;
    private final CouponService couponService;

    @Transactional
    public Order placeOrder(Long userId, Long addressId, String instruction, String couponCode) {
        Cart cart = cartService.getCartByUser(userId);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new InvalidDataException("Cannot place order. Cart is empty.");
        }

        // 1. Resolve & Validate Address
        UserAddress userAddress = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + addressId));

        // Security check: Ensure address belongs to the user placing the order
        if(!userAddress.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Address does not belong to this user");
        }

        Store store = cart.getStore();

        Order order = new Order();
        order.setUser(cart.getUser());
        order.setStore(store);
        order.setOrderNumber(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.PENDING);

        // 2. Set Location Details from UserAddress
        order.setDeliveryAddress(userAddress.getAddressLine());
        order.setDeliveryLatitude(userAddress.getLatitude());
        order.setDeliveryLongitude(userAddress.getLongitude());

        // 3. Set Instruction
        order.setSelectedInstruction(instruction);

        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 4. Calculate Subtotal & Process Items
        double subtotal = 0.0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setVariant(cartItem.getVariant());
            orderItem.setProductName(cartItem.getProduct().getName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setNotes(cartItem.getNotes());

            double price = cartItem.getProduct().getBasePrice();

            // Add variant price if exists
            if (cartItem.getVariant() != null) {
                price += cartItem.getVariant().getPriceAdjustment();
                orderItem.setVariantDetails(cartItem.getVariant().getVariantValue());
            }

            orderItem.setUnitPrice(price);
            orderItem.setTotalPrice(price * cartItem.getQuantity());

            subtotal += orderItem.getTotalPrice();
            orderItems.add(orderItem);
        }

        order.setOrderItems(orderItems);
        order.setSubtotal(subtotal);

        // 5. Calculate Delivery Fee based on Distance
        double deliveryFee = 0.0;

        if (store.getLatitude() != null && store.getLongitude() != null &&
                userAddress.getLatitude() != null && userAddress.getLongitude() != null) {

            double distanceInKm = distanceUtil.calculateDistance(
                    userAddress.getLatitude(), userAddress.getLongitude(),
                    store.getLatitude(), store.getLongitude()
            );

            Double feePerKm = store.getDeliveryFeeKM();
            if(feePerKm == null) feePerKm = 0.0;

            deliveryFee = distanceInKm * feePerKm;

            // Round to 2 decimal places
            deliveryFee = Math.round(deliveryFee * 100.0) / 100.0;
        }

        // 6. Handle Coupon Logic
        double discountAmount = 0.0;
        Coupon validCoupon = null;

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            // Validate the coupon against the user and the cart contents
            validCoupon = couponService.validateCoupon(couponCode, userId, cart);

            // Calculate exact discount value
            discountAmount = couponService.calculateDiscount(validCoupon, subtotal, deliveryFee);

            // Set fields in Order
            order.setCouponId(validCoupon.getCouponId());
            order.setDiscountAmount(discountAmount);
        }

        order.setDeliveryFee(deliveryFee);

        // Final Total Calculation
        double finalTotal = (subtotal + deliveryFee) - discountAmount;
        // Ensure total doesn't drop below zero (unlikely due to validation logic, but safe to add)
        order.setTotalAmount(Math.max(finalTotal, 0.0));

        // 7. Save Order
        Order savedOrder = orderRepository.save(order);

        // 8. Record Coupon Usage
        if(validCoupon != null) {
            // We need the savedOrder ID to record usage
            couponService.recordUsage(validCoupon, userId, savedOrder.getOrderId(), discountAmount);
        }

        // 9. Post-Order actions
        logStatusChange(savedOrder, null, OrderStatus.PENDING, "Order Placed");
        cartService.clearCart(userId);

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
}