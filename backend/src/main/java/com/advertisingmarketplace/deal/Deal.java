package com.advertisingmarketplace.deal;

import com.advertisingmarketplace.offer.Offer;
import com.advertisingmarketplace.posting.Posting;
import com.advertisingmarketplace.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "deals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deal {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id")
    private Posting posting;

    @Column(name = "posting_title", nullable = false, length = 200)
    private String postingTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marketer_id")
    private User marketer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private DealStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "auto_complete_at")
    private Instant autoCompleteAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancel_reason", length = 1000)
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Deal(Offer offer, User marketer, User customer, BigDecimal amount, Instant now) {
        this.id = UUID.randomUUID();
        this.offer = offer;
        this.posting = offer.getPosting();
        this.postingTitle = offer.getPosting().getTitle();
        this.marketer = marketer;
        this.customer = customer;
        this.amount = amount;
        this.currency = offer.getCurrency();
        this.status = DealStatus.IN_PROGRESS;
        this.createdAt = now;
    }

    public Optional<DealRole> roleOf(UUID userId) {
        if (userId != null && marketer != null && marketer.getId().equals(userId)) {
            return Optional.of(DealRole.MARKETER);
        }
        if (userId != null && customer != null && customer.getId().equals(userId)) {
            return Optional.of(DealRole.CUSTOMER);
        }
        return Optional.empty();
    }

    public boolean isParticipant(UUID userId) {
        return roleOf(userId).isPresent();
    }

    public User counterpartOf(UUID userId) {
        return roleOf(userId).map(role -> role == DealRole.MARKETER ? customer : marketer).orElse(null);
    }

    void markDelivered(Instant now, Instant autoCompleteAt) {
        this.status = DealStatus.DELIVERED;
        this.deliveredAt = now;
        this.autoCompleteAt = autoCompleteAt;
    }

    void markInProgress() {
        this.status = DealStatus.IN_PROGRESS;
        this.autoCompleteAt = null;
    }

    void markCompleted(Instant now) {
        this.status = DealStatus.COMPLETED;
        this.completedAt = now;
        this.autoCompleteAt = null;
    }

    void markCancelled(User actor, String reason, Instant now) {
        this.status = DealStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancelledBy = actor;
        this.cancelReason = reason;
        this.autoCompleteAt = null;
    }
}
