package com.advertisingmarketplace.offer.dto;

import com.advertisingmarketplace.offer.OfferStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OfferStatusRequest(
        @NotNull OfferStatus status,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal amount) {
}
