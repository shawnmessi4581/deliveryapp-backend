package com.deliveryapp.controller;

import com.deliveryapp.dto.order.*;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    // 1. Calculate Delivery Fee
    @PostMapping("/calc-fee")
    public ResponseEntity<DeliveryFeeResponse> calculateFee(@RequestBody DeliveryFeeRequest request) {
        return ResponseEntity.ok(orderService.calculateDeliveryFee(request.getStoreId(), request.getAddressId()));
    }

    // 2. Verify Coupon
    @PostMapping("/verify-coupon")
    public ResponseEntity<CouponCheckResponse> verifyCoupon(@RequestBody CouponCheckRequest request) {
        return ResponseEntity.ok(orderService.verifyCoupon(request));
    }
    @PostMapping("/place")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody PlaceOrderRequest request) {
        Order order = orderService.placeOrder(
                request.getUserId(),
                request.getAddressId(),
                request.getInstruction(),
                request.getCouponCode(),
                request.getItems() // Pass the list
        );
        return ResponseEntity.ok(mapToOrderResponse(order));
    }
    // Get User History
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getUserOrders(@PathVariable Long userId) {
        List<Order> orders = orderService.getUserOrders(userId);
        List<OrderResponse> responseList = orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseList);
    }

    // Update Status
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusRequest request) {

        Order order = orderService.updateOrderStatus(
                orderId,
                request.getNewStatus(),
                request.getUserId()
        );
        return ResponseEntity.ok(mapToOrderResponse(order));
    }
    // TRACK ORDER (Real-time)
    @GetMapping("/{orderId}/track")
    public ResponseEntity<OrderTrackingResponse> trackOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.trackOrder(orderId));
    }

    // --- Manual Mapping Logic (Entity -> DTO) ---
    private OrderResponse mapToOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());

        if (order.getStore() != null) {
            response.setStoreId(order.getStore().getStoreId());
            response.setStoreName(order.getStore().getName());
        }

        if (order.getDriver() != null) {
            response.setDriverId(order.getDriver().getUserId());
            response.setDriverName(order.getDriver().getName());
            response.setDriverPhone(order.getDriver().getPhoneNumber());
        }

        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());

        // --- FINANCIALS ---
        response.setSubtotal(order.getSubtotal());
        response.setDeliveryFee(order.getDeliveryFee());

        // [ADDED] Map the Discount so the user sees how much they saved
        response.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : 0.0);
        response.setCouponId(order.getCouponId());

        response.setTotalAmount(order.getTotalAmount());

        List<OrderItemResponse> itemResponses;
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            itemResponses = order.getOrderItems().stream().map(item -> {
                OrderItemResponse r = new OrderItemResponse();
                r.setProductName(item.getProductName());
                r.setVariantDetails(item.getVariantDetails());
                r.setQuantity(item.getQuantity());
                r.setUnitPrice(item.getUnitPrice());
                r.setTotalPrice(item.getTotalPrice());
                r.setNotes(item.getNotes());
                return r;
            }).collect(Collectors.toList());
        } else {
            itemResponses = Collections.emptyList();
        }

        response.setItems(itemResponses);

        return response;
    }
}