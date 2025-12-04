package com.deliveryapp.dto.coupon;


import com.deliveryapp.entity.Coupon;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponRequest {
    private String code;
    private String title;
    private String description;
    private Coupon.DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private Coupon.ApplicableTo applicableTo;
    private Long applicableId;
    private Boolean isFirstOrderOnly;
    private Integer maxUsagePerUser;
    private Integer totalUsageLimit;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}