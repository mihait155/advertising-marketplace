package com.advertisingmarketplace.profile;

import com.advertisingmarketplace.review.dto.ReviewResponse;
import com.advertisingmarketplace.social.dto.SocialAccountResponse;
import com.advertisingmarketplace.user.UserType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String fullName,
        UserType userType,
        Instant memberSince,
        double ratingAverage,
        long ratingCount,
        long completedDeals,
        long verifiedReach,
        List<SocialAccountResponse> accounts,
        List<ReviewResponse> recentReviews) {
}
