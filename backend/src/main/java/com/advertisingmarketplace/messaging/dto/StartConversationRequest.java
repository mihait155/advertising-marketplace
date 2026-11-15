package com.advertisingmarketplace.messaging.dto;

import jakarta.validation.constraints.Size;

public record StartConversationRequest(@Size(max = 5_000) String text) {
}
