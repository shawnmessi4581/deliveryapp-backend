package com.deliveryapp.mapper.catalog;

import com.deliveryapp.dto.catalog.AdminProductResponse;
import com.deliveryapp.dto.catalog.AdminProductVariantResponse;
import com.deliveryapp.entity.Product;
import com.deliveryapp.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminCatalogMapper {

    private final CatalogMapper catalogMapper;
    private final PricingService pricingService;

    public AdminProductResponse toAdminProductResponse(Product product) {
        AdminProductResponse dto = new AdminProductResponse();

        // 1. Copy Public Fields
        var publicDto = catalogMapper.toProductResponse(product);
        dto.setProductId(publicDto.getProductId());
        dto.setName(publicDto.getName());
        dto.setDescription(publicDto.getDescription());
        dto.setImageUrl(publicDto.getImageUrl());
        // 💹 Both prices: the store's own price + what customers pay (markup included)
        dto.setCalculatedPrice(pricingService.getFinalPriceInSYP(product));
        dto.setCustomerPrice(publicDto.getCalculatedPrice());
        dto.setPriceMarkupPercentage(pricingService.getMarkupPercentage(product.getStore()));
        dto.setIsAvailable(publicDto.getIsAvailable());
        dto.setIsTrending(publicDto.getIsTrending());
        dto.setDisplayOrder(publicDto.getDisplayOrder());
        dto.setImages(publicDto.getImages());
        dto.setColors(publicDto.getColors());
        // Contains full store object (admin view, with the markup)
        dto.setStore(product.getStore() != null ? catalogMapper.toAdminStoreResponse(product.getStore()) : null);
        dto.setCategoryId(publicDto.getCategoryId());
        dto.setCategoryName(publicDto.getCategoryName()); // Make sure this is mapped
        dto.setSubCategoryId(publicDto.getSubCategoryId());
        dto.setSubCategoryName(publicDto.getSubCategoryName()); // Make sure this is mapped
        dto.setStoreCategoryId(publicDto.getStoreCategoryId());
        dto.setStoreCategoryName(publicDto.getStoreCategoryName());
        // ❌ NO LONGER MAPPING flat storeId / storeName

        // 2. Admin Raw Pricing
        dto.setBasePrice(product.getBasePrice());
        dto.setUsdPrice(product.getUsdPrice());
        dto.setIsUsd(product.getIsUsd());

        // 3. MAP ADMIN VARIANTS
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {

            List<AdminProductVariantResponse> adminVariantsList = product.getVariants().stream().map(v -> {
                AdminProductVariantResponse vDto = new AdminProductVariantResponse();

                vDto.setVariantId(v.getVariantId());
                vDto.setVariantName(v.getVariantValue());

                // Retrieve calculated price from public DTO
                var publicVariant = publicDto.getVariants().stream()
                        .filter(pv -> pv.getVariantId().equals(v.getVariantId()))
                        .findFirst()
                        .orElse(null);

                // Store's own variant price, plus what customers pay for it
                vDto.setCalculatedPriceAdjustment(pricingService.getVariantFinalPriceInSYP(v));
                if (publicVariant != null) {
                    vDto.setCustomerPriceAdjustment(publicVariant.getCalculatedPriceAdjustment());
                }

                // Set the raw price adjustment
                vDto.setPriceAdjustment(v.getPriceAdjustment());

                return vDto;
            }).collect(Collectors.toList());

            dto.setAdminVariants(adminVariantsList);
        } else {
            // Keep it clean if there are no variants
            dto.setVariants(java.util.Collections.emptyList());
            dto.setAdminVariants(java.util.Collections.emptyList());
        }
        dto.setHasOffer(product.getHasOffer());
        dto.setOfferBasePrice(product.getOfferBasePrice());
        dto.setOfferUsdPrice(product.getOfferUsdPrice());

        return dto;
    }
}