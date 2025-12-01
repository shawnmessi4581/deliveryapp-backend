package com.deliveryapp.mapper;

import com.deliveryapp.dto.cart.CartItemResponse;
import com.deliveryapp.dto.cart.CartResponse;
import com.deliveryapp.entity.*;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class EntityDtoMapper {

    // --- CART MAPPING ---
    public CartResponse toCartResponse(Cart cart) {
        CartResponse response = new CartResponse();
        response.setCartId(cart.getCartId());

        if (cart.getStore() != null) {
            response.setStoreId(cart.getStore().getStoreId());
            response.setStoreName(cart.getStore().getName());
        }

        double total = 0.0;
        if (cart.getItems() != null) {
            response.setItems(cart.getItems().stream()
                    .map(this::toCartItemResponse)
                    .collect(Collectors.toList()));

            // simple calculation for the view
            total = response.getItems().stream().mapToDouble(CartItemResponse::getTotalPrice).sum();
        }
        response.setTotalEstimatedPrice(total);

        return response;
    }

    private CartItemResponse toCartItemResponse(CartItem item) {
        CartItemResponse response = new CartItemResponse();
        response.setCartItemId(item.getCartItemId());
        response.setProductId(item.getProduct().getProductId());
        response.setProductName(item.getProduct().getName());
        response.setQuantity(item.getQuantity());
        response.setNotes(item.getNotes());

        // Calculate Price (Base + Variant)
        double price = item.getProduct().getBasePrice();
        if (item.getVariant() != null) {
            price += item.getVariant().getPriceAdjustment();
            response.setVariantName(item.getVariant().getVariantValue());
        }

        response.setUnitPrice(price);
        response.setTotalPrice(price * item.getQuantity());

        return response;
    }

    // --- ORDER MAPPING ---
    public OrderResponse toOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());
        response.setStatus(order.getStatus().name());
        response.setStoreName(order.getStore().getName());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setSubtotal(order.getSubtotal());
        response.setDeliveryFee(order.getDeliveryFee());
        response.setTotalAmount(order.getTotalAmount());

        if (order.getOrderItems() != null) {
            response.setItems(order.getOrderItems().stream()
                    .map(this::toOrderItemResponse)
                    .collect(Collectors.toList()));
        }

        return response;
    }

    private OrderItemResponse toOrderItemResponse(OrderItem item) {
        OrderItemResponse response = new OrderItemResponse();
        response.setProductName(item.getProductName());
        response.setVariantDetails(item.getVariantDetails());
        response.setQuantity(item.getQuantity());
        response.setUnitPrice(item.getUnitPrice());
        response.setTotalPrice(item.getTotalPrice());
        return response;
    }
}