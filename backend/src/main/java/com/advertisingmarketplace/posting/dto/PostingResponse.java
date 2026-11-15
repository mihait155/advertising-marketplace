package com.advertisingmarketplace.posting.dto;

import com.advertisingmarketplace.image.dto.ImageResponse;
import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostingResponse(
        UUID id,
        PostingCategory category,
        PostingStatus status,
        String title,
        String text,
        BigDecimal payFrom,
        BigDecimal payTo,
        String payCurrency,
        UserSummary creator,
        List<ImageResponse> images,
        Instant createdAt,
        Instant updatedAt) {
}
