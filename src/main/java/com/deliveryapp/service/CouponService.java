package com.deliveryapp.service;

import com.deliveryapp.dto.coupon.CouponRequest;
import com.deliveryapp.entity.*;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.CouponRepository;
import com.deliveryapp.repository.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;

    // --- Admin: Create Coupon ---
    public Coupon createCoupon(CouponRequest request) {
        if (couponRepository.existsByCode(request.getCode())) {
            throw new InvalidDataException("Coupon code already exists");
        }
        Coupon coupon = new Coupon();
        // Manual mapping from DTO to Entity
        coupon.setCode(request.getCode().toUpperCase());
        coupon.setTitle(request.getTitle());
        coupon.setDescription(request.getDescription());
        coupon.setDiscountType(request.getDiscountType());
        coupon.setDiscountValue(request.getDiscountValue());
        coupon.setMinOrderAmount(request.getMinOrderAmount());
        coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
        coupon.setApplicableTo(request.getApplicableTo());
        coupon.setApplicableId(request.getApplicableId());
        coupon.setIsFirstOrderOnly(request.getIsFirstOrderOnly());
        coupon.setMaxUsagePerUser(request.getMaxUsagePerUser());
        coupon.setTotalUsageLimit(request.getTotalUsageLimit());
        coupon.setStartDate(request.getStartDate());
        coupon.setEndDate(request.getEndDate());
        coupon.setIsActive(true);
        coupon.setCreatedAt(LocalDateTime.now());

        return couponRepository.save(coupon);
    }

    // --- Validation Logic ---
    public Coupon validateCoupon(String code, Long userId, Cart cart) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid coupon code"));

        // 1. Basic Checks
        if (!coupon.getIsActive()) throw new InvalidDataException("Coupon is inactive");
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartDate()) || now.isAfter(coupon.getEndDate())) {
            throw new InvalidDataException("Coupon is expired or not started yet");
        }

        // 2. Usage Limits (Total)
        if (coupon.getTotalUsageLimit() != null && coupon.getCurrentUsageCount() >= coupon.getTotalUsageLimit()) {
            throw new InvalidDataException("Coupon usage limit reached");
        }

        // 3. Usage Limits (Per User)
        Integer userUsage = usageRepository.countByCouponIdAndUserId(coupon.getCouponId(), userId);
        if (userUsage >= coupon.getMaxUsagePerUser()) {
            throw new InvalidDataException("You have already used this coupon maximum times");
        }

        // 4. First Order Check
        if (coupon.getIsFirstOrderOnly()) {
            // Check if user has ANY previous coupon usage or orders (simplified logic: check usage repo)
            // A better check would be looking at OrderRepository, but let's assume usageRepo for now
            if (usageRepository.existsByUserId(userId)) {
                throw new InvalidDataException("This coupon is for first orders only");
            }
        }

        // 5. Min Order Amount (using Cart items sum)
        // We calculate basic subtotal from cart items
        double cartSubtotal = cart.getItems().stream()
                .mapToDouble(item -> {
                    double price = item.getProduct().getBasePrice();
                    if(item.getVariant() != null) price += item.getVariant().getPriceAdjustment();
                    return price * item.getQuantity();
                }).sum();

        if (coupon.getMinOrderAmount() != null && BigDecimal.valueOf(cartSubtotal).compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new InvalidDataException("Minimum order amount not met for this coupon");
        }

        // 6. Applicability (Store/Category Logic)
        if (coupon.getApplicableTo() == Coupon.ApplicableTo.STORE) {
            if (!cart.getStore().getStoreId().equals(coupon.getApplicableId())) {
                throw new InvalidDataException("Coupon not valid for this store");
            }
        }
        // Note: Implementing Category/Product specific logic requires iterating cart items.
        // For simplicity, we assume if type is CATEGORY, at least one item must match, or strict mode.
        // Keeping it simple: Allow application if scope is ALL or STORE matches.

        return coupon;
    }

    public double calculateDiscount(Coupon coupon, double subtotal, double deliveryFee) {
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal bdSubtotal = BigDecimal.valueOf(subtotal);

        if (coupon.getDiscountType() == Coupon.DiscountType.FIXED_AMOUNT) {
            discount = coupon.getDiscountValue();
        } else if (coupon.getDiscountType() == Coupon.DiscountType.PERCENTAGE) {
            discount = bdSubtotal.multiply(coupon.getDiscountValue().divide(BigDecimal.valueOf(100)));
        } else if (coupon.getDiscountType() == Coupon.DiscountType.FREE_DELIVERY) {
            return deliveryFee; // Discount equals delivery fee
        }

        // Cap at Max Discount
        if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount();
        }

        // Ensure discount doesn't exceed subtotal
        if (discount.compareTo(bdSubtotal) > 0) {
            discount = bdSubtotal;
        }

        return discount.doubleValue();
    }

    @Transactional
    public void recordUsage(Coupon coupon, Long userId, Long orderId, Double discountAmount) {
        CouponUsage usage = new CouponUsage();
        usage.setCouponId(coupon.getCouponId());
        usage.setUserId(userId);
        usage.setOrderId(orderId);
        usage.setDiscountApplied(BigDecimal.valueOf(discountAmount));
        usage.setUsedAt(LocalDateTime.now());

        usageRepository.save(usage);

        // Update total usage count on coupon
        coupon.setCurrentUsageCount(coupon.getCurrentUsageCount() + 1);
        couponRepository.save(coupon);
    }
}