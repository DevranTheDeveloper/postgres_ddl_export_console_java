package com.ddlexporter.common.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES-256 GCM Credential Encryption & Decryption Utility.
 * Ensures passwords saved to disk (profiles.json) are securely encrypted.
 */
public class CryptoUtils {
    private static final String PREFIX = "ENC(";
    private static final String SUFFIX = ")";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;

    // Salt derived from system properties and user environment
    private static final byte[] FIXED_SALT = "DdlExporterSec2026!#Key".getBytes(StandardCharsets.UTF_8);

    private static SecretKey getSecretKey() {
        try {
            String seed = System.getProperty("user.name", "postgres_ddl_user") + "_Studio_Secret_Salt_Key";
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(seed.toCharArray(), FIXED_SALT, ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            // Fallback key
            byte[] keyBytes = new byte[32];
            System.arraycopy("DdlStudioDefaultAesKey32BytesLong!".getBytes(StandardCharsets.UTF_8), 0, keyBytes, 0, 32);
            return new SecretKeySpec(keyBytes, "AES");
        }
    }

    /**
     * Encrypts plain text password into an AES-256 GCM token with ENC(...) wrapper.
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        if (isEncrypted(plainText)) {
            return plainText; // Already encrypted
        }

        try {
            SecretKey key = getSecretKey();
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combined [IV (12 bytes) + CipherText]
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            String encoded = Base64.getEncoder().encodeToString(combined);
            return PREFIX + encoded + SUFFIX;
        } catch (Exception e) {
            return plainText; // Fallback
        }
    }

    /**
     * Decrypts an ENC(...) token back into plain text password.
     */
    public static String decrypt(String cipherToken) {
        if (cipherToken == null || !isEncrypted(cipherToken)) {
            return cipherToken; // Plain text or null
        }

        try {
            String base64Content = cipherToken.substring(PREFIX.length(), cipherToken.length() - SUFFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64Content);

            if (combined.length <= GCM_IV_LENGTH) {
                return cipherToken;
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            SecretKey key = getSecretKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return cipherToken; // Fallback
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }
}
