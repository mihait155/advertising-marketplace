package com.advertisingmarketplace.social;

public record ChannelSnapshot(String externalId, String handle, String displayName, String profileUrl,
        String avatarUrl, long followers, Long totalViews, Long contentCount) {
}
