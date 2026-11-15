package com.advertisingmarketplace.review;

import com.advertisingmarketplace.deal.Deal;
import com.advertisingmarketplace.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false, updatable = false)
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewee_id", nullable = false, updatable = false)
    private User reviewee;

    @Column(name = "rating", nullable = false)
    private short rating;

    @Column(name = "body", columnDefinition = "text")
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Review(Deal deal, User reviewer, User reviewee, int rating, String text, Instant now) {
        this.id = UUID.randomUUID();
        this.deal = deal;
        this.reviewer = reviewer;
        this.reviewee = reviewee;
        this.rating = (short) rating;
        this.text = text;
        this.createdAt = now;
    }
}
