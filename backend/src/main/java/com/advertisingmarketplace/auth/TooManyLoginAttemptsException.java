package com.advertisingmarketplace.auth;

import java.time.Duration;
import lombok.Getter;

public class TooManyLoginAttemptsException extends RuntimeException {

    @Getter
    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Too many failed login attempts. Try again later.");
        this.retryAfter = retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    }
}
