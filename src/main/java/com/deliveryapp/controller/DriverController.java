package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.user.DriverLocationResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.mapper.order.OrderMapper; // Import the Mapper
import com.deliveryapp.service.DriverOrderService;
import com.deliveryapp.service.OrderService;
import com.deliveryapp.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
public class DriverController {

    private final OrderService orderService;
    private final OrderMapper orderMapper; // Inject Mapper
    private final UserService userService; // 👉 Inject this
    private final DriverOrderService driverOrderService;

    // 🟢 GET DRIVER ORDERS (Paginated + Search)
    @GetMapping("/{driverId}/orders")
    public ResponseEntity<PagedResponse<OrderResponse>> getDriverOrders(
            @PathVariable Long driverId,
            @RequestParam(defaultValue = "true") Boolean active,
            @RequestParam(required = false) String orderNumber, // Added
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orderPage = driverOrderService.getDriverOrders(driverId, active, orderNumber, pageable);

        List<OrderResponse> content = orderPage.getContent().stream()
                .map(orderMapper::toOrderResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse<>(
                content, orderPage.getNumber(), orderPage.getSize(),
                orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast()));
    }

    @PatchMapping("/{driverId}/orders/{orderId}/respond")
    public ResponseEntity<OrderResponse> respondToOrder(
            @PathVariable Long driverId,
            @PathVariable Long orderId,
            @RequestParam Boolean accept) {

        Order order = driverOrderService.driverRespondToOrder(orderId, driverId, accept);
        return ResponseEntity.ok(orderMapper.toOrderResponse(order));
    }
    // 2. TOGGLE AVAILABILITY (ONLINE / OFFLINE)

    @PatchMapping("/{driverId}/availability")
    public ResponseEntity<String> updateAvailability(
            @PathVariable Long driverId,
            @RequestParam Boolean isAvailable) {

        userService.updateDriverAvailability(driverId, isAvailable);
        String status = isAvailable ? "Online" : "Offline";
        return ResponseEntity.ok("تم تحديث الحالة إلى " + status);
    }

    @PatchMapping("/{driverId}/location")
    public ResponseEntity<String> updateLocation(
            @PathVariable Long driverId,
            @RequestParam Double lat,
            @RequestParam Double lng) {

        userService.updateDriverLocation(driverId, lat, lng);
        return ResponseEntity.ok("تم تحديث الموقع");
    }

    @GetMapping("/{driverId}/location")
    public ResponseEntity<DriverLocationResponse> getDriverLocation(@PathVariable Long driverId) {
        return ResponseEntity.ok(userService.getDriverLocation(driverId));
    }

    // 🟢 NEW: Driver marks order as DELIVERED
    @PatchMapping("/{driverId}/orders/{orderId}/deliver")
    public ResponseEntity<OrderResponse> markOrderAsDelivered(
            @PathVariable Long driverId,
            @PathVariable Long orderId) {

        // Call the existing OrderService method
        Order updatedOrder = orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED, driverId);

        return ResponseEntity.ok(orderMapper.toOrderResponse(updatedOrder));
    }
}