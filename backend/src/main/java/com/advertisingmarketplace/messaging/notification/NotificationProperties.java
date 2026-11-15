package com.advertisingmarketplace.messaging.notification;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.notifications.email")
public record NotificationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("Advertising Marketplace <no-reply@marketplace.local>") String from,
        @DefaultValue("5m") Duration delay,
        @DefaultValue("http://localhost:5173") URI frontendUrl,
        @DefaultValue("50") int batchSize) {
}
