package com.advertisingmarketplace.common.money;

import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.posting.PostingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    public static final String BASE_CURRENCY = "EUR";

    private final ExchangeRateRepository repository;
    private final ExchangeRateProvider provider;
    private final PostingRepository postingRepository;
    private final CurrencyProperties currencyProperties;
    private final ExchangeRateProperties properties;
    private final Clock clock;
    private final MarketplaceMetrics metrics;

    @EventListener(ApplicationReadyEvent.class)
    void refreshOnStartup() {
        if (properties.refreshOnStartup()) {
            refresh();
        }
    }

    @Scheduled(cron = "${app.exchange-rates.refresh-cron:0 15 17 * * MON-FRI}", zone = "Europe/Berlin")
    public void scheduledRefresh() {
        refresh();
    }

    @Transactional
    public boolean refresh() {
        ExchangeRates latest;
        try {
            latest = metrics.observe("marketplace.exchange_rates.fetch", "ecb fetch", provider::fetchLatest);
        } catch (RuntimeException e) {
            log.warn("Could not fetch exchange rates, keeping the previous ones: {}", e.getMessage());
            metrics.exchangeRatesRefresh("failure");
            return false;
        }
        Instant now = clock.instant();
        for (String code : currencyProperties.supported()) {
            String currency = Currencies.normalize(code);
            BigDecimal unitsPerEur = BASE_CURRENCY.equals(currency) ? BigDecimal.ONE : latest.unitsPerEur().get(currency);
            if (unitsPerEur == null) {
                log.warn("The exchange rate source has no rate for supported currency {}", currency);
                continue;
            }
            repository.findById(currency).ifPresentOrElse(
                    rate -> rate.update(unitsPerEur, latest.date(), now),
                    () -> repository.save(new ExchangeRate(currency, unitsPerEur, latest.date(), now)));
        }
        repository.flush();
        int updated = postingRepository.recomputeEurAmounts();
        log.info("Exchange rates updated to {}; recomputed EUR amounts of {} postings", latest.date(), updated);
        metrics.exchangeRatesRefresh("success");
        return true;
    }

    public Optional<BigDecimal> toEur(BigDecimal amount, String currency) {
        if (amount == null || currency == null) {
            return Optional.empty();
        }
        return repository.findById(currency)
                .map(rate -> amount.divide(rate.getUnitsPerEur(), 2, RoundingMode.HALF_EVEN));
    }

    public BigDecimal toEurOrThrow(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        return toEur(amount, currency).orElseThrow(() -> new NoExchangeRateException(currency));
    }

    public Map<String, ExchangeRate> currentRates() {
        return repository.findAll().stream().collect(Collectors.toMap(ExchangeRate::getCurrency, Function.identity()));
    }
}
