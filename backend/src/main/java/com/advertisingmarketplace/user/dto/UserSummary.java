package com.advertisingmarketplace.user.dto;

import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserType;
import java.util.UUID;

public record UserSummary(UUID id, String fullName, UserType userType) {

    public static final String DELETED_USER_NAME = "Deleted user";

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getUserType());
    }

    public static UserSummary fromNullable(User user) {
        return user == null ? new UserSummary(null, DELETED_USER_NAME, null) : from(user);
    }
}
