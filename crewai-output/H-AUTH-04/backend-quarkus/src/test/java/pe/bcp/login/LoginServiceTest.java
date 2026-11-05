// LoginServiceTest.java
package pe.bcp.login.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginServiceTest {

    @Test
    void validCredentialsReturnTokenAndUsernameWithoutDebugField() {
        LoginService service = new LoginService(
                (username, password) -> {
                    assertEquals("student", username);
                    assertEquals("Workshop123!", password);
                    return Optional.of(new LoginService.User(username));
                },
                user -> new LoginService.IssuedToken("test-token", 3600));

        LoginService.LoginResponse response = service.login(
                new LoginService.LoginRequest("student", "Workshop123!"));

        assertEquals("test-token", response.getToken());
        assertEquals(3600, response.getExpiresIn());
        assertEquals("student", response.getUser().getUsername());
        assertFalse(hasDebugGetter(response.getClass()));
    }

    @Test
    void invalidCredentialsDoNotReflectUsername() {
        LoginService service = new LoginService(
                (username, password) -> Optional.empty(),
                user -> new LoginService.IssuedToken("unused", 3600));

        LoginService.InvalidCredentialsException exception = assertThrows(
                LoginService.InvalidCredentialsException.class,
                () -> service.login(new LoginService.LoginRequest(
                        "private-user-value", "wrong-password")));

        LoginService.ApiError error = LoginService.toPublicError(exception);
        assertEquals(401, error.getStatus());
        assertFalse(error.getMessage().contains("private-user-value"));
        assertFalse(error.getError().contains("private-user-value"));
    }

    @Test
    void databaseFailureMapsTo503RatherThanInvalidCredentials() {
        LoginService service = new LoginService(
                (username, password) -> {
                    throw new LoginService.AuthenticationServiceUnavailableException(
                            new RuntimeException("internal database detail"));
                },
                user -> new LoginService.IssuedToken("unused", 3600));

        LoginService.AuthenticationServiceUnavailableException exception = assertThrows(
                LoginService.AuthenticationServiceUnavailableException.class,
                () -> service.login(new LoginService.LoginRequest("student", "password")));

        LoginService.ApiError error = LoginService.toPublicError(exception);
        assertEquals(503, error.getStatus());
        assertFalse(error.getMessage().contains("internal database detail"));
    }

    @Test
    void unexpectedFailureMapsToGeneric500() {
        LoginService service = new LoginService(
                (username, password) -> Optional.of(new LoginService.User(username)),
                user -> {
                    throw new IllegalStateException("internal exception detail");
                });

        LoginService.AuthenticationOperationException exception = assertThrows(
                LoginService.AuthenticationOperationException.class,
                () -> service.login(new LoginService.LoginRequest("student", "password")));

        LoginService.ApiError error = LoginService.toPublicError(exception);
        assertEquals(500, error.getStatus());
        assertEquals("internal_error", error.getError());
        assertFalse(error.getMessage().contains("IllegalStateException"));
        assertFalse(error.getMessage().contains("internal exception detail"));
    }

    private static boolean hasDebugGetter(Class<?> responseClass) {
        for (java.lang.reflect.Method method : responseClass.getMethods()) {
            if ("getDebug".equals(method.getName())) {
                return true;
            }
        }
        return false;
    }
}