package com.deliveryapp.service;

import com.deliveryapp.entity.Coupon;
import com.deliveryapp.entity.CouponUsage;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.SubCategory;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.repository.CouponRepository;
import com.deliveryapp.repository.CouponUsageRepository;
import com.deliveryapp.dto.coupon.CouponRequest;
import com.deliveryapp.entity.FlashSale;
import com.deliveryapp.repository.FlashSaleRepository;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private CouponRepository couponRepository;
    @Mock private CouponUsageRepository usageRepository;
    @Mock private FlashSaleRepository flashSaleRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private ProductRepository productRepository;

    private CouponService service;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        service = new CouponService(couponRepository, usageRepository, flashSaleRepository,
                storeRepository, productRepository);

        coupon = new Coupon();
        coupon.setCouponId(99L);
        coupon.setCode("FLASH-AB12");
        coupon.setDiscountType(Coupon.DiscountType.PERCENTAGE);
        coupon.setDiscountValue(new BigDecimal("10"));
        coupon.setApplicableTo(Coupon.ApplicableTo.ALL);
        coupon.setIsActive(true);
        coupon.setStartDate(LocalDateTime.now().minusDays(1));
        coupon.setEndDate(LocalDateTime.now().plusDays(1));
        coupon.setMaxUsagePerUser(1);
        coupon.setCurrentUsageCount(0);
    }

    @Test
    void codeLookup_ignoresCaseAndCopyPastedSpaces() {
        when(couponRepository.findByCodeIgnoreCase("flash-ab12")).thenReturn(Optional.of(coupon));
        when(usageRepository.countByCouponIdAndUserId(99L, USER_ID)).thenReturn(0);

        assertSame(coupon, service.validateCouponForOrder("  flash-ab12 ", USER_ID, List.of(line(10_000, 7L))));
    }

    @Test
    void subcategoryScope_isEnforced() {
        // Used to fall through to "valid for everything"
        coupon.setApplicableTo(Coupon.ApplicableTo.SUBCATEGORY);
        coupon.setApplicableId(7L);
        when(couponRepository.findByCodeIgnoreCase("FLASH-AB12")).thenReturn(Optional.of(coupon));
        when(usageRepository.countByCouponIdAndUserId(99L, USER_ID)).thenReturn(0);

        assertThrows(InvalidDataException.class,
                () -> service.validateCouponForOrder("FLASH-AB12", USER_ID, List.of(line(10_000, 8L))));
        assertSame(coupon, service.validateCouponForOrder("FLASH-AB12", USER_ID, List.of(line(10_000, 7L))));
    }

    @Test
    void minimumOrder_isMeasuredAfterOfferDiscounts() {
        coupon.setMinOrderAmount(new BigDecimal("15000"));
        when(couponRepository.findByCodeIgnoreCase("FLASH-AB12")).thenReturn(Optional.of(coupon));
        when(usageRepository.countByCouponIdAndUserId(99L, USER_ID)).thenReturn(0);
        OrderItem buyOneGetOne = line(10_000, 7L);
        buyOneGetOne.setQuantity(2);
        buyOneGetOne.setTotalPrice(20_000.0);
        buyOneGetOne.setOfferDiscountAmount(10_000.0); // pays 10,000 < 15,000

        assertThrows(InvalidDataException.class,
                () -> service.validateCouponForOrder("FLASH-AB12", USER_ID, List.of(buyOneGetOne)));
    }

    @Test
    void recordUsage_failsWhenAParallelCheckoutTookTheLastSlot() {
        when(couponRepository.claimUsage(99L)).thenReturn(0);

        assertThrows(InvalidDataException.class, () -> service.recordUsage(coupon, USER_ID, 1L, 1_000.0));
        verify(usageRepository, never()).save(any(CouponUsage.class));
    }

    @Test
    void recordUsage_rejectsASecondParallelUseBySameUser() {
        when(couponRepository.claimUsage(99L)).thenReturn(1);
        when(usageRepository.countByCouponIdAndUserId(99L, USER_ID)).thenReturn(2);

        assertThrows(InvalidDataException.class, () -> service.recordUsage(coupon, USER_ID, 1L, 1_000.0));
    }

    @Test
    void percentageAbove100_isRejected() {
        assertThrows(InvalidDataException.class, () -> service.validateDiscountSettings(
                Coupon.DiscountType.PERCENTAGE, new BigDecimal("150"), Coupon.ApplicableTo.ALL, null));
        assertThrows(InvalidDataException.class, () -> service.validateDiscountSettings(
                Coupon.DiscountType.FIXED_AMOUNT, new BigDecimal("500"), Coupon.ApplicableTo.STORE, null));
    }

    // ── who pays ─────────────────────────────────────────────────────────────

    @Test
    void storeFundedCoupon_isPaidByTheCouponsStore() {
        coupon.setApplicableTo(Coupon.ApplicableTo.STORE);
        coupon.setApplicableId(4L);
        when(storeRepository.existsById(4L)).thenReturn(true);

        service.applyFunding(coupon, true);

        assertEquals(4L, service.findFundingStoreId(coupon));
    }

    @Test
    void storeFundedProductCoupon_isPaidByTheProductsStore() {
        coupon.setApplicableTo(Coupon.ApplicableTo.PRODUCT);
        coupon.setApplicableId(10L);
        when(productRepository.findById(10L)).thenReturn(Optional.of(line(1_000, 1).getProduct()));

        service.applyFunding(coupon, true);

        assertEquals(1L, service.findFundingStoreId(coupon));
    }

    @Test
    void storeFunding_needsACouponOnOneStoreOrProduct() {
        // ALL (from setUp) and CATEGORY span many stores
        assertThrows(InvalidDataException.class, () -> service.applyFunding(coupon, true));
        coupon.setApplicableTo(Coupon.ApplicableTo.CATEGORY);
        coupon.setApplicableId(3L);
        assertThrows(InvalidDataException.class, () -> service.applyFunding(coupon, true));
    }

    @Test
    void platformFunding_winsOverTheOldVendorFlashSaleRule() {
        service.applyFunding(coupon, false);

        assertNull(service.findFundingStoreId(coupon));
        verify(flashSaleRepository, never()).findFirstByBackingCouponId(any());
    }

    @Test
    void olderCoupon_behindAVendorFlashSale_isStillPaidByThatStore() {
        Store store = new Store();
        store.setStoreId(6L);
        FlashSale flashSale = new FlashSale();
        flashSale.setStore(store);
        when(flashSaleRepository.findFirstByBackingCouponId(99L)).thenReturn(Optional.of(flashSale));

        assertEquals(6L, service.findFundingStoreId(coupon)); // fundedByStore never set
    }

    @Test
    void update_withoutFundedByStore_keepsTheCurrentPayer() {
        coupon.setApplicableTo(Coupon.ApplicableTo.STORE);
        coupon.setApplicableId(4L);
        coupon.setFundedByStore(true);
        coupon.setFundedByStoreId(4L);
        when(couponRepository.findById(99L)).thenReturn(Optional.of(coupon));
        when(storeRepository.existsById(4L)).thenReturn(true);
        when(couponRepository.save(coupon)).thenReturn(coupon);
        CouponRequest request = new CouponRequest();
        request.setCode("FLASH-AB12");
        request.setTitle("عنوان جديد");

        Coupon updated = service.updateCoupon(99L, request);

        assertTrue(updated.getFundedByStore());
        assertEquals(4L, updated.getFundedByStoreId());
    }

    @Test
    void usesPerCustomer_mustBeAtLeastOne() {
        assertThrows(InvalidDataException.class, () -> service.validateMaxUsagePerUser(0));
        assertDoesNotThrow(() -> service.validateMaxUsagePerUser(3));
        assertDoesNotThrow(() -> service.validateMaxUsagePerUser(null));
    }

    private OrderItem line(double price, long subCategoryId) {
        SubCategory subCategory = new SubCategory();
        subCategory.setSubcategoryId(subCategoryId);
        Store store = new Store();
        store.setStoreId(1L);
        Product product = new Product();
        product.setProductId(10L);
        product.setStore(store);
        product.setSubCategory(subCategory);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(price);
        item.setQuantity(1);
        item.setTotalPrice(price);
        return item;
    }
}
