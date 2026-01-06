package com.deliveryapp.mapper.banner;

import com.deliveryapp.dto.banners.BannerResponse;
import com.deliveryapp.entity.Banner;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BannerMapper {

    private final UrlUtil urlUtil;

    public BannerResponse toBannerResponse(Banner banner) {
        BannerResponse dto = new BannerResponse();
        dto.setBannerId(banner.getBannerId());
        dto.setTitle(banner.getTitle());
        // Handle URL generation here
        dto.setImageUrl(urlUtil.getFullUrl(banner.getImage()));
        dto.setLinkType(banner.getLinkType());
        dto.setLinkId(banner.getLinkId());
        dto.setDisplayOrder(banner.getDisplayOrder());
        dto.setStartDate(banner.getStartDate());
        dto.setEndDate(banner.getEndDate());
        dto.setIsActive(banner.getIsActive());
        return dto;
    }
}