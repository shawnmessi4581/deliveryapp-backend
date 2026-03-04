package com.deliveryapp.mapper.banner;

import com.deliveryapp.dto.banners.BannerResponse;
import com.deliveryapp.entity.Banner;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BannerMapper {

    private final UrlUtil urlUtil;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final CatalogMapper catalogMapper;

    public BannerResponse toBannerResponse(Banner banner) {
        BannerResponse dto = new BannerResponse();
        dto.setBannerId(banner.getBannerId());
        dto.setTitle(banner.getTitle());
        dto.setImageUrl(urlUtil.getFullUrl(banner.getImage()));
        dto.setLinkType(banner.getLinkType());
        dto.setLinkId(banner.getLinkId());
        dto.setExternalUrl(banner.getExternalUrl());
        dto.setDisplayOrder(banner.getDisplayOrder());
        dto.setStartDate(banner.getStartDate());
        dto.setEndDate(banner.getEndDate());
        dto.setIsActive(banner.getIsActive());

        // --- FETCH RICH DATA BASED ON LINK TYPE ---
        if (banner.getLinkType() != null && banner.getLinkId() != null) {

            if (banner.getLinkType().equalsIgnoreCase("store")) {
                storeRepository.findById(banner.getLinkId()).ifPresent(store -> {
                    dto.setStore(catalogMapper.toStoreResponse(store));
                });
            } else if (banner.getLinkType().equalsIgnoreCase("product")) {
                productRepository.findById(banner.getLinkId()).ifPresent(product -> {
                    dto.setProduct(catalogMapper.toProductResponse(product));
                });
            }
        }

        return dto;
    }
}