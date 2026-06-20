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
    private Double maxMinimumDeliveryFee;
    private Double totalDistanceKm;
    private List<RouteSegmentResponse> routeSegments;

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