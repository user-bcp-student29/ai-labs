# Pruebas — auth

## Alcance y aislamiento

Esta evidencia corresponde solo al componente `auth` y a los endpoints de su sección en [ENDPOINTS.md](ENDPOINTS.md): `GET /q/health` y `POST /auth/login`. El contrato de `auth` allí descrito es observado, no aprobado humanamente.

La suite usa SQLite. `AuthResourceTest` toma `AUTH_DB_URL` y, si no se define, cae por defecto en `./data/workshop.db`; algunas pruebas migran todas las filas y eliminan/recrean `trainer`. Para evitar alterar la base persistente, cada ejecución debe definir `AUTH_DB_URL` con una base descartable bajo `target/` (por ejemplo, `jdbc:sqlite:./target/auth-tests.db`). No ejecutes estas pruebas sin una base descartable hasta resolver H-AUTH-09 (`HALLAZGOS.md`).

## Casos

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | Health sin autenticación | Camino feliz | `200`, `status=UP`, `checks=[]` |
| `GET /q/health` | `healthHasNoRegisteredChecks` | Borde | `200`; lista de checks vacía |
| `POST /auth/login` | Cuenta `student` con `client` | Camino feliz | `200`; token no nulo, `expiresIn=3600`, usuario y `debug` |
| `POST /auth/login` | Omitir `client` | Borde | `200`; campo opcional no impide autenticar |
| `POST /auth/login` | Username con apóstrofo | Borde / H-AUTH-01 | `200`; username se consulta como dato, no como SQL |
| `POST /auth/login` | Body ausente o JSON `null` | Error `400` | `{"message":"Request is null"}` |
| `POST /auth/login` | Username ausente o en blanco | Error `400` | `{"message":"Username is required"}` |
| `POST /auth/login` | Password ausente o en blanco | Error `400` | `{"message":"Password is required"}` |
| `POST /auth/login` | JSON malformado | Error `400` | Cuerpo vacío |
| `POST /auth/login` | Password incorrecta | Error `401` | Mensaje `Invalid user student or password` |
| `POST /auth/login` | Username inexistente | Error `401` | Mensaje refleja el username recibido |
| `POST /auth/login` | Excepción de servicio simulada | Error `500` | Mensaje y clase de excepción en JSON, como contrato observado |
| `GET /auth/login` | Método no admitido | Error `405` | Cuerpo vacío |
| `POST /auth/login` | `Content-Type: text/plain` | Error `415` | Cuerpo vacío |
| `POST /auth/login` | Migrar contraseña legada y autenticar | Camino feliz de migración / H-AUTH-02 | Contraseña almacenada como hash y login `200` |
| `POST /auth/login` | Recrear seed `trainer` ausente | Borde de persistencia / H-AUTH-02 | Seed almacenado como hash y login `200` |

**Health no saludable:** `auth` no declara health checks, por lo que `503` no es una respuesta actual comprobable aquí. Si se añade un check que pueda devolver `DOWN`, hay que documentar su cuerpo real y agregar su prueba; queda pendiente, no se inventa un ejemplo.

## Código de los tests HTTP

