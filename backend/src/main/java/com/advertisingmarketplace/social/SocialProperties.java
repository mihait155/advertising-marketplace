package com.advertisingmarketplace.social;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.social")
public record SocialProperties(URI redirectUri, String tokenEncryptionKey, Provider twitch, Provider youtube) {

    public record Provider(String clientId, String clientSecret, URI authUrl, URI tokenUrl, URI apiUrl, String scope) {

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
