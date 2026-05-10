package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.order.*;
import com.deliveryapp.entity.Order;
import com.deliveryapp.mapper.order.OrderMapper;
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @PostMapping("/verify-coupon")
    public ResponseEntity<CouponCheckResponse> verifyCoupon(@RequestBody CouponCheckRequest request) {
        return ResponseEntity.ok(orderService.verifyCoupon(request));
    }

    @PostMapping("/place")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody PlaceOrderRequest request) {
        Order order = orderService.placeOrder(request);
        return ResponseEntity.ok(orderMapper.toOrderResponse(order));
    }

    // 🟢 GET USER HISTORY (Paginated + Search)
    @GetMapping("/user/{userId}")
    public ResponseEntity<PagedResponse<OrderResponse>> getUserOrders(
            @PathVariable Long userId,
            @RequestParam(required = false) String orderNumber, // Added
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orderPage = orderService.getUserOrders(userId, orderNumber, pageable);

        List<OrderResponse> content = orderPage.getContent().stream()
                .map(orderMapper::toOrderResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse<>(
                content, orderPage.getNumber(), orderPage.getSize(),
                orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast()));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusRequest request) {

        Order order = orderService.updateOrderStatus(orderId, request.getNewStatus(), request.getUserId());
        return ResponseEntity.ok(orderMapper.toOrderResponse(order));
    }

    @GetMapping("/{orderId}/track")
    public ResponseEntity<OrderTrackingResponse> trackOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.trackOrder(orderId));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> cancelOrder(@PathVariable Long orderId) {
        Long userId = ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaim("userId");
        orderService.cancelOrder(orderId, userId);
        return ResponseEntity.ok("تم إلغاء الطلب بنجاح");
    }
}