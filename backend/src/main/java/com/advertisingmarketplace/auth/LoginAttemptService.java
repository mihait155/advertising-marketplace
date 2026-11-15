package com.advertisingmarketplace.auth;

import com.advertisingmarketplace.security.SecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginAttemptRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;

    static String accountKey(String email) {
        return "account:" + email.toLowerCase(Locale.ROOT);
    }

    static String clientKey(String email, String clientIp) {
        return "client:" + email.toLowerCase(Locale.ROOT) + "|" + clientIp;
    }

    @Transactional(readOnly = true)
    public void checkAllowed(String email, String clientIp) {
        Instant now = clock.instant();
        check(clientKey(email, clientIp), properties.login().maxFailedAttempts(), now);
        check(accountKey(email), properties.login().maxFailedAttemptsPerAccount(), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, String clientIp) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.login().lockoutDuration());
        repository.recordFailure(clientKey(email, clientIp), now, cutoff);
        repository.recordFailure(accountKey(email), now, cutoff);
    }

    @Transactional
    public void recordSuccess(String email, String clientIp) {
        repository.deleteById(clientKey(email, clientIp));
    }

    @Scheduled(cron = "${app.security.login-attempt-cleanup-cron:0 */10 * * * *}")
    @Transactional
    public void purgeExpired() {
        repository.deleteExpired(clock.instant().minus(properties.login().lockoutDuration()));
    }

    private void check(String key, int max, Instant now) {
        Duration window = properties.login().lockoutDuration();
        repository.findById(key)
                .filter(attempt -> attempt.getWindowStart().plus(window).isAfter(now))
                .filter(attempt -> attempt.getFailures() >= max)
                .ifPresent(attempt -> {
                    throw new TooManyLoginAttemptsException(
                            Duration.between(now, attempt.getWindowStart().plus(window)));
                });
    }
}
