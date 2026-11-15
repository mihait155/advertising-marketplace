package com.advertisingmarketplace.posting;

import com.advertisingmarketplace.user.UserType;

public enum PostingCategory {

    MARKETER_POSTING,
    CUSTOMER_POSTING;

    public boolean canBeCreatedBy(UserType userType) {
        return switch (userType) {
            case ADMIN -> true;
            case MARKETER -> this == MARKETER_POSTING;
            case CUSTOMER -> this == CUSTOMER_POSTING;
        };
    }

    public UserType offerMakerType() {
        return this == MARKETER_POSTING ? UserType.CUSTOMER : UserType.MARKETER;
    }
}
