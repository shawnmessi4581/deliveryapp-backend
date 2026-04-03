package com.deliveryapp.mapper.catalog;

import com.deliveryapp.dto.catalog.AdminProductResponse;
import com.deliveryapp.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminCatalogMapper {

    private final CatalogMapper catalogMapper; // Re-use public mapping logic

    public AdminProductResponse toAdminProductResponse(Product product) {
        AdminProductResponse dto = new AdminProductResponse();

        // 1. Copy all public fields (like calculatedPrice, images, variants)
        // Note: You can use
        // BeanUtils.copyProperties(catalogMapper.toProductResponse(product), dto);
        // or map them manually. Assuming manual for clarity:
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
        dto.setVariants(publicDto.getVariants());
        dto.setStore(publicDto.getStore());
        dto.setCategoryId(publicDto.getCategoryId());
        dto.setSubCategoryId(publicDto.getSubCategoryId());

        // 2. Add the Admin-Only Raw Pricing Fields
        dto.setBasePrice(product.getBasePrice());
        dto.setUsdPrice(product.getUsdPrice());
        dto.setIsUsd(product.getIsUsd());

        return dto;
    }
}