package com.advertisingmarketplace.common.exception;

import java.time.Duration;
import java.util.Optional;
import lombok.Getter;
import org.springframework.http.HttpStatus;


public abstract class ApiException extends RuntimeException {

    @Getter
    private final HttpStatus status;

    public Optional<Duration> retryAfter() {
        return status == HttpStatus.SERVICE_UNAVAILABLE ? Optional.of(Duration.ofSeconds(30))
                : Optional.empty();
    }

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    protected ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
