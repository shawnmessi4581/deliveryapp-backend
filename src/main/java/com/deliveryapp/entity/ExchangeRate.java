package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates", indexes = {
        @Index(name = "idx_exchange_rate_lookup", columnList = "from_currency, to_currency, effective_date DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_currency", nullable = false, length = 10)
    private String fromCurrency; // Always "USD"

    @Column(name = "to_currency", nullable = false, length = 10)
    private String toCurrency; // Always "SYP"

    @Column(nullable = false)
    private Double rate; // e.g. 13500.0

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
}