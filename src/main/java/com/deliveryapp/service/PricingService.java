package com.deliveryapp.service;

import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.ProductVariant;
import com.deliveryapp.util.MathUtil; // 🟢 Import the new Utility
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final ExchangeRateService exchangeRateService;
    private final MathUtil mathUtil; // 🟢 Inject MathUtil

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

            // 🟢 ONLY CEIL IF USD
            return mathUtil.roundUpToNearestTen(convertedPrice);
        }

        // 🟢 DO NOT CEIL IF ALREADY SYP
        return product.getBasePrice() != null ? product.getBasePrice() : 0.0;
    }

    // 3. Calculate the Offer (Discounted) Price
    private Double getOfferPriceInSYP(Product product) {
        if (Boolean.TRUE.equals(product.getIsUsd())) {
            if (product.getOfferUsdPrice() == null)
                return getRegularPriceInSYP(product); // Fallback

            Double rate = exchangeRateService.getCurrentRate();
            double convertedPrice = product.getOfferUsdPrice() * rate;

            // 🟢 ONLY CEIL IF USD
            return mathUtil.roundUpToNearestTen(convertedPrice);
        }

        // 🟢 DO NOT CEIL IF ALREADY SYP
        return product.getOfferBasePrice() != null ? product.getOfferBasePrice() : getRegularPriceInSYP(product);
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
    // Variants inherit the currency rules from the parent product.
    public Double getVariantFinalPriceInSYP(ProductVariant variant) {
        if (Boolean.TRUE.equals(variant.getProduct().getIsUsd())) {
            Double rate = exchangeRateService.getCurrentRate();
            double convertedPrice = variant.getPriceAdjustment() * rate;

            // 🟢 ONLY CEIL IF USD
            return mathUtil.roundUpToNearestTen(convertedPrice);
        }

        // 🟢 DO NOT CEIL IF ALREADY SYP
        return variant.getPriceAdjustment() != null ? variant.getPriceAdjustment() : 0.0;
    }
}