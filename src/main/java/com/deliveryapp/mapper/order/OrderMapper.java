package com.deliveryapp.mapper.order;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.dto.order.DeliveryFeeResponse;
import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.mapper.user.UserMapper;
import com.deliveryapp.util.DistanceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final CatalogMapper catalogMapper;
    private final UserMapper userMapper;
    private final DistanceUtil distanceUtil;

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
        response.setDriverOrderStatus(order.getDriverOrderStatus());

        // --- Customer Info (Reusing UserMapper) ---
        if (order.getUser() != null) {
            response.setCustomerDetails(userMapper.toOrderCustomerResponse(order.getUser()));
        }

        // --- Location & Status ---
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        if (order.getStores() != null && !order.getStores().isEmpty()
                && order.getDeliveryLatitude() != null && order.getDeliveryLongitude() != null) {
            List<Store> validStores = new ArrayList<>();
            for (Store store : order.getStores()) {
                if (store != null && store.getLatitude() != null && store.getLongitude() != null) {
                    validStores.add(store);
                }
            }

            validStores.sort((left, right) -> {
                double leftDistance = distanceUtil.calculateDistance(
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude(),
                        left.getLatitude(),
                        left.getLongitude());
                double rightDistance = distanceUtil.calculateDistance(
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude(),
                        right.getLatitude(),
                        right.getLongitude());
                return Double.compare(rightDistance, leftDistance);
            });

            response.setMaxMinimumDeliveryFee(validStores.stream()
                    .mapToDouble(store -> store.getMinimumDeliveryFee() != null ? store.getMinimumDeliveryFee() : 0.0)
                    .max()
                    .orElse(0.0));

            response.setTotalDistanceKm(distanceUtil.calculateOptimizedDistance(
                    validStores,
                    order.getDeliveryLatitude(),
                    order.getDeliveryLongitude()));

            List<DeliveryFeeResponse.RouteSegmentResponse> segments = new ArrayList<>();
            for (int i = 0; i < validStores.size() - 1; i++) {
                Store fromStore = validStores.get(i);
                Store toStore = validStores.get(i + 1);
                segments.add(new DeliveryFeeResponse.RouteSegmentResponse(
                        fromStore.getName(),
                        toStore.getName(),
                        distanceUtil.calculateDistance(
                                fromStore.getLatitude(),
                                fromStore.getLongitude(),
                                toStore.getLatitude(),
                                toStore.getLongitude()),
                        "STORE_TO_STORE"));
            }

            if (!validStores.isEmpty()) {
                Store lastStore = validStores.get(validStores.size() - 1);
                String userName = order.getUser() != null && order.getUser().getName() != null
                        ? order.getUser().getName()
                        : "User";
                segments.add(new DeliveryFeeResponse.RouteSegmentResponse(
                        lastStore.getName(),
                        userName,
                        distanceUtil.calculateDistance(
                                lastStore.getLatitude(),
                                lastStore.getLongitude(),
                                order.getDeliveryLatitude(),
                                order.getDeliveryLongitude()),
                        "STORE_TO_USER"));
            }

            response.setRouteSegments(segments);
        }

        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());
        // 🟢 FIX: Map Instructions and Notes
        response.setSelectedInstruction(order.getSelectedInstruction());
        response.setOrderNote(order.getOrderNote());

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

                // Map Product details
                r.setProductName(item.getProductName());
                String variantDetails = item.getVariantDetails();
                if ((variantDetails == null || variantDetails.isBlank()) && item.getVariant() != null) {
                    variantDetails = item.getVariant().getVariantValue();
                }
                r.setVariantDetails(variantDetails);
                r.setQuantity(item.getQuantity());
                r.setUnitPrice(item.getUnitPrice());
                r.setTotalPrice(item.getTotalPrice());
                r.setNotes(item.getNotes());
                r.setSelectedColor(item.getSelectedColor());

                // 👉 Map Store details for grouping and payout math
                if (item.getProduct() != null && item.getProduct().getStore() != null) {
                    r.setStoreId(item.getProduct().getStore().getStoreId());
                    r.setStoreName(item.getProduct().getStore().getName());

                    // Safely get commission (default to 0.0 if null)
                    Double commission = item.getProduct().getStore().getCommissionPercentage();
                    r.setStoreCommissionPercentage(commission != null ? commission : 0.0);
                }

                return r;
            }).collect(Collectors.toList()));
        } else {
            response.setItems(Collections.emptyList());
        }

        return response;
    }
}