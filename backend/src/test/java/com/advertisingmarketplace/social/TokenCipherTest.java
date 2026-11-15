package com.advertisingmarketplace.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static TokenCipher cipher(String key) {
        return new TokenCipher(new SocialProperties(null, key, null, null));
    }

    @Test
    void roundTripsAndRandomisesEachCiphertext() {
        TokenCipher cipher = cipher(KEY);

        String first = cipher.encrypt("refresh-token-123");
        String second = cipher.encrypt("refresh-token-123");

        assertThat(first).startsWith("v1:").isNotEqualTo(second).doesNotContain("refresh-token");
        assertThat(cipher.decrypt(first)).isEqualTo("refresh-token-123");
        assertThat(cipher.decrypt(second)).isEqualTo("refresh-token-123");
    }

    @Test
    void detectsTamperingAndWrongKeys() {
        String encrypted = cipher(KEY).encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(encrypted.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;

        assertThatThrownBy(() -> cipher(KEY).decrypt(tampered)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher(Base64.getEncoder().encodeToString(otherKey)).decrypt(encrypted))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher(KEY).decrypt("plain")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsKeysOfTheWrongLength() {
        assertThatThrownBy(() -> cipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
