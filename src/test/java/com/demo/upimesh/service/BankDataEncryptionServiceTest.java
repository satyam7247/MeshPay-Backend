package com.demo.upimesh.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankDataEncryptionServiceTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsHolderNamesWithRandomizedAuthenticatedCiphertext() {
        BankDataEncryptionService encryption = new BankDataEncryptionService(KEY);

        String first = encryption.encrypt("Example Account Holder");
        String second = encryption.encrypt("Example Account Holder");

        assertTrue(encryption.isEncrypted(first));
        assertNotEquals(first, second);
        assertEquals("Example Account Holder", encryption.decrypt(first));
    }

    @Test
    void rejectsTamperedCiphertextAndInvalidKeys() {
        BankDataEncryptionService encryption = new BankDataEncryptionService(KEY);
        String encrypted = encryption.encrypt("Example Account Holder");
        char replacement = encrypted.charAt(encrypted.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 1) + replacement;

        assertThrows(IllegalStateException.class, () -> encryption.decrypt(tampered));
        assertFalse(new BankDataEncryptionService("not-base64").isConfigured());
        assertFalse(new BankDataEncryptionService(
                Base64.getEncoder().encodeToString(new byte[16])).isConfigured());
    }

    @Test void fingerprintIsStableAndDistinguishesFullAccountNumbers() {
        BankDataEncryptionService encryption = new BankDataEncryptionService(
                java.util.Base64.getEncoder().encodeToString(new byte[32]));
        String first = encryption.fingerprint("1234567890123456");
        assertEquals(first, encryption.fingerprint("1234567890123456"));
        assertEquals(64, first.length());
        assertNotEquals(first, encryption.fingerprint("999956"));
    }
}
