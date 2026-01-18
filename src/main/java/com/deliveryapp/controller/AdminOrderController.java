package com.deliveryapp.controller;

import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.order.UpdateOrderStatusRequest;
import com.deliveryapp.entity.Order;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.mapper.order.OrderMapper; // Import the Mapper
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper; // Inject Mapper

    // 1. GET ALL ORDERS (With Optional Filters)
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Order> orders = orderService.getAdminOrders(status, startDate, endDate);

        List<OrderResponse> response = orders.stream()
                .map(orderMapper::toOrderResponse) // Use Mapper
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // 2. UPDATE STATUS
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusRequest request) {

        Order updatedOrder = orderService.updateOrderStatus(orderId, request.getNewStatus(), request.getUserId());
        return ResponseEntity.ok(orderMapper.toOrderResponse(updatedOrder)); // Use Mapper
    }

    // 3. GET SINGLE ORDER DETAILS
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderDetails(@PathVariable Long orderId) {
        Order order = orderService.getOrderById(orderId);
        return ResponseEntity.ok(orderMapper.toOrderResponse(order)); // Use Mapper
    }

    // 4. DELETE ORDER
    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> deleteOrder(@PathVariable Long orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.ok("Order deleted successfully");
    }
}