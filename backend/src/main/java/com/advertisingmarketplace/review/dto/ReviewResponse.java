package com.advertisingmarketplace.review.dto;

import com.advertisingmarketplace.review.Review;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID dealId, String postingTitle, UserSummary reviewer, int rating, String text,
        Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getDeal().getId(), review.getDeal().getPostingTitle(),
                UserSummary.fromNullable(review.getReviewer()), review.getRating(), review.getText(),
                review.getCreatedAt());
    }
}
