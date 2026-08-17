package com.ddlexporter;

import com.ddlexporter.common.util.CryptoUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoUtilsTest {

    @Test
    public void testEncryptionAndDecryption() {
        String plainPassword = "MySecretDatabasePassword!2026";
        String encrypted = CryptoUtils.encrypt(plainPassword);

        assertNotNull(encrypted);
        assertTrue(CryptoUtils.isEncrypted(encrypted));
        assertTrue(encrypted.startsWith("ENC("));
        assertTrue(encrypted.endsWith(")"));
        assertNotEquals(plainPassword, encrypted);

        String decrypted = CryptoUtils.decrypt(encrypted);
        assertEquals(plainPassword, decrypted);
    }

    @Test
    public void testNullAndEmptyHandling() {
        assertNull(CryptoUtils.encrypt(null));
        assertNull(CryptoUtils.decrypt(null));
        assertEquals("", CryptoUtils.encrypt(""));
        assertEquals("", CryptoUtils.decrypt(""));
    }

    @Test
    public void testIdempotentEncryption() {
        String plain = "TestPass123";
        String enc1 = CryptoUtils.encrypt(plain);
        String enc2 = CryptoUtils.encrypt(enc1); // Double encrypt should not re-wrap

        assertEquals(enc1, enc2);
        assertEquals(plain, CryptoUtils.decrypt(enc2));
    }
}
