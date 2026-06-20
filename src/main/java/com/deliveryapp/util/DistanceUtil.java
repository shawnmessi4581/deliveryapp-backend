package com.deliveryapp.util;

import com.deliveryapp.entity.Store;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DistanceUtil {

    private static final int EARTH_RADIUS_KM = 6371;

    public double calculateDistance(double startLat, double startLong, double endLat, double endLong) {
        double dLat = Math.toRadians(endLat - startLat);
        double dLon = Math.toRadians(endLong - startLong);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c; // Returns distance in Kilometers
    }

    public double calculateOptimizedDistance(List<Store> stores, double userLat, double userLng) {
        if (stores == null || stores.isEmpty()) {
            return 0.0;
        }

        List<Store> validStores = stores.stream()
                .filter(store -> store != null && store.getLatitude() != null && store.getLongitude() != null)
                .sorted((left, right) -> {
                    double leftDistance = calculateDistance(userLat, userLng, left.getLatitude(), left.getLongitude());
                    double rightDistance = calculateDistance(userLat, userLng, right.getLatitude(),
                            right.getLongitude());
                    return Double.compare(rightDistance, leftDistance);
                })
                .toList();

        if (validStores.isEmpty()) {
            return 0.0;
        }

        double totalDistance = 0.0;

        for (int i = 0; i < validStores.size() - 1; i++) {
            Store currentStore = validStores.get(i);
            Store nextStore = validStores.get(i + 1);

            totalDistance += calculateDistance(
                    currentStore.getLatitude(),
                    currentStore.getLongitude(),
                    nextStore.getLatitude(),
                    nextStore.getLongitude());
        }

        Store lastStore = validStores.get(validStores.size() - 1);
        totalDistance += calculateDistance(
                lastStore.getLatitude(),
                lastStore.getLongitude(),
                userLat,
                userLng);

        return totalDistance;
    }
}