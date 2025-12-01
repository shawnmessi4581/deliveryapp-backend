package com.deliveryapp.service;

import com.deliveryapp.entity.Banner;
import com.deliveryapp.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerRepository bannerRepository;

    public List<Banner> getActiveBanners() {
        LocalDateTime now = LocalDateTime.now();
        // Returns banners where Active=True, StartDate < Now, EndDate > Now
        return bannerRepository.findByIsActiveTrueAndStartDateBeforeAndEndDateAfterOrderByDisplayOrder(now, now);
    }
}