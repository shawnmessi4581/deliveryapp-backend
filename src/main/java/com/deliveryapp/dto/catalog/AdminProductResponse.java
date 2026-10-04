package com.deliveryapp.dto.catalog;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminProductResponse extends ProductResponse {

    private Double basePrice;
    private Double usdPrice;
    private Boolean isUsd;

    // 🟢 FIX: Override the parent list to explicitly use the Admin DTO
    private List<AdminProductVariantResponse> adminVariants;
    private Boolean hasOffer;
    private Double offerBasePrice;
    private Double offerUsdPrice;

    // 💹 Both prices for admin & vendor: calculatedPrice is the store's own final price (what the
    // store is paid on); customerPrice is what customers see and pay (store markup included).
    private Double customerPrice;
    private Double priceMarkupPercentage;
}