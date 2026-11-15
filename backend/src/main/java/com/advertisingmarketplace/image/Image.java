package com.advertisingmarketplace.image;

import com.advertisingmarketplace.messaging.Message;
import com.advertisingmarketplace.posting.Posting;
import com.advertisingmarketplace.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id")
    private Posting posting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private Message message;

    @Column(name = "storage_key", nullable = false, unique = true, updatable = false, length = 255)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "orphaned_since")
    private Instant orphanedSince;

    public Image(User owner, String storageKey, String contentType, long sizeBytes, String sha256, Instant now) {
        this.owner = owner;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.orphanedSince = this.createdAt;
    }

    public void attachTo(Posting posting) {
        this.posting = posting;
        this.orphanedSince = null;
    }

    public void attachTo(Message message) {
        this.message = message;
        this.orphanedSince = null;
    }

    public void detach(Instant now) {
        this.posting = null;
        this.message = null;
        this.orphanedSince = now;
    }

    public boolean isAttached() {
        return posting != null || message != null;
    }

    public boolean isOwnedBy(UUID userId) {
        return owner != null && owner.getId().equals(userId);
    }
}
