package com.advertisingmarketplace.auth;

import com.advertisingmarketplace.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class MissingCsrfHeaderException extends ApiException {

    public MissingCsrfHeaderException() {
        super(HttpStatus.FORBIDDEN, "Cookie-based requests must include the X-Requested-With header");
    }
}
