# Hallazgos

## Contrato observado: auth

`docs/ENDPOINTS.md` documenta el comportamiento observado. Este resumen se deriva del código y ninguno de los dos constituye por sí solo un contrato aprobado humanamente.

| Operación | Comportamiento observado | Evidencia |
|---|---|---|
| `POST /auth/login` | Recibe JSON con `username` y `password`; `client` también está declarado en el request. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15,22-24`; `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-5` |
| Validación | Request nulo o username/password nulos o vacíos producen `400`. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:26-33` |
| Autenticación fallida | Si no se encuentra usuario, responde `401` con mensaje que incluye el username recibido. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35-37` |
| Autenticación exitosa | Responde `200` con token, `expiresIn`, usuario (username, displayName y role) y campo `debug`. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:39-50` |
| Persistencia y configuración | SQLite usa `AUTH_DB_URL` o `jdbc:sqlite:./data/workshop.db`; la inicialización crea `legacy_users` y carga usuarios de práctica. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:15-24` |

Contrato observado: documentado en `docs/ENDPOINTS.md`. El acuerdo y la confirmación humana siguen pendientes; la documentación del comportamiento no constituye aprobación.

## Matriz de seguimiento

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-01 | auth | Concatenación de credenciales en SQL | Vulnerabilidad | Los valores recibidos se insertaban en el texto SQL. Se corrigió con username parametrizado y verificación de password contra hash, conservando el resultado del login. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:98-103` | alta | Resuelto |
| H-AUTH-02 | auth | Contraseñas almacenadas y comparadas en texto plano | Vulnerabilidad | Los seeds usan PBKDF2-HMAC-SHA256 con salt aleatorio; las filas legadas se migran en transacción y el login compara hashes sin persistir la contraseña en claro. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:27-35,50-69,73-85,97-104`; `auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:10-75` | alta | Resuelto |
| H-AUTH-03 | auth | Token codificado en Base64 sin firma criptográfica en su creación | Vulnerabilidad | Base64 solo codifica los campos; el formato creado no demuestra integridad ni autenticidad del token. El valor de expiración se incorpora al contenido, pero no se valida en este flujo. | `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:12-15`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:21` | alta | Pendiente |
| H-AUTH-04 | auth | Secreto embebido y CORS permite orígenes mediante una expresión amplia | Vulnerabilidad | El secreto queda incluido en el código y la expresión de origen `/.*/` es amplia; esto expone el secreto y permite solicitudes desde orígenes web no restringidos. | `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8`; `auth/src/main/resources/application.properties:4-7` | media | Pendiente |
| H-AUTH-05 | auth | Respuesta de error expone detalles internos | Vulnerabilidad | En errores no controlados, la respuesta incluye el mensaje de excepción y su clase; el error de credenciales también refleja el username enviado. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:36,40-42` | media | Pendiente |
| H-AUTH-06 | auth | Resource mezcla validación, acceso a datos, token y armado de respuesta | Refactor | Se extrajo autenticación/orquestación a `AuthService` y la respuesta a DTOs; el resource conserva HTTP y usa guard clauses. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:20-47`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:8-27`; `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-4` | baja | Resuelto |
| H-AUTH-07 | auth | Dependencias con advisory/lifecycle por revisar | Dependencias | Commons Text `1.9` estaba dentro del rango afectado por CVE-2022-42889; Apache condiciona el impacto al uso de `StringSubstitutor` con entrada no confiable, mientras este componente usa `StringEscapeUtils`. Quarkus `3.27.4` había alcanzado el fin de soporte de su línea LTS. Se actualizaron a Commons Text `1.15.0` y Quarkus `3.40.1.1` (línea LTS 3.40); el cierre se basa en advisory/lifecycle y versión resuelta, no en un escaneo remoto. | `auth/pom.xml:13,49-50`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:5,24` | baja | Resuelto |
| H-AUTH-08 | auth | Clave de perfil de host de producción mal formada | Regresión funcional | La clave `prod.quarkus.http.host` no usa la sintaxis de propiedad por perfil de Quarkus (`%prod.`), por lo que no reemplaza el host base `127.0.0.1`; la aplicación puede quedar inaccesible desde fuera del contenedor en producción. | `auth/src/main/resources/application.properties:2-3` | alta | Pendiente |
| H-AUTH-09 | auth | Pruebas usan y mutan la base persistente de la aplicación | Riesgo de pruebas/datos | La URL de pruebas hereda `AUTH_DB_URL` y, si no está definida, apunta a `./data/workshop.db`. Las pruebas invocan `initialize()` (que migra todas las contraseñas) y borran/recrean `trainer`, por lo que pueden alterar datos compartidos o reales. | `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:26,154-191`; `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:19,30,50` | alta | Pendiente |

### Resoluciones

- **H-AUTH-01:** username usa `PreparedStatement`; la password se verifica contra el hash almacenado. La prueba local autentica correctamente un username ficticio con apóstrofo.
- **H-AUTH-02:** incorporado hash PBKDF2 con salt aleatorio, migración transaccional de filas heredadas y seeds almacenados como hash; verificado con pruebas locales de almacenamiento, migración y login.
- **H-AUTH-06:** `AuthResource` delega autenticación a `AuthService` y responde con DTOs; las pruebas caracterizan los campos y códigos HTTP observados.
- **H-AUTH-07:** Commons Text se actualizó a `1.15.0` y Quarkus a `3.40.1.1` en la línea LTS 3.40 después de revisar el advisory de Commons Text y el lifecycle de Quarkus. `mvn dependency:tree` confirmó `commons-text:1.15.0`; `mvn verify` pasó con 12 tests. No se ejecutaron scanners remotos.

## Límites de esta revisión

- Suite local de auth: 12 tests, 0 fallos en la última ejecución de `mvn verify`; cobertura JaCoCo registrada en `PLAN.md`.
- Se revisó el advisory de Apache Commons Text para CVE-2022-42889 y la información oficial de lifecycle de Quarkus; H-AUTH-07 se cerró tras actualizar las versiones. Esta revisión no equivale a un análisis completo del árbol con scanner.
- No se ejecutaron CodeQL ni Dependency Review remotos.
- El contrato observado está documentado en la sección auth de `docs/ENDPOINTS.md`, pero sigue pendiente de aprobación humana; no se modificaron los cuerpos actuales de error, el formato del token ni CORS.
