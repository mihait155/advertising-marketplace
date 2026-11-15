package com.advertisingmarketplace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.support.TestEntities;
import com.advertisingmarketplace.support.TestSecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private static final String EMAIL = "Jane@Example.com";
    private static final String IP = "203.0.113.7";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final LoginAttemptRepository repository = mock(LoginAttemptRepository.class);
    private final LoginAttemptService service = new LoginAttemptService(repository, TestSecurityProperties.defaults(),
            clock);
    private final Map<String, LoginAttempt> stored = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<String>getArgument(0))));
    }

    private void attempts(String key, int failures, Instant windowStart) {
        LoginAttempt attempt = TestEntities.withField(TestEntities.newInstance(LoginAttempt.class), "key", key);
        TestEntities.withField(attempt, "failures", failures);
        TestEntities.withField(attempt, "windowStart", windowStart);
        stored.put(key, attempt);
    }

    @Test
    void keysAreCaseInsensitiveAndPerClient() {
        assertThat(LoginAttemptService.accountKey(EMAIL)).isEqualTo("account:jane@example.com");
        assertThat(LoginAttemptService.clientKey(EMAIL, IP)).isEqualTo("client:jane@example.com|203.0.113.7");
    }

    @Test
    void allowsBelowThresholds() {
        attempts(LoginAttemptService.clientKey(EMAIL, IP), 2, clock.instant());
        attempts(LoginAttemptService.accountKey(EMAIL), 9, clock.instant());

        assertThatCode(() -> service.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void locksTheClientAfterTooManyFailures() {
        attempts(LoginAttemptService.clientKey(EMAIL, IP), 3, clock.instant().minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> service.checkAllowed(EMAIL, IP))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMinutes(10)));
        assertThatCode(() -> service.checkAllowed(EMAIL, "198.51.100.1")).doesNotThrowAnyException();
    }

    @Test
    void locksTheAccountAfterManyFailuresFromAnywhere() {
        attempts(LoginAttemptService.accountKey(EMAIL), 10, clock.instant());

        assertThatThrownBy(() -> service.checkAllowed(EMAIL, "198.51.100.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void expiredWindowsDontCount() {
        attempts(LoginAttemptService.clientKey(EMAIL, IP), 50, clock.instant().minus(Duration.ofMinutes(15)));

        assertThatCode(() -> service.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void failuresAreRecordedForClientAndAccount() {
        service.recordFailure(EMAIL, IP);

        Instant cutoff = clock.instant().minus(Duration.ofMinutes(15));
        verify(repository).recordFailure(LoginAttemptService.clientKey(EMAIL, IP), clock.instant(), cutoff);
        verify(repository).recordFailure(LoginAttemptService.accountKey(EMAIL), clock.instant(), cutoff);
    }

    @Test
    void successClearsOnlyTheClientCounter() {
        service.recordSuccess(EMAIL, IP);

        verify(repository).deleteById(LoginAttemptService.clientKey(EMAIL, IP));
    }
}
