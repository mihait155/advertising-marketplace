package com.advertisingmarketplace.image.scan;

import com.advertisingmarketplace.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class VirusScanUnavailableException extends ApiException {

    public VirusScanUnavailableException(String reason, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "File scanning is temporarily unavailable, please try again later", cause);
        addSuppressed(new IllegalStateException(reason));
    }

    public VirusScanUnavailableException(String reason) {
        this(reason, null);
    }
}
