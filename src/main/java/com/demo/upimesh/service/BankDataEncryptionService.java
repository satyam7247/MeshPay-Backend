package com.demo.upimesh.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class BankDataEncryptionService {
    private static final String PREFIX = "ENC:v1:";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public BankDataEncryptionService(
            @Value("${app.bank-data.encryption-key:}") String encodedKey) {
        this.key = parseKey(encodedKey);
    }

    public String fingerprint(String value) {
        requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Bank data fingerprinting is unavailable.", error);
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String value) {
        requireConfigured();
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                            .put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Bank data encryption is unavailable.", error);
        }
    }

    public String decrypt(String value) {
        requireConfigured();
        if (!isEncrypted(value)) {
            throw new IllegalStateException("Stored bank account holder data is not encrypted.");
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value.substring(PREFIX.length()));
            if (bytes.length <= NONCE_LENGTH) {
                throw new IllegalArgumentException("Encrypted bank data is incomplete.");
            }
            ByteBuffer payload = ByteBuffer.wrap(bytes);
            byte[] nonce = new byte[NONCE_LENGTH];
            payload.get(nonce);
            byte[] encrypted = new byte[payload.remaining()];
            payload.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("Stored bank account holder data could not be decrypted.", error);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Bank data encryption key is not configured.");
        }
    }

    private static SecretKeySpec parseKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);
            return decoded.length == 32 ? new SecretKeySpec(decoded, "AES") : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
