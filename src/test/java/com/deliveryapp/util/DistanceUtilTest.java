package com.deliveryapp.util;

import com.deliveryapp.entity.Store;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceUtilTest {

    private final DistanceUtil distanceUtil = new DistanceUtil();

    @Test
    void calculateOptimizedDistance_shouldOrderStoresFromFarthestToClosestAndReturnToUser() {
        Store farthest = new Store();
        farthest.setLatitude(0.10);
        farthest.setLongitude(0.00);

        Store middle = new Store();
        middle.setLatitude(0.05);
        middle.setLongitude(0.00);

        Store closest = new Store();
        closest.setLatitude(0.02);
        closest.setLongitude(0.00);

        double routeDistance = distanceUtil.calculateOptimizedDistance(
                List.of(closest, middle, farthest),
                0.0,
                0.0);

        double expected = distanceUtil.calculateDistance(0.10, 0.00, 0.05, 0.00)
                + distanceUtil.calculateDistance(0.05, 0.00, 0.02, 0.00)
                + distanceUtil.calculateDistance(0.02, 0.00, 0.00, 0.00);

        assertEquals(expected, routeDistance, 1e-9);
    }
}
