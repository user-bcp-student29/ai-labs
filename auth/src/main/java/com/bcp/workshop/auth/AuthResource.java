package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        if (request == null) {
            return Response.status(400).entity(Map.of("message", "Request is null")).build();
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return Response.status(400).entity(Map.of("message", "Username is required")).build();
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return Response.status(400).entity(Map.of("message", "Password is required")).build();
        }

        try {
            LoginResponse response = authService.login(request.username, request.password);
            if (response == null) {
                return Response.status(401).entity(Map.of("message", "Invalid user " + request.username + " or password")).build();
            }
            return Response.ok(response).build();
        } catch (Exception error) {
            return Response.status(500).entity(Map.of(
                    "message", error.getMessage() == null ? "Unknown error" : error.getMessage(),
                    "exception", error.getClass().getName()
            )).build();
        }
    }
}
