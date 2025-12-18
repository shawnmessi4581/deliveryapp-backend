package com.deliveryapp.repository;

import com.deliveryapp.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

    // Fetch active banners where NOW is between start and end date, sorted by display order
    @Query("SELECT b FROM Banner b WHERE b.isActive = true AND :now BETWEEN b.startDate AND b.endDate ORDER BY b.displayOrder ASC")
    List<Banner> findActiveBanners(LocalDateTime now);

    // For Admin: See all, sorted by ID or Order
    List<Banner> findAllByOrderByDisplayOrderAsc();
}