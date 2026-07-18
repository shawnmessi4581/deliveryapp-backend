package com.deliveryapp.service;

import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.order.VendorOrderResponse; // 🟢 Import this
import com.deliveryapp.dto.websocket.OrderWebSocketEvent;
import com.deliveryapp.entity.Order;
import com.deliveryapp.mapper.order.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final OrderMapper orderMapper;

    public void broadcastOrderCreated(Order order) {
        broadcast(order, "CREATED");
    }

    public void broadcastOrderUpdated(Order order) {
        broadcast(order, "UPDATED");
    }

    public void broadcastOrderDeleted(Long orderId, List<Long> storeIds) {
        OrderWebSocketEvent event = new OrderWebSocketEvent("DELETED", orderId, null);

        // Broadcast to Global Admin
        safeSend("/topic/orders", event);

        // Broadcast to specific stores
        if (storeIds != null) {
            storeIds.forEach(storeId -> safeSend("/topic/store-orders/" + storeId, event));
        }
    }

    private void broadcast(Order order, String action) {
        // 1. Send the FULL response to Global Admin Topic
        OrderResponse fullResponse = orderMapper.toOrderResponse(order);
        OrderWebSocketEvent globalEvent = new OrderWebSocketEvent(action, order.getOrderId(), fullResponse);
        safeSend("/topic/orders", globalEvent);

        // 2. 🟢 FIX: Send the FILTERED response to Specific Store Topics
        if (order.getStores() != null) {
            order.getStores().forEach(store -> {
                Long storeId = store.getStoreId();

                // Use the dedicated Vendor mapper to hide other stores' items and sensitive
                // customer info
                VendorOrderResponse vendorResponse = orderMapper.toVendorOrderResponse(order, storeId);
                OrderWebSocketEvent storeEvent = new OrderWebSocketEvent(action, order.getOrderId(), vendorResponse);

                safeSend("/topic/store-orders/" + storeId, storeEvent);
            });
        }
    }

    private void safeSend(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            System.err.println("Failed to broadcast to " + destination + ": " + e.getMessage());
        }
    }
}