package com.deliveryapp.mapper.catalog;

import com.deliveryapp.dto.catalog.*;
import com.deliveryapp.entity.*;
import com.deliveryapp.service.PricingService;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CatalogMapper {

    private final UrlUtil urlUtil;
    private final PricingService pricingService;

    // --- CATEGORY ---
    public CategoryResponse toCategoryResponse(Category category) {
        CategoryResponse dto = new CategoryResponse();
        dto.setCategoryId(category.getCategoryId());
        dto.setName(category.getName());
        dto.setImageUrl(urlUtil.getFullUrl(category.getIcon())); // Use getIcon() per your entity
        dto.setActive(category.getIsActive());
        dto.setDisplayOrder(category.getDisplayOrder());

        return dto;
    }

    // --- SUBCATEGORY ---
    public SubCategoryResponse toSubCategoryResponse(SubCategory subCategory) {
        SubCategoryResponse dto = new SubCategoryResponse();
        dto.setSubCategoryId(subCategory.getSubcategoryId());
        dto.setName(subCategory.getName());
        dto.setImageUrl(urlUtil.getFullUrl(subCategory.getIcon())); // Use getIcon()
        dto.setIsActive(subCategory.getIsActive());
        dto.setDisplayOrder(subCategory.getDisplayOrder());

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
        // Map Times
        if (store.getOpeningTime() != null)
            dto.setOpeningTime(store.getOpeningTime().toString());
        if (store.getClosingTime() != null)
            dto.setClosingTime(store.getClosingTime().toString());

        // Calculate Open Status
        dto.setIsOpenNow(isStoreOpen(store));
        dto.setIsBusy(store.getIsBusy()); // Map Busy Flag
        dto.setDisplayOrder(store.getDisplayOrder());
        dto.setCommissionPercentage(store.getCommissionPercentage() != null ? store.getCommissionPercentage() : 0.0);

        // 🟢 NEW: Map the Store Categories
        if (store.getStoreCategories() != null && !store.getStoreCategories().isEmpty()) {
            List<StoreCategoryResponse> categoriesList = store.getStoreCategories().stream()
                    // Filter to only show Active categories to the user
                    .filter(sc -> Boolean.TRUE.equals(sc.getIsActive()))
                    .map(sc -> {
                        StoreCategoryResponse scDto = new StoreCategoryResponse();
                        scDto.setStoreCategoryId(sc.getStoreCategoryId());
                        scDto.setStoreId(sc.getStore().getStoreId());
                        scDto.setName(sc.getName());
                        scDto.setIsActive(sc.getIsActive());
                        scDto.setDisplayOrder(sc.getDisplayOrder());
                        return scDto;
                    })
                    .collect(Collectors.toList());
            dto.setStoreCategories(categoriesList);
        } else {
            dto.setStoreCategories(Collections.emptyList());
        }
        return dto;
    }

    // --- PRODUCT ---

    public ProductResponse toProductResponse(Product product) {
        ProductResponse dto = new ProductResponse();
        dto.setProductId(product.getProductId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setImageUrl(urlUtil.getFullUrl(product.getImage()));
        dto.setAvailable(product.getIsAvailable());
        dto.setIsTrending(product.getIsTrending() != null ? product.getIsTrending() : false);
        dto.setDisplayOrder(product.getDisplayOrder());

        // 🔴 Calculate SYP price using today's exchange rate
        dto.setCalculatedPrice(pricingService.getFinalPriceInSYP(product));

        // --- MAP GALLERY IMAGES ---
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            dto.setImages(product.getImages().stream()
                    .map(urlUtil::getFullUrl)
                    .collect(Collectors.toList()));
        } else {
            dto.setImages(Collections.emptyList());
        }

        // --- MAP COLORS ---
        if (product.getColors() != null && !product.getColors().isEmpty()) {
            List<ColorResponse> colorDtos = product.getColors().stream().map(c -> {
                ColorResponse cd = new ColorResponse();
                cd.setColorId(c.getColorId());
                cd.setName(c.getName());
                cd.setHexCode(c.getHexCode());
                return cd;
            }).collect(Collectors.toList());
            dto.setColors(colorDtos);
        } else {
            dto.setColors(Collections.emptyList());
        }

        // --- MAP STORE (Updated) ---
        if (product.getStore() != null) {
            StoreResponse storeDto = toStoreResponse(product.getStore());
            dto.setStore(storeDto);

            // ❌ REMOVED: dto.setStoreId(...) and dto.setStoreName(...)
        }

        // --- MAP CATEGORY / SUBCATEGORY ---
        if (product.getCategory() != null) {
            dto.setCategoryId(product.getCategory().getCategoryId());
            dto.setCategoryName(product.getCategory().getName());
        }
        if (product.getSubCategory() != null) {
            dto.setSubCategoryId(product.getSubCategory().getSubcategoryId());
            dto.setSubCategoryName(product.getSubCategory().getName());
        }

        // --- MAP VARIANTS ---
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            dto.setVariants(product.getVariants().stream().map(v -> {
                ProductVariantResponse vDto = new ProductVariantResponse();
                vDto.setVariantId(v.getVariantId());
                vDto.setVariantName(v.getVariantValue());
                vDto.setCalculatedPriceAdjustment(pricingService.getVariantFinalPriceInSYP(v));
                return vDto;
            }).collect(Collectors.toList()));
        } else {
            dto.setVariants(Collections.emptyList());
        }
        // --- Store Category Info ---
        if (product.getStoreCategory() != null) {
            dto.setStoreCategoryId(product.getStoreCategory().getStoreCategoryId());
            dto.setStoreCategoryName(product.getStoreCategory().getName());
        }
        // Set Offer UI Data
        dto.setHasOffer(product.getHasOffer() != null ? product.getHasOffer() : false);

        if (dto.getHasOffer()) {
            // If there is an offer, we need to show the old crossed-out price
            dto.setOriginalPrice(pricingService.getRegularPriceInSYP(product));
            // And calculate the percentage badge
            dto.setDiscountPercentage(pricingService.getDiscountPercentage(product));
        } else {
            dto.setOriginalPrice(null);
            dto.setDiscountPercentage(null);
        }

        return dto;
    }

    private boolean isStoreOpen(Store store) {
        if (store.getOpeningTime() == null || store.getClosingTime() == null)
            return true; // Assume open if not set

        LocalTime now = LocalTime.now();
        // Handle midnight crossing (e.g. 18:00 to 02:00)
        if (store.getClosingTime().isBefore(store.getOpeningTime())) {
            return now.isAfter(store.getOpeningTime()) || now.isBefore(store.getClosingTime());
        } else {
            // Normal day (e.g. 09:00 to 22:00)
            return now.isAfter(store.getOpeningTime()) && now.isBefore(store.getClosingTime());
        }
    }
}