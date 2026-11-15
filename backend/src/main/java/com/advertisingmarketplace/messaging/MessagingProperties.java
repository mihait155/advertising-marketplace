package com.advertisingmarketplace.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.messaging")
public record MessagingProperties(
        @DefaultValue("30") int maxMessagesPerMinute,
        @DefaultValue("10") int maxImagesPerMessage,
        @DefaultValue("false") boolean maskContactDetails,
        @DefaultValue("60s") Duration streamTicketTtl,
        @DefaultValue("30m") Duration streamTimeout,
        @DefaultValue("5") int maxStreamsPerUser) {
}
