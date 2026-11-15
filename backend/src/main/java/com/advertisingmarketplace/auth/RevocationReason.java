package com.advertisingmarketplace.auth;

public enum RevocationReason {

    ROTATED,
    LOGOUT,
    LOGOUT_ALL,
    PASSWORD_CHANGED,
    REUSE_DETECTED
}
