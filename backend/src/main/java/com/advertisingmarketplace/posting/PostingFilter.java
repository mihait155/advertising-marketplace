package com.advertisingmarketplace.posting;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingFilter(PostingCategory category, PostingStatus status, UUID creatorId, BigDecimal minPay,
        BigDecimal maxPay, String currency) {

    public static PostingFilter none() {
        return new PostingFilter(null, null, null, null, null, null);
    }
}
