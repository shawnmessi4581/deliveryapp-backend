package com.deliveryapp.controller;

import com.deliveryapp.dto.banners.BannerResponse;
import com.deliveryapp.entity.Banner;
import com.deliveryapp.service.BannerService;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;
    private final UrlUtil urlUtil; // 2. Inject

    // Get Active Banners (Public)
    @GetMapping
    public ResponseEntity<List<BannerResponse>> getActiveBanners() {
        List<Banner> banners = bannerService.getActiveBanners();
        return ResponseEntity.ok(banners.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList()));
    }

    private BannerResponse mapToResponse(Banner banner) {
        BannerResponse dto = new BannerResponse();
        dto.setBannerId(banner.getBannerId());
        dto.setTitle(banner.getTitle());
        dto.setImageUrl(urlUtil.getFullUrl(banner.getImage()));
        dto.setLinkType(banner.getLinkType());
        dto.setLinkId(banner.getLinkId());
        dto.setDisplayOrder(banner.getDisplayOrder());
        return dto;
    }
}