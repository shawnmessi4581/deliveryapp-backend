package com.deliveryapp.service;

import com.deliveryapp.dto.coupon.CouponRequest;
import com.deliveryapp.entity.*;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.CouponRepository;
import com.deliveryapp.repository.CouponUsageRepository;
import com.deliveryapp.repository.FlashSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;
    private final FlashSaleRepository flashSaleRepository;

    // --- Admin: Create Coupon ---
    public Coupon createCoupon(CouponRequest request) {
        if (couponRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("رمز القسيمة موجود مسبقاً");
        }
        Coupon coupon = new Coupon();
        mapRequestToEntity(coupon, request); // Helper method used here
        validateDiscountSettings(coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getApplicableTo(), coupon.getApplicableId());
        coupon.setCurrentUsageCount(0); // Initialize
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
                .orElseThrow(() -> new ResourceNotFoundException("القسيمة غير موجودة برقم: " + id));
    }

    // 3. Update Coupon
    @Transactional
    public Coupon updateCoupon(Long id, CouponRequest request) {
        Coupon coupon = getCouponById(id);

        // Check if code is being changed and if new code already exists
        if (!coupon.getCode().equalsIgnoreCase(request.getCode()) &&
                couponRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("رمز القسيمة " + request.getCode() + " موجود مسبقاً");
        }

        // Update fields
        mapRequestToEntity(coupon, request);
        validateDiscountSettings(coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getApplicableTo(), coupon.getApplicableId());

        coupon.setUpdatedAt(LocalDateTime.now());
        return couponRepository.save(coupon);
    }

    // 4. Delete Coupon
    public void deleteCoupon(Long id) {
        if (!couponRepository.existsById(id)) {
            throw new ResourceNotFoundException("القسيمة غير موجودة برقم: " + id);
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
        if (request.getCode() != null)
            coupon.setCode(request.getCode().trim().toUpperCase());
        if (request.getTitle() != null)
            coupon.setTitle(request.getTitle());
        if (request.getDescription() != null)
            coupon.setDescription(request.getDescription());
        if (request.getDiscountType() != null)
            coupon.setDiscountType(request.getDiscountType());
        // To this:
        if (request.getDiscountValue() != null) {
            coupon.setDiscountValue(request.getDiscountValue());
        } else if (request.getDiscountType() == Coupon.DiscountType.FREE_DELIVERY) {
            // Force it to zero to satisfy the database NOT NULL constraint
            coupon.setDiscountValue(BigDecimal.ZERO);
        }
        if (request.getMinOrderAmount() != null)
            coupon.setMinOrderAmount(request.getMinOrderAmount());
        if (request.getMaxDiscountAmount() != null)
            coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
        if (request.getApplicableTo() != null)
            coupon.setApplicableTo(request.getApplicableTo());
        if (request.getApplicableId() != null)
            coupon.setApplicableId(request.getApplicableId());
        if (request.getIsFirstOrderOnly() != null)
            coupon.setIsFirstOrderOnly(request.getIsFirstOrderOnly());
        if (request.getMaxUsagePerUser() != null)
            coupon.setMaxUsagePerUser(request.getMaxUsagePerUser());
        if (request.getTotalUsageLimit() != null)
            coupon.setTotalUsageLimit(request.getTotalUsageLimit());
        if (request.getStartDate() != null)
            coupon.setStartDate(request.getStartDate());
        if (request.getEndDate() != null)
            coupon.setEndDate(request.getEndDate());
    }

    /**
     * Shared rules for coupons and flash sales: PERCENTAGE must be 1–100, FIXED_AMOUNT above 0,
     * and a scoped discount (STORE / CATEGORY / SUBCATEGORY / PRODUCT) needs its target id.
     */
    public void validateDiscountSettings(Coupon.DiscountType type, BigDecimal value,
                                         Coupon.ApplicableTo applicableTo, Long applicableId) {
        if (type == null) {
            throw new InvalidDataException("نوع الخصم مطلوب");
        }
        if (type == Coupon.DiscountType.PERCENTAGE
                && (value == null || value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new InvalidDataException("نسبة الخصم يجب أن تكون بين 1 و 100");
        }
        if (type == Coupon.DiscountType.FIXED_AMOUNT && (value == null || value.signum() <= 0)) {
            throw new InvalidDataException("قيمة الخصم يجب أن تكون أكبر من صفر");
        }
        if (applicableTo != null && applicableTo != Coupon.ApplicableTo.ALL && applicableId == null) {
            throw new InvalidDataException("يجب تحديد المتجر أو الفئة أو المنتج الذي ينطبق عليه الخصم");
        }
    }

    // =================================================================================
    // CHECKOUT
    // =================================================================================

    /**
     * Validates a code against the priced cart lines (offers already applied).
     * Scope is checked per item, so it works for multi-store orders, and the minimum order is
     * measured on the items the coupon applies to, after offer discounts.
     */
    public Coupon validateCouponForOrder(String code, Long userId, List<OrderItem> items) {
        // 1. Fetch Coupon (customers copy/paste codes — ignore case and stray spaces)
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("رمز قسيمة غير صالح"));

        // 2. Basic Status Checks
        if (Boolean.FALSE.equals(coupon.getIsActive())) {
            throw new InvalidDataException("القسيمة غير فعالة");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartDate()) || now.isAfter(coupon.getEndDate())) {
            throw new InvalidDataException("مدة القسيمة انتهت أو لم تبدأ بعد");
        }

        // 3. Global Usage Limit (fast check — enforced atomically in recordUsage)
        int currentUsage = coupon.getCurrentUsageCount() != null ? coupon.getCurrentUsageCount() : 0;
        if (coupon.getTotalUsageLimit() != null && currentUsage >= coupon.getTotalUsageLimit()) {
            throw new InvalidDataException("تم الوصول إلى الحد الأقصى لاستخدام القسيمة");
        }

        // 4. Per User Usage Limit
        Integer userUsage = usageRepository.countByCouponIdAndUserId(coupon.getCouponId(), userId);
        if (coupon.getMaxUsagePerUser() != null && userUsage != null && userUsage >= coupon.getMaxUsagePerUser()) {
            throw new InvalidDataException("لقد استخدمت هذه القسيمة الحد الأقصى من المرات");
        }

        // 5. First Order Check
        if (Boolean.TRUE.equals(coupon.getIsFirstOrderOnly())) {
            // Here checking if they have a coupon usage record
            if (usageRepository.existsByUserId(userId)) {
                throw new InvalidDataException("هذه القسيمة صالحة للطلبات الأولى فقط");
            }
        }

        // 6. Applicability Check (Scope) — at least one item must be covered
        List<OrderItem> eligibleItems = findEligibleItems(coupon, items);
        if (eligibleItems.isEmpty()) {
            throw new InvalidDataException(scopeErrorMessage(coupon.getApplicableTo()));
        }

        // 7. Minimum Order Amount Check — on the covered items, after offer discounts
        double eligibleSubtotal = eligibleItems.stream().mapToDouble(OrderItem::getNetTotalPrice).sum();
        if (coupon.getMinOrderAmount() != null &&
                BigDecimal.valueOf(eligibleSubtotal).compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new InvalidDataException("لم يتم الوصول للحد الأدنى للطلب " + coupon.getMinOrderAmount());
        }

        return coupon;
    }

    /** The lines a coupon applies to (all lines for ALL scope). */
    public List<OrderItem> findEligibleItems(Coupon coupon, List<OrderItem> items) {
        return items.stream().filter(item -> isEligible(coupon, item)).toList();
    }

    private boolean isEligible(Coupon coupon, OrderItem item) {
        Product product = item.getProduct();
        if (coupon.getApplicableTo() == null || product == null) {
            return coupon.getApplicableTo() == null || coupon.getApplicableTo() == Coupon.ApplicableTo.ALL;
        }
        Long targetId = coupon.getApplicableId();
        return switch (coupon.getApplicableTo()) {
            case STORE -> product.getStore() != null && Objects.equals(product.getStore().getStoreId(), targetId);
            case CATEGORY -> product.getCategory() != null
                    && Objects.equals(product.getCategory().getCategoryId(), targetId);
            case SUBCATEGORY -> product.getSubCategory() != null
                    && Objects.equals(product.getSubCategory().getSubcategoryId(), targetId);
            case PRODUCT -> Objects.equals(product.getProductId(), targetId);
            case ALL -> true;
        };
    }

    private String scopeErrorMessage(Coupon.ApplicableTo applicableTo) {
        if (applicableTo == null) {
            return "القسيمة غير صالحة لهذا الطلب";
        }
        return switch (applicableTo) {
            case STORE -> "القسيمة غير صالحة لهذا المتجر";
            case CATEGORY -> "تتطلب القسيمة عناصر من فئة معينة";
            case SUBCATEGORY -> "تتطلب القسيمة عناصر من فئة فرعية معينة";
            case PRODUCT -> "تتطلب القسيمة منتجاً معيناً في سلة التسوق";
            case ALL -> "القسيمة غير صالحة لهذا الطلب";
        };
    }

    /**
     * Item discount for PERCENTAGE / FIXED_AMOUNT coupons, on the subtotal of the covered items
     * (after offer discounts). Capped by maxDiscountAmount and by that subtotal, so a coupon can
     * never eat into the delivery fee. FREE_DELIVERY is handled by the delivery fee calculation.
     */
    public double calculateDiscount(Coupon coupon, double eligibleSubtotal) {
        if (eligibleSubtotal <= 0 || coupon.getDiscountType() == Coupon.DiscountType.FREE_DELIVERY) {
            return 0.0;
        }

        BigDecimal base = BigDecimal.valueOf(eligibleSubtotal);
        BigDecimal value = coupon.getDiscountValue() != null ? coupon.getDiscountValue() : BigDecimal.ZERO;
        BigDecimal discount;

        if (coupon.getDiscountType() == Coupon.DiscountType.PERCENTAGE) {
            // Whole SYP — no fractional pounds on receipts
            discount = base.multiply(value).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        } else {
            discount = value;
        }

        // Cap at Max Discount
        if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount();
        }

        // Ensure discount doesn't exceed what the covered items cost
        if (discount.compareTo(base) > 0) {
            discount = base;
        }

        return Math.max(discount.doubleValue(), 0.0);
    }

    /** Store paying for this coupon's discount: the vendor's store for a vendor flash sale, else null (platform). */
    public Long findFundingStoreId(Coupon coupon) {
        return flashSaleRepository.findFirstByBackingCouponId(coupon.getCouponId())
                .map(FlashSale::getStore)
                .map(Store::getStoreId)
                .orElse(null);
    }

    /**
     * Records the usage and takes a usage slot atomically. Must run inside the order transaction:
     * if a limit was reached by a concurrent checkout, this throws and the whole order rolls back.
     */
    @Transactional
    public void recordUsage(Coupon coupon, Long userId, Long orderId, Double discountAmount) {
        // The conditional UPDATE locks the coupon row until commit, so parallel checkouts queue here
        if (couponRepository.claimUsage(coupon.getCouponId()) == 0) {
            throw new InvalidDataException("تم الوصول إلى الحد الأقصى لاستخدام القسيمة");
        }

        CouponUsage usage = new CouponUsage();
        usage.setCouponId(coupon.getCouponId());
        usage.setUserId(userId);
        usage.setOrderId(orderId);
        usage.setDiscountApplied(BigDecimal.valueOf(discountAmount));
        usage.setUsedAt(LocalDateTime.now());

        usageRepository.save(usage);

        // Re-check the per-user limit while holding the coupon row lock: a parallel order by the
        // same user has committed by now, so its usage row is visible to this count
        Integer userUsage = usageRepository.countByCouponIdAndUserId(coupon.getCouponId(), userId);
        if (coupon.getMaxUsagePerUser() != null && userUsage != null && userUsage > coupon.getMaxUsagePerUser()) {
            throw new InvalidDataException("لقد استخدمت هذه القسيمة الحد الأقصى من المرات");
        }
    }

    /** Gives the coupon usage back when an order is cancelled or deleted. */
    @Transactional
    public void releaseUsageForOrder(Long orderId, Long couponId) {
        long removed = usageRepository.deleteByOrderId(orderId);
        if (couponId != null) {
            for (long i = 0; i < removed; i++) {
                couponRepository.releaseUsage(couponId);
            }
        }
    }
}
