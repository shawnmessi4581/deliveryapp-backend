package com.deliveryapp.controller;

import com.deliveryapp.dto.banners.BannerRequest;
import com.deliveryapp.dto.banners.BannerResponse;
import com.deliveryapp.entity.Banner;
import com.deliveryapp.mapper.banner.BannerMapper; // Import the Mapper
import com.deliveryapp.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBannerController {

    private final BannerService bannerService;
    private final BannerMapper bannerMapper; // Inject Mapper

    @GetMapping
    public ResponseEntity<List<BannerResponse>> getAllBanners() {
        return ResponseEntity.ok(bannerService.getAllBanners().stream()
                .map(bannerMapper::toBannerResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BannerResponse> createBanner(
            @ModelAttribute BannerRequest request,
            @RequestParam("image") MultipartFile image) {

        Banner banner = bannerService.createBanner(request, image);
        return ResponseEntity.ok(bannerMapper.toBannerResponse(banner));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BannerResponse> updateBanner(
            @PathVariable Long id,
            @ModelAttribute BannerRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        Banner banner = bannerService.updateBanner(id, request, image);
        return ResponseEntity.ok(bannerMapper.toBannerResponse(banner));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteBanner(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.ok("Banner deleted successfully");
    }
}