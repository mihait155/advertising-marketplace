package com.advertisingmarketplace.common.money;

import com.advertisingmarketplace.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AmountPrecisionException extends ApiException {

    public AmountPrecisionException(String currency, int fractionDigits) {
        super(HttpStatus.BAD_REQUEST, "Amounts in " + currency + " can have at most " + fractionDigits + " decimals");
    }
}
