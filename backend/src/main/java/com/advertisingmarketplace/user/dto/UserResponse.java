package com.advertisingmarketplace.user.dto;

import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserType;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        UserType userType,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getUserType(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
