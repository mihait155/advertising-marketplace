package com.advertisingmarketplace.deal.dto;

import com.advertisingmarketplace.deal.DealRole;
import com.advertisingmarketplace.deal.DealStatus;
import com.advertisingmarketplace.review.dto.ReviewResponse;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DealResponse(
        UUID id,
        UUID offerId,
        UUID postingId,
        String postingTitle,
        UserSummary marketer,
        UserSummary customer,
        DealRole myRole,
        BigDecimal amount,
        String currency,
        DealStatus status,
        Instant createdAt,
        Instant deliveredAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant autoCompleteAt,
        UserSummary cancelledBy,
        String cancelReason,
        UUID conversationId,
        List<DeliveryResponse> deliveries,
        ReviewState reviews) {

    public record ReviewState(boolean canReview, Instant reviewDeadline, ReviewResponse mine, boolean theirsSubmitted,
            ReviewResponse theirs, Instant revealAt) {
    }
}
