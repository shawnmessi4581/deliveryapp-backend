package com.deliveryapp.service;

import com.deliveryapp.dto.order.StorePayoutResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Store;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * What the driver hands each store for an order — the single place this is calculated
 * (Telegram message, vendor app, admin/driver apps).
 *
 * <p>Everything here is at the STORE's own prices: the admin's price markup is platform profit and
 * never reaches the store. Discounts created by the store itself (its own Buy-X-Get-Y offers, its own
 * flash-sale coupon) are deducted at its own prices and commission is taken on what remains. Admin
 * offers, admin coupons and store free-delivery rules are paid by the platform.
 */
@Service
public class StorePayoutService {

    public StorePayoutResponse calculate(Order order, Store store) {
        List<OrderItem> storeItems = order.getOrderItems() == null ? List.of() : order.getOrderItems().stream()
                .filter(item -> item.getProduct() != null && item.getProduct().getStore() != null
                        && store.getStoreId().equals(item.getProduct().getStore().getStoreId()))
                .toList();

        double itemsSubtotal = storeItems.stream().mapToDouble(OrderItem::getEffectiveStoreTotalPrice).sum();
        double customerItemsSubtotal = storeItems.stream()
                .mapToDouble(item -> item.getTotalPrice() != null ? item.getTotalPrice() : 0.0)
                .sum();

        double storeDiscount = storeItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getOfferFundedByStore()))
                .mapToDouble(OrderItem::getEffectiveStoreOfferDiscount)
                .sum();
        if (store.getStoreId().equals(order.getCouponFundedByStoreId())) {
            Double couponShare = order.getCouponStoreDiscountAmount() != null
                    ? order.getCouponStoreDiscountAmount()
                    : order.getDiscountAmount();
            storeDiscount += couponShare != null ? couponShare : 0.0;
        }

        double commissionRate = store.getCommissionPercentage() != null ? store.getCommissionPercentage() : 0.0;
        BigDecimal net = BigDecimal.valueOf(Math.max(itemsSubtotal - storeDiscount, 0.0));
        // Round up for SYP, computed exactly (no 9000.0000001 → 9001 floating-point surprises)
        BigDecimal payout = net.multiply(BigDecimal.valueOf(100 - commissionRate))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING);

        StorePayoutResponse response = new StorePayoutResponse();
        response.setStoreId(store.getStoreId());
        response.setStoreName(store.getName());
        response.setItemsSubtotal(itemsSubtotal);
        response.setCustomerItemsSubtotal(customerItemsSubtotal);
        response.setStoreDiscountAmount(storeDiscount);
        response.setCommissionPercentage(commissionRate);
        response.setCommissionAmount(net.subtract(payout).doubleValue());
        response.setStorePayout(payout.doubleValue());
        return response;
    }
}
