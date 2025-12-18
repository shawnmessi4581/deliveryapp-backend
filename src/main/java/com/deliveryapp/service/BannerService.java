package com.deliveryapp.service;

import com.deliveryapp.dto.banners.BannerRequest;
import com.deliveryapp.entity.Banner;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerRepository bannerRepository;
    private final FileStorageService fileStorageService;

    // --- PUBLIC: Get Active Banners ---
    public List<Banner> getActiveBanners() {
        return bannerRepository.findActiveBanners(LocalDateTime.now());
    }

    // --- ADMIN: Get All Banners ---
    public List<Banner> getAllBanners() {
        return bannerRepository.findAllByOrderByDisplayOrderAsc();
    }

    // --- ADMIN: Create Banner ---
    @Transactional
    public Banner createBanner(BannerRequest request, MultipartFile image) {
        Banner banner = new Banner();
        banner.setTitle(request.getTitle());
        banner.setLinkType(request.getLinkType());
        banner.setLinkId(request.getLinkId());
        banner.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        banner.setStartDate(request.getStartDate());
        banner.setEndDate(request.getEndDate());
        banner.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        banner.setCreatedAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "banners");
            banner.setImage(imageUrl);
        }

        return bannerRepository.save(banner);
    }

    // --- ADMIN: Update Banner ---
    @Transactional
    public Banner updateBanner(Long id, BannerRequest request, MultipartFile image) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner not found with id: " + id));

        if (request.getTitle() != null) banner.setTitle(request.getTitle());
        if (request.getLinkType() != null) banner.setLinkType(request.getLinkType());
        if (request.getLinkId() != null) banner.setLinkId(request.getLinkId());
        if (request.getDisplayOrder() != null) banner.setDisplayOrder(request.getDisplayOrder());
        if (request.getStartDate() != null) banner.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) banner.setEndDate(request.getEndDate());
        if (request.getIsActive() != null) banner.setIsActive(request.getIsActive());

        if (image != null && !image.isEmpty()) {
            // Optional: delete old image
            String imageUrl = fileStorageService.storeFile(image, "banners");
            banner.setImage(imageUrl);
        }

        return bannerRepository.save(banner);
    }

    // --- ADMIN: Delete Banner ---
    public void deleteBanner(Long id) {
        if (!bannerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Banner not found with id: " + id);
        }
        bannerRepository.deleteById(id);
    }
}