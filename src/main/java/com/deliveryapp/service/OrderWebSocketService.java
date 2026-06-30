package com.deliveryapp.service;

import com.deliveryapp.dto.order.OrderResponse;
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
        safeSend("/topic/orders", event);

        if (storeIds != null) {
            storeIds.forEach(storeId -> safeSend("/topic/store-orders/" + storeId, event));
        }
    }

    private void broadcast(Order order, String action) {
        OrderResponse response = orderMapper.toOrderResponse(order);
        OrderWebSocketEvent event = new OrderWebSocketEvent(action, order.getOrderId(), response);

        // 1. Send to Global Admin Topic
        safeSend("/topic/orders", event);

        // 2. 🟢 NEW: Send to Specific Store Topics (For Vendor App Live Updates)
        if (order.getStores() != null) {
            order.getStores().forEach(store -> {
                safeSend("/topic/store-orders/" + store.getStoreId(), event);
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