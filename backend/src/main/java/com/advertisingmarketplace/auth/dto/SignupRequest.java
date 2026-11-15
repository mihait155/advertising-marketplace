package com.advertisingmarketplace.auth.dto;

import com.advertisingmarketplace.common.validation.ValidPassword;
import com.advertisingmarketplace.user.UserType;
import com.advertisingmarketplace.user.dto.UserValidation;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = UserValidation.PHONE_REGEX, message = UserValidation.PHONE_MESSAGE) String phone,
        @NotNull UserType userType,
        @ValidPassword String password) {

    @AssertTrue(message = "userType must be MARKETER or CUSTOMER")
    public boolean isSelfRegistrableUserType() {
        return userType != UserType.ADMIN;
    }

    @Override
    public String toString() {
        return "SignupRequest[fullName=%s, email=%s, phone=%s, userType=%s, password=***]"
                .formatted(fullName, email, phone, userType);
    }
}
