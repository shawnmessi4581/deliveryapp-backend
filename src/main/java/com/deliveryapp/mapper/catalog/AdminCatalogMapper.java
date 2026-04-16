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
        dto.setStore(publicDto.getStore());
        dto.setCategoryId(publicDto.getCategoryId());
        dto.setSubCategoryId(publicDto.getSubCategoryId());

        // We set the public variants list just in case it's needed
        dto.setVariants(publicDto.getVariants());

        // 2. Admin Raw Pricing
        dto.setBasePrice(product.getBasePrice());
        dto.setUsdPrice(product.getUsdPrice());
        dto.setIsUsd(product.getIsUsd());

        // 3. MAP ADMIN VARIANTS (Type-Safe Fix)
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {

            List<AdminProductVariantResponse> adminVariantsList = product.getVariants().stream().map(v -> {
                AdminProductVariantResponse vDto = new AdminProductVariantResponse();

                vDto.setVariantId(v.getVariantId());
                vDto.setVariantName(v.getVariantValue());

                // Retrieve the calculated price from the public DTO
                var publicVariant = publicDto.getVariants().stream()
                        .filter(pv -> pv.getVariantId().equals(v.getVariantId()))
                        .findFirst()
                        .orElse(null);

                if (publicVariant != null) {
                    vDto.setCalculatedPriceAdjustment(publicVariant.getCalculatedPriceAdjustment());
                }

                // Set the raw price adjustment from the database
                vDto.setPriceAdjustment(v.getPriceAdjustment());

                return vDto;
            }).collect(Collectors.toList());

            // 🟢 FIX: Set the dedicated admin list
            dto.setAdminVariants(adminVariantsList);
        }

        return dto;
    }
}