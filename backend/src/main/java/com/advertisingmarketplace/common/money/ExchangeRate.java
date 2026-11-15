package com.advertisingmarketplace.common.money;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exchange_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate {

    @Id
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "units_per_eur", nullable = false, precision = 19, scale = 8)
    private BigDecimal unitsPerEur;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public ExchangeRate(String currency, BigDecimal unitsPerEur, LocalDate rateDate, Instant fetchedAt) {
        this.currency = currency;
        update(unitsPerEur, rateDate, fetchedAt);
    }

    public void update(BigDecimal unitsPerEur, LocalDate rateDate, Instant fetchedAt) {
        this.unitsPerEur = unitsPerEur;
        this.rateDate = rateDate;
        this.fetchedAt = fetchedAt;
    }
}
