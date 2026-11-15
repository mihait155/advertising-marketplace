package com.advertisingmarketplace.common.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;

public record CurrencyResponse(String code, String name, String symbol, int fractionDigits, BigDecimal unitsPerEur,
        LocalDate rateDate) {

    public static CurrencyResponse of(String code) {
        return of(code, null);
    }

    public static CurrencyResponse of(String code, ExchangeRate rate) {
        Currency currency = Currency.getInstance(code);
        return new CurrencyResponse(currency.getCurrencyCode(), currency.getDisplayName(Locale.ENGLISH),
                currency.getSymbol(Locale.ENGLISH), currency.getDefaultFractionDigits(),
                rate == null ? null : rate.getUnitsPerEur().stripTrailingZeros(),
                rate == null ? null : rate.getRateDate());
    }
}
