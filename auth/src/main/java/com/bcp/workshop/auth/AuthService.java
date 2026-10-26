package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

@ApplicationScoped
public class AuthService {
    private static final int TOKEN_EXPIRY_SECONDS = 3600;
    private static final String DEBUG_MARKER = "authenticated-with-legacy-flow";

    @Inject
    LegacyDatabase database;

    public LoginResponse login(String username, String password) {
        UserRecord user = database.findByCredentials(username, password);
        if (user == null) {
            return null;
        }

        String token = LegacyTokenUtil.create(user.username(), user.role());
        LoginResponse.AuthenticatedUser responseUser = new LoginResponse.AuthenticatedUser(
                user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()),
                user.role()
        );
        return new LoginResponse(token, TOKEN_EXPIRY_SECONDS, responseUser, DEBUG_MARKER);
    }
}