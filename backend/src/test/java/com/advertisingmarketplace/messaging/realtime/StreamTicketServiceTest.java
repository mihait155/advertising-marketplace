package com.advertisingmarketplace.messaging.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.advertisingmarketplace.auth.AccessTokenService;
import com.advertisingmarketplace.messaging.MessagingProperties;
import com.advertisingmarketplace.security.JwtConfig;
import com.advertisingmarketplace.security.SecurityProperties;
import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.support.TestSecurityProperties;
import com.advertisingmarketplace.user.UserType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

class StreamTicketServiceTest {

    private static final SecurityProperties PROPERTIES = TestSecurityProperties.defaults();

    private final MutableClock clock = new MutableClock(Instant.now());
    private final AnnotationConfigApplicationContext jwt = jwtContext();
    private final StreamTicketService service = new StreamTicketService(jwt.getBean(JwtEncoder.class),
            jwt.getBean(SecretKey.class), PROPERTIES,
            new MessagingProperties(30, 10, false, Duration.ofSeconds(60), Duration.ofMinutes(30), 5), clock);

    private static AnnotationConfigApplicationContext jwtContext() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(SecurityProperties.class, () -> PROPERTIES);
        ctx.register(JwtConfig.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void ticketIdentifiesTheUser() {
        UUID userId = UUID.randomUUID();

        StreamTicketService.StreamTicket ticket = service.issue(userId);

        assertThat(service.validate(ticket.ticket())).contains(userId);
        assertThat(ticket.expiresAt()).isEqualTo(clock.instant().plusSeconds(60));
    }

    @Test
    void expiredGarbageAndAccessTokensAreRejected() {
        clock.advance(Duration.ofMinutes(-10));
        String expired = service.issue(UUID.randomUUID()).ticket();
        String accessToken = new AccessTokenService(jwt.getBean(JwtEncoder.class), PROPERTIES, new MutableClock(
                Instant.now())).issue(UUID.randomUUID(), "x@example.com", UserType.MARKETER).value();

        assertThat(service.validate(expired)).isEmpty();
        assertThat(service.validate("garbage")).isEmpty();
        assertThat(service.validate(null)).isEmpty();
        assertThat(service.validate(accessToken)).isEmpty();
    }

    @Test
    void ticketIsNotAnAccessToken() {
        String ticket = service.issue(UUID.randomUUID()).ticket();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jwt.getBean(JwtDecoder.class).decode(ticket))
                .isInstanceOf(JwtException.class);
    }
}
