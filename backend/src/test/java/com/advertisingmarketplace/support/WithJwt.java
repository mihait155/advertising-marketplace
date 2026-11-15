package com.advertisingmarketplace.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.advertisingmarketplace.user.UserType;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class WithJwt {

    private WithJwt() {
    }

    public static RequestPostProcessor as(UUID userId, UserType userType) {
        return jwt()
                .jwt(token -> token.subject(userId.toString()).claim("roles", java.util.List.of(userType.name())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + userType.name()));
    }
}
