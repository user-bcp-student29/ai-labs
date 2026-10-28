package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {
    private static final String TEST_SIGNING_KEY = "isolated-test-signing-key";
    private static final TokenService TOKEN_SERVICE = new TokenService(TEST_SIGNING_KEY);

    @Test
    void verifiesIssuedToken() {
        String token = TOKEN_SERVICE.create("student", "STUDENT");

        assertTrue(TOKEN_SERVICE.verify(token));
    }

    @Test
    void rejectsTokenWithModifiedPayload() {
        String token = TOKEN_SERVICE.create("student", "STUDENT");
        String[] parts = token.split("\\.", -1);
        parts[0] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("attacker".getBytes(StandardCharsets.UTF_8));

        assertFalse(TOKEN_SERVICE.verify(String.join(".", parts)));
    }

    @Test
    void rejectsTokenWithModifiedSignature() {
        String token = TOKEN_SERVICE.create("student", "STUDENT");
        String[] parts = token.split("\\.", -1);
        parts[3] = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{1, 2, 3});

        assertFalse(TOKEN_SERVICE.verify(String.join(".", parts)));
    }

    @Test
    void rejectsMalformedSignatureEncoding() {
        String token = TOKEN_SERVICE.create("student", "STUDENT");
        String[] parts = token.split("\\.", -1);
        parts[3] = "!";

        assertFalse(TOKEN_SERVICE.verify(String.join(".", parts)));
    }

    @Test
    void rejectsExpiredToken() {
        long expiredAt = Instant.now().minusSeconds(1).getEpochSecond();
        String token = TOKEN_SERVICE.create("student", "STUDENT", expiredAt);

        assertFalse(TOKEN_SERVICE.verify(token));
    }

    @Test
    void rejectsMalformedToken() {
        assertFalse(TOKEN_SERVICE.verify("malformed"));
    }

    @Test
    void rejectsTokenWithMalformedExpiry() {
        assertFalse(TOKEN_SERVICE.verify(signPayload("..not-a-number")));
    }

    @Test
    void rejectsMalformedPayloadEncoding() {
        long futureExpiry = Instant.now().plusSeconds(60).getEpochSecond();

        assertFalse(TOKEN_SERVICE.verify(signPayload("!.!." + futureExpiry)));
    }

    @Test
    void rejectsNullToken() {
        assertFalse(TOKEN_SERVICE.verify(null));
    }

    @Test
    void requiresConfiguredSigningKey() {
        assertThrows(IllegalStateException.class, () -> new TokenService(" "));
    }

    @Test
    void rejectsMissingSigningKey() {
        assertThrows(IllegalStateException.class, () -> new TokenService(null));
    }

    private String signPayload(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException error) {
            throw new AssertionError(error);
        }
    }
}
