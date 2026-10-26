package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private static final String TEST_PASSWORD = "LocalHasherTest123!";
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesWithUniqueSaltsAndVerifiesCorrectPassword() {
        String firstHash = passwordHasher.hash(TEST_PASSWORD);
        String secondHash = passwordHasher.hash(TEST_PASSWORD);

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.isEncoded(firstHash));
        assertTrue(passwordHasher.matches(TEST_PASSWORD, firstHash));
        assertFalse(passwordHasher.matches("incorrect", firstHash));
    }

    @Test
    void rejectsPlaintextAndMalformedHashes() {
        assertFalse(passwordHasher.isEncoded(TEST_PASSWORD));
        assertFalse(passwordHasher.matches(TEST_PASSWORD, TEST_PASSWORD));
        assertFalse(passwordHasher.matches(TEST_PASSWORD, "pbkdf2-sha256$not-a-valid-hash"));
    }
}
