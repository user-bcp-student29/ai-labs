// LoginService.java
package pe.bcp.login.service;

import java.util.Objects;
import java.util.Optional;

/**
 * Lógica de autenticación independiente del framework REST.
 *
 * El adaptador de credenciales debe traducir las fallas técnicas de la base
 * de datos a AuthenticationServiceUnavailableException; nunca debe convertirlas
 * en un resultado vacío. LoginResponse no expone el campo debug.
 */
public final class LoginService {

    private final CredentialVerifier credentialVerifier;
    private final TokenIssuer tokenIssuer;

    public LoginService(CredentialVerifier credentialVerifier, TokenIssuer tokenIssuer) {
        this.credentialVerifier = Objects.requireNonNull(credentialVerifier);
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
    }

    public LoginResponse login(LoginRequest request) {
        validate(request);

        final Optional<User> verifiedUser;
        try {
            verifiedUser = credentialVerifier.findByCredentials(
                    request.getUsername(), request.getPassword());
        } catch (AuthenticationServiceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthenticationOperationException(exception);
        }

        if (verifiedUser == null) {
            throw new AuthenticationOperationException(null);
        }
        if (!verifiedUser.isPresent()) {
            throw new InvalidCredentialsException();
        }

        User user = Objects.requireNonNull(
                verifiedUser.get(), "CredentialVerifier devolvió un usuario nulo.");

        try {
            IssuedToken issuedToken = Objects.requireNonNull(
                    tokenIssuer.issue(user), "TokenIssuer devolvió un token nulo.");

            if (issuedToken.getToken() == null || issuedToken.getToken().isEmpty()) {
                throw new IllegalStateException("El emisor devolvió un token vacío.");
            }

            return new LoginResponse(issuedToken.getToken(),
                    issuedToken.getExpiresIn(), user);
        } catch (Exception exception) {
            throw new AuthenticationOperationException(exception);
        }
    }

    /**
     * Traduce errores a cuerpos públicos fijos. El adaptador REST debe utilizar
     * este método en lugar de devolver mensajes o tipos de excepciones.
     */
    public static ApiError toPublicError(Exception exception) {
        if (exception instanceof InvalidLoginRequestException) {
            return new ApiError(400, "invalid_request", "Solicitud inválida.");
        }
        if (exception instanceof InvalidCredentialsException) {
            return new ApiError(401, "invalid_credentials", "Credenciales inválidas.");
        }
        if (exception instanceof AuthenticationServiceUnavailableException) {
            return new ApiError(503, "authentication_unavailable",
                    "El servicio de autenticación no está disponible temporalmente.");
        }
        return new ApiError(500, "internal_error", "No se pudo completar la solicitud.");
    }

    private static void validate(LoginRequest request) {
        if (request == null
                || isBlank(request.getUsername())
                || isBlank(request.getPassword())) {
            throw new InvalidLoginRequestException();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @FunctionalInterface
    public interface CredentialVerifier {
        /**
         * Devuelve Optional.empty() solo si las credenciales son incorrectas.
         * Las fallas de base de datos deben traducirse a
         * AuthenticationServiceUnavailableException.
         */
        Optional<User> findByCredentials(String username, String password)
                throws Exception;
    }

    @FunctionalInterface
    public interface TokenIssuer {
        IssuedToken issue(User user) throws Exception;
    }

    public static final class LoginRequest {
        private final String username;
        private final String password;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    public static final class LoginResponse {
        private final String token;
        private final long expiresIn;
        private final User user;

        private LoginResponse(String token, long expiresIn, User user) {
            this.token = token;
            this.expiresIn = expiresIn;
            this.user = user;
        }

        public String getToken() {
            return token;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public User getUser() {
            return user;
        }
    }

    public static final class User {
        private final String username;

        public User(String username) {
            this.username = Objects.requireNonNull(username);
        }

        public String getUsername() {
            return username;
        }
    }

    public static final class IssuedToken {
        private final String token;
        private final long expiresIn;

        public IssuedToken(String token, long expiresIn) {
            this.token = token;
            this.expiresIn = expiresIn;
        }

        public String getToken() {
            return token;
        }

        public long getExpiresIn() {
            return expiresIn;
        }
    }

    public static final class ApiError {
        private final int status;
        private final String error;
        private final String message;

        private ApiError(int status, String error, String message) {
            this.status = status;
            this.error = error;
            this.message = message;
        }

        public int getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class InvalidLoginRequestException extends RuntimeException {
        private InvalidLoginRequestException() {
            super("Invalid login request");
        }
    }

    public static final class InvalidCredentialsException extends RuntimeException {
        private InvalidCredentialsException() {
            super("Invalid credentials");
        }
    }

    public static final class AuthenticationServiceUnavailableException
            extends RuntimeException {
        public AuthenticationServiceUnavailableException(Throwable cause) {
            super("Authentication dependency unavailable", cause);
        }

        public AuthenticationServiceUnavailableException() {
            super("Authentication dependency unavailable");
        }
    }

    public static final class AuthenticationOperationException extends RuntimeException {
        private AuthenticationOperationException(Throwable cause) {
            super("Authentication operation failed", cause);
        }
    }
}