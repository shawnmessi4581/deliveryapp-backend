package com.deliveryapp.service;

import com.deliveryapp.dto.flashsale.FlashSaleRequest;
import com.deliveryapp.dto.flashsale.FlashSaleResponse;
import com.deliveryapp.entity.Coupon;
import com.deliveryapp.entity.FlashSale;
import com.deliveryapp.entity.Store;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.mapper.flashsale.FlashSaleMapper;
import com.deliveryapp.repository.CouponRepository;
import com.deliveryapp.repository.FlashSaleRepository;
import com.deliveryapp.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core business logic for {@link FlashSale} lifecycle management.
 *
 * <h3>Coupon auto-management</h3>
 * Every flash sale owns a backing {@link Coupon} that is:
 * <ul>
 * <li>Auto-created with a unique {@code FLASH-XXXXXXXX} code on
 * {@code create}.</li>
 * <li>Kept in sync (dates, discount, scope, status) on every
 * {@code update}.</li>
 * <li>Deactivated when the flash sale is toggled off.</li>
 * <li>Hard-deleted when the flash sale is deleted.</li>
 * </ul>
 *
 * <h3>Security contract</h3>
 * <ul>
 * <li>Admin methods: manage any flash sale.</li>
 * <li>Vendor methods: automatically constrained to their store;
 * {@code applicableTo} is
 * forced to {@code STORE} and {@code applicableId} to their store ID.</li>
 * </ul>
 *
 * <h3>Auto-expiry</h3>
 * A {@link #autoExpireFlashSales()} scheduled job runs every minute to
 * deactivate
 * flash sales (and their backing coupons) whose {@code countdownEndsAt} has
 * passed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleService {

    private final FlashSaleRepository flashSaleRepository;
    private final CouponRepository couponRepository;
    private final StoreRepository storeRepository;
    private final FlashSaleMapper flashSaleMapper;

    // =================================================================================
    // ADMIN CRUD
    // =================================================================================

    @Transactional
    public FlashSaleResponse adminCreate(FlashSaleRequest request, Long adminUserId) {
        validateDates(request);
        String code = resolveCode(request.getCouponCode());

        Coupon coupon = buildCoupon(request, code, adminUserId, null);
        coupon = couponRepository.save(coupon);

        FlashSale fs = buildFlashSale(request, coupon, null, adminUserId, code);
        fs = flashSaleRepository.save(fs);

        return flashSaleMapper.toResponse(fs, coupon);
    }

    public Page<FlashSaleResponse> adminGetAll(Pageable pageable) {
        return flashSaleRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(flashSaleMapper::toResponseWithCouponLookup);
    }

    public FlashSaleResponse adminGetById(Long flashSaleId) {
        return flashSaleMapper.toResponseWithCouponLookup(findById(flashSaleId));
    }

    @Transactional
    public FlashSaleResponse adminUpdate(Long flashSaleId, FlashSaleRequest request, Long adminUserId) {
        validateDates(request);
        FlashSale fs = findById(flashSaleId);
        Coupon coupon = findBackingCoupon(fs);

        syncCoupon(coupon, request, null);
        couponRepository.save(coupon);

        applyRequestToFlashSale(fs, request, null);
        fs.setUpdatedAt(LocalDateTime.now());
        fs = flashSaleRepository.save(fs);

        return flashSaleMapper.toResponse(fs, coupon);
    }

    @Transactional
    public FlashSaleResponse adminToggleStatus(Long flashSaleId) {
        FlashSale fs = findById(flashSaleId);
        Coupon coupon = findBackingCoupon(fs);

        boolean newStatus = !fs.getIsActive();
        fs.setIsActive(newStatus);
        fs.setUpdatedAt(LocalDateTime.now());
        coupon.setIsActive(newStatus);

        couponRepository.save(coupon);
        flashSaleRepository.save(fs);
        return flashSaleMapper.toResponse(fs, coupon);
    }

    @Transactional
    public void adminDelete(Long flashSaleId) {
        FlashSale fs = findById(flashSaleId);
        deleteFlashSaleAndCoupon(fs);
    }

    // =================================================================================
    // VENDOR CRUD
    // =================================================================================

    @Transactional
    public FlashSaleResponse vendorCreate(FlashSaleRequest request, Long vendorStoreId, Long vendorUserId) {
        validateDates(request);
        Store store = storeRepository.findById(vendorStoreId)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

        // Force vendor scope
        request.setApplicableTo(Coupon.ApplicableTo.STORE);
        request.setApplicableId(vendorStoreId);

        String code = resolveCode(request.getCouponCode());

        Coupon coupon = buildCoupon(request, code, vendorUserId, store);
        coupon = couponRepository.save(coupon);

        FlashSale fs = buildFlashSale(request, coupon, store, vendorUserId, code);
        fs = flashSaleRepository.save(fs);

        return flashSaleMapper.toResponse(fs, coupon);
    }

    public Page<FlashSaleResponse> vendorGetAll(Long vendorStoreId, Pageable pageable) {
        return flashSaleRepository.findByStoreStoreIdOrderByCreatedAtDesc(vendorStoreId, pageable)
                .map(flashSaleMapper::toResponseWithCouponLookup);
    }

    public FlashSaleResponse vendorGetById(Long flashSaleId, Long vendorStoreId) {
        FlashSale fs = findById(flashSaleId);
        assertOwnership(fs, vendorStoreId);
        return flashSaleMapper.toResponseWithCouponLookup(fs);
    }

    @Transactional
    public FlashSaleResponse vendorUpdate(Long flashSaleId, FlashSaleRequest request,
            Long vendorStoreId, Long vendorUserId) {
        validateDates(request);
        FlashSale fs = findById(flashSaleId);
        assertOwnership(fs, vendorStoreId);

        // Always keep vendor scope locked
        request.setApplicableTo(Coupon.ApplicableTo.STORE);
        request.setApplicableId(vendorStoreId);

        Coupon coupon = findBackingCoupon(fs);
        syncCoupon(coupon, request, fs.getStore());
        couponRepository.save(coupon);

        applyRequestToFlashSale(fs, request, fs.getStore());
        fs.setUpdatedAt(LocalDateTime.now());
        fs = flashSaleRepository.save(fs);

        return flashSaleMapper.toResponse(fs, coupon);
    }

    @Transactional
    public FlashSaleResponse vendorToggleStatus(Long flashSaleId, Long vendorStoreId) {
        FlashSale fs = findById(flashSaleId);
        assertOwnership(fs, vendorStoreId);

        boolean newStatus = !fs.getIsActive();
        Coupon coupon = findBackingCoupon(fs);

        fs.setIsActive(newStatus);
        fs.setUpdatedAt(LocalDateTime.now());
        coupon.setIsActive(newStatus);

        couponRepository.save(coupon);
        flashSaleRepository.save(fs);
        return flashSaleMapper.toResponse(fs, coupon);
    }

    @Transactional
    public void vendorDelete(Long flashSaleId, Long vendorStoreId) {
        FlashSale fs = findById(flashSaleId);
        assertOwnership(fs, vendorStoreId);
        deleteFlashSaleAndCoupon(fs);
    }

    // =================================================================================
    // PUBLIC
    // =================================================================================

    public List<FlashSaleResponse> getPublicActive() {
        return flashSaleRepository.findAllActiveAndValid(LocalDateTime.now())
                .stream()
                .map(flashSaleMapper::toResponseWithCouponLookup)
                .collect(Collectors.toList());
    }

    public List<FlashSaleResponse> getPublicActiveForStore(Long storeId) {
        return flashSaleRepository.findActiveAndValidByStore(storeId, LocalDateTime.now())
                .stream()
                .map(flashSaleMapper::toResponseWithCouponLookup)
                .collect(Collectors.toList());
    }

    // =================================================================================
    // SCHEDULED: Auto-expire flash sales
    // =================================================================================

    /**
     * Runs every 60 seconds. Deactivates flash sales and their backing coupons
     * whose {@code countdownEndsAt} timestamp has passed.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void autoExpireFlashSales() {
        List<FlashSale> expired = flashSaleRepository.findExpiredActiveFlashSales(LocalDateTime.now());
        if (expired.isEmpty())
            return;

        log.info("[FlashSale] Auto-expiring {} flash sale(s).", expired.size());

        for (FlashSale fs : expired) {
            fs.setIsActive(false);
            fs.setUpdatedAt(LocalDateTime.now());
            flashSaleRepository.save(fs);

            if (fs.getBackingCouponId() != null) {
                couponRepository.findById(fs.getBackingCouponId()).ifPresent(c -> {
                    c.setIsActive(false);
                    couponRepository.save(c);
                });
            }
        }
    }

    // =================================================================================
    // PRIVATE HELPERS
    // =================================================================================

    private String resolveCode(String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String upper = requestedCode.toUpperCase().trim();
            if (flashSaleRepository.existsByCouponCodeIgnoreCase(upper)
                    || couponRepository.existsByCode(upper)) {
                throw new DuplicateResourceException("رمز الكوبون '" + upper + "' موجود مسبقاً، اختر رمزاً آخر.");
            }
            return upper;
        }
        // Auto-generate unique code
        String code;
        int attempts = 0;
        do {
            code = "FLASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            attempts++;
            if (attempts > 20)
                throw new RuntimeException("فشل توليد رمز كوبون فريد.");
        } while (couponRepository.existsByCode(code) || flashSaleRepository.existsByCouponCodeIgnoreCase(code));
        return code;
    }

    private Coupon buildCoupon(FlashSaleRequest req, String code, Long userId, Store store) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setTitle(req.getTitle());
        coupon.setDescription(req.getDescription());
        coupon.setDiscountType(req.getDiscountType());
        coupon.setDiscountValue(req.getDiscountValue());
        coupon.setMinOrderAmount(req.getMinOrderAmount());
        coupon.setMaxDiscountAmount(req.getMaxDiscountAmount());
        coupon.setApplicableTo(req.getApplicableTo() != null ? req.getApplicableTo() : Coupon.ApplicableTo.ALL);
        coupon.setApplicableId(req.getApplicableId());
        coupon.setIsFirstOrderOnly(false);
        coupon.setMaxUsagePerUser(req.getMaxUsagePerUser() != null ? req.getMaxUsagePerUser() : 1);
        coupon.setTotalUsageLimit(req.getTotalUsageLimit());
        coupon.setCurrentUsageCount(0);
        coupon.setStartDate(req.getStartDate());
        coupon.setEndDate(req.getCountdownEndsAt());
        coupon.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
        coupon.setCreatedBy(userId);
        coupon.setCreatedAt(LocalDateTime.now());
        return coupon;
    }

    private void syncCoupon(Coupon coupon, FlashSaleRequest req, Store store) {
        coupon.setTitle(req.getTitle());
        coupon.setDescription(req.getDescription());
        coupon.setDiscountType(req.getDiscountType());
        coupon.setDiscountValue(req.getDiscountValue());
        coupon.setMinOrderAmount(req.getMinOrderAmount());
        coupon.setMaxDiscountAmount(req.getMaxDiscountAmount());
        coupon.setApplicableTo(req.getApplicableTo() != null ? req.getApplicableTo() : Coupon.ApplicableTo.ALL);
        coupon.setApplicableId(req.getApplicableId());
        coupon.setMaxUsagePerUser(req.getMaxUsagePerUser() != null ? req.getMaxUsagePerUser() : 1);
        coupon.setTotalUsageLimit(req.getTotalUsageLimit());
        coupon.setStartDate(req.getStartDate());
        coupon.setEndDate(req.getCountdownEndsAt());
        coupon.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
    }

    private FlashSale buildFlashSale(FlashSaleRequest req, Coupon coupon, Store store,
            Long userId, String code) {
        FlashSale fs = new FlashSale();
        applyRequestToFlashSale(fs, req, store);
        fs.setCouponCode(code);
        fs.setBackingCouponId(coupon.getCouponId());
        fs.setStore(store);
        fs.setCreatedBy(userId);
        fs.setCreatedAt(LocalDateTime.now());
        fs.setUpdatedAt(LocalDateTime.now());
        return fs;
    }

    private void applyRequestToFlashSale(FlashSale fs, FlashSaleRequest req, Store store) {
        fs.setTitle(req.getTitle());
        fs.setDescription(req.getDescription());
        fs.setBannerImage(req.getBannerImage());
        fs.setDiscountType(req.getDiscountType());
        fs.setDiscountValue(req.getDiscountValue());
        fs.setMinOrderAmount(req.getMinOrderAmount());
        fs.setMaxDiscountAmount(req.getMaxDiscountAmount());
        fs.setApplicableTo(req.getApplicableTo() != null ? req.getApplicableTo() : Coupon.ApplicableTo.ALL);
        fs.setApplicableId(req.getApplicableId());
        fs.setTotalUsageLimit(req.getTotalUsageLimit());
        fs.setMaxUsagePerUser(req.getMaxUsagePerUser() != null ? req.getMaxUsagePerUser() : 1);
        fs.setStartDate(req.getStartDate());
        fs.setCountdownEndsAt(req.getCountdownEndsAt());
        fs.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
        if (store != null)
            fs.setStore(store);
    }

    private void deleteFlashSaleAndCoupon(FlashSale fs) {
        if (fs.getBackingCouponId() != null) {
            couponRepository.findById(fs.getBackingCouponId()).ifPresent(couponRepository::delete);
        }
        flashSaleRepository.delete(fs);
    }

    private void validateDates(FlashSaleRequest req) {
        if (req.getStartDate() == null || req.getCountdownEndsAt() == null) {
            throw new InvalidDataException("تاريخ البداية ووقت انتهاء العد التنازلي مطلوبان");
        }
        if (req.getCountdownEndsAt().isBefore(req.getStartDate())) {
            throw new InvalidDataException("وقت انتهاء العد التنازلي يجب أن يكون بعد تاريخ البداية");
        }
        if (req.getCountdownEndsAt().isBefore(LocalDateTime.now())) {
            throw new InvalidDataException("وقت انتهاء العد التنازلي يجب أن يكون في المستقبل");
        }
    }

    private FlashSale findById(Long id) {
        return flashSaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("الفلاش سيل غير موجود برقم: " + id));
    }

    private Coupon findBackingCoupon(FlashSale fs) {
        if (fs.getBackingCouponId() == null) {
            throw new ResourceNotFoundException("لا يوجد كوبون مرتبط بهذا الفلاش سيل");
        }
        return couponRepository.findById(fs.getBackingCouponId())
                .orElseThrow(() -> new ResourceNotFoundException("الكوبون المرتبط غير موجود"));
    }

    private void assertOwnership(FlashSale fs, Long storeId) {
        if (fs.getStore() == null || !fs.getStore().getStoreId().equals(storeId)) {
            throw new InvalidDataException("هذا الفلاش سيل لا ينتمي لمتجرك");
        }
    }

}
