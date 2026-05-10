package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.order.UpdateOrderStatusRequest;
import com.deliveryapp.entity.Order;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.mapper.order.OrderMapper;
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final OrderMapper orderMapper;

    // 1. GET ALL ORDERS (Paginated with Optional Filters + Search)
    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponse>> getAllOrders(
            @RequestParam(required = false) String orderNumber, // Added
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Order> orderPage = orderService.getAdminOrders(orderNumber, status, startDate, endDate, pageable);

        return ResponseEntity.ok(createPagedResponse(orderPage));
    }

    // 2. UPDATE STATUS
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusRequest request) {

        Order updatedOrder = orderService.updateOrderStatus(orderId, request.getNewStatus(), request.getUserId());
        return ResponseEntity.ok(orderMapper.toOrderResponse(updatedOrder));
    }

    // 3. GET SINGLE ORDER DETAILS
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderDetails(@PathVariable Long orderId) {
        Order order = orderService.getOrderById(orderId);
        return ResponseEntity.ok(orderMapper.toOrderResponse(order));
    }

    // 4. DELETE ORDER
    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> deleteOrder(@PathVariable Long orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.ok("تم حذف الطلب بنجاح");
    }

    // --- HELPER METHOD ---
    private PagedResponse<OrderResponse> createPagedResponse(Page<Order> orderPage) {
        List<OrderResponse> content = orderPage.getContent().stream()
                .map(orderMapper::toOrderResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content, orderPage.getNumber(), orderPage.getSize(),
                orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast());
    }
}