package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    private static final String TEST_USERNAME = "o'connor-characterization";
    private static final String TEST_PASSWORD = "LocalTest123!";
    private static final String TEST_DATABASE_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final String PASSWORD_HASH_PREFIX = "pbkdf2-sha256$";
    private static final String MIGRATION_USERNAME = "legacy-migration-characterization";

    @Inject
    LegacyDatabase database;

    @Inject
    TokenService tokenService;

    @Test
    void logsInWithWorkshopUser() {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("token", notNullValue())
                .extract().path("token");

        assertTrue(tokenService.verify(token));
    }

    @Test
    void healthReportsUpWithoutAuthentication() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void healthHasNoRegisteredChecks() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("checks.size()", is(0));
    }

    @Test
    void logsInWhenOptionalClientIsOmitted() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"));
    }

    @Test
    void logsInWithUsernameContainingApostrophe() throws SQLException {
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL)) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM legacy_users WHERE username = ?")) {
                delete.setString(1, TEST_USERNAME);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO legacy_users(username, password, display_name, role) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, TEST_USERNAME);
                insert.setString(2, new PasswordHasher().hash(TEST_PASSWORD));
                insert.setString(3, "Apostrophe Test User");
                insert.setString(4, "TEST");
                insert.executeUpdate();
            }
        }

        try {
            given()
                    .contentType("application/json")
                    .body("{\"username\":\"o'connor-characterization\",\"password\":\"LocalTest123!\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .body("user.username", is(TEST_USERNAME))
                    .body("token", notNullValue());
        } finally {
            try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
                 PreparedStatement delete = connection.prepareStatement("DELETE FROM legacy_users WHERE username = ?")) {
                delete.setString(1, TEST_USERNAME);
                delete.executeUpdate();
            }
        }
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrect\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));

            given()
                .contentType("application/json")
                .body("{\"username\":\"unknown-user\",\"password\":\"incorrect\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user unknown-user or password"));
    }

    @Test
    void requiresUsernameAndPassword() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"LocalTest123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsNullAndBlankLoginRequestsOverHttp() {
        given()
                .contentType("application/json")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));

        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));

            given()
                .contentType("application/json")
                .body("{\"username\":\" \",\"password\":\"LocalTest123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\" \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void returnsInternalErrorResponseFromLoginEndpoint() {
        QuarkusMock.installMockForType(new AuthService() {
            @Override
            public LoginResponse login(String username, String password) {
                throw new IllegalStateException("local test failure");
            }
        }, AuthService.class);

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .body("message", is("local test failure"))
                .body("exception", is(IllegalStateException.class.getName()));
    }

    @Test
    void returnsBadRequestForMalformedJson() {
        given()
                .contentType("application/json")
                .body("{\"username\":")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body(is(""));
    }

    @Test
    void rejectsUnsupportedLoginMethodAndContentType() {
        given()
                .when().get("/auth/login")
                .then()
            .statusCode(405)
            .body(is(""));

        given()
                .contentType("text/plain")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(415)
                .body(is(""));
    }

    @Test
    void returnsBadRequestForNullRequestAndBlankFields() {
        AuthResource resource = new AuthResource();
        assertEquals(400, resource.login(null).getStatus());

        LoginRequest blankUsername = new LoginRequest();
        blankUsername.username = " ";
        blankUsername.password = TEST_PASSWORD;
        assertEquals(400, resource.login(blankUsername).getStatus());

        LoginRequest blankPassword = new LoginRequest();
        blankPassword.username = "student";
        blankPassword.password = " ";
        assertEquals(400, resource.login(blankPassword).getStatus());
    }

    @Test
    void retainsInternalErrorResponseShape() {
        AuthResource resource = resourceFailingWith(new IllegalStateException("local test failure"));
        Response response = resource.login(validTestRequest());

        assertEquals(500, response.getStatus());
        assertEquals(Map.of(
                "message", "local test failure",
                "exception", IllegalStateException.class.getName()
        ), response.getEntity());
    }

    @Test
    void usesFallbackMessageWhenInternalErrorHasNoMessage() {
        AuthResource resource = resourceFailingWith(new IllegalStateException());
        Response response = resource.login(validTestRequest());

        assertEquals(500, response.getStatus());
        assertEquals("Unknown error", ((Map<?, ?>) response.getEntity()).get("message"));
    }

    @Test
    void storesWorkshopPasswordAsSaltedHash() throws SQLException {
        assertStoredPasswordIsHashed("student", "Workshop123!");
    }

    @Test
    void migratesLegacyPlaintextPasswordAndKeepsLoginWorking() throws SQLException {
        deleteUser(MIGRATION_USERNAME);
        insertUser(MIGRATION_USERNAME, TEST_PASSWORD);

        try {
            database.initialize();
            assertStoredPasswordIsHashed(MIGRATION_USERNAME, TEST_PASSWORD);

            given()
                    .contentType("application/json")
                    .body("{\"username\":\"legacy-migration-characterization\",\"password\":\"LocalTest123!\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .body("user.username", is(MIGRATION_USERNAME));
        } finally {
            deleteUser(MIGRATION_USERNAME);
        }
    }

    @Test
    void recreatesMissingSeedWithSaltedPassword() throws SQLException {
        deleteUser("trainer");

        try {
            database.initialize();
            assertStoredPasswordIsHashed("trainer", "Trainer123!");

            given()
                    .contentType("application/json")
                    .body("{\"username\":\"trainer\",\"password\":\"Trainer123!\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .body("user.username", is("trainer"));
        } finally {
            deleteUser("trainer");
            database.initialize();
        }
    }

    private void assertStoredPasswordIsHashed(String username, String plaintextPassword) throws SQLException {
        String sql = "SELECT password FROM legacy_users WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, username);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                String storedPassword = result.getString("password");
                assertNotEquals(plaintextPassword, storedPassword);
                assertTrue(storedPassword.startsWith(PASSWORD_HASH_PREFIX));
            }
        }
    }

    private void insertUser(String username, String password) throws SQLException {
        String sql = "INSERT INTO legacy_users(username, password, display_name, role) VALUES (?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setString(1, username);
            insert.setString(2, password);
            insert.setString(3, "Legacy Migration Test User");
            insert.setString(4, "TEST");
            insert.executeUpdate();
        }
    }

    private void deleteUser(String username) throws SQLException {
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement delete = connection.prepareStatement("DELETE FROM legacy_users WHERE username = ?")) {
            delete.setString(1, username);
            delete.executeUpdate();
        }
    }

    private AuthResource resourceFailingWith(RuntimeException failure) {
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public LoginResponse login(String username, String password) {
                throw failure;
            }
        };
        return resource;
    }

    private LoginRequest validTestRequest() {
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";
        return request;
    }
}
