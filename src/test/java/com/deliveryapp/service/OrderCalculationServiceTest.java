package com.deliveryapp.service;

import com.deliveryapp.dto.order.OrderItemRequest;
import com.deliveryapp.dto.order.OrderQuote;
import com.deliveryapp.entity.Coupon;
import com.deliveryapp.entity.FlashSale;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.repository.*;
import com.deliveryapp.util.DistanceUtil;
import com.deliveryapp.util.MathUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Geometry: customer at (0,0). 0.01° ≈ 1.112 km.
 * Store A at (0, 0.01) → 1.112 km east. Store B at (0.02, 0) → 2.224 km north.
 * Both charge 2,000/km; minimum fee 2,000 (A) and 3,000 (B).
 *   Fee for B alone      = 2.224 km × 2,000 = 4,447.8 → 4,450
 *   Fee for A + B route  = (1.112 + 2.486) km × 2,000 = 7,196.7 → 7,200
 */
@ExtendWith(MockitoExtension.class)
class OrderCalculationServiceTest {

    private static final Long USER_ID = 7L;
    private static final double USER_LAT = 0.0;
    private static final double USER_LNG = 0.0;

    @Mock private StoreRepository storeRepository;
    @Mock private UserAddressRepository addressRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private PricingService pricingService;
    @Mock private PromotionalOfferService promotionalOfferService;
    @Mock private CouponRepository couponRepository;
    @Mock private CouponUsageRepository usageRepository;
    @Mock private FlashSaleRepository flashSaleRepository;

    private OrderCalculationService service;
    private Store storeA;
    private Store storeB;

    @BeforeEach
    void setUp() {
        CouponService couponService = new CouponService(couponRepository, usageRepository, flashSaleRepository);
        service = new OrderCalculationService(storeRepository, addressRepository, productRepository,
                variantRepository, colorRepository, new DistanceUtil(), new MathUtil(), pricingService,
                couponService, promotionalOfferService);

        storeA = store(1L, 0.0, 0.01, 2_000);
        storeB = store(2L, 0.02, 0.0, 3_000);
    }

    // ── Delivery fee rules ────────────────────────────────────────────────────

    @Test
    void freeDeliveryStore_doesNotMakeTheOtherStoreFree() {
        storeA.setFreeDelivery(true);

        OrderQuote quote = quote(List.of(line(storeA, 5_000, 1), line(storeB, 5_000, 1)), null);

        // Customer pays B's delivery only (old code: one free store → whole order free)
        assertEquals(4_450.0, quote.getDeliveryFee());
        assertEquals(Set.of(storeA.getStoreId()), quote.getFreeDeliveryStoreIds());
    }

    @Test
    void allStoresFree_deliveryIsFree() {
        storeA.setFreeDelivery(true);
        storeB.setFreeDelivery(true);

        OrderQuote quote = quote(List.of(line(storeA, 5_000, 1), line(storeB, 5_000, 1)), null);

        assertEquals(0.0, quote.getDeliveryFee());
    }

    @Test
    void threshold_isCheckedAgainstThatStoresOwnItems() {
        enableThreshold(storeA, 10_000);

        // Whole order is 52,000 but only 2,000 is from store A (old code: free for everyone)
        OrderQuote quote = quote(List.of(line(storeA, 2_000, 1), line(storeB, 50_000, 1)), null);

        assertEquals(7_200.0, quote.getDeliveryFee());
        assertTrue(quote.getFreeDeliveryStoreIds().isEmpty());
    }

    @Test
    void threshold_countsTheSubtotalAfterOfferDiscounts() {
        enableThreshold(storeB, 20_000);
        OrderItem buyOneGetOne = line(storeB, 10_000, 2);
        buyOneGetOne.setOfferDiscountAmount(10_000.0); // customer actually pays 10,000

        OrderQuote quote = quote(List.of(buyOneGetOne), null);

        assertEquals(4_450.0, quote.getDeliveryFee());
    }

