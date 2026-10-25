package com.deliveryapp.dto.report;

import lombok.Data;

/** One store's share of the profit report. */
@Data
public class StoreProfitResponse {
    private Long storeId;
    private String storeName;
    private int orderCount;
    /** The store's items at the customer price, before discounts. */
    private double grossSales;
    /** The store's items at its own prices, before discounts. */
    private double storeCost;
    private double markupRevenue;
    private double commissionRevenue;
    /** Discounts this store paid (its own offers and flash sales). */
    private double storeFundedDiscounts;
    /** What this store was paid. */
    private double storePayout;
    /** markupRevenue + commissionRevenue — what the platform earned from this store before the discounts it paid. */
    private double platformEarnings;
}
