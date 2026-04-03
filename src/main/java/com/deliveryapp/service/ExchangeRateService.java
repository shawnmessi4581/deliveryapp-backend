package com.deliveryapp.service;

import com.deliveryapp.entity.ExchangeRate;
import com.deliveryapp.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRate updateRate(Double newRate) {
        ExchangeRate rate = new ExchangeRate();
        rate.setFromCurrency("USD");
        rate.setToCurrency("SYP");
        rate.setRate(newRate);
        rate.setUpdatedAt(LocalDateTime.now());
        rate.setEffectiveDate(LocalDate.now());
        return exchangeRateRepository.save(rate);
    }

    public Double getCurrentRate() {
        return exchangeRateRepository
                .findFirstByFromCurrencyAndToCurrencyOrderByEffectiveDateDescIdDesc("USD", "SYP")
                .map(ExchangeRate::getRate)
                .orElseThrow(() -> new RuntimeException(
                        "Exchange rate not set. Admin must update it before USD prices can be calculated."));
    }
}