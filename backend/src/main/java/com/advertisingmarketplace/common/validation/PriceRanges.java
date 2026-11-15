package com.advertisingmarketplace.common.validation;

import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PriceRanges {

    public static boolean isOrdered(BigDecimal from, BigDecimal to) {
        return from == null || to == null || from.compareTo(to) <= 0;
    }
}
