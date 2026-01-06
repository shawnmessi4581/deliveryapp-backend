package com.deliveryapp.mapper.catalog;

import com.deliveryapp.dto.catalog.*;
import com.deliveryapp.entity.*;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CatalogMapper {

    private final UrlUtil urlUtil;

    // --- CATEGORY ---
    public CategoryResponse toCategoryResponse(Category category) {
        CategoryResponse dto = new CategoryResponse();
        dto.setCategoryId(category.getCategoryId());
        dto.setName(category.getName());
        dto.setImageUrl(urlUtil.getFullUrl(category.getIcon())); // Use getIcon() per your entity
        dto.setActive(category.getIsActive());
        return dto;
    }

    // --- SUBCATEGORY ---
    public SubCategoryResponse toSubCategoryResponse(SubCategory subCategory) {
        SubCategoryResponse dto = new SubCategoryResponse();
        dto.setSubCategoryId(subCategory.getSubcategoryId());
        dto.setName(subCategory.getName());
        dto.setImageUrl(urlUtil.getFullUrl(subCategory.getIcon())); // Use getIcon()
        dto.setIsActive(subCategory.getIsActive());

        if (subCategory.getCategory() != null) {
            dto.setParentCategoryId(subCategory.getCategory().getCategoryId());
            dto.setParentCategoryName(subCategory.getCategory().getName());
        }
        return dto;
    }

    // --- STORE ---
    public StoreResponse toStoreResponse(Store store) {
        StoreResponse dto = new StoreResponse();
        dto.setStoreId(store.getStoreId());
        dto.setName(store.getName());
        dto.setDescription(store.getDescription());
        dto.setPhone(store.getPhone());
        dto.setAddress(store.getAddress());
        dto.setLatitude(store.getLatitude());
        dto.setLongitude(store.getLongitude());
        dto.setIsActive(store.getIsActive());
        dto.setRating(store.getRating());
        dto.setTotalOrders(store.getTotalOrders());
        dto.setEstimatedDeliveryTime(store.getEstimatedDeliveryTime());
        dto.setDeliveryFeeKM(store.getDeliveryFeeKM());
        dto.setMinimumOrder(store.getMinimumOrder());

        dto.setLogo(urlUtil.getFullUrl(store.getLogo()));
        dto.setCoverImage(urlUtil.getFullUrl(store.getCoverImage()));

        if (store.getCategory() != null) {
            dto.setCategoryId(store.getCategory().getCategoryId());
            dto.setCategoryName(store.getCategory().getName());
        }
        if (store.getSubCategory() != null) {
            dto.setSubCategoryId(store.getSubCategory().getSubcategoryId());
            dto.setSubCategoryName(store.getSubCategory().getName());
        }
        return dto;
    }

    // --- PRODUCT ---
    public ProductResponse toProductResponse(Product product) {
        ProductResponse dto = new ProductResponse();
        dto.setProductId(product.getProductId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setBasePrice(product.getBasePrice());
        dto.setImageUrl(urlUtil.getFullUrl(product.getImage())); // Use getImage() per your entity
        dto.setAvailable(product.getIsAvailable());

        if (product.getStore() != null) {
            // Reuse store logic to get logo etc.
            StoreResponse storeDto = toStoreResponse(product.getStore());
            dto.setStore(storeDto);

            // Flat fields
            dto.setStoreId(product.getStore().getStoreId());
            dto.setStoreName(product.getStore().getName());
        }

        if (product.getCategory() != null) {
            dto.setCategoryId(product.getCategory().getCategoryId());
            dto.setCategoryName(product.getCategory().getName());
        }
        if (product.getSubCategory() != null) {
            dto.setSubCategoryId(product.getSubCategory().getSubcategoryId());
            dto.setSubCategoryName(product.getSubCategory().getName());
        }

        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            dto.setVariants(product.getVariants().stream().map(v -> {
                ProductVariantResponse vDto = new ProductVariantResponse();
                vDto.setVariantId(v.getVariantId());
                vDto.setVariantName(v.getVariantValue());
                vDto.setPriceAdjustment(v.getPriceAdjustment());
                return vDto;
            }).collect(Collectors.toList()));
        } else {
            dto.setVariants(Collections.emptyList());
        }

        return dto;
    }
}