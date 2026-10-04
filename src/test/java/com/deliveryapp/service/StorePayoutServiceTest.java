package com.deliveryapp.service;

import com.deliveryapp.dto.order.StorePayoutResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorePayoutServiceTest {

    private final StorePayoutService service = new StorePayoutService();

    @Test
    void vendorOffer_isDeductedBeforeCommission() {
        Store store = store(1L, 10.0);
        // Buy 1 Get 1 on 10,000: customer paid 10,000, the store's own offer gave the second unit
        OrderItem item = item(store, 20_000, 10_000, true);

        StorePayoutResponse payout = service.calculate(order(item), store);

        assertEquals(20_000.0, payout.getItemsSubtotal());
        assertEquals(10_000.0, payout.getStoreDiscountAmount());
        assertEquals(1_000.0, payout.getCommissionAmount());
        assertEquals(9_000.0, payout.getStorePayout()); // old: 18,000 for a 10,000 sale
    }

    @Test
    void adminOffer_doesNotReduceThePayout() {
        Store store = store(1L, 10.0);
        OrderItem item = item(store, 20_000, 10_000, false);

        StorePayoutResponse payout = service.calculate(order(item), store);

        assertEquals(0.0, payout.getStoreDiscountAmount());
        assertEquals(18_000.0, payout.getStorePayout());
    }

    @Test
    void vendorFlashSaleCoupon_isChargedToThatStoreOnly() {
        Store storeA = store(1L, 0.0);
        Store storeB = store(2L, 0.0);
        Order order = order(item(storeA, 10_000, 0, false), item(storeB, 90_000, 0, false));
        order.setDiscountAmount(2_000.0);
        order.setCouponFundedByStoreId(storeA.getStoreId());

        assertEquals(8_000.0, service.calculate(order, storeA).getStorePayout());
        assertEquals(90_000.0, service.calculate(order, storeB).getStorePayout());
    }

    @Test
    void markup_isPlatformProfit_storeIsPaidOnItsOwnPrices() {
        Store store = store(1L, 10.0);
        // Customer paid 2 × 11,000; store price is 2 × 10,000
        OrderItem item = item(store, 22_000, 0, false);
        item.setStoreTotalPrice(20_000.0);

        StorePayoutResponse payout = service.calculate(order(item), store);

        assertEquals(20_000.0, payout.getItemsSubtotal());
        assertEquals(22_000.0, payout.getCustomerItemsSubtotal());
        assertEquals(2_000.0, payout.getCommissionAmount()); // 10% of the store price, not of 22,000
        assertEquals(18_000.0, payout.getStorePayout());
    }

    @Test
    void storePaidDiscounts_areChargedAtTheStoresOwnPrice() {
        Store store = store(1L, 0.0);
        // Vendor's Buy 1 Get 1: customer saved 11,000 (marked-up unit), the store pays its 10,000
        OrderItem item = item(store, 22_000, 11_000, true);
        item.setStoreTotalPrice(20_000.0);
        item.setStoreOfferDiscountAmount(10_000.0);
        Order order = order(item);
        // Vendor flash sale: customer saved 2,200, store's share at its prices is 2,000
        order.setCouponFundedByStoreId(store.getStoreId());
        order.setDiscountAmount(2_200.0);
        order.setCouponStoreDiscountAmount(2_000.0);

        StorePayoutResponse payout = service.calculate(order, store);

        assertEquals(12_000.0, payout.getStoreDiscountAmount());
        assertEquals(8_000.0, payout.getStorePayout());
    }

    @Test
    void payout_roundsUpToWholePoundsWithoutFloatingPointNoise() {
        Store store = store(1L, 7.5);

        StorePayoutResponse payout = service.calculate(order(item(store, 10_333, 0, false)), store);

        assertEquals(9_559.0, payout.getStorePayout()); // 9,558.025 → 9,559
        assertEquals(774.0, payout.getCommissionAmount());
    }

    private Store store(long id, double commission) {
        Store store = new Store();
        store.setStoreId(id);
        store.setName("Store " + id);
        store.setCommissionPercentage(commission);
        return store;
    }

    private OrderItem item(Store store, double total, double offerDiscount, boolean fundedByStore) {
        Product product = new Product();
        product.setStore(store);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setTotalPrice(total);
        item.setOfferDiscountAmount(offerDiscount);
        item.setOfferFundedByStore(fundedByStore);
        return item;
    }

    private Order order(OrderItem... items) {
        Order order = new Order();
        order.setOrderItems(List.of(items));
        return order;
    }
}
