package com.advertisingmarketplace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.support.TestSecurityProperties;
import com.advertisingmarketplace.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service =
            new RefreshTokenService(repository, TestSecurityProperties.defaults(), clock);

    private final UUID userId = UUID.randomUUID();
    private final User user = mock(User.class);

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(userId);
    }

    @Test
    void issueStoresOnlyTheHashWithExpiry() {
        RefreshTokenService.IssuedRefreshToken issued = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        assertThat(stored.getTokenHash())
                .isEqualTo(TokenHasher.sha256(issued.value()))
                .isNotEqualTo(issued.value())
                .hasSize(64);
        assertThat(stored.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(30)));
        assertThat(issued.expiresAt()).isEqualTo(stored.getExpiresAt());
        assertThat(issued.value()).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void issuedTokensAreUnique() {
        assertThat(service.issue(user).value()).isNotEqualTo(service.issue(user).value());
    }

    @Test
    void consumeRevokesTokenAndReturnsUser() {
        RefreshToken stored = stored("token", clock.instant().plus(Duration.ofDays(1)));

        assertThat(service.consume("token")).isSameAs(user);
        assertThat(stored.getRevokedReason()).isEqualTo(RevocationReason.ROTATED);
    }

    @Test
    void consumeRejectsUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("nope")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void consumeRejectsExpiredToken() {
        RefreshToken stored = stored("token", clock.instant().plus(Duration.ofDays(1)));
        clock.advance(Duration.ofDays(1));

        assertThatThrownBy(() -> service.consume("token")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(stored.isRevoked()).isFalse();
        verify(repository, never()).revokeAllForUser(any(), any(), any());
    }

    @Test
    void reuseOfRotatedTokenRevokesAllSessionsOfThatUser() {
        RefreshToken stored = stored("token", clock.instant().plus(Duration.ofDays(1)));
        stored.revoke(clock.instant(), RevocationReason.ROTATED);

        assertThatThrownBy(() -> service.consume("token")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revokeAllForUser(eq(userId), any(), eq(RevocationReason.REUSE_DETECTED));
    }

    @Test
    void loggedOutTokenIsRejectedWithoutRevokingOtherSessions() {
        RefreshToken stored = stored("token", clock.instant().plus(Duration.ofDays(1)));
        stored.revoke(clock.instant(), RevocationReason.LOGOUT);

        assertThatThrownBy(() -> service.consume("token")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).revokeAllForUser(any(), any(), any());
    }

    @Test
    void revokeIsIdempotentAndIgnoresUnknownTokens() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        service.revoke("unknown");

        RefreshToken stored = stored("token", clock.instant().plus(Duration.ofDays(1)));
        service.revoke("token");
        assertThat(stored.getRevokedReason()).isEqualTo(RevocationReason.LOGOUT);
        Instant firstRevocation = stored.getRevokedAt();
        clock.advance(Duration.ofMinutes(1));
        service.revoke("token");

        assertThat(stored.getRevokedAt()).isEqualTo(firstRevocation);
    }

    private RefreshToken stored(String token, Instant expiresAt) {
        RefreshToken stored = new RefreshToken(user, TokenHasher.sha256(token), clock.instant(), expiresAt);
        when(repository.findByTokenHash(TokenHasher.sha256(token))).thenReturn(Optional.of(stored));
        return stored;
    }
}
