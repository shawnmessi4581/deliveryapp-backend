package com.deliveryapp.repository;

import com.deliveryapp.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

    // Find active banners where current date is within range
    List<Banner> findByIsActiveTrueAndStartDateBeforeAndEndDateAfterOrderByDisplayOrder(
            LocalDateTime now1, LocalDateTime now2);
}