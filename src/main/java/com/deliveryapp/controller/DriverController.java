package com.deliveryapp.controller;

import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.mapper.order.OrderMapper; // Import the Mapper
import com.deliveryapp.service.OrderService;
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
}