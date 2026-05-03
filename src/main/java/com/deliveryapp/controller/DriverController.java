package com.deliveryapp.controller;

import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.user.DriverLocationResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.mapper.order.OrderMapper; // Import the Mapper
import com.deliveryapp.service.OrderService;
import com.deliveryapp.service.UserService;

import lombok.RequiredArgsConstructor;
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

    // GET ASSIGNED ORDERS
    // Usage: /api/driver/{driverId}/orders?active=true (Current Tasks)
    // Usage: /api/driver/{driverId}/orders?active=false (History)
    @GetMapping("/{driverId}/orders")
    public ResponseEntity<List<OrderResponse>> getDriverOrders(
            @PathVariable Long driverId,
            @RequestParam(defaultValue = "true") Boolean active) {

        List<Order> orders = orderService.getDriverOrders(driverId, active);

        List<OrderResponse> response = orders.stream()
                .map(orderMapper::toOrderResponse) // Use the shared, smart Mapper
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{driverId}/orders/{orderId}/respond")
    public ResponseEntity<OrderResponse> respondToOrder(
            @PathVariable Long driverId,
            @PathVariable Long orderId,
            @RequestParam Boolean accept) {

        Order order = orderService.driverRespondToOrder(orderId, driverId, accept);
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