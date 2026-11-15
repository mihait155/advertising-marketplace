package com.advertisingmarketplace.image;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("app.images")
public record ImageProperties(
        @DefaultValue("10MB") DataSize maxSize,
        @DefaultValue("20") int maxPerPosting,
        @DefaultValue("60") int maxUploadsPerHour,
        @DefaultValue("24h") Duration orphanRetention) {
}
