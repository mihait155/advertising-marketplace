package com.advertisingmarketplace.common.money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Currencies {

    public static String normalize(String code) {
        return code == null || code.isBlank() ? null : code.strip().toUpperCase(Locale.ROOT);
    }

    public static void requireAmountsFit(String currencyCode, BigDecimal... amounts) {
        if (currencyCode == null) {
            return;
        }
        int fractionDigits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        for (BigDecimal amount : amounts) {
            if (amount != null && amount.stripTrailingZeros().scale() > Math.max(fractionDigits, 0)) {
                throw new AmountPrecisionException(currencyCode, fractionDigits);
            }
        }
    }
}
