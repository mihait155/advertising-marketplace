package com.advertisingmarketplace.social.dto;

import com.advertisingmarketplace.social.SocialAccount;
import com.advertisingmarketplace.social.SocialProvider;
import java.time.Instant;
import java.util.UUID;

public record SocialAccountResponse(UUID id, SocialProvider provider, String handle, String displayName,
        String profileUrl, String avatarUrl, long followers, Long totalViews, Long contentCount, boolean needsReconnect,
        Instant connectedAt, Instant lastSyncedAt) {

    public static SocialAccountResponse from(SocialAccount account) {
        return new SocialAccountResponse(account.getId(), account.getProvider(), account.getHandle(),
                account.getDisplayName(), account.getProfileUrl(), account.getAvatarUrl(), account.getFollowers(),
                account.getTotalViews(), account.getContentCount(), account.isNeedsReconnect(),
                account.getConnectedAt(), account.getLastSyncedAt());
    }
}
