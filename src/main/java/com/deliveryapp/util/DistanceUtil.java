package com.deliveryapp.util;

import com.deliveryapp.entity.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DistanceUtil {

    private static final int EARTH_RADIUS_KM = 6371;
    private static final String GOOGLE_ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.routes.api-key:}")
    private String googleRoutesApiKey;

    public double calculateDistance(double startLat, double startLong, double endLat, double endLong) {
        if (startLat == endLat && startLong == endLong) {
            return 0.0;
        }

        try {
            return fetchDrivingDistance(startLat, startLong, endLat, endLong);
        } catch (Exception ex) {
            return calculateHaversineDistance(startLat, startLong, endLat, endLong);
        }
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

    private double fetchDrivingDistance(double startLat, double startLong, double endLat, double endLong) {
        if (googleRoutesApiKey == null || googleRoutesApiKey.isBlank()) {
            return calculateHaversineDistance(startLat, startLong, endLat, endLong);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", googleRoutesApiKey);
        headers.set("X-Goog-FieldMask", "routes.distanceMeters");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("origin", Map.of(
                "location", Map.of(
                        "latLng", Map.of(
                                "latitude", startLat,
                                "longitude", startLong))));
        requestBody.put("destination", Map.of(
                "location", Map.of(
                        "latLng", Map.of(
                                "latitude", endLat,
                                "longitude", endLong))));
        requestBody.put("travelMode", "DRIVE");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        Object rawResponse = restTemplate.postForObject(
                GOOGLE_ROUTES_API_URL,
                request,
                Object.class);

        if (!(rawResponse instanceof Map<?, ?> response)) {
            throw new IllegalStateException("Google Routes API response was not a JSON object.");
        }

        if (!response.containsKey("routes")) {
            throw new IllegalStateException("Google Routes API response did not contain routes.");
        }

        Object routes = response.get("routes");
        if (!(routes instanceof List<?> routeList) || routeList.isEmpty()) {
            throw new IllegalStateException("Google Routes API response did not contain any routes.");
        }

        Object firstRoute = routeList.get(0);
        if (!(firstRoute instanceof Map<?, ?> routeMap) || !routeMap.containsKey("distanceMeters")) {
            throw new IllegalStateException("Google Routes API response did not contain distanceMeters.");
        }

        Object distanceMeters = routeMap.get("distanceMeters");
        if (distanceMeters instanceof Number number) {
            return number.doubleValue() / 1000.0;
        }

        throw new IllegalStateException("Google Routes API distanceMeters was not numeric.");
    }

    private double calculateHaversineDistance(double startLat, double startLong, double endLat, double endLong) {
        double dLat = Math.toRadians(endLat - startLat);
        double dLon = Math.toRadians(endLong - startLong);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}