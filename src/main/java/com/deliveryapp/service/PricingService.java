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
            // Optional: Round to nearest 100 or 500 SYP if desired
            return product.getUsdPrice() * rate;
        }
        return product.getBasePrice() != null ? product.getBasePrice() : 0.0;
    }

    // --- Calculate Final Variant Price in SYP ---
    // Variants inherit the currency type (isUsd) from their parent product
    public Double getVariantFinalPriceInSYP(ProductVariant variant) {
        if (Boolean.TRUE.equals(variant.getProduct().getIsUsd())) {
            Double rate = exchangeRateService.getCurrentRate();
            return variant.getPriceAdjustment() * rate;
        }
        return variant.getPriceAdjustment() != null ? variant.getPriceAdjustment() : 0.0;
    }
}