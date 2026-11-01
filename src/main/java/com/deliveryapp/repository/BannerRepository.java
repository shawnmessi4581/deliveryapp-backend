package com.deliveryapp.repository;

import com.deliveryapp.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

    // Fetch active banners where NOW is between start and end date, sorted by display order
    @Query("SELECT b FROM Banner b WHERE b.isActive = true AND :now BETWEEN b.startDate AND b.endDate ORDER BY b.displayOrder ASC")
    List<Banner> findActiveBanners(LocalDateTime now);

    // For Admin: See all, sorted by ID or Order
    List<Banner> findAllByOrderByDisplayOrderAsc();

    /** Removes the banners linking to the given stores / products (linkType: "store" or "product"). */
    @Modifying
    @Query("DELETE FROM Banner b WHERE LOWER(b.linkType) = :linkType AND b.linkId IN :ids")
    void deleteByLinkTypeAndLinkIds(@Param("linkType") String linkType, @Param("ids") Collection<Long> ids);
}