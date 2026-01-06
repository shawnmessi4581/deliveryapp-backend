package com.deliveryapp.controller;

import com.deliveryapp.dto.banners.BannerResponse;
import com.deliveryapp.mapper.banner.BannerMapper; // Import the Mapper
import com.deliveryapp.service.BannerService;
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
    private final BannerMapper bannerMapper; // Inject Mapper

    // Get Active Banners (Public)
    @GetMapping
    public ResponseEntity<List<BannerResponse>> getActiveBanners() {
        return ResponseEntity.ok(bannerService.getActiveBanners().stream()
                .map(bannerMapper::toBannerResponse) // Use the shared Mapper
                .collect(Collectors.toList()));
    }
}