package com.advertisingmarketplace.image.dto;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record ImageResponse(
        UUID id,
        String contentType,
        long sizeBytes,
        URI url,
        Instant urlExpiresAt,
        Instant createdAt) {
}
