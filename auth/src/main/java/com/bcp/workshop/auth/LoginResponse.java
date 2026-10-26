package com.bcp.workshop.auth;

public record LoginResponse(String token, int expiresIn, AuthenticatedUser user, String debug) {
    public record AuthenticatedUser(String username, String displayName, String role) {}
}