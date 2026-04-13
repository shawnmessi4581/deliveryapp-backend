package com.deliveryapp.service;

import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.ProductVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final ExchangeRateService exchangeRateService;

    // --- Calculate Final Price in SYP ---
    public Double getFinalPriceInSYP(Product product) {
        if (Boolean.TRUE.equals(product.getIsUsd())) {
            Double rate = exchangeRateService.getCurrentRate();
            double convertedPrice = product.getUsdPrice() * rate;

            // 🟢 NEW: Round UP to the nearest 10 SYP
            return roundUpToNearestTen(convertedPrice);
        }

        // Even for base SYP prices, ensure it's rounded up to the nearest 10
        return product.getBasePrice() != null ? roundUpToNearestTen(product.getBasePrice()) : 0.0;
    }

    // --- Calculate Final Variant Price in SYP ---
    public Double getVariantFinalPriceInSYP(ProductVariant variant) {
        if (Boolean.TRUE.equals(variant.getProduct().getIsUsd())) {
            Double rate = exchangeRateService.getCurrentRate();
            double convertedPrice = variant.getPriceAdjustment() * rate;

            // 🟢 NEW: Round UP to the nearest 10 SYP
            return roundUpToNearestTen(convertedPrice);
        }

        return variant.getPriceAdjustment() != null ? roundUpToNearestTen(variant.getPriceAdjustment()) : 0.0;
    }

    // =================================================================================
    // HELPER: ROUND UP TO NEAREST 10
    // =================================================================================
    /**
     * Rounds any double up to the next multiple of 10.
     * Examples:
     * 23.0 -> 30.0
     * 4728.0 -> 4730.0
     * 4730.0 -> 4730.0 (Already a multiple of 10, stays the same)
     */
    private Double roundUpToNearestTen(double amount) {
        if (amount == 0.0)
            return 0.0;

        // 1. Divide by 10.0 (e.g., 4728.0 / 10 = 472.8)
        // 2. Math.ceil rounds it up to the nearest whole number (e.g., 473.0)
        // 3. Multiply by 10.0 to get it back to the right scale (e.g., 4730.0)
        return Math.ceil(amount / 10.0) * 10.0;
    }
}