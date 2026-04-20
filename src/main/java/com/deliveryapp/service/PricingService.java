package com.deliveryapp.service;

import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.ProductVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final ExchangeRateService exchangeRateService;

    // =================================================================================
    // MAIN PRODUCT PRICING
    // =================================================================================

    // 1. Calculate the FINAL ACTIVE PRICE (Used by the Cart/Order system and Public
    // DTOs)
    public Double getFinalPriceInSYP(Product product) {
        if (Boolean.TRUE.equals(product.getHasOffer())) {
            return getOfferPriceInSYP(product);
        } else {
            return getRegularPriceInSYP(product);
        }
    }

    // 2. Calculate the Regular (Original) Price
    public Double getRegularPriceInSYP(Product product) {
        if (Boolean.TRUE.equals(product.getIsUsd())) {
            Double rate = exchangeRateService.getCurrentRate();
            double convertedPrice = product.getUsdPrice() * rate;
            return roundUpToNearestTen(convertedPrice);
        }

        return product.getBasePrice() != null ? roundUpToNearestTen(product.getBasePrice()) : 0.0;
    }

    // 3. Calculate the Offer (Discounted) Price
    private Double getOfferPriceInSYP(Product product) {
        if (Boolean.TRUE.equals(product.getIsUsd())) {
            if (product.getOfferUsdPrice() == null)
                return getRegularPriceInSYP(product); // Fallback
            Double rate = exchangeRateService.getCurrentRate();
            double convertedPrice = product.getOfferUsdPrice() * rate;
            return roundUpToNearestTen(convertedPrice);
        }

        return product.getOfferBasePrice() != null ? roundUpToNearestTen(product.getOfferBasePrice())
                : getRegularPriceInSYP(product);
    }

    // 4. Calculate Discount Percentage (For Frontend Badges: e.g. "20% OFF")
    public Integer getDiscountPercentage(Product product) {
        if (!Boolean.TRUE.equals(product.getHasOffer()))
            return null;

        Double oldPrice = getRegularPriceInSYP(product);
        Double newPrice = getOfferPriceInSYP(product);

        if (oldPrice == null || newPrice == null || oldPrice == 0.0 || newPrice >= oldPrice) {
            return null;
        }

        // Formula: ((Old - New) / Old) * 100
        double percentage = ((oldPrice - newPrice) / oldPrice) * 100;

        return (int) Math.round(percentage); // Round to nearest whole integer (e.g. 15)
    }

    // =================================================================================
    // VARIANT PRICING
    // =================================================================================

    // --- Calculate Final Variant Price in SYP ---
    // Variants currently do not have their own separate "offer" logic, they just
    // inherit the currency rules.
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