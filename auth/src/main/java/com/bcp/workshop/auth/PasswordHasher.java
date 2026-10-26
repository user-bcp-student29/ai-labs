package com.bcp.workshop.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

final class PasswordHasher {
    private static final String FORMAT_VERSION = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATION_COUNT = 600_000;
    private static final int MAX_ACCEPTED_ITERATIONS = 1_000_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BITS = 256;
    private static final int HASH_LENGTH_BYTES = HASH_LENGTH_BITS / Byte.SIZE;
    private static final String DELIMITER = "$";

    private final SecureRandom secureRandom = new SecureRandom();

    String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derivedKey = deriveKey(password, salt, ITERATION_COUNT);
        return FORMAT_VERSION + DELIMITER + ITERATION_COUNT + DELIMITER
                + Base64.getEncoder().encodeToString(salt) + DELIMITER
                + Base64.getEncoder().encodeToString(derivedKey);
    }

    boolean matches(String password, String encodedPassword) {
        if (password == null || !isEncoded(encodedPassword)) {
            return false;
        }

        String[] parts = encodedPassword.split("\\$", -1);
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expectedKey = Base64.getDecoder().decode(parts[3]);
        byte[] actualKey = deriveKey(password, salt, iterations);
        return MessageDigest.isEqual(expectedKey, actualKey);
    }

    boolean isEncoded(String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }

        String[] parts = encodedPassword.split("\\$", -1);
        if (parts.length != 4 || !FORMAT_VERSION.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] derivedKey = Base64.getDecoder().decode(parts[3]);
            return iterations > 0 && iterations <= MAX_ACCEPTED_ITERATIONS
                    && salt.length >= SALT_LENGTH_BYTES
                    && derivedKey.length == HASH_LENGTH_BYTES;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private byte[] deriveKey(String password, byte[] salt, int iterations) {
        char[] passwordCharacters = password.toCharArray();
        PBEKeySpec keySpec = new PBEKeySpec(passwordCharacters, salt, iterations, HASH_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            keySpec.clearPassword();
            java.util.Arrays.fill(passwordCharacters, '\0');
        }
    }
}
