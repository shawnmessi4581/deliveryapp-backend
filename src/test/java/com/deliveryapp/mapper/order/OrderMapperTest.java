package com.deliveryapp.mapper.order;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.order.OrderOfferResponse;
import com.deliveryapp.dto.order.VendorOrderResponse;
import com.deliveryapp.entity.Coupon;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.PromotionalOffer;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.mapper.user.UserMapper;
import com.deliveryapp.repository.CouponRepository;
import com.deliveryapp.repository.PromotionalOfferRepository;
import com.deliveryapp.service.OrderProfitService;
import com.deliveryapp.service.StorePayoutService;
import com.deliveryapp.util.DistanceUtil;
import com.deliveryapp.util.MathUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMapperTest {

    @Mock private CatalogMapper catalogMapper;
    @Mock private UserMapper userMapper;
    @Mock private CouponRepository couponRepository;
    @Mock private PromotionalOfferRepository offerRepository;

    private OrderMapper mapper;
    private Order order;

    @BeforeEach
    void setUp() {
        StorePayoutService payouts = new StorePayoutService();
        mapper = new OrderMapper(catalogMapper, userMapper, new DistanceUtil(), payouts,
                new OrderProfitService(null, payouts, new MathUtil()), couponRepository, offerRepository);

        Store store = new Store();
        store.setStoreId(1L);
        store.setName("Store");
        store.setCommissionPercentage(10.0);
        Product product = new Product();
        product.setProductId(5L);
        product.setStore(store);

        // 10% markup: customer pays 11,000, the store's price is 10,000
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(11_000.0);
        item.setTotalPrice(11_000.0);
        item.setStoreUnitPrice(10_000.0);
        item.setStoreTotalPrice(10_000.0);

        order = new Order();
        order.setStores(List.of(store));
        order.setOrderItems(List.of(item));
        // Only the customer and staff views map the store card
        lenient().when(catalogMapper.toStoreResponse(store)).thenReturn(new StoreResponse());
    }

    @Test
    void customerView_neverContainsStorePricesOrPayouts() {
        OrderResponse response = mapper.toCustomerOrderResponse(order);
        OrderItemResponse item = response.getItems().get(0);

        assertEquals(11_000.0, item.getUnitPrice());
        assertNull(item.getStoreUnitPrice());
        assertNull(item.getStoreTotalPrice());
        assertNull(item.getStoreCommissionPercentage());
        assertNull(response.getStorePayouts());
    }

    @Test
    void staffAndDriverView_hasBothPricesAndThePayout() {
        OrderResponse response = mapper.toOrderResponse(order);
        OrderItemResponse item = response.getItems().get(0);

        assertEquals(11_000.0, item.getUnitPrice());
        assertEquals(10_000.0, item.getStoreUnitPrice());
        assertEquals(9_000.0, response.getStorePayouts().get(0).getStorePayout()); // 10,000 − 10%
    }

    @Test
    void vendorView_explainsTheStoresOfferAndCoupon() {
        givenStorePaidOfferAndCoupon();

        VendorOrderResponse response = mapper.toVendorOrderResponse(order, 1L);

        assertEquals(10_000.0, response.getStoreSubtotal());
        assertEquals(2_000.0, response.getStoreOfferDiscountAmount());
        assertEquals(1_000.0, response.getStoreCouponDiscountAmount());
        assertEquals(3_000.0, response.getStoreDiscountAmount());
        assertEquals(10.0, response.getCommissionPercentage());
        assertEquals(700.0, response.getCommissionAmount());
        assertEquals(6_300.0, response.getStorePayout()); // (10,000 − 3,000) − 10%
        assertEquals("FLASH-AB12CD34", response.getCouponCode());
        assertEquals("خصم الجمعة", response.getCouponTitle());
        assertEquals("PERCENTAGE", response.getCouponDiscountType());
        assertEquals(10.0, response.getCouponDiscountValue());
        assertEquals(1_100.0, response.getCouponDiscountAmount()); // customer's saving; the store pays 1,000 of it
        assertTrue(response.getCouponFundedByStore());
        assertEquals(List.of(new OrderOfferResponse(40L, "اشترِ 1 واحصل على 1", true, 1L, "Store")),
                response.getOffers());
    }

    @Test
    void staffView_hasTheWholeCouponAndOfferPicture() {
        givenStorePaidOfferAndCoupon();

        OrderResponse response = mapper.toAdminOrderResponse(order);

        assertEquals("FLASH-AB12CD34", response.getCouponCode());
        assertEquals("خصم الجمعة", response.getCouponTitle());
        assertEquals("PERCENTAGE", response.getCouponDiscountType());
        assertEquals(10.0, response.getCouponDiscountValue());
        assertEquals(1_100.0, response.getDiscountAmount());      // the customer's saving
        assertTrue(response.getCouponFundedByStore());
        assertEquals(1L, response.getCouponFundedByStoreId());
        assertEquals("Store", response.getCouponFundedByStoreName());
        assertEquals(1_000.0, response.getCouponStoreDiscountAmount()); // the store's part, same as its payout
        assertEquals(1_000.0, response.getStorePayouts().get(0).getStoreCouponDiscountAmount());
        assertEquals(List.of(new OrderOfferResponse(40L, "اشترِ 1 واحصل على 1", true, 1L, "Store")),
                response.getOffers());
    }

    @Test
    void staffView_appPaidCoupon_hasNoPayingStore() {
        order.setCouponId(31L);
        order.setDiscountAmount(1_500.0);
        when(couponRepository.findById(31L)).thenReturn(Optional.empty());

        OrderResponse response = mapper.toOrderResponse(order);

        assertFalse(response.getCouponFundedByStore());
        assertNull(response.getCouponFundedByStoreId());
        assertNull(response.getCouponFundedByStoreName());
        assertEquals(0.0, response.getCouponStoreDiscountAmount());
        assertTrue(response.getOffers().isEmpty());
    }

    @Test
    void customerView_showsTheCouponAndOffers_butNotWhoPays() {
        givenStorePaidOfferAndCoupon();

        OrderResponse response = mapper.toCustomerOrderResponse(order);

        assertEquals("FLASH-AB12CD34", response.getCouponCode());
        assertEquals(10.0, response.getCouponDiscountValue());
        assertNull(response.getCouponFundedByStore());
        assertNull(response.getCouponFundedByStoreId());
        assertNull(response.getCouponFundedByStoreName());
        assertNull(response.getCouponStoreDiscountAmount());
        assertEquals("اشترِ 1 واحصل على 1", response.getOffers().get(0).getTitle());
        assertNull(response.getOffers().get(0).getFundedByStore());
    }

    /** The store's own offer (2,000 at its prices) and its own flash-sale coupon (1,000 of the customer's 1,100). */
    private void givenStorePaidOfferAndCoupon() {
        OrderItem item = order.getOrderItems().get(0);
        item.setAppliedOfferId(40L);
        item.setOfferDiscountAmount(2_200.0);
        item.setOfferFundedByStore(true);
        item.setStoreOfferDiscountAmount(2_000.0);
        order.setCouponId(30L);
        order.setCouponFundedByStoreId(1L);
        order.setDiscountAmount(1_100.0);
        order.setCouponStoreDiscountAmount(1_000.0);

        Coupon coupon = new Coupon();
        coupon.setCode("FLASH-AB12CD34");
        coupon.setTitle("خصم الجمعة");
        coupon.setDiscountType(Coupon.DiscountType.PERCENTAGE);
        coupon.setDiscountValue(new java.math.BigDecimal("10.00"));
        when(couponRepository.findById(30L)).thenReturn(Optional.of(coupon));
        PromotionalOffer offer = new PromotionalOffer();
        offer.setOfferId(40L);
        offer.setTitle("اشترِ 1 واحصل على 1");
        when(offerRepository.findAllById(any())).thenReturn(List.of(offer));
    }

    @Test
    void vendorView_appPaidCoupon_leavesThePayoutAlone() {
        order.setCouponId(31L);
        order.setDiscountAmount(1_500.0); // an admin coupon: the app pays it
        when(couponRepository.findById(31L)).thenReturn(Optional.empty()); // e.g. deleted since

        VendorOrderResponse response = mapper.toVendorOrderResponse(order, 1L);

        assertFalse(response.getCouponFundedByStore());
        assertEquals(0.0, response.getStoreCouponDiscountAmount());
        assertEquals(1_500.0, response.getCouponDiscountAmount()); // still shown, paid by the app
        assertNull(response.getCouponCode());
        assertNull(response.getCouponDiscountValue());
        assertTrue(response.getOffers().isEmpty());
        assertEquals(9_000.0, response.getStorePayout());
    }

    @Test
    void vendorView_freeDeliveryCoupon_hasNoValue_andNoCouponMeansZero() {
        Coupon freeDelivery = new Coupon();
        freeDelivery.setCode("FREESHIP");
        freeDelivery.setDiscountType(Coupon.DiscountType.FREE_DELIVERY);
        freeDelivery.setDiscountValue(java.math.BigDecimal.ZERO);
        order.setCouponId(32L);
        order.setDiscountAmount(4_000.0); // the waived delivery fee
        when(couponRepository.findById(32L)).thenReturn(Optional.of(freeDelivery));

        VendorOrderResponse withCoupon = mapper.toVendorOrderResponse(order, 1L);
        assertEquals("FREE_DELIVERY", withCoupon.getCouponDiscountType());
        assertNull(withCoupon.getCouponDiscountValue());
        assertEquals(4_000.0, withCoupon.getCouponDiscountAmount());

        order.setCouponId(null);
        order.setDiscountAmount(null);
        VendorOrderResponse withoutCoupon = mapper.toVendorOrderResponse(order, 1L);
        assertEquals(0.0, withoutCoupon.getCouponDiscountAmount());
        assertNull(withoutCoupon.getCouponDiscountValue());
    }
}
