package com.advertisingmarketplace.auth.dto;

import com.advertisingmarketplace.user.dto.UserResponse;
import java.time.Instant;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserResponse user) {

    public static final String BEARER = "Bearer";

    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(tokenType, accessToken, accessTokenExpiresAt, null, refreshTokenExpiresAt, user);
    }

    @Override
    public String toString() {
        return "AuthResponse[tokenType=%s, accessToken=***, refreshToken=***, user=%s]".formatted(tokenType, user);
    }
}
