package com.advertisingmarketplace.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.posting.PostingRepository;
import com.advertisingmarketplace.support.MutableClock;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExchangeRateServiceTest {

    private final ExchangeRateRepository repository = mock(ExchangeRateRepository.class);
    private final ExchangeRateProvider provider = mock(ExchangeRateProvider.class);
    private final PostingRepository postingRepository = mock(PostingRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T16:30:00Z"));
    private final ExchangeRateService service = new ExchangeRateService(repository, provider, postingRepository,
            new CurrencyProperties(List.of("EUR", "USD", "GBP", "RON", "SEK")),
            new ExchangeRateProperties(URI.create("http://ecb.test"), Duration.ofSeconds(1), Duration.ofSeconds(1),
                    false),
            clock, MarketplaceMetrics.noop());

    private final Map<String, ExchangeRate> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<String>getArgument(0))));
        when(repository.save(any(ExchangeRate.class))).thenAnswer(inv -> {
            ExchangeRate rate = inv.getArgument(0);
            stored.put(rate.getCurrency(), rate);
            return rate;
        });
        stored.put("EUR", new ExchangeRate("EUR", BigDecimal.ONE, LocalDate.of(2026, 1, 1), clock.instant()));
    }

    @Test
    void refreshStoresSupportedCurrenciesAndRecomputesPostings() {
        stored.put("USD", new ExchangeRate("USD", new BigDecimal("1.10"), LocalDate.of(2026, 11, 2), clock.instant()));
        when(provider.fetchLatest()).thenReturn(new ExchangeRates(LocalDate.of(2026, 11, 4), Map.of(
                "USD", new BigDecimal("1.25"), "GBP", new BigDecimal("0.8"), "RON", new BigDecimal("5"),
                "JPY", new BigDecimal("165"))));

        assertThat(service.refresh()).isTrue();

        assertThat(stored).containsOnlyKeys("EUR", "USD", "GBP", "RON");
        assertThat(stored.get("USD").getUnitsPerEur()).isEqualByComparingTo("1.25");
        assertThat(stored.get("USD").getRateDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(stored.get("EUR").getUnitsPerEur()).isEqualByComparingTo("1");
        assertThat(stored.get("EUR").getRateDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        ArgumentCaptor<ExchangeRate> saved = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ExchangeRate::getCurrency).containsExactlyInAnyOrder("GBP", "RON");
        verify(postingRepository).recomputeEurAmounts();
    }

    @Test
    void failedFetchKeepsPreviousRates() {
        when(provider.fetchLatest()).thenThrow(new IllegalStateException("ECB down"));

        assertThat(service.refresh()).isFalse();

        verify(repository, never()).save(any());
        verify(postingRepository, never()).recomputeEurAmounts();
    }

    @Test
    void convertsToEurWithBankersRounding() {
        stored.put("RON", new ExchangeRate("RON", new BigDecimal("4.97"), LocalDate.now(), clock.instant()));

        assertThat(service.toEur(new BigDecimal("1000"), "RON")).contains(new BigDecimal("201.21"));
        assertThat(service.toEur(new BigDecimal("12.34"), "EUR")).contains(new BigDecimal("12.34"));
        assertThat(service.toEur(null, "EUR")).isEmpty();
        assertThat(service.toEur(BigDecimal.TEN, null)).isEmpty();
        assertThat(service.toEur(BigDecimal.TEN, "SEK")).isEmpty();
    }

    @Test
    void toEurOrThrowFailsWithoutRate() {
        assertThat(service.toEurOrThrow(null, "SEK")).isNull();
        assertThatThrownBy(() -> service.toEurOrThrow(BigDecimal.TEN, "SEK"))
                .isInstanceOf(NoExchangeRateException.class)
                .hasMessage("No exchange rate is available for SEK yet");
    }
}
