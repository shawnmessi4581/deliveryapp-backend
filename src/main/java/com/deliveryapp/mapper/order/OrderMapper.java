package com.deliveryapp.mapper.order;

import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final CatalogMapper catalogMapper;
    private final UserMapper userMapper;

    public OrderResponse toOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());

        // --- Store Info (Reusing CatalogMapper) ---
        if (order.getStore() != null) {
            // Get full store details (address, logo, etc.)
            response.setStore(catalogMapper.toStoreResponse(order.getStore()));

            // Set flat fields for backward compatibility
            response.setStoreId(order.getStore().getStoreId());
            response.setStoreName(order.getStore().getName());
        }

        // --- Driver Info ---
        if (order.getDriver() != null) {
            response.setDriverId(order.getDriver().getUserId());
            response.setDriverName(order.getDriver().getName());
            response.setDriverPhone(order.getDriver().getPhoneNumber());
        }

        // --- Customer Info (Reusing UserMapper) ---
        if (order.getUser() != null) {
            response.setCustomerDetails(userMapper.toOrderCustomerResponse(order.getUser()));
        }

        // --- Location & Status ---
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());

        // --- Financials ---
        response.setSubtotal(order.getSubtotal());
        response.setDeliveryFee(order.getDeliveryFee());
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