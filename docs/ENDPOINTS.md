# Contratos de endpoints

## Auth

### Estado del contrato

Este documento describe el comportamiento observado de `auth`; no registra una aprobación humana de contrato. Las respuestas de abajo se comprobaron con el runner Quarkus 3.40.1.1 y una base SQLite aislada en `auth/target`. El código es la fuente para los errores internos y las preguntas abiertas se indican expresamente.

Base local observada: `http://127.0.0.1:8080`. El README anuncia `http://localhost:8080` para Compose, pero la configuración actual tiene `quarkus.http.host=127.0.0.1` y `prod.quarkus.http.host=0.0.0.0`; la segunda clave no usa el prefijo de perfil `%prod.`. En el runner con perfil `prod` se observó que escucha en `127.0.0.1`. Por tanto, la integración desde fuera del contenedor queda pendiente de corregir/verificar esa configuración (`auth/src/main/resources/application.properties:1-3`, `auth/Dockerfile:12-13`).

### `GET /q/health`

#### 1. Qué hace
Indica si la instancia de autenticación está activa para que un proceso de despliegue pueda comprobar su disponibilidad.

#### 2. Autenticación y headers
No requiere autenticación ni `Authorization`. No tiene request body. El método documentado por SmallRye Health es `GET`; durante la verificación local, `POST /q/health` también devolvió `200`, pero no debe usarse como contrato.

#### 3. Request
No hay campos de ruta, query ni body.

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| ninguno | — | — | La comprobación no recibe datos. |

Ejemplo de request:

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json
```

#### 4. Response exitosa
**Estado:** `200 OK` cuando el estado reportado es `UP`.

| campo | tipo | descripción |
|---|---|---|
| `status` | string | Estado global; en la configuración comprobada es `UP`. |
| `checks` | array | Lista de comprobaciones registradas; está vacía porque `auth` no declara checks propios. |

Ejemplo JSON observado:

```json
{
  "status": "UP",
  "checks": []
}
```

#### 5. Respuestas de error
`auth` no registra checks de health que puedan producir un estado `DOWN`; no se observó una respuesta HTTP de error para este endpoint. Si el proceso no está disponible, el cliente obtiene un error de conexión, no un status/body HTTP. Una respuesta `503` por health `DOWN` requeriría un check no presente en este componente; su cuerpo queda como pregunta abierta hasta que se añada uno.

#### 6. Notas de seguridad
El endpoint no está autenticado. La respuesta comprobada solo informa `UP` y una lista vacía; no incluye datos de usuario ni de la base. SmallRye Health procede de la dependencia `quarkus-smallrye-health` (`auth/pom.xml:38-41`).

### `POST /auth/login`

#### 1. Qué hace
Autentica una cuenta del workshop y, si las credenciales coinciden, devuelve la identidad básica y el token que el flujo actual emite.

#### 2. Autenticación y headers
No requiere un token previo ni header `Authorization`. Requiere `Content-Type: application/json`; la respuesta usa JSON. `Accept: application/json` puede enviarse para declarar el formato esperado.

#### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| `username` | string | sí | Nombre de usuario; debe contener al menos un carácter que no sea espacio en blanco. |
| `password` | string | sí | Contraseña; debe contener al menos un carácter que no sea espacio en blanco. |
| `client` | string | no | Campo aceptado en el request; el flujo de autenticación actual no lo usa. |

Ejemplo JSON completo para la cuenta ficticia incluida por el workshop:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

#### 4. Response exitosa
**Estado:** `200 OK` cuando las credenciales son válidas.

| campo | tipo | descripción |
|---|---|---|
| `token` | string | Valor generado por el servicio para esta respuesta. En el ejemplo se enmascara para no publicar un token emitido. |
| `expiresIn` | integer | Duración declarada en segundos; el servicio devuelve `3600`. |
| `user` | object | Datos de la cuenta autenticada. |
| `user.username` | string | Username persistido. |
| `user.displayName` | string | Nombre mostrado; el servicio lo escapa para HTML 4. |
| `user.role` | string | Rol persistido, por ejemplo `STUDENT`. |
| `debug` | string | Marcador actual del flujo legado; no usarlo como dato de autorización. |

Ejemplo JSON completo con el token temporal enmascarado:

```json
{
  "token": "<token emitido; valor omitido>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

El ejemplo se basa en una respuesta `200` observada; solo se sustituyó el valor del token por el marcador indicado.

#### 5. Respuestas de error

- **`400 Bad Request` — body ausente o JSON `null`:** el request deserializado es nulo.

  ```json
  {"message":"Request is null"}
  ```

- **`400 Bad Request` — username ausente, nulo o en blanco:**

  ```json
  {"message":"Username is required"}
  ```

- **`400 Bad Request` — password ausente, nula o en blanco:**

  ```json
  {"message":"Password is required"}
  ```

- **`400 Bad Request` — JSON malformado:** el caso `{"username":` devolvió cuerpo vacío durante la comprobación local.

  ```text
  (sin cuerpo)
  ```

- **`401 Unauthorized` — username inexistente o contraseña incorrecta:**

  ```json
  {"message":"Invalid user student or password"}
  ```

  El username de ese ejemplo corresponde a la request; el endpoint lo refleja en el mensaje.

- **`500 Internal Server Error` — una excepción escapa del servicio de autenticación:** el resource copia el mensaje y la clase de la excepción. La prueba HTTP instala un mock de `AuthService` que lanza una `IllegalStateException` y verifica esta respuesta.

  ```json
  {
    "message": "local test failure",
    "exception": "java.lang.IllegalStateException"
  }
  ```

- **`405 Method Not Allowed` — método GET en `/auth/login`:** comprobado localmente; respuesta sin cuerpo.

  ```text
  (sin cuerpo)
  ```

- **`415 Unsupported Media Type` — `Content-Type: text/plain`:** comprobado localmente; respuesta sin cuerpo.

  ```text
  (sin cuerpo)
  ```

#### 6. Notas de seguridad

- El `token` actual es Base64 de datos concatenados con un secreto estático; no es un token firmado ni una prueba verificable de identidad. El login incorpora una expiración al contenido y devuelve `expiresIn`, pero no existe aquí una validación de token/expiración. No asumir que el formato equivale a JWT ni que un header `Authorization: Bearer` es un contrato admitido por este servicio (`auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,12-15`).
- No se identificó en `auth` otro endpoint protegido por el token. El header exacto de autorización para consumidores externos es una pregunta abierta; no inferirlo de la respuesta de login.
- La respuesta `401` refleja el username y la respuesta `500` expone mensaje y clase de excepción. Son comportamientos observados, no una recomendación para nuevos clientes (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35-43`).
- CORS está habilitado con el patrón amplio `/.*/` (`auth/src/main/resources/application.properties:4-8`).

### Evidencia de código

- Request y validación/HTTP: `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-6`, `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:13-43`.
- Construcción de la respuesta: `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:9-27`, `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-4`.
- Extensión y puerto: `auth/pom.xml:38-41`, `auth/src/main/resources/application.properties:1-3`.
- Health: SmallRye Health expone `/q/health`; el ejemplo `UP` con `checks` vacío se observó en el runner local y coincide con [la guía oficial de Quarkus](https://quarkus.io/guides/smallrye-health).
