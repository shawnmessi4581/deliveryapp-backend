package com.deliveryapp.service;

import com.deliveryapp.dto.order.StorePayoutResponse;
import com.deliveryapp.dto.report.DailyProfitResponse;
import com.deliveryapp.dto.report.ProfitBreakdownResponse;
import com.deliveryapp.dto.report.ProfitReportResponse;
import com.deliveryapp.dto.report.StoreProfitResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.repository.OrderRepository;
import com.deliveryapp.util.MathUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform profit (admin only). The price markup is platform revenue, commission is taken from the
 * stores, and the platform pays the discounts the stores don't. Store figures come from
 * {@link StorePayoutService}, so profit always matches what the driver hands the stores.
 *
 * <p>The report counts DELIVERED orders by the date they were placed — the same dates the admin
 * order list filters on.
 */
@Service
@RequiredArgsConstructor
public class OrderProfitService {

    private static final int MAX_REPORT_DAYS = 366;

    private final OrderRepository orderRepository;
    private final StorePayoutService storePayoutService;
    private final MathUtil mathUtil;

    /** Profit of one order. */
    public ProfitBreakdownResponse breakdown(Order order) {
        return breakdown(order, payoutsOf(order));
    }

    /** Profit of one order whose store payouts are already calculated. */
    public ProfitBreakdownResponse breakdown(Order order, List<StorePayoutResponse> payouts) {
        List<OrderItem> items = order.getOrderItems() != null ? order.getOrderItems() : List.of();
        double grossSales = items.stream().mapToDouble(item -> nz(item.getTotalPrice())).sum();
        double storeCost = items.stream().mapToDouble(OrderItem::getEffectiveStoreTotalPrice).sum();
        double deliveryFees = nz(order.getDeliveryFee());
        double totalCollected = nz(order.getTotalAmount());

        ProfitBreakdownResponse profit = new ProfitBreakdownResponse();
        profit.setGrossSales(grossSales);
        profit.setStoreCost(storeCost);
        profit.setDeliveryFees(deliveryFees);
        profit.setTotalCollected(totalCollected);
        // What the items brought in after discounts, against what they cost before them
        profit.setCustomerDiscounts(Math.max(grossSales - (totalCollected - deliveryFees), 0.0));
        profit.setCommissionRevenue(payouts.stream().mapToDouble(p -> nz(p.getCommissionAmount())).sum());
        profit.setStoreFundedDiscounts(payouts.stream().mapToDouble(p -> nz(p.getStoreDiscountAmount())).sum());
        profit.setTotalStorePayouts(payouts.stream().mapToDouble(p -> nz(p.getStorePayout())).sum());
        return finish(profit);
    }

    /** Profit of the DELIVERED orders placed in the period (default: this month so far). */
    @Transactional(readOnly = true)
    public ProfitReportResponse report(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new InvalidDataException("تاريخ البداية يجب أن يكون قبل تاريخ النهاية");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_REPORT_DAYS) {
            throw new InvalidDataException("أقصى مدة للتقرير سنة واحدة");
        }

