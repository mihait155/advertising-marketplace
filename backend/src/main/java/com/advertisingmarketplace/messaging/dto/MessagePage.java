package com.advertisingmarketplace.messaging.dto;

import java.util.List;
import java.util.UUID;

public record MessagePage(List<MessageResponse> messages, UUID nextBefore) {
}
