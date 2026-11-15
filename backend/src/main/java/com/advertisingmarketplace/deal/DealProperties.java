package com.advertisingmarketplace.deal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.deals")
public record DealProperties(
        @DefaultValue("7d") Duration autoApproveAfter,
        @DefaultValue("30d") Duration reviewWindow,
        @DefaultValue("14d") Duration blindReviewPeriod) {
}
