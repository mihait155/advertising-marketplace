package com.advertisingmarketplace.auth;

import com.advertisingmarketplace.security.SecurityProperties;
import com.advertisingmarketplace.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;

    @Transactional
    public IssuedRefreshToken issue(User user) {
        Instant now = clock.instant();
        String token = TokenHasher.newToken();
        Instant expiresAt = now.plus(properties.jwt().refreshTokenTtl());
        repository.save(new RefreshToken(user, TokenHasher.sha256(token), now, expiresAt));
        return new IssuedRefreshToken(token, expiresAt);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public User consume(String token) {
        Instant now = clock.instant();
        RefreshToken stored = repository.findByTokenHash(TokenHasher.sha256(token))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (stored.isRevoked()) {
            if (stored.getRevokedReason() == RevocationReason.ROTATED) {
                UUID userId = stored.getUser().getId();
                log.warn("Reuse of rotated refresh token {} detected for user {}; revoking all sessions",
                        stored.getId(), userId);
                repository.revokeAllForUser(userId, now, RevocationReason.REUSE_DETECTED);
            }
            throw new InvalidRefreshTokenException();
        }
        if (stored.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        stored.revoke(now, RevocationReason.ROTATED);
        return stored.getUser();
    }

    @Transactional
    public void revoke(String token) {
        repository.findByTokenHash(TokenHasher.sha256(token))
                .ifPresent(t -> t.revoke(clock.instant(), RevocationReason.LOGOUT));
    }

    @Transactional
    public void revokeAll(UUID userId, RevocationReason reason) {
        repository.revokeAllForUser(userId, clock.instant(), reason);
    }

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }
}