    @Test
    void threshold_reached_makesThatStoreFree() {
        enableThreshold(storeB, 20_000);

        OrderQuote quote = quote(List.of(line(storeB, 25_000, 1)), null);

        assertEquals(0.0, quote.getDeliveryFee());
        assertEquals(25_000.0, quote.getTotalAmount());
    }

    // ── Coupons / flash sales ─────────────────────────────────────────────────

    @Test
    void storeScopedFlashSale_discountsOnlyThatStoresItems_andIsChargedToThatStore() {
        Coupon coupon = coupon(Coupon.DiscountType.PERCENTAGE, "20");
        coupon.setApplicableTo(Coupon.ApplicableTo.STORE);
        coupon.setApplicableId(storeA.getStoreId());
        stubValidCoupon(coupon);
        FlashSale vendorFlashSale = new FlashSale();
        vendorFlashSale.setStore(storeA);
        when(flashSaleRepository.findFirstByBackingCouponId(coupon.getCouponId()))
                .thenReturn(Optional.of(vendorFlashSale));

        OrderQuote quote = quote(List.of(line(storeA, 10_000, 1), line(storeB, 90_000, 1)), "SALE");

        // 20% of store A's 10,000 — not 20% of the whole 100,000 order
        assertEquals(2_000.0, quote.getCouponDiscountAmount());
        assertEquals(storeA.getStoreId(), quote.getCouponFundedByStoreId());
        assertEquals(100_000 - 2_000 + 7_200, quote.getTotalAmount());
    }

    @Test
    void percentCoupon_appliesAfterOfferDiscounts() {
        stubValidCoupon(coupon(Coupon.DiscountType.PERCENTAGE, "10"));
        when(flashSaleRepository.findFirstByBackingCouponId(99L)).thenReturn(Optional.empty());
        OrderItem buyOneGetOne = line(storeB, 10_000, 2);
        buyOneGetOne.setOfferDiscountAmount(10_000.0);

        OrderQuote quote = quote(List.of(buyOneGetOne), "SALE");

        // 10% of the 10,000 actually paid, not of 20,000 (the free unit isn't discounted twice)
        assertEquals(1_000.0, quote.getCouponDiscountAmount());
        assertNull(quote.getCouponFundedByStoreId());
        assertEquals(20_000 - 10_000 - 1_000 + 4_450, quote.getTotalAmount());
    }

    @Test
    void fixedCoupon_neverEatsIntoTheDeliveryFee() {
        stubValidCoupon(coupon(Coupon.DiscountType.FIXED_AMOUNT, "15000"));
        when(flashSaleRepository.findFirstByBackingCouponId(99L)).thenReturn(Optional.empty());

        OrderQuote quote = quote(List.of(line(storeB, 10_000, 1)), "SALE");

        assertEquals(10_000.0, quote.getCouponDiscountAmount());
        assertEquals(4_450.0, quote.getTotalAmount()); // old code: 0 — delivery was wiped out too
    }

    @Test
    void freeDeliveryCoupon_waivesTheDeliveryFee() {
        stubValidCoupon(coupon(Coupon.DiscountType.FREE_DELIVERY, "0"));
        when(flashSaleRepository.findFirstByBackingCouponId(99L)).thenReturn(Optional.empty());

        OrderQuote quote = quote(List.of(line(storeB, 10_000, 1)), "SALE");

        assertEquals(4_450.0, quote.getCouponDiscountAmount());
        assertEquals(0.0, quote.getDeliveryFee());
        assertEquals(10_000.0, quote.getTotalAmount());
        assertTrue(quote.isCouponApplied());
    }

    @Test
    void freeDeliveryCoupon_whenDeliveryIsAlreadyFree_isNotConsumed() {
        storeB.setFreeDelivery(true);
        stubValidCoupon(coupon(Coupon.DiscountType.FREE_DELIVERY, "0"));

        OrderQuote quote = quote(List.of(line(storeB, 10_000, 1)), "SALE");

        assertEquals(0.0, quote.getCouponDiscountAmount());
        assertFalse(quote.isCouponApplied());
    }

