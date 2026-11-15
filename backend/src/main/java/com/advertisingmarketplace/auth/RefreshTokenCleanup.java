package com.advertisingmarketplace.auth;

import com.advertisingmarketplace.security.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanup {

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${app.security.refresh-token-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void purge() {
        Instant cutoff = clock.instant().minus(properties.jwt().refreshTokenTtl());
        int deleted = repository.deleteExpiredOrRevokedBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} stale refresh tokens", deleted);
        }
    }
}
