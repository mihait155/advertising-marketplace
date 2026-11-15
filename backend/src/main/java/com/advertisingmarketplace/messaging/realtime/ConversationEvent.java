package com.advertisingmarketplace.messaging.realtime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationEvent(
        ConversationEventType type,
        UUID conversationId,
        UUID messageId,
        UUID actorId,
        List<UUID> recipientIds,
        Instant at) {
}
