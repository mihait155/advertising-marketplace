package com.advertisingmarketplace.messaging.dto;

public record UnreadCountResponse(long unreadMessages, long conversationsWithUnread) {
}
