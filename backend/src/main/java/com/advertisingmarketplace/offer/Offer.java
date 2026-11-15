package com.advertisingmarketplace.offer;

import com.advertisingmarketplace.posting.Posting;
import com.advertisingmarketplace.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "offers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posting_id", nullable = false, updatable = false)
    private Posting posting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offerer_id", nullable = false, updatable = false)
    private User offerer;

    @Setter
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String text;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private OfferStatus status;

    @Setter
    @Column(name = "price_from", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceFrom;

    @Setter
    @Column(name = "price_to", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceTo;

    @Setter
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Offer(Posting posting, User offerer, String text, BigDecimal priceFrom, BigDecimal priceTo,
            String currency) {
        this.posting = posting;
        this.offerer = offerer;
        this.text = text;
        this.priceFrom = priceFrom;
        this.priceTo = priceTo;
        this.currency = currency;
        this.status = OfferStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isMadeBy(UUID userId) {
        return offerer.getId().equals(userId);
    }

    public boolean isVisibleTo(UUID viewerId, boolean viewerIsAdmin) {
        return viewerIsAdmin || isMadeBy(viewerId) || posting.isCreatedBy(viewerId);
    }
}
