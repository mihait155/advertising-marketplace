package com.advertisingmarketplace.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MarketplaceMetrics {

    private final MeterRegistry registry;
    private final ObservationRegistry observations;

    public MarketplaceMetrics(MeterRegistry registry, ObservationRegistry observations) {
        this.registry = registry;
        this.observations = observations;
    }

    public static MarketplaceMetrics noop() {
        return new MarketplaceMetrics(new SimpleMeterRegistry(), ObservationRegistry.NOOP);
    }

    public void login(String outcome) {
        increment("marketplace.auth.logins", "Login attempts by outcome", "outcome", outcome);
    }

    public void imageUpload(String outcome) {
        increment("marketplace.images.uploads", "Image uploads by outcome", "outcome", outcome);
    }

    public void offerCreated() {
        afterCommit(() -> increment("marketplace.offers.sent", "Offers sent"));
    }

    public void offerDecided(String status) {
        afterCommit(() -> increment("marketplace.offers.decisions", "Offer status changes", "status", status));
    }

    public void dealEvent(String event) {
        afterCommit(() -> increment("marketplace.deals.events", "Deal lifecycle events", "event", event));
    }

    public void messageSent(String type) {
        afterCommit(() -> increment("marketplace.messages.sent", "Messages sent", "type", type));
    }

    public void reviewSubmitted(int rating) {
        afterCommit(() -> increment("marketplace.reviews.submitted", "Reviews submitted", "rating",
                Integer.toString(rating)));
    }

    public void socialSync(String provider, String outcome) {
        increment("marketplace.social.syncs", "Connected account syncs", "provider", provider, "outcome", outcome);
    }

    public void exchangeRatesRefresh(String outcome) {
        increment("marketplace.exchange_rates.refreshes", "Exchange rate refreshes", "outcome", outcome);
    }

    public <T> T observe(String name, String contextualName, Supplier<T> action) {
        return Observation.createNotStarted(name, observations).contextualName(contextualName).observe(action);
    }

    private void increment(String name, String description, String... tags) {
        Counter.builder(name).description(description).tags(tags).register(registry).increment();
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
