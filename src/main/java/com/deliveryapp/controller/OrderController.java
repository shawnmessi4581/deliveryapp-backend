package com.deliveryapp.controller;

import com.deliveryapp.dto.order.*;
import com.deliveryapp.entity.Order;
import com.deliveryapp.mapper.order.OrderMapper;
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @PostMapping("/calc-fee")
    public ResponseEntity<DeliveryFeeResponse> calculateFee(@RequestBody DeliveryFeeRequest request) {
        return ResponseEntity.ok(orderService.calculateMultiStoreFee(request.getStoreIds(), request.getAddressId()));
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
        return ResponseEntity.ok(orderMapper.toOrderResponse(order));
    }

    // Get User History
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getUserOrders(@PathVariable Long userId) {
        List<Order> orders = orderService.getUserOrders(userId);
        List<OrderResponse> responseList = orders.stream()
                .map(orderMapper::toOrderResponse)
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
                request.getUserId());
        return ResponseEntity.ok(orderMapper.toOrderResponse(order));
    }

    // TRACK ORDER (Real-time)
    @GetMapping("/{orderId}/track")
    public ResponseEntity<OrderTrackingResponse> trackOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.trackOrder(orderId));
    }

}