package com.deliveryapp.controller.admin;

import com.deliveryapp.dto.report.ProfitReportResponse;
import com.deliveryapp.service.OrderProfitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin – Reports", description = "Platform profit reports")
public class AdminReportController {

    private final OrderProfitService orderProfitService;

    /**
     * GET /api/admin/reports/profit?startDate=2026-10-01&endDate=2026-10-31
     */
    @GetMapping("/profit")
    @Operation(summary = "Platform profit for delivered orders",
            description = "Totals, per store and per day. Dates are when orders were placed (both included); "
                    + "default is this month so far, at most one year.")
    public ResponseEntity<ProfitReportResponse> getProfitReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(orderProfitService.report(startDate, endDate));
    }
}
