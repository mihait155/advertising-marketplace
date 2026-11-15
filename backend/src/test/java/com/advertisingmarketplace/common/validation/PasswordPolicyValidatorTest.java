package com.advertisingmarketplace.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @ParameterizedTest
    @ValueSource(strings = {"abcdefg1", "Sup3r-secret", "pässwört1", "1234567a",
            "a1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void acceptsValidPasswords(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "abc1",
            "abcdefgh",
            "12345678",
            "a1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "1€€€€€€€€€€€€€€€€€€€€€€€€€€€€€"
    })
    void rejectsInvalidPasswords(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }
}