    @Test
    void vendorFlashSaleWithMarkup_storePaysItsShareAtItsOwnPrices() {
        Coupon coupon = coupon(Coupon.DiscountType.PERCENTAGE, "20");
        coupon.setApplicableTo(Coupon.ApplicableTo.STORE);
        coupon.setApplicableId(storeA.getStoreId());
        stubValidCoupon(coupon);
        FlashSale vendorFlashSale = new FlashSale();
        vendorFlashSale.setStore(storeA);
        when(flashSaleRepository.findFirstByBackingCouponId(coupon.getCouponId()))
                .thenReturn(Optional.of(vendorFlashSale));
        OrderItem markedUp = line(storeA, 11_000, 1);
        markedUp.setStoreUnitPrice(10_000.0);
        markedUp.setStoreTotalPrice(10_000.0);

        OrderQuote quote = quote(List.of(markedUp), "SALE");

        assertEquals(2_200.0, quote.getCouponDiscountAmount());       // 20% of what the customer pays
        assertEquals(2_000.0, quote.getCouponStoreDiscountAmount());  // 20% of the store's own price
    }

    // ── Line validation ───────────────────────────────────────────────────────

    @Test
    void priceItems_chargesTheCustomerPriceAndKeepsTheStorePrice() {
        Product product = new Product();
        product.setProductId(10L);
        product.setName("Burger");
        product.setStore(storeA);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(pricingService.getFinalPriceInSYP(product)).thenReturn(10_000.0);
        when(pricingService.getCustomerFinalPrice(product)).thenReturn(11_000.0);
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(10L);
        request.setQuantity(2);

        OrderItem item = service.priceItems(List.of(request)).get(0);

        assertEquals(11_000.0, item.getUnitPrice());
        assertEquals(22_000.0, item.getTotalPrice());
        assertEquals(10_000.0, item.getStoreUnitPrice());
        assertEquals(20_000.0, item.getStoreTotalPrice());
    }

    @Test
    void priceItems_rejectsZeroOrNegativeQuantity() {
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(10L);
        request.setQuantity(-4);

        assertThrows(InvalidDataException.class, () -> service.priceItems(List.of(request)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OrderQuote quote(List<OrderItem> items, String couponCode) {
        return service.quote(items, USER_ID, couponCode, USER_LAT, USER_LNG);
    }

    private Store store(long id, double lat, double lng, double minimumFee) {
        Store store = new Store();
        store.setStoreId(id);
        store.setName("Store " + id);
        store.setLatitude(lat);
        store.setLongitude(lng);
        store.setDeliveryFeeKM(2_000.0);
        store.setMinimumDeliveryFee(minimumFee);
        return store;
    }

    private void enableThreshold(Store store, double threshold) {
        store.setFreeDeliveryThreshold(threshold);
        store.setFreeDeliveryThresholdEnabled(true);
    }

    private OrderItem line(Store store, double unitPrice, int quantity) {
        Product product = new Product();
        product.setProductId(store.getStoreId() * 100 + (long) unitPrice);
        product.setStore(store);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTotalPrice(unitPrice * quantity);
        return item;
    }

    private Coupon coupon(Coupon.DiscountType type, String value) {
        Coupon coupon = new Coupon();
        coupon.setCouponId(99L);
        coupon.setCode("SALE");
        coupon.setDiscountType(type);
        coupon.setDiscountValue(new BigDecimal(value));
        coupon.setApplicableTo(Coupon.ApplicableTo.ALL);
        coupon.setIsActive(true);
        coupon.setStartDate(LocalDateTime.now().minusDays(1));
        coupon.setEndDate(LocalDateTime.now().plusDays(1));
        coupon.setMaxUsagePerUser(1);
        coupon.setCurrentUsageCount(0);
        return coupon;
    }

    private void stubValidCoupon(Coupon coupon) {
        when(couponRepository.findByCodeIgnoreCase("SALE")).thenReturn(Optional.of(coupon));
        when(usageRepository.countByCouponIdAndUserId(coupon.getCouponId(), USER_ID)).thenReturn(0);
    }
}
