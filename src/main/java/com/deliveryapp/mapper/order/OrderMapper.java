package com.deliveryapp.mapper.order;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
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

        // --- MAP LIST OF STORES ---
        if (order.getStores() != null && !order.getStores().isEmpty()) {
            List<StoreResponse> storeDtos = order.getStores().stream()
                    .map(catalogMapper::toStoreResponse)
                    .collect(Collectors.toList());
            response.setStores(storeDtos);

            // Set first store name for flat display if needed
            response.setStoreName(
                    order.getStores().get(0).getName() + (order.getStores().size() > 1 ? " & others" : ""));
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