package com.advertisingmarketplace.image.scan;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.clamav")
public record ClamAvProperties(
        @DefaultValue("localhost") String host,
        @DefaultValue("3310") int port,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("65536") int chunkSize) {
}
