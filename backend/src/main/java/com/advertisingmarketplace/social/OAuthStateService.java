package com.advertisingmarketplace.social;

import com.advertisingmarketplace.security.SecurityProperties;
import com.advertisingmarketplace.social.SocialExceptions.InvalidOAuthStateException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class OAuthStateService {

    static final String STATE_TYPE = "oauth-state";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final String issuer;
    private final Clock clock;

    public OAuthStateService(JwtEncoder encoder, SecretKey jwtSigningKey, SecurityProperties securityProperties,
            Clock clock) {
        this.encoder = encoder;
        this.issuer = securityProperties.jwt().issuer();
        this.clock = clock;
        this.decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<>("typ", STATE_TYPE::equals)));
    }

    public String issue(UUID userId, SocialProvider provider) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .claim("typ", STATE_TYPE)
                .claim("provider", provider.name())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public SocialProvider verify(String state, UUID userId) {
        try {
            Jwt jwt = decoder.decode(state);
            if (!userId.toString().equals(jwt.getSubject())) {
                throw new InvalidOAuthStateException();
            }
            return SocialProvider.valueOf(jwt.getClaimAsString("provider"));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidOAuthStateException();
        }
    }
}
