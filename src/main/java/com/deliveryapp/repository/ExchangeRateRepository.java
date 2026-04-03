package com.deliveryapp.repository;

import com.deliveryapp.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate> findFirstByFromCurrencyAndToCurrencyOrderByEffectiveDateDescIdDesc(
            String fromCurrency, String toCurrency);
}