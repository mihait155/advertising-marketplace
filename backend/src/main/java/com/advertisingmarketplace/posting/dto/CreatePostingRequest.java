package com.advertisingmarketplace.posting.dto;

import com.advertisingmarketplace.common.money.SupportedCurrency;
import com.advertisingmarketplace.common.validation.PriceRanges;
import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.posting.PostingStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePostingRequest(
        @NotNull PostingCategory category,
        PostingStatus status,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 20_000) String text,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal payFrom,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal payTo,
        @SupportedCurrency String payCurrency) {

    @AssertTrue(message = "payFrom must not be greater than payTo")
    public boolean isPayRangeOrdered() {
        return PriceRanges.isOrdered(payFrom, payTo);
    }

    @AssertTrue(message = "payCurrency is required when payFrom or payTo is set, and only allowed then")
    public boolean isPayCurrencyConsistent() {
        boolean hasAmount = payFrom != null || payTo != null;
        boolean hasCurrency = payCurrency != null && !payCurrency.isBlank();
        return hasAmount == hasCurrency;
    }

    @AssertTrue(message = "status must be DRAFT or OPEN")
    public boolean isInitialStatusAllowed() {
        return status != PostingStatus.CLOSED;
    }

    public PostingStatus initialStatus() {
        return status == null ? PostingStatus.DRAFT : status;
    }
}
