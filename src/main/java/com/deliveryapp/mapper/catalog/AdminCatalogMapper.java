package com.deliveryapp.mapper.catalog;

import com.deliveryapp.dto.catalog.AdminProductResponse;
import com.deliveryapp.dto.catalog.AdminProductVariantResponse;
import com.deliveryapp.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminCatalogMapper {

    private final CatalogMapper catalogMapper;

    public AdminProductResponse toAdminProductResponse(Product product) {
        AdminProductResponse dto = new AdminProductResponse();

        // 1. Copy Public Fields
        var publicDto = catalogMapper.toProductResponse(product);
        dto.setProductId(publicDto.getProductId());
        dto.setName(publicDto.getName());
        dto.setDescription(publicDto.getDescription());
        dto.setImageUrl(publicDto.getImageUrl());
        dto.setCalculatedPrice(publicDto.getCalculatedPrice());
        dto.setAvailable(publicDto.isAvailable());
        dto.setIsTrending(publicDto.getIsTrending());
        dto.setDisplayOrder(publicDto.getDisplayOrder());
        dto.setImages(publicDto.getImages());
        dto.setColors(publicDto.getColors());
        dto.setStore(publicDto.getStore()); // Contains full store object
        dto.setCategoryId(publicDto.getCategoryId());
        dto.setCategoryName(publicDto.getCategoryName()); // Make sure this is mapped
        dto.setSubCategoryId(publicDto.getSubCategoryId());
        dto.setSubCategoryName(publicDto.getSubCategoryName()); // Make sure this is mapped

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

                if (publicVariant != null) {
                    vDto.setCalculatedPriceAdjustment(publicVariant.getCalculatedPriceAdjustment());
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

        return dto;
    }
}