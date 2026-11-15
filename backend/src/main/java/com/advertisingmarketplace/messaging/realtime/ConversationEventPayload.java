package com.advertisingmarketplace.messaging.realtime;

import com.advertisingmarketplace.messaging.dto.MessageResponse;
import java.time.Instant;
import java.util.UUID;

public record ConversationEventPayload(
        ConversationEventType type,
        UUID conversationId,
        MessageResponse message,
        UUID actorId,
        Instant at) {
}
