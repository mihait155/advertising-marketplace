package com.advertisingmarketplace.image.storage;

import com.advertisingmarketplace.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class StorageException extends ApiException {

    public StorageException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "File storage is temporarily unavailable, please try again later",
                cause);
        addSuppressed(new IllegalStateException(message));
    }
}
