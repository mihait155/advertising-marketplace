package com.advertisingmarketplace.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MarketplaceMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MarketplaceMetrics metrics = new MarketplaceMetrics(registry, ObservationRegistry.NOOP);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void countsImmediatelyOutsideATransaction() {
        metrics.dealEvent("started");

        assertThat(count("marketplace.deals.events", "event", "started")).isEqualTo(1);
    }

    @Test
    void transactionalEventsAreCountedOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.dealEvent("approved");
        assertThat(registry.find("marketplace.deals.events").counter()).isNull();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(count("marketplace.deals.events", "event", "approved")).isEqualTo(1);
    }

    @Test
    void transactionalEventsAreDroppedOnRollback() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.offerCreated();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(registry.find("marketplace.offers.sent").counter()).isNull();
    }

    @Test
    void loginOutcomesAreTagged() {
        metrics.login("success");
        metrics.login("bad_credentials");
        metrics.login("bad_credentials");

        assertThat(count("marketplace.auth.logins", "outcome", "success")).isEqualTo(1);
        assertThat(count("marketplace.auth.logins", "outcome", "bad_credentials")).isEqualTo(2);
    }

    @Test
    void observationsReturnTheActionResult() {
        assertThat(metrics.observe("marketplace.test", "test", () -> 42)).isEqualTo(42);
    }

    private double count(String name, String tagKey, String tagValue) {
        return registry.get(name).tag(tagKey, tagValue).counter().count();
    }
}