Los tests ejecutables completos, incluidos los casos de SQL y migración, están en [AuthResourceTest.java](../auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Los casos de hash están en [PasswordHasherTest.java](../auth/src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java). Estos métodos representan las aserciones HTTP del contrato:

```java
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
void logsInWithWorkshopUser() {
    given()
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
            .body("token", notNullValue());
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
```

La prueba de username con apóstrofo usa una fila de prueba insertada con `PreparedStatement`; las pruebas de migración y recreación de seed manipulan solo la DB temporal indicada arriba. Sus implementaciones están en el archivo de prueba enlazado.

## Comandos y resultados

Todas las pruebas se ejecutaron con cwd `auth/`, PowerShell y `AUTH_DB_URL=jdbc:sqlite:./target/auth-tests.db`. La variable aísla las pruebas de `auth/data/workshop.db`.

| comando | directorio | código de salida | resultado |
|---|---|---:|---|
| `mvn "-Dtest=AuthResourceTest" test` | `auth/` | 0 | 16 tests, 0 fallos, 0 errores en esa ejecución; luego se añadieron checks separados para health. |
| `mvn "-Dtest=AuthResourceTest#healthReportsUpWithoutAuthentication+healthHasNoRegisteredChecks" test` | `auth/` | 0 | 2 tests, 0 fallos; happy path y borde de health. |
| `mvn "-Dtest=AuthResourceTest#rejectsNullAndBlankLoginRequestsOverHttp+returnsInternalErrorResponseFromLoginEndpoint" test` | `auth/` | 0 | 2 tests, 0 fallos. |
| `mvn "-Dtest=AuthResourceTest#rejectsInvalidCredentials" test` | `auth/` | 0 | 1 test, 0 fallos; prueba password incorrecta y username inexistente. |
| `mvn "-Dtest=AuthResourceTest#rejectsUnsupportedLoginMethodAndContentType" test` | `auth/` | 0 | 1 test, 0 fallos; comprueba `405` y `415` con cuerpo vacío. |
| `mvn verify` | `auth/` | 0 | 19 tests, 0 fallos, 0 errores; construyó el JAR y generó JaCoCo. |

Comando reproducible en PowerShell:

```powershell
Set-Location auth
$env:AUTH_DB_URL='jdbc:sqlite:./target/auth-tests.db'
mvn verify
```

El primer intento de la suite enfocada se lanzó desde la raíz y terminó con código `1` porque Maven no encontró allí un `pom.xml`; al repetir el comando desde `auth/`, terminó correctamente. No hubo fallos de tests en las ejecuciones correctas.

## Cobertura

Método: JaCoCo `0.8.12`, cobertura de líneas ejecutables por clase, generada por `mvn verify`; CSV fuente: `auth/target/site/jacoco/jacoco.csv`. La métrica cubre código productivo, no los propios archivos de test.

| clase productiva | líneas cubiertas / totales | cobertura |
|---|---:|---:|
| `AuthResource` | 16/16 | 100% |
| `AuthService` | 10/10 | 100% |
| `LegacyDatabase` | 59/65 | 90.8% |
| `PasswordHasher` | 29/34 | 85.3% |

Las clases productivas tocadas superan la meta local del 85%; `PasswordHasher` queda más cerca del umbral. El reporte HTML está en `auth/target/site/jacoco/index.html`.

## Fallos, pendientes y pruebas propuestas

### Fallos observados

- La invocación inicial desde la raíz falló por cwd incorrecto (Maven no encontró `pom.xml`); repetirla desde `auth/` pasó. No se ocultó como fallo de test.
- `mvn quarkus:dev` terminó con `BUILD SUCCESS` pero el plugin omitió el goal por su configuración de build. Para observar HTTP se ejecutó el runner empaquetado con `mvn quarkus:build` y `java -jar target/quarkus-app/quarkus-run.jar`; el proceso se detuvo intencionalmente tras las solicitudes.

### Pendientes

- H-AUTH-09: la clase de tests conserva un fallback a `./data/workshop.db`; esta evidencia solo vale para los comandos que fijan `AUTH_DB_URL` a `target/auth-tests.db`. No ejecutar la suite sobre datos persistentes.
- H-AUTH-08: probar el acceso desde fuera del contenedor cuando se corrija la clave de perfil del host. En la configuración actual el proceso observado escucha en `127.0.0.1`.
- `/q/health` no tiene checks y devuelve `UP` con lista vacía. No hay prueba para `503` porque ese estado no existe en el código/configuración actual; si se añade un check, agregar caso `DOWN` con su cuerpo observado.
- H-AUTH-03: la emisión firmada y el verificador con validación de expiración tienen pruebas unitarias. No existe un endpoint consumidor en `auth`, por lo que la verificación no se aplica a solicitudes entrantes. H-AUTH-04 sigue pendiente para CORS amplio; retirar el secreto embebido y usar `AUTH_TOKEN_SECRET` fue necesario para firmar tokens.

### Pruebas propuestas

- Una vez aislada la DB desde el propio fixture o configuración de tests, ejecutar `mvn verify` sin override ambiental y comprobar que no lee ni modifica `auth/data/workshop.db`.
- Para completar la validación del token en el flujo, identificar y aprobar el componente consumidor y el contrato de autorización antes de integrar `TokenService.verify`. Para H-AUTH-04/-05, acordar cambios y añadir pruebas de allowlist CORS y cuerpos genéricos de error.
- Tras corregir H-AUTH-08, ejecutar una prueba de integración bajo perfil `prod` desde un contenedor/red distinta y verificar que el servicio escucha en la interfaz esperada.
