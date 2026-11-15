package com.advertisingmarketplace.auth;

import com.advertisingmarketplace.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    public static final String COOKIE_NAME = "am_refresh";
    public static final String PATH = "/api/v1/auth";
    public static final String BODY_DELIVERY_HEADER = "X-Token-Delivery";
    public static final String CSRF_HEADER = "X-Requested-With";

    private final SecurityProperties properties;

    public String create(String token) {
        return build(token, properties.jwt().refreshTokenTtl()).toString();
    }

    public String clear() {
        return build("", Duration.ZERO).toString();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    public static boolean wantsTokenInBody(HttpServletRequest request) {
        return "body".equalsIgnoreCase(request.getHeader(BODY_DELIVERY_HEADER));
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
