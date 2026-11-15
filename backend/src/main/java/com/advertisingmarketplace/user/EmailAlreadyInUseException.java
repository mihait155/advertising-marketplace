package com.advertisingmarketplace.user;

import com.advertisingmarketplace.common.exception.ConflictException;

public class EmailAlreadyInUseException extends ConflictException {

    public EmailAlreadyInUseException(String email) {
        super("Email " + email + " is already in use");
    }
}
