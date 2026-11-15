package com.advertisingmarketplace.common.money;

import com.advertisingmarketplace.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class NoExchangeRateException extends ApiException {

    public NoExchangeRateException(String currency) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "No exchange rate is available for " + currency + " yet");
    }
}
