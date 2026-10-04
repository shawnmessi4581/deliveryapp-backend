package com.deliveryapp.dto.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryFeeResponse {
    private Double deliveryFee;
    private String estimatedTime;
    private Double maxMinimumDeliveryFee; // min-fee floor of the stores the customer pays for
    private Double totalDistanceKm; // full delivery route (all stores)
    private List<RouteSegmentResponse> routeSegments;

    // Stores whose delivery is free; the fee only covers the other stores
    private List<Long> freeDeliveryStoreIds;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteSegmentResponse {
        private String fromStoreName;
        private String toStoreName;
        private Double distanceKm;
        private String segmentType;
    }
}