package com.deliveryapp.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    /**
     * Rounds a computed SYP amount (e.g. a percentage discount) to the nearest whole pound,
     * so totals never carry fractions or floating-point noise like 4073.8500000000004.
     */
    public double roundMoney(double amount) {
        return BigDecimal.valueOf(amount).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Adds a percentage and rounds up to the next multiple of 10, computed exactly:
     * 10,000 + 10% = 11,000 (plain double math gives 11,000.000000000002 and would round to 11,010).
     * Example: 12,345 + 10% = 13,579.5 → 13,580.
     */
    public double addPercentRoundedUpToTen(double amount, double percent) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100 + percent))
                .divide(BigDecimal.valueOf(1000), 0, RoundingMode.CEILING)
                .multiply(BigDecimal.TEN)
                .doubleValue();
    }
}
