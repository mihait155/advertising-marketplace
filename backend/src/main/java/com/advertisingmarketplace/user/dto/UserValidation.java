package com.advertisingmarketplace.user.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UserValidation {

    public static final String PHONE_REGEX = "^\\+?[0-9 ().-]{7,20}$";
    public static final String PHONE_MESSAGE = "must be a valid phone number";
}
