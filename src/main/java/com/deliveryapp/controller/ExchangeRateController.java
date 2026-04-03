package com.deliveryapp.controller;

import com.deliveryapp.entity.ExchangeRate;
import com.deliveryapp.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/exchange-rate")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN','EMPLOYEE')")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @PostMapping
    public ResponseEntity<ExchangeRate> updateRate(@RequestParam Double rate) {
        return ResponseEntity.ok(exchangeRateService.updateRate(rate));
    }

    @GetMapping
    public ResponseEntity<Double> getCurrentRate() {
        return ResponseEntity.ok(exchangeRateService.getCurrentRate());
    }
}