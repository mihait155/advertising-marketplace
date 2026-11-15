package com.advertisingmarketplace.messaging.realtime;

import com.advertisingmarketplace.messaging.MessagingProperties;
import com.advertisingmarketplace.security.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class StreamTicketService {

    static final String TICKET_TYPE = "stream-ticket";

    private final JwtEncoder jwtEncoder;
    private final NimbusJwtDecoder decoder;
    private final SecurityProperties securityProperties;
    private final MessagingProperties properties;
    private final Clock clock;

    public StreamTicketService(JwtEncoder jwtEncoder, SecretKey jwtSigningKey, SecurityProperties securityProperties,
            MessagingProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.securityProperties = securityProperties;
        this.properties = properties;
        this.clock = clock;
        this.decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(securityProperties.jwt().issuer()),
                new JwtClaimValidator<>("typ", TICKET_TYPE::equals)));
    }

    public StreamTicket issue(UUID userId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.streamTicketTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.jwt().issuer())
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("typ", TICKET_TYPE)
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new StreamTicket(value, expiresAt);
    }

    public Optional<UUID> validate(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(decoder.decode(ticket).getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record StreamTicket(String ticket, Instant expiresAt) {
    }
}
