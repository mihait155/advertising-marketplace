package com.advertisingmarketplace.offer.dto;

import com.advertisingmarketplace.offer.Offer;
import com.advertisingmarketplace.offer.OfferStatus;
import com.advertisingmarketplace.posting.Posting;
import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferResponse(
        UUID id,
        PostingSummary posting,
        UserSummary offerer,
        String text,
        OfferStatus status,
        BigDecimal priceFrom,
        BigDecimal priceTo,
        String currency,
        UUID dealId,
        Instant createdAt,
        Instant updatedAt) {

    public static OfferResponse from(Offer offer) {
        return from(offer, null);
    }

    public static OfferResponse from(Offer offer, UUID dealId) {
        return new OfferResponse(
                offer.getId(),
                PostingSummary.from(offer.getPosting()),
                UserSummary.from(offer.getOfferer()),
                offer.getText(),
                offer.getStatus(),
                offer.getPriceFrom(),
                offer.getPriceTo(),
                offer.getCurrency(),
                dealId,
                offer.getCreatedAt(),
                offer.getUpdatedAt());
    }

    public record PostingSummary(UUID id, String title, PostingCategory category, PostingStatus status,
            String payCurrency, UserSummary creator) {

        static PostingSummary from(Posting posting) {
            return new PostingSummary(posting.getId(), posting.getTitle(), posting.getCategory(),
                    posting.getStatus(), posting.getPayCurrency(), UserSummary.from(posting.getCreator()));
        }
    }
}
