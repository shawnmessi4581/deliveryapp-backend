package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.order.*;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.UserAddress;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.mapper.order.OrderMapper;
import com.deliveryapp.service.OrderCalculationService;
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Set<String> STAFF_AND_DRIVER_ROLES = Set.of("ROLE_ADMIN", "ROLE_EMPLOYEE", "ROLE_DRIVER");

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final OrderCalculationService orderCalculationService;

    @PostMapping("/calc-fee")
    public ResponseEntity<DeliveryFeeResponse> calculateFee(@RequestBody DeliveryFeeRequest request) {
        return ResponseEntity.ok(orderCalculationService.calculateMultiStoreFee(request));
    }

    @PostMapping("/verify-coupon")
    public ResponseEntity<CouponCheckResponse> verifyCoupon(@RequestBody CouponCheckRequest request) {
        return ResponseEntity.ok(orderCalculationService.verifyCoupon(request));
    }

    /**
     * Checkout preview: same body as /place, same pricing engine, nothing is saved.
     * Returns subtotal, offer discounts per line, delivery fee, coupon discount and the exact total.
     */
    @PostMapping("/quote")
    public ResponseEntity<OrderQuoteResponse> quoteOrder(@RequestBody PlaceOrderRequest request) {
        if (request.getAddressId() == null) {
            throw new InvalidDataException("عنوان التوصيل مطلوب.");
        }
        UserAddress address = orderCalculationService.findUserAddress(request.getAddressId(), request.getUserId());
        OrderQuote quote = orderCalculationService.quote(
                orderCalculationService.priceItems(request.getItems()),
                request.getUserId(),
                request.getCouponCode(),
                address.getLatitude(),
                address.getLongitude());
        return ResponseEntity.ok(orderMapper.toQuoteResponse(quote));
    }

    @PostMapping("/place")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody PlaceOrderRequest request) {
        Order order = orderService.placeOrder(request);
        return ResponseEntity.ok(toResponseForCaller(order));
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
                .map(this::toResponseForCaller)
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
        return ResponseEntity.ok(toResponseForCaller(order));
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

    // 💹 Store prices and payouts only for admin, employee and driver — customers never see the markup
    private OrderResponse toResponseForCaller(Order order) {
        boolean staffOrDriver = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> STAFF_AND_DRIVER_ROLES.contains(role));
        return staffOrDriver ? orderMapper.toOrderResponse(order) : orderMapper.toCustomerOrderResponse(order);
    }
}