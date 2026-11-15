package com.advertisingmarketplace.social;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TokenCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public TokenCipher(SocialProperties properties) {
        this.key = loadKey(properties.tokenEncryptionKey());
    }

    static SecretKey loadKey(String configured) {
        if (configured == null || configured.isBlank()) {
            log.warn("app.social.token-encryption-key (SOCIAL_TOKEN_ENCRYPTION_KEY) is not set: using a random key. "
                    + "Connected accounts will need to reconnect after a restart.");
            byte[] random = new byte[32];
            RANDOM.nextBytes(random);
            return new SecretKeySpec(random, "AES");
        }
        byte[] bytes = Base64.getDecoder().decode(configured.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException("app.social.token-encryption-key must be 32 bytes, base64-encoded");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        if (!encrypted.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unknown token format");
        }
        try {
            byte[] data = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Token could not be decrypted", e);
        }
    }
}
