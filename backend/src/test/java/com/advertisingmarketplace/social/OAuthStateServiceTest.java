package com.advertisingmarketplace.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.advertisingmarketplace.security.JwtConfig;
import com.advertisingmarketplace.security.SecurityProperties;
import com.advertisingmarketplace.social.SocialExceptions.InvalidOAuthStateException;
import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.support.TestSecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class OAuthStateServiceTest {

    private final MutableClock clock = new MutableClock(Instant.now());
    private final OAuthStateService service;

    OAuthStateServiceTest() {
        SecurityProperties properties = TestSecurityProperties.defaults();
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(SecurityProperties.class, () -> properties);
        ctx.register(JwtConfig.class);
        ctx.refresh();
        service = new OAuthStateService(ctx.getBean(JwtEncoder.class), ctx.getBean(SecretKey.class), properties, clock);
    }

    @Test
    void stateRoundTripsForTheSameUser() {
        UUID user = UUID.randomUUID();

        assertThat(service.verify(service.issue(user, SocialProvider.YOUTUBE), user)).isEqualTo(SocialProvider.YOUTUBE);
    }

    @Test
    void stateIsBoundToTheUserAndExpires() {
        UUID user = UUID.randomUUID();
        String state = service.issue(user, SocialProvider.TWITCH);

        assertThatThrownBy(() -> service.verify(state, UUID.randomUUID())).isInstanceOf(InvalidOAuthStateException.class);
        assertThatThrownBy(() -> service.verify("forged", user)).isInstanceOf(InvalidOAuthStateException.class);

        clock.advance(Duration.ofMinutes(-30));
        String expired = service.issue(user, SocialProvider.TWITCH);
        assertThatThrownBy(() -> service.verify(expired, user)).isInstanceOf(InvalidOAuthStateException.class);
    }
}
