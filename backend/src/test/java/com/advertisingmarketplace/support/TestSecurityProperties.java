package com.advertisingmarketplace.support;

import com.advertisingmarketplace.security.SecurityProperties;
import java.time.Duration;
import java.util.List;

public final class TestSecurityProperties {

    public static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy0zMi1ieXRlcy1sb25nIQ==";

    private TestSecurityProperties() {
    }

    public static SecurityProperties defaults() {
        return new SecurityProperties(
                new SecurityProperties.Jwt(SECRET, "test-issuer", Duration.ofMinutes(15), Duration.ofDays(30)),
                new SecurityProperties.Cors(List.of("http://localhost:3000")),
                new SecurityProperties.Login(3, 10, Duration.ofMinutes(15)),
                new SecurityProperties.BootstrapAdmin(null, null, "Administrator"),
                new SecurityProperties.Cookie(true));
    }
}
