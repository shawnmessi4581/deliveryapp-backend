package com.deliveryapp.mapper.flashsale;

import com.deliveryapp.dto.flashsale.FlashSaleResponse;
import com.deliveryapp.entity.Coupon;
import com.deliveryapp.entity.FlashSale;
import com.deliveryapp.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class FlashSaleMapper {

    private final CouponRepository couponRepository;

    /** Lookup backing coupon and map to response. */
    public FlashSaleResponse toResponseWithCouponLookup(FlashSale fs) {
        Coupon coupon = fs.getBackingCouponId() != null
                ? couponRepository.findById(fs.getBackingCouponId()).orElse(null)
                : null;
        return toResponse(fs, coupon);
    }

    /** Maps FlashSale entity + its backing Coupon into the response DTO. */
    public FlashSaleResponse toResponse(FlashSale fs, Coupon coupon) {
        FlashSaleResponse dto = new FlashSaleResponse();
        dto.setFlashSaleId(fs.getFlashSaleId());
        dto.setTitle(fs.getTitle());
        dto.setDescription(fs.getDescription());
        dto.setBannerImage(fs.getBannerImage());
        dto.setCouponCode(fs.getCouponCode());
        dto.setDiscountType(fs.getDiscountType());
        dto.setDiscountValue(fs.getDiscountValue());
        dto.setMinOrderAmount(fs.getMinOrderAmount());
        dto.setMaxDiscountAmount(fs.getMaxDiscountAmount());
        dto.setApplicableTo(fs.getApplicableTo());
        dto.setApplicableId(fs.getApplicableId());
        dto.setTotalUsageLimit(fs.getTotalUsageLimit());
        dto.setMaxUsagePerUser(fs.getMaxUsagePerUser());
        dto.setStartDate(fs.getStartDate());
        dto.setCountdownEndsAt(fs.getCountdownEndsAt());
        dto.setIsActive(fs.getIsActive());
        dto.setCreatedBy(fs.getCreatedBy());
        dto.setCreatedAt(fs.getCreatedAt());
        dto.setUpdatedAt(fs.getUpdatedAt());

        // Store snapshot
        if (fs.getStore() != null) {
            dto.setStoreId(fs.getStore().getStoreId());
            dto.setStoreName(fs.getStore().getName());
        }

        // Usage count from backing coupon
        if (coupon != null) {
            dto.setCurrentUsageCount(coupon.getCurrentUsageCount());
        } else {
            dto.setCurrentUsageCount(0);
        }

        // Countdown — computed server-side to eliminate client clock-skew
        LocalDateTime now = LocalDateTime.now();
        if (fs.getCountdownEndsAt() != null) {
            long remaining = ChronoUnit.SECONDS.between(now, fs.getCountdownEndsAt());
            dto.setRemainingSeconds(Math.max(remaining, 0L));
        } else {
            dto.setRemainingSeconds(0L);
        }

        // isLive convenience flag
        boolean live = Boolean.TRUE.equals(fs.getIsActive())
                && fs.getStartDate() != null && !now.isBefore(fs.getStartDate())
                && fs.getCountdownEndsAt() != null && now.isBefore(fs.getCountdownEndsAt());
        dto.setIsLive(live);

        return dto;
    }
}
