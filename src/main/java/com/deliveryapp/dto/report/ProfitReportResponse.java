package com.deliveryapp.dto.report;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** Platform profit for delivered orders placed between startDate and endDate (both included). */
@Data
public class ProfitReportResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private int orderCount;
    private ProfitBreakdownResponse summary;
    /** Highest platformEarnings first. */
    private List<StoreProfitResponse> byStore;
    private List<DailyProfitResponse> byDay;
}
