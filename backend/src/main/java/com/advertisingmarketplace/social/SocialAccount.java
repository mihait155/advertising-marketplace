package com.advertisingmarketplace.social;

import com.advertisingmarketplace.user.User;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20, updatable = false)
    private SocialProvider provider;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "handle", nullable = false, length = 200)
    private String handle;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "profile_url", nullable = false, length = 500)
    private String profileUrl;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "followers", nullable = false)
    private long followers;

    @Column(name = "total_views")
    private Long totalViews;

    @Column(name = "content_count")
    private Long contentCount;

    @Column(name = "encrypted_refresh_token", columnDefinition = "text")
    private String encryptedRefreshToken;

    @Column(name = "needs_reconnect", nullable = false)
    private boolean needsReconnect;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    public SocialAccount(User user, SocialProvider provider, ChannelSnapshot snapshot, String encryptedRefreshToken,
            Instant now) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.connectedAt = now;
        update(snapshot, encryptedRefreshToken, now);
    }

    public void update(ChannelSnapshot snapshot, String encryptedRefreshToken, Instant now) {
        this.externalId = snapshot.externalId();
        this.handle = snapshot.handle();
        this.displayName = snapshot.displayName();
        this.profileUrl = snapshot.profileUrl();
        this.avatarUrl = snapshot.avatarUrl();
        this.followers = snapshot.followers();
        this.totalViews = snapshot.totalViews();
        this.contentCount = snapshot.contentCount();
        if (encryptedRefreshToken != null) {
            this.encryptedRefreshToken = encryptedRefreshToken;
        }
        this.needsReconnect = false;
        this.lastSyncedAt = now;
    }

    public void reconnect(Instant now) {
        this.connectedAt = now;
    }

    public void markNeedsReconnect() {
        this.needsReconnect = true;
    }
}
