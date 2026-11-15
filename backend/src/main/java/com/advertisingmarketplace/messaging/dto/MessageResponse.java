package com.advertisingmarketplace.messaging.dto;

import com.advertisingmarketplace.image.dto.ImageResponse;
import com.advertisingmarketplace.messaging.MessageType;
import com.advertisingmarketplace.messaging.SystemEvent;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        MessageType type,
        SystemEvent systemEvent,
        UserSummary sender,
        String text,
        UUID offerId,
        List<ImageResponse> images,
        boolean deleted,
        Instant createdAt,
        Instant editedAt) {
}
