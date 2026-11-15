package com.advertisingmarketplace.social;

public enum SocialProvider {

    TWITCH("Twitch"),
    YOUTUBE("YouTube");

    private final String displayName;

    SocialProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
