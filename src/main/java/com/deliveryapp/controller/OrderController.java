package com.deliveryapp.controller;

import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.order.PlaceOrderRequest;
import com.deliveryapp.dto.order.UpdateOrderStatusRequest;
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

    @PostMapping("/place")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody PlaceOrderRequest request) {
        // Updated to pass lat, lng, and notes directly to the service
        Order order = orderService.placeOrder(
                request.getUserId(),
                request.getDeliveryAddress(),
                request.getDeliveryLatitude(),
                request.getDeliveryLongitude(),
                request.getNotes()
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
            response.setDriverName(order.getDriver().getName()); // Assuming User has getFullName()
            response.setDriverPhone(order.getDriver().getPhoneNumber());
        }

        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());

        response.setSubtotal(order.getSubtotal());
        response.setDeliveryFee(order.getDeliveryFee());
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