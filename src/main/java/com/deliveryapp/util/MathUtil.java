package com.deliveryapp.util;

import org.springframework.stereotype.Component;

@Component
public class MathUtil {

    /**
     * Rounds any double up to the next multiple of 10.
     * Examples: 23.0 -> 30.0 | 4728.0 -> 4730.0
     */
    public Double roundUpToNearestTen(double amount) {
        if (amount <= 0.0)
            return 0.0;
        return Math.ceil(amount / 10.0) * 10.0;
    }
}