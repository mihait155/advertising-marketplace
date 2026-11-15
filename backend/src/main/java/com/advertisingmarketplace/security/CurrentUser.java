package com.advertisingmarketplace.security;

import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;

@UtilityClass
public class CurrentUser {

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
