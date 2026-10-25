package com.deliveryapp.dto.report;

import lombok.Data;

/**
 * The platform's money for one order, or summed over a period (admin only).
 *
 * <pre>
 *   markupRevenue        = grossSales − storeCost
 *   platformDiscountCost = customerDiscounts − storeFundedDiscounts
 *   netProfit            = markupRevenue + commissionRevenue − platformDiscountCost
 *                        = (totalCollected − deliveryFees) − totalStorePayouts
 * </pre>
 */
@Data
public class ProfitBreakdownResponse {
    /** Items at the customer price (markup included), before discounts. */
    private double grossSales;
    /** The same items at the stores' own prices, before discounts. */
    private double storeCost;
    /** The platform's price markup: grossSales − storeCost. */
    private double markupRevenue;
    /** Commission taken from the stores. */
    private double commissionRevenue;
    /** Offer and coupon discounts the customers got on items. */
    private double customerDiscounts;
    /** The part of the discounts the stores paid (their own offers and flash sales). */
    private double storeFundedDiscounts;
    /** The part of the discounts the platform paid. */
    private double platformDiscountCost;
    /** markupRevenue + commissionRevenue − platformDiscountCost. */
    private double netProfit;
    /** netProfit as a percentage of grossSales. */
    private double marginPercent;
    /** Delivery fees the customers paid (reported separately, not part of netProfit). */
    private double deliveryFees;
    /** What the stores are paid. */
    private double totalStorePayouts;
    /** What the customers paid: items after discounts + delivery. */
    private double totalCollected;
}