        List<Order> orders = orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
                OrderStatus.DELIVERED, start.atStartOfDay(), end.atTime(LocalTime.MAX));

        ProfitBreakdownResponse summary = new ProfitBreakdownResponse();
        Map<Long, StoreProfitResponse> byStore = new LinkedHashMap<>();
        Map<LocalDate, DailyProfitResponse> byDay = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            DailyProfitResponse daily = new DailyProfitResponse();
            daily.setDate(day);
            byDay.put(day, daily);
        }

        for (Order order : orders) {
            List<StorePayoutResponse> payouts = payoutsOf(order);
            ProfitBreakdownResponse profit = breakdown(order, payouts);
            add(summary, profit);
            addDay(byDay.get(order.getCreatedAt().toLocalDate()), profit);
            payouts.forEach(payout -> addStore(byStore, payout));
        }

        ProfitReportResponse report = new ProfitReportResponse();
        report.setStartDate(start);
        report.setEndDate(end);
        report.setOrderCount(orders.size());
        report.setSummary(finish(summary));
        report.setByStore(byStore.values().stream()
                .sorted(Comparator.comparingDouble(StoreProfitResponse::getPlatformEarnings).reversed())
                .toList());
        report.setByDay(new ArrayList<>(byDay.values()));
        return report;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<StorePayoutResponse> payoutsOf(Order order) {
        return order.getStores() == null ? List.of() : order.getStores().stream()
                .map(store -> storePayoutService.calculate(order, store))
                .toList();
    }

    /** Fills in the derived figures once the raw ones are set (or summed). */
    private ProfitBreakdownResponse finish(ProfitBreakdownResponse p) {
        p.setMarkupRevenue(mathUtil.roundMoney(p.getGrossSales() - p.getStoreCost()));
        p.setPlatformDiscountCost(mathUtil.roundMoney(p.getCustomerDiscounts() - p.getStoreFundedDiscounts()));
        p.setNetProfit(mathUtil.roundMoney(
                p.getMarkupRevenue() + p.getCommissionRevenue() - p.getPlatformDiscountCost()));
        p.setMarginPercent(p.getGrossSales() > 0
                ? Math.round(p.getNetProfit() / p.getGrossSales() * 10_000) / 100.0
                : 0.0);
        return p;
    }

    private void add(ProfitBreakdownResponse total, ProfitBreakdownResponse p) {
        total.setGrossSales(total.getGrossSales() + p.getGrossSales());
        total.setStoreCost(total.getStoreCost() + p.getStoreCost());
        total.setCommissionRevenue(total.getCommissionRevenue() + p.getCommissionRevenue());
        total.setCustomerDiscounts(total.getCustomerDiscounts() + p.getCustomerDiscounts());
        total.setStoreFundedDiscounts(total.getStoreFundedDiscounts() + p.getStoreFundedDiscounts());
        total.setDeliveryFees(total.getDeliveryFees() + p.getDeliveryFees());
        total.setTotalStorePayouts(total.getTotalStorePayouts() + p.getTotalStorePayouts());
        total.setTotalCollected(total.getTotalCollected() + p.getTotalCollected());
    }

    private void addDay(DailyProfitResponse day, ProfitBreakdownResponse p) {
        day.setOrderCount(day.getOrderCount() + 1);
        day.setGrossSales(day.getGrossSales() + p.getGrossSales());
        day.setMarkupRevenue(day.getMarkupRevenue() + p.getMarkupRevenue());
        day.setCommissionRevenue(day.getCommissionRevenue() + p.getCommissionRevenue());
        day.setPlatformDiscountCost(day.getPlatformDiscountCost() + p.getPlatformDiscountCost());
        day.setNetProfit(day.getNetProfit() + p.getNetProfit());
    }

    private void addStore(Map<Long, StoreProfitResponse> byStore, StorePayoutResponse payout) {
        StoreProfitResponse store = byStore.computeIfAbsent(payout.getStoreId(), id -> {
            StoreProfitResponse s = new StoreProfitResponse();
            s.setStoreId(id);
            s.setStoreName(payout.getStoreName());
            return s;
        });
        double markup = nz(payout.getCustomerItemsSubtotal()) - nz(payout.getItemsSubtotal());
        store.setOrderCount(store.getOrderCount() + 1);
        store.setGrossSales(store.getGrossSales() + nz(payout.getCustomerItemsSubtotal()));
        store.setStoreCost(store.getStoreCost() + nz(payout.getItemsSubtotal()));
        store.setMarkupRevenue(store.getMarkupRevenue() + markup);
        store.setCommissionRevenue(store.getCommissionRevenue() + nz(payout.getCommissionAmount()));
        store.setStoreFundedDiscounts(store.getStoreFundedDiscounts() + nz(payout.getStoreDiscountAmount()));
        store.setStorePayout(store.getStorePayout() + nz(payout.getStorePayout()));
        store.setPlatformEarnings(store.getMarkupRevenue() + store.getCommissionRevenue());
    }

    private static double nz(Double value) {
        return value != null ? value : 0.0;
    }
}
