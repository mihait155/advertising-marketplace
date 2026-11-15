package com.advertisingmarketplace.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.security")
public record SecurityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue Cors cors,
        @DefaultValue Login login,
        @DefaultValue BootstrapAdmin bootstrapAdmin,
        @DefaultValue Cookie cookie) {

    public record Jwt(
            String secret,
            @DefaultValue("advertising-marketplace") String issuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("30d") Duration refreshTokenTtl) {
    }

    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }

    public record Login(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("20") int maxFailedAttemptsPerAccount,
            @DefaultValue("15m") Duration lockoutDuration) {
    }

    public record Cookie(@DefaultValue("true") boolean secure) {
    }

    public record BootstrapAdmin(
            String email,
            String password,
            @DefaultValue("Administrator") String fullName) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
