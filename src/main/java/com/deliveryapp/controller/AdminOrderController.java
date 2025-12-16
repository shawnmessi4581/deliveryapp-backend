package com.deliveryapp.controller;


import com.deliveryapp.dto.order.OrderCustomerResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.order.UpdateOrderStatusRequest;
import com.deliveryapp.dto.order.OrderItemResponse;

import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    // 1. GET ALL ORDERS (With Optional Filters)
    // Usage: GET /api/admin/orders?status=PENDING&startDate=2024-01-01&endDate=2024-01-31
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<Order> orders = orderService.getAdminOrders(status, startDate, endDate);

        List<OrderResponse> response = orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // 2. UPDATE STATUS
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusRequest request) {

        // Pass a dummy userId (e.g., 0) or extract Admin ID from token if needed for logging
        Order updatedOrder = orderService.updateOrderStatus(orderId, request.getNewStatus(), request.getUserId());
        return ResponseEntity.ok(mapToOrderResponse(updatedOrder));
    }
    // GET SINGLE ORDER DETAILS
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderDetails(@PathVariable Long orderId) {
        Order order = orderService.getOrderById(orderId);
        return ResponseEntity.ok(mapToOrderResponse(order));
    }

    // 3. DELETE ORDER
    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> deleteOrder(@PathVariable Long orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.ok("Order deleted successfully");
    }
    // ==================== MAPPER (Updated with Discount & Customer Info) ====================
    private OrderResponse mapToOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());

        // --- Store Info ---
        if (order.getStore() != null) {
            response.setStoreId(order.getStore().getStoreId());
            response.setStoreName(order.getStore().getName());
        }

        // --- Driver Info ---
        if (order.getDriver() != null) {
            response.setDriverId(order.getDriver().getUserId());
            response.setDriverName(order.getDriver().getName());
            response.setDriverPhone(order.getDriver().getPhoneNumber());
        }

        // --- Customer Info ---
        if (order.getUser() != null) {
            User customer = order.getUser();
            OrderCustomerResponse customerDto = new OrderCustomerResponse();
            customerDto.setUserId(customer.getUserId());
            customerDto.setName(customer.getName());
            customerDto.setPhoneNumber(customer.getPhoneNumber());
            customerDto.setProfileAddress(customer.getAddress());

            response.setCustomerDetails(customerDto);
        }

        // --- Location & Status ---
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());

        // --- FINANCIALS (Updated) ---
        response.setSubtotal(order.getSubtotal());
        response.setDeliveryFee(order.getDeliveryFee());

        // [ADDED] Map Discount for Admin View
        response.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : 0.0);
        response.setCouponId(order.getCouponId());

        response.setTotalAmount(order.getTotalAmount());

        // --- Order Items ---
        if (order.getOrderItems() != null) {
            response.setItems(order.getOrderItems().stream().map(item -> {
                OrderItemResponse r = new OrderItemResponse();
                r.setProductName(item.getProductName());
                r.setVariantDetails(item.getVariantDetails());
                r.setQuantity(item.getQuantity());
                r.setUnitPrice(item.getUnitPrice());
                r.setTotalPrice(item.getTotalPrice());
                r.setNotes(item.getNotes());
                return r;
            }).collect(Collectors.toList()));
        } else {
            response.setItems(Collections.emptyList());
        }

        return response;
    }

}