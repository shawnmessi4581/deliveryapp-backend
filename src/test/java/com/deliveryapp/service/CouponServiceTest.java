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
import com.deliveryapp.repository.FlashSaleRepository;
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

    private CouponService service;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        service = new CouponService(couponRepository, usageRepository, flashSaleRepository);

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
