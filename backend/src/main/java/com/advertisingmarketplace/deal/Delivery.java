package com.advertisingmarketplace.deal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false, updatable = false)
    private Deal deal;

    @Column(name = "links", nullable = false, columnDefinition = "text")
    private String links;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "feedback", columnDefinition = "text")
    private String feedback;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public Delivery(Deal deal, List<String> links, String note, Instant now) {
        this.id = UUID.randomUUID();
        this.deal = deal;
        this.links = String.join("\n", links);
        this.note = note;
        this.status = DeliveryStatus.PENDING;
        this.submittedAt = now;
    }

    public List<String> linkList() {
        return Arrays.stream(links.split("\n")).filter(link -> !link.isBlank()).toList();
    }

    void approve(Instant now) {
        this.status = DeliveryStatus.APPROVED;
        this.reviewedAt = now;
    }

    void requestChanges(String feedback, Instant now) {
        this.status = DeliveryStatus.CHANGES_REQUESTED;
        this.feedback = feedback;
        this.reviewedAt = now;
    }
}
