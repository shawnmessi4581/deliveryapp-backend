package com.deliveryapp.service;

import com.deliveryapp.dto.coupon.CouponRequest;
import com.deliveryapp.entity.*;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.CouponRepository;
import com.deliveryapp.repository.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;

    // --- Admin: Create Coupon ---
    public Coupon createCoupon(CouponRequest request) {
        if (couponRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Coupon code already exists");
        }
        Coupon coupon = new Coupon();
        mapRequestToEntity(coupon, request); // Helper method used here
        coupon.setCurrentUsageCount(0); // Initialize
        coupon.setCurrentUsageCount(0);
        coupon.setIsActive(true);
        coupon.setCreatedAt(LocalDateTime.now());

        return couponRepository.save(coupon);
    }

    // =================================================================================
    // NEW CRUD METHODS (ADDED)
    // =================================================================================

    // 1. Get All Coupons
    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    // 2. Get Coupon By ID
    public Coupon getCouponById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + id));
    }

    // 3. Update Coupon
    @Transactional
    public Coupon updateCoupon(Long id, CouponRequest request) {
        Coupon coupon = getCouponById(id);

        // Check if code is being changed and if new code already exists
        if (!coupon.getCode().equalsIgnoreCase(request.getCode()) &&
                couponRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Coupon code " + request.getCode() + " already exists");
        }

        // Update fields
        mapRequestToEntity(coupon, request);

        coupon.setUpdatedAt(LocalDateTime.now());
        return couponRepository.save(coupon);
    }

    // 4. Delete Coupon
    public void deleteCoupon(Long id) {
        if (!couponRepository.existsById(id)) {
            throw new ResourceNotFoundException("Coupon not found with id: " + id);
        }
        // Note: You might want to prevent deletion if the coupon has usage history
        // or just use soft delete (isActive = false). For now, standard delete:
        couponRepository.deleteById(id);
    }

    // 5. Toggle Status (Active/Inactive)
    @Transactional
    public Coupon toggleCouponStatus(Long id) {
        Coupon coupon = getCouponById(id);
        coupon.setIsActive(!coupon.getIsActive());
        return couponRepository.save(coupon);
    }

    // Helper to map DTO to Entity (Used by Create and Update)
    private void mapRequestToEntity(Coupon coupon, CouponRequest request) {
        if (request.getCode() != null) coupon.setCode(request.getCode().toUpperCase());
        if (request.getTitle() != null) coupon.setTitle(request.getTitle());
        if (request.getDescription() != null) coupon.setDescription(request.getDescription());
        if (request.getDiscountType() != null) coupon.setDiscountType(request.getDiscountType());
        if (request.getDiscountValue() != null) coupon.setDiscountValue(request.getDiscountValue());
        if (request.getMinOrderAmount() != null) coupon.setMinOrderAmount(request.getMinOrderAmount());
        if (request.getMaxDiscountAmount() != null) coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
        if (request.getApplicableTo() != null) coupon.setApplicableTo(request.getApplicableTo());
        if (request.getApplicableId() != null) coupon.setApplicableId(request.getApplicableId());
        if (request.getIsFirstOrderOnly() != null) coupon.setIsFirstOrderOnly(request.getIsFirstOrderOnly());
        if (request.getMaxUsagePerUser() != null) coupon.setMaxUsagePerUser(request.getMaxUsagePerUser());
        if (request.getTotalUsageLimit() != null) coupon.setTotalUsageLimit(request.getTotalUsageLimit());
        if (request.getStartDate() != null) coupon.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) coupon.setEndDate(request.getEndDate());
    }

    // =================================================================================
    // EXISTING LOGIC (Unchanged)
    // =================================================================================

    public Coupon validateCouponForOrder(String code, Long userId, List<OrderItem> items, Store store) {
        // 1. Fetch Coupon
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid coupon code"));

        // 2. Basic Status Checks
        if (Boolean.FALSE.equals(coupon.getIsActive())) {
            throw new InvalidDataException("Coupon is inactive");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartDate()) || now.isAfter(coupon.getEndDate())) {
            throw new InvalidDataException("Coupon is expired or not started yet");
        }

        // 3. Global Usage Limit
        if (coupon.getTotalUsageLimit() != null &&
                coupon.getCurrentUsageCount() >= coupon.getTotalUsageLimit()) {
            throw new InvalidDataException("Coupon usage limit reached");
        }

        // 4. Per User Usage Limit
        Integer userUsage = usageRepository.countByCouponIdAndUserId(coupon.getCouponId(), userId);
        if (userUsage >= coupon.getMaxUsagePerUser()) {
            throw new InvalidDataException("You have already used this coupon the maximum number of times");
        }

        // 5. First Order Check
        if (Boolean.TRUE.equals(coupon.getIsFirstOrderOnly())) {
            // Check if user has ever used a coupon OR placed an order (depending on strictness)
            // Here checking if they have a coupon usage record
            if (usageRepository.existsByUserId(userId)) {
                throw new InvalidDataException("This coupon is valid for first orders only");
            }
        }

        // 6. Minimum Order Amount Check
        // Calculate subtotal from the list of OrderItems
        double itemsSubtotal = items.stream()
                .mapToDouble(OrderItem::getTotalPrice)
                .sum();

        if (coupon.getMinOrderAmount() != null &&
                BigDecimal.valueOf(itemsSubtotal).compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new InvalidDataException("Minimum order amount of " + coupon.getMinOrderAmount() + " not met");
        }

        // 7. Applicability Check (Scope)
        if (coupon.getApplicableTo() != null) {
            switch (coupon.getApplicableTo()) {
                case STORE:
                    if (!store.getStoreId().equals(coupon.getApplicableId())) {
                        throw new InvalidDataException("Coupon is not valid for this store");
                    }
                    break;

                case CATEGORY:
                    boolean hasCategory = items.stream()
                            .anyMatch(item -> item.getProduct().getCategory().getCategoryId().equals(coupon.getApplicableId()));
                    if (!hasCategory) {
                        throw new InvalidDataException("Coupon requires items from a specific category");
                    }
                    break;

                case PRODUCT:
                    boolean hasProduct = items.stream()
                            .anyMatch(item -> item.getProduct().getProductId().equals(coupon.getApplicableId()));
                    if (!hasProduct) {
                        throw new InvalidDataException("Coupon requires a specific product in cart");
                    }
                    break;

                case ALL:
                default:
                    // Valid for everything
                    break;
            }
        }

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
            return deliveryFee;
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

        coupon.setCurrentUsageCount(coupon.getCurrentUsageCount() + 1);
        couponRepository.save(coupon);
    }
}