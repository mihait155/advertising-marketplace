package com.advertisingmarketplace.messaging.dto;

import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        PostingSummary posting,
        UserSummary postingCreator,
        UserSummary counterpart,
        UserSummary otherParticipant,
        MessageResponse lastMessage,
        long unreadCount,
        Instant otherParticipantLastReadAt,
        Instant lastMessageAt,
        Instant createdAt) {

    public record PostingSummary(UUID id, String title, PostingCategory category, PostingStatus status) {
    }
}
