package com.advertisingmarketplace.posting;

public enum PostingStatus {

    DRAFT,
    OPEN,
    CLOSED;

    public boolean canTransitionTo(PostingStatus target) {
        return switch (this) {
            case DRAFT -> target == OPEN;
            case OPEN -> target == CLOSED;
            case CLOSED -> target == OPEN;
        };
    }
}
