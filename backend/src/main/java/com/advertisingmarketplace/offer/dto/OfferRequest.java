package com.advertisingmarketplace.offer.dto;

import com.advertisingmarketplace.common.money.SupportedCurrency;
import com.advertisingmarketplace.common.validation.PriceRanges;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record OfferRequest(
        @NotBlank @Size(max = 5_000) String text,
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal priceFrom,
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal priceTo,
        @SupportedCurrency String currency) {

    @AssertTrue(message = "priceFrom must not be greater than priceTo")
    public boolean isPriceRangeOrdered() {
        return PriceRanges.isOrdered(priceFrom, priceTo);
    }
}
