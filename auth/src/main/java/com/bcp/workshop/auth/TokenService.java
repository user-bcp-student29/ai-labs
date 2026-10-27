package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class TokenService {
    static final int TOKEN_EXPIRY_SECONDS = 3600;
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_SEPARATOR = ".";
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec signingKey;

    @Inject
    public TokenService(@ConfigProperty(name = "auth.token.secret") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Token signing secret is not configured");
        }
        signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SIGNATURE_ALGORITHM);
    }

    public String create(String username, String role) {
        long expiresAt = Instant.now().plusSeconds(TOKEN_EXPIRY_SECONDS).getEpochSecond();
        return create(username, role, expiresAt);
    }

    String create(String username, String role, long expiresAt) {
        String payload = String.join(
                TOKEN_SEPARATOR,
                encode(username),
                encode(role),
                Long.toString(expiresAt)
        );
        return payload + TOKEN_SEPARATOR + encode(sign(payload));
    }

    public boolean verify(String token) {
        if (token == null) {
            return false;
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }

        String payload = String.join(TOKEN_SEPARATOR, parts[0], parts[1], parts[2]);
        byte[] suppliedSignature;
        try {
            suppliedSignature = BASE64_DECODER.decode(parts[3]);
        } catch (IllegalArgumentException malformedToken) {
            return false;
        }

        if (!MessageDigest.isEqual(sign(payload), suppliedSignature)) {
            return false;
        }

        try {
            long expiresAt = Long.parseLong(parts[2]);
            if (expiresAt <= Instant.now().getEpochSecond()) {
                return false;
            }
            BASE64_DECODER.decode(parts[0]);
            BASE64_DECODER.decode(parts[1]);
        } catch (IllegalArgumentException malformedToken) {
            return false;
        }

        return true;
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Unable to sign authentication token", error);
        }
    }

    private String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return BASE64_ENCODER.encodeToString(value);
    }
}
