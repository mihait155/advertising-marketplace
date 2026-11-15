package com.advertisingmarketplace.posting;

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
@Table(name = "postings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private PostingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PostingStatus status;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "pay_from", precision = 12, scale = 2)
    private BigDecimal payFrom;

    @Column(name = "pay_to", precision = 12, scale = 2)
    private BigDecimal payTo;

    @Column(name = "pay_currency", length = 3)
    private String payCurrency;

    @Column(name = "pay_from_eur", precision = 14, scale = 2)
    private BigDecimal payFromEur;

    @Column(name = "pay_to_eur", precision = 14, scale = 2)
    private BigDecimal payToEur;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Setter(AccessLevel.NONE)
    private long version;

    public Posting(User creator, PostingCategory category, PostingStatus status, String title, String text,
            BigDecimal payFrom, BigDecimal payTo, String payCurrency) {
        this.creator = creator;
        this.category = category;
        this.status = status;
        this.title = title;
        this.text = text;
        this.payFrom = payFrom;
        this.payTo = payTo;
        this.payCurrency = payCurrency;
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

    public boolean isCreatedBy(UUID userId) {
        return creator.getId().equals(userId);
    }

    public boolean isVisibleTo(UUID viewerId, boolean viewerIsAdmin) {
        return status != PostingStatus.DRAFT || viewerIsAdmin || isCreatedBy(viewerId);
    }
}
