package com.deliveryapp.dto.report;

import lombok.Data;

import java.time.LocalDate;

/** One day of the profit report (every day in the period, zeros included, for charts). */
@Data
public class DailyProfitResponse {
    private LocalDate date;
    private int orderCount;
    private double grossSales;
    private double markupRevenue;
    private double commissionRevenue;
    private double platformDiscountCost;
    private double netProfit;
}
