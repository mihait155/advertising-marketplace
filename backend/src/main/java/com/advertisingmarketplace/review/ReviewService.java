package com.advertisingmarketplace.review;

import com.advertisingmarketplace.common.exception.ApiException;
import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.deal.Deal;
import com.advertisingmarketplace.deal.DealExceptions.DealNotFoundException;
import com.advertisingmarketplace.deal.DealProperties;
import com.advertisingmarketplace.deal.DealRepository;
import com.advertisingmarketplace.deal.DealStatus;
import com.advertisingmarketplace.deal.dto.DealResponse.ReviewState;
import com.advertisingmarketplace.review.dto.ReviewRequest;
import com.advertisingmarketplace.review.dto.ReviewResponse;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final DealRepository dealRepository;
    private final UserRepository userRepository;
    private final DealProperties properties;
    private final Clock clock;
    private final MarketplaceMetrics metrics;

    public record RatingSummary(double average, long count) {
    }

    @Transactional
    public ReviewResponse review(UUID dealId, UUID reviewerId, ReviewRequest request) {
        Deal deal = dealRepository.findDetailedById(dealId)
                .filter(d -> d.isParticipant(reviewerId))
                .orElseThrow(() -> new DealNotFoundException(dealId));
        if (deal.getStatus() != DealStatus.COMPLETED) {
            throw new ReviewNotAllowedException("You can review once the deal is completed");
        }
        if (now().isAfter(deal.getCompletedAt().plus(properties.reviewWindow()))) {
            throw new ReviewNotAllowedException("The review window for this deal has closed");
        }
        User reviewee = deal.counterpartOf(reviewerId);
        if (reviewee == null) {
            throw new ReviewNotAllowedException("The other party deleted their account");
        }
        if (reviewRepository.existsByDealIdAndRevieweeId(dealId, reviewee.getId())) {
            throw new AlreadyReviewedException();
        }
        String text = request.text() == null || request.text().isBlank() ? null : request.text().strip();
        Review review = reviewRepository.save(new Review(deal, userRepository.getReferenceById(reviewerId), reviewee,
                request.rating(), text, now()));
        metrics.reviewSubmitted(request.rating());
        return ReviewResponse.from(review);
    }

    public ReviewState stateFor(Deal deal, UUID viewerId) {
        if (!deal.isParticipant(viewerId)) {
            return null;
        }
        List<Review> reviews = reviewRepository.findAllByDealId(deal.getId());
        Review mine = reviews.stream().filter(r -> r.getReviewer() != null && r.getReviewer().getId().equals(viewerId))
                .findFirst().orElse(null);
        Review theirs = reviews.stream().filter(r -> r.getReviewee().getId().equals(viewerId)).findFirst().orElse(null);
        Instant deadline = deal.getCompletedAt() == null ? null : deal.getCompletedAt().plus(properties.reviewWindow());
        Instant revealAt = deal.getCompletedAt() == null ? null
                : deal.getCompletedAt().plus(properties.blindReviewPeriod());
        boolean revealed = theirs != null && (mine != null || !now().isBefore(revealAt));
        boolean canReview = deal.getStatus() == DealStatus.COMPLETED && mine == null && deadline != null
                && now().isBefore(deadline) && deal.counterpartOf(viewerId) != null;
        return new ReviewState(canReview, deadline, mine == null ? null : ReviewResponse.from(mine), theirs != null,
                revealed ? ReviewResponse.from(theirs) : null, revealAt);
    }

    public Page<ReviewResponse> visibleFor(UUID userId, Pageable pageable) {
        return reviewRepository.findVisibleFor(userId, revealCutoff(), pageable).map(ReviewResponse::from);
    }

    public RatingSummary summaryFor(UUID userId) {
        Object[] row = reviewRepository.ratingSummary(userId, revealCutoff()).getFirst();
        double average = ((Number) row[0]).doubleValue();
        return new RatingSummary(Math.round(average * 10) / 10.0, ((Number) row[1]).longValue());
    }

    private Instant revealCutoff() {
        return now().minus(properties.blindReviewPeriod());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public static class ReviewNotAllowedException extends ApiException {

        public ReviewNotAllowedException(String message) {
            super(HttpStatus.CONFLICT, message);
        }
    }

    public static class AlreadyReviewedException extends ApiException {

        public AlreadyReviewedException() {
            super(HttpStatus.CONFLICT, "You already reviewed this deal");
        }
    }
}
