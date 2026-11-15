package com.advertisingmarketplace.posting;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingSearch(PostingCategory category, PostingStatus status, UUID creatorId, BigDecimal minPayEur,
        BigDecimal maxPayEur) {
}
