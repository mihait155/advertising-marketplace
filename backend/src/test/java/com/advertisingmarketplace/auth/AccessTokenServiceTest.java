package com.advertisingmarketplace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.advertisingmarketplace.security.JwtConfig;
import com.advertisingmarketplace.security.SecurityProperties;
import com.advertisingmarketplace.support.MutableClock;
import com.advertisingmarketplace.support.TestSecurityProperties;
import com.advertisingmarketplace.user.UserType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

class AccessTokenServiceTest {

    private static final SecurityProperties PROPERTIES = TestSecurityProperties.defaults();

    private final MutableClock clock = new MutableClock(Instant.now());
    private final JwtBeans jwt = JwtBeans.from(PROPERTIES);
    private final AccessTokenService service = new AccessTokenService(jwt.encoder(), PROPERTIES, clock);

    @Test
    void issuedTokenRoundTripsWithExpectedClaims() {
        UUID userId = UUID.randomUUID();

        AccessTokenService.IssuedAccessToken token = service.issue(userId, "jane@example.com", UserType.MARKETER);
        Jwt decoded = jwt.decoder().decode(token.value());

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo("jane@example.com");
        assertThat(decoded.getClaimAsStringList("roles")).isEqualTo(List.of("MARKETER"));
        assertThat(decoded.getClaimAsString("typ")).isEqualTo("access");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(decoded.getId()).isNotBlank();
        assertThat(token.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = service.issue(UUID.randomUUID(), "jane@example.com", UserType.CUSTOMER).value().split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"roles\":[\"ADMIN\"],\"typ\":\"access\","
                        + "\"iss\":\"test-issuer\",\"exp\":" + (Instant.now().getEpochSecond() + 600) + "}")
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> jwt.decoder().decode(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"x\",\"roles\":[\"ADMIN\"],\"typ\":\"access\",\"iss\":\"test-issuer\"}")
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> jwt.decoder().decode(header + "." + payload + "."))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        SecurityProperties other = withJwt("b3RoZXItc2VjcmV0LXRoYXQtaXMtMzItYnl0ZXMtbG9uZyE=", "test-issuer");
        String token = new AccessTokenService(JwtBeans.from(other).encoder(), other, clock)
                .issue(UUID.randomUUID(), "x@example.com", UserType.ADMIN).value();

        assertThatThrownBy(() -> jwt.decoder().decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        SecurityProperties other = withJwt(TestSecurityProperties.SECRET, "someone-else");
        String token = new AccessTokenService(jwt.encoder(), other, clock)
                .issue(UUID.randomUUID(), "x@example.com", UserType.CUSTOMER).value();

        assertThatThrownBy(() -> jwt.decoder().decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        clock.advance(Duration.ofHours(-1));
        String token = service.issue(UUID.randomUUID(), "x@example.com", UserType.CUSTOMER).value();

        assertThatThrownBy(() -> jwt.decoder().decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tooShortSecretFailsStartup() {
        assertThatThrownBy(() -> JwtBeans.from(withJwt("c2hvcnQ=", "test-issuer")))
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseMessage("app.security.jwt.secret must decode to at least 256 bits");
    }

    @Test
    void blankSecretFallsBackToRandomKey() {
        JwtBeans random = JwtBeans.from(withJwt("", "test-issuer"));
        String token = new AccessTokenService(random.encoder(), PROPERTIES, clock)
                .issue(UUID.randomUUID(), "x@example.com", UserType.CUSTOMER).value();

        assertThat(random.decoder().decode(token).getSubject()).isNotBlank();
        assertThatThrownBy(() -> jwt.decoder().decode(token)).isInstanceOf(JwtException.class);
    }

    private static SecurityProperties withJwt(String secret, String issuer) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(secret, issuer, Duration.ofMinutes(15), Duration.ofDays(30)),
                PROPERTIES.cors(), PROPERTIES.login(), PROPERTIES.bootstrapAdmin(), PROPERTIES.cookie());
    }

    private record JwtBeans(JwtEncoder encoder, JwtDecoder decoder) {

        static JwtBeans from(SecurityProperties properties) {
            try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
                ctx.registerBean(SecurityProperties.class, () -> properties);
                ctx.register(JwtConfig.class);
                ctx.refresh();
                return new JwtBeans(ctx.getBean(JwtEncoder.class), ctx.getBean(JwtDecoder.class));
            }
        }
    }
}
