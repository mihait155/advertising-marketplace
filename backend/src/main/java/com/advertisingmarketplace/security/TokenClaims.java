package com.advertisingmarketplace.security;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TokenClaims {

    public static final String ROLES = "roles";
    public static final String EMAIL = "email";
    public static final String TOKEN_TYPE = "typ";
    public static final String ACCESS_TOKEN_TYPE = "access";
}
