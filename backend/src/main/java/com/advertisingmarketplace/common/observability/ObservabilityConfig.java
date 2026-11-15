package com.advertisingmarketplace.common.observability;

import com.advertisingmarketplace.common.money.ExchangeRate;
import com.advertisingmarketplace.common.money.ExchangeRateRepository;
import com.advertisingmarketplace.messaging.realtime.SseHub;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    @Bean
    MeterBinder realtimeConnectionsMetric(SseHub sseHub) {
        return registry -> Gauge.builder("marketplace.realtime.sse_connections", sseHub, SseHub::connectionCount)
                .description("Open server-sent event streams on this instance")
                .register(registry);
    }

    @Bean
    MeterBinder exchangeRatesAgeMetric(ExchangeRateRepository repository, Clock clock) {
        return registry -> Gauge.builder("marketplace.exchange_rates.age", repository,
                        rates -> ageSeconds(rates, clock))
                .description("Seconds since the oldest exchange rate in use was published")
                .baseUnit("seconds")
                .register(registry);
    }

    private static double ageSeconds(ExchangeRateRepository repository, Clock clock) {
        return repository.findAll().stream()
                .map(ExchangeRate::getRateDate)
                .min(LocalDate::compareTo)
                .map(date -> (double) Duration.between(date.atStartOfDay().toInstant(ZoneOffset.UTC), clock.instant())
                        .toSeconds())
                .orElse(Double.NaN);
    }
}
