package com.advertisingmarketplace.common.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ExchangeRates(LocalDate date, Map<String, BigDecimal> unitsPerEur) {
}
