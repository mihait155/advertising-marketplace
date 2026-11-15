package com.advertisingmarketplace.messaging.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SendMessageRequest(@Size(max = 5_000) String text, @Size(max = 10) List<UUID> imageIds) {

    @AssertTrue(message = "a message needs text or at least one image")
    public boolean isNotEmpty() {
        return (text != null && !text.isBlank()) || (imageIds != null && !imageIds.isEmpty());
    }

    public List<UUID> imageIdsOrEmpty() {
        return imageIds == null ? List.of() : imageIds;
    }
}
