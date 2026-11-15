package com.advertisingmarketplace.common.money;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.exchange-rates")
public record ExchangeRateProperties(
        @DefaultValue("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml") URI ecbUrl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout,
        @DefaultValue("true") boolean refreshOnStartup) {
}
