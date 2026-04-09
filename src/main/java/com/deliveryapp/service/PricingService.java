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

            // 🟢 NEW: Round UP to the nearest integer
            return Math.ceil(convertedPrice);
        }

        // Even for base SYP prices, it's good practice to ensure it's a clean integer
        return product.getBasePrice() != null ? Math.ceil(product.getBasePrice()) : 0.0;
    }

    // --- Calculate Final Variant Price in SYP ---
    // Variants inherit the currency type (isUsd) from their parent product
    public Double getVariantFinalPriceInSYP(ProductVariant variant) {
        if (Boolean.TRUE.equals(variant.getProduct().getIsUsd())) {
            Double rate = exchangeRateService.getCurrentRate();
            double convertedPrice = variant.getPriceAdjustment() * rate;

            // 🟢 NEW: Round UP to the nearest integer
            return Math.ceil(convertedPrice);
        }

        return variant.getPriceAdjustment() != null ? Math.ceil(variant.getPriceAdjustment()) : 0.0;
    }
}