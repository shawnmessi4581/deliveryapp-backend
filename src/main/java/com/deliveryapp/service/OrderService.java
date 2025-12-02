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
    private final DistanceUtil distanceUtil; // Inject Utility


    @Transactional
    public Order placeOrder(Long userId, String address, Double lat, Double lng, String notes) {
        Cart cart = cartService.getCartByUser(userId);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new InvalidDataException("Cannot place order. Cart is empty.");
        }

        // Validate Location presence
        if(lat == null || lng == null) {
            throw new InvalidDataException("Delivery location (latitude/longitude) is required.");
        }

        Store store = cart.getStore();

        Order order = new Order();
        order.setUser(cart.getUser());
        order.setStore(store);
        order.setOrderNumber(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.PENDING);
        order.setDeliveryAddress(address);
        order.setDeliveryLatitude(lat);
        order.setDeliveryLongitude(lng);
        order.setNotes(notes);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

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

        // --- NEW: Calculate Delivery Fee based on Distance ---
        double deliveryFee = 0.0;

        if (store.getLatitude() != null && store.getLongitude() != null) {
            double distanceInKm = distanceUtil.calculateDistance(
                    lat, lng,
                    store.getLatitude(), store.getLongitude()
            );

            Double feePerKm = store.getDeliveryFeeKM();
            if(feePerKm == null) feePerKm = 0.0;

            deliveryFee = distanceInKm * feePerKm;

            // Round to 2 decimal places
            deliveryFee = Math.round(deliveryFee * 100.0) / 100.0;
        }

        order.setDeliveryFee(deliveryFee);
        order.setTotalAmount(subtotal + deliveryFee);

        Order savedOrder = orderRepository.save(order);
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