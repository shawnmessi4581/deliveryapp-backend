package com.deliveryapp.service;

import com.deliveryapp.dto.report.ProfitBreakdownResponse;
import com.deliveryapp.dto.report.ProfitReportResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.repository.OrderRepository;
import com.deliveryapp.util.MathUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProfitServiceTest {

    @Mock private OrderRepository orderRepository;

    private OrderProfitService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OrderProfitService(orderRepository, new StorePayoutService(), new MathUtil());

        // Restaurant: 10% commission, customer price 11,000 vs store price 10,000, its own "buy 1 get 1"
        Store restaurant = store(7L, "مطعم الشام", 10.0);
        OrderItem shawarma = item(restaurant, 11_000, 10_000, 2);
        shawarma.setOfferDiscountAmount(11_000.0);
        shawarma.setStoreOfferDiscountAmount(10_000.0);
        shawarma.setOfferFundedByStore(true);
        // Supermarket: no commission, 5,500 vs 5,000
        Store market = store(12L, "سوبر ماركت النور", 0.0);
        OrderItem pepsi = item(market, 5_500, 5_000, 1);

        order = new Order();
        order.setStores(List.of(restaurant, market));
        order.setOrderItems(List.of(shawarma, pepsi));
        order.setSubtotal(27_500.0);
        order.setOfferDiscountAmount(11_000.0);
        order.setDiscountAmount(2_000.0); // admin coupon — the platform pays it
        order.setDeliveryFee(5_000.0);
        order.setTotalAmount(19_500.0);  // 27,500 − 11,000 − 2,000 + 5,000
        order.setStatus(OrderStatus.DELIVERED);
        order.setCreatedAt(LocalDateTime.of(2026, 10, 10, 13, 0));
    }

    @Test
    void breakdown_splitsMarkupCommissionAndWhoPaysTheDiscounts() {
        ProfitBreakdownResponse profit = service.breakdown(order);

        assertEquals(27_500.0, profit.getGrossSales());
        assertEquals(25_000.0, profit.getStoreCost());
        assertEquals(2_500.0, profit.getMarkupRevenue());
        assertEquals(1_000.0, profit.getCommissionRevenue());      // 10% of the restaurant's 10,000 after its offer
        assertEquals(13_000.0, profit.getCustomerDiscounts());     // 11,000 offer + 2,000 coupon
        assertEquals(10_000.0, profit.getStoreFundedDiscounts());  // the restaurant's offer at its own price
        assertEquals(3_000.0, profit.getPlatformDiscountCost());   // coupon 2,000 + offer markup 1,000
        assertEquals(500.0, profit.getNetProfit());
        assertEquals(1.82, profit.getMarginPercent());
        assertEquals(14_000.0, profit.getTotalStorePayouts());     // 9,000 + 5,000
    }

    @Test
    void netProfit_isWhatThePlatformKeepsInCash() {
        ProfitBreakdownResponse profit = service.breakdown(order);

        double kept = profit.getTotalCollected() - profit.getDeliveryFees() - profit.getTotalStorePayouts();
        assertEquals(kept, profit.getNetProfit());
    }

    @Test
    void report_sumsDeliveredOrders_perStoreAndPerDay() {
        when(orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(OrderStatus.DELIVERED), any(), any())).thenReturn(List.of(order));

        ProfitReportResponse report = service.report(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals(1, report.getOrderCount());
        assertEquals(500.0, report.getSummary().getNetProfit());
        assertEquals(31, report.getByDay().size());
        assertEquals(500.0, report.getByDay().get(9).getNetProfit());   // October 10
        assertEquals(0, report.getByDay().get(0).getOrderCount());
        assertEquals(List.of(7L, 12L), report.getByStore().stream().map(s -> s.getStoreId()).toList());
        assertEquals(3_000.0, report.getByStore().get(0).getPlatformEarnings()); // 2,000 markup + 1,000 commission
        assertEquals(9_000.0, report.getByStore().get(0).getStorePayout());
    }

    @Test
    void report_startAfterEnd_isRejected() {
        assertThrows(InvalidDataException.class,
                () -> service.report(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1)));
    }

    private static Store store(long id, String name, double commission) {
        Store store = new Store();
        store.setStoreId(id);
        store.setName(name);
        store.setCommissionPercentage(commission);
        return store;
    }

    private static OrderItem item(Store store, double customerPrice, double storePrice, int quantity) {
        Product product = new Product();
        product.setProductId(store.getStoreId() * 100);
        product.setStore(store);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(customerPrice);
        item.setTotalPrice(customerPrice * quantity);
        item.setStoreUnitPrice(storePrice);
        item.setStoreTotalPrice(storePrice * quantity);
        return item;
    }
}
