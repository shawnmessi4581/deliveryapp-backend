package com.deliveryapp.controller;

import com.deliveryapp.dto.order.OrderCustomerResponse;
import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.User;
import com.deliveryapp.service.OrderService;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
public class DriverController {

    private final OrderService orderService;
    private final UrlUtil urlUtil;

    // GET ASSIGNED ORDERS
    // Usage: /api/driver/{driverId}/orders?active=true (Current Tasks)
    // Usage: /api/driver/{driverId}/orders?active=false (History)
    @GetMapping("/{driverId}/orders")
    public ResponseEntity<List<OrderResponse>> getDriverOrders(
            @PathVariable Long driverId,
            @RequestParam(defaultValue = "true") Boolean active) {

        List<Order> orders = orderService.getDriverOrders(driverId, active);

        List<OrderResponse> response = orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // ==================== MAIN MAPPER ====================
    private OrderResponse mapToOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());

        // --- 1. Map Full Store Details (Pickup Location) ---
        if (order.getStore() != null) {
            // A. Populate the full object (New)
            response.setStore(mapToStoreResponse(order.getStore()));

            // B. Populate flat fields (Existing)
            response.setStoreId(order.getStore().getStoreId());
            response.setStoreName(order.getStore().getName());
        }

        // --- 2. Customer Info (Drop-off Contact) ---
        if (order.getUser() != null) {
            User customer = order.getUser();
            OrderCustomerResponse customerDto = new OrderCustomerResponse();
            customerDto.setUserId(customer.getUserId());
            customerDto.setName(customer.getName());
            customerDto.setPhoneNumber(customer.getPhoneNumber());
            customerDto.setProfileAddress(customer.getAddress());
            response.setCustomerDetails(customerDto);
        }

        // --- 3. Delivery Location (Drop-off Coordinates) ---
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        // --- 4. Status & Dates ---
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());

        // --- 5. Financials ---
        response.setSubtotal(order.getSubtotal());
        response.setDeliveryFee(order.getDeliveryFee());
        response.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : 0.0);
        response.setCouponId(order.getCouponId());
        response.setTotalAmount(order.getTotalAmount());

        // --- 6. Items ---
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

    // ==================== HELPER: STORE MAPPER ====================
    private StoreResponse mapToStoreResponse(Store store) {
        StoreResponse dto = new StoreResponse();
        dto.setStoreId(store.getStoreId());
        dto.setName(store.getName());
        dto.setDescription(store.getDescription());

        // Contact Info (Crucial for Driver)
        dto.setPhone(store.getPhone());

        // Pickup Address & Location
        dto.setAddress(store.getAddress());
        dto.setLatitude(store.getLatitude());
        dto.setLongitude(store.getLongitude());

        // Images (With Full URLs)
        dto.setLogo(urlUtil.getFullUrl(store.getLogo()));
        dto.setCoverImage(urlUtil.getFullUrl(store.getCoverImage()));

        return dto;
    }
}