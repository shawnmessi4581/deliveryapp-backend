package com.deliveryapp.mapper.order;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.order.VendorOrderOfferResponse;
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
        // The store's own offer (2,000 at its prices) and its own flash-sale coupon (1,000)
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
        when(couponRepository.findById(30L)).thenReturn(Optional.of(coupon));
        PromotionalOffer offer = new PromotionalOffer();
        offer.setOfferId(40L);
        offer.setTitle("اشترِ 1 واحصل على 1");
        when(offerRepository.findAllById(any())).thenReturn(List.of(offer));

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
        assertTrue(response.getCouponFundedByStore());
        assertEquals(List.of(new VendorOrderOfferResponse(40L, "اشترِ 1 واحصل على 1", true)), response.getOffers());
    }

    @Test
    void vendorView_appPaidCoupon_leavesThePayoutAlone() {
        order.setCouponId(31L);
        order.setDiscountAmount(1_500.0); // an admin coupon: the app pays it
        when(couponRepository.findById(31L)).thenReturn(Optional.empty()); // e.g. deleted since

        VendorOrderResponse response = mapper.toVendorOrderResponse(order, 1L);

        assertFalse(response.getCouponFundedByStore());
        assertEquals(0.0, response.getStoreCouponDiscountAmount());
        assertNull(response.getCouponCode());
        assertTrue(response.getOffers().isEmpty());
        assertEquals(9_000.0, response.getStorePayout());
    }
}
