# Plan de corrección — auth
## Aprobacion Humana
robert Wong
26/10/26
H-AUTH-01, H-AUTH-03, H-AUTH-04, H-AUTH-05 y H-AUTH-07
## Estado y selección

Alcance aprobado para implementación por la solicitud explícita del usuario del 2026-10-26. Como el pedido no especifica IDs, se incluyen los hallazgos `auth` que estaban `Pendiente` en `HALLAZGOS.md`, priorizados por severidad. H-AUTH-07 es una revisión de dependencias: cualquier actualización debe apoyarse en evidencia de advisory/lifecycle y confirmación de la versión resuelta.

**Decisión humana:** la solicitud explícita del usuario del 2026-10-26 autoriza implementar el alcance de este plan, hallazgo por hallazgo. Se inicia por H-AUTH-01. Las decisiones señaladas en H-AUTH-03, H-AUTH-04 y H-AUTH-05 siguen siendo condiciones previas para esos cambios; H-AUTH-07 se cerró tras revisión de advisory/lifecycle y actualización de versiones.

La sección auth de `docs/ENDPOINTS.md` documenta el comportamiento observado de `POST /auth/login`; no se ha registrado aprobación humana de ese contrato. Incluye `400` para request/campos requeridos ausentes, `401` para credenciales inválidas y `200` con `token`, `expiresIn` y `user`. La respuesta exitosa debe conservar su forma y los códigos HTTP. Los cambios en mensajes/campos de error, formato de token y política CORS requieren resolver las decisiones anotadas en sus apartados antes de implementar.

## Secuencia de pruebas

1. Antes de cambiar código de producción, ejecutar la línea base `mvn test` desde `auth/`; se ejecutó con 1 test y 0 fallos. No se creó `docs/TESTING.md`, conforme al alcance de documentación de este paso.
2. Añadir primero las pruebas de caracterización indicadas para el hallazgo seleccionado. Las pruebas nuevas que expresen el comportamiento corregido pueden fallar contra la implementación actual; registrar esa línea base sin presentar el fallo como prueba pasada.
3. Implementar únicamente después de revisar el plan y resolver los cambios de contrato pendientes. Ejecutar `mvn test` desde `auth/`; usar `mvn verify` para el informe JaCoCo cuando corresponda.

## H-AUTH-01 — Concatenación de credenciales en SQL

**Hallazgo:** `HALLAZGOS.md` registra la concatenación de credenciales en `LegacyDatabase` (severidad alta, Resuelto).

**Problema:** valores del request se incorporan al SQL como texto, lo que permite que caracteres de entrada cambien la interpretación de la consulta.

**Cambio propuesto:** sustituir `Statement` por `PreparedStatement`; usar placeholders para ambos valores y enlazarlos con `setString`. Mantener el resultado de autenticación y los códigos HTTP observados.

**Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y, si hace falta aislar persistencia, una prueba nueva `auth/src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java`.

**Prueba previa:** caracterizar login válido (`200`), credenciales inválidas (`401`) y campos requeridos (`400`). Añadir una fila ficticia de prueba con username que contenga un apóstrofo y comprobar que ese usuario válido puede autenticarse. Esta última regresión debe fallar con la consulta actual y pasar con parámetros enlazados.

**Ejecuciones (auth, H-AUTH-01):**

- `mvn test` antes de añadir la caracterización: 1 test, 0 fallos; línea base correcta.
- `mvn -Dtest=AuthResourceTest test` con caracterización y antes del cambio SQL: 4 tests, 1 fallo esperado; el username `o'connor-characterization` produjo error de sintaxis SQLite y HTTP `401` en vez de `200`.
- Primera ejecución tras cambiar SQL: compilación fallida porque faltaba importar `PreparedStatement`; se agregó el import y no se modificó el alcance.
- `mvn -Dtest=AuthResourceTest test` tras corregir el import: 4 tests, 0 fallos.
- `mvn verify`: 4 tests, 0 fallos; JaCoCo para `LegacyDatabase`: 94/100 instrucciones (94%) y 21/24 líneas (87.5%). Informe: `auth/target/site/jacoco/index.html`.
- Verificación final compartida con H-AUTH-02 y H-AUTH-06: `mvn verify`, 12 tests, 0 fallos; `LegacyDatabase` 59/65 líneas (90.8%).
- CodeQL y Dependency Review remotos: no ejecutados en este recorrido local.

## H-AUTH-02 — Contraseñas en texto plano

**Hallazgo:** `HALLAZGOS.md` registra almacenamiento y comparación de contraseñas sin hash (severidad alta, Resuelto).

**Problema:** quien obtenga una copia de SQLite puede leer las contraseñas almacenadas; el seed también persiste el valor directamente.

**Cambio propuesto:** incorporar un servicio pequeño de hashing lento con salt y formato versionado usando una API criptográfica del JDK (por ejemplo `PBKDF2WithHmacSHA256`), con parámetros en constantes nombradas. No agregar una dependencia si la API del JDK satisface el requisito. Migrar filas existentes de texto plano de forma transaccional y comprobable; conservar el login de las cuentas ficticias y no registrar contraseñas ni hashes en logs.

**Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; nueva `auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java`; nuevas pruebas `auth/src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java` y/o `LegacyDatabaseTest.java`; `AuthResourceTest.java` para el contrato HTTP.

**Prueba previa:** registrar que el login válido existente sigue respondiendo `200` y que uno inválido responde `401`. Antes de modificar producción, añadir pruebas para sal distinto por hash, aceptación de contraseña correcta, rechazo de contraseña incorrecta y migración de una base temporal con una fila antigua en texto plano; las comprobaciones de hash/migración deben fallar hasta implementar el cambio.

**Ejecuciones (auth, H-AUTH-02):**

- `mvn -Dtest=AuthResourceTest,PasswordHasherTest test` con pruebas de almacenamiento/migración y antes del cambio de producción: 6 tests, 2 fallos esperados; el seed seguía en texto plano y `initialize()` no migraba la fila heredada.
- `mvn -Dtest=PasswordHasherTest test` antes de crear `PasswordHasher`: compilación fallida por la clase ausente, como caracterización previa.
- `mvn -Dtest=PasswordHasherTest test` después de crear el hasher: 2 tests, 0 fallos; salts distintos, verificación correcta e invalidación de plaintext/formato mal formado.
- Primera suite combinada tras integrar hashing/migración: 8 tests, 1 fallo porque el fixture de H-AUTH-01 insertaba una contraseña en claro; se corrigió el fixture para insertar hash.
- Suite combinada tras corregir fixtures y añadir recreación del seed: 9 tests, 0 fallos.
- Verificación final compartida con H-AUTH-01/H-AUTH-06: `mvn verify`, 12 tests, 0 fallos. JaCoCo: `LegacyDatabase` 59/65 líneas (90.8%), `PasswordHasher` 29/34 (85.3%). Informe: `auth/target/site/jacoco/index.html`.

## H-AUTH-03 — Token Base64 sin firma

**Hallazgo:** `HALLAZGOS.md` registra token codificado en Base64 sin firma verificable en su creación (severidad alta, Pendiente).

**Problema:** la codificación no aporta integridad ni autenticidad; un valor de expiración dentro del texto no acredita por sí mismo que el token sea válido o vigente.

**Cambio propuesto:** reemplazar el formato casero por un token firmado con expiración y claims explícitos, al menos sujeto/username, rol y `exp`. Preferir una biblioteca JOSE/JWT mantenida compatible con Quarkus a implementar criptografía propia. Justificar y revisar cualquier dependencia antes de añadirla; usar secreto/clave de configuración externa según H-AUTH-04. Mantener el campo de respuesta `token` y `expiresIn` observado.

**Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`; posiblemente `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`, `auth/src/main/resources/application.properties` y `auth/pom.xml` si la biblioteca elegida se justifica; pruebas nuevas `auth/src/test/java/com/bcp/workshop/auth/LegacyTokenUtilTest.java` y `AuthResourceTest.java`.

**Prueba previa:** caracterizar que login exitoso conserva el status y los campos `token`, `expiresIn` y `user`. Añadir pruebas que validen firma y claims con clave ficticia de test, rechacen token alterado y comprueben expiración. Antes de cambiar el formato, coordinar compatibilidad: el código de otros componentes está fuera del alcance y no se verificó si consumen el formato actual.

## H-AUTH-04 — Secreto embebido y CORS amplio

**Hallazgo:** `HALLAZGOS.md` registra secreto estático y origen CORS configurado con expresión amplia (severidad media, Pendiente).

**Problema:** el secreto está en el código y la política actual no limita los orígenes web que pueden leer respuestas CORS.

**Cambio propuesto:** cargar el secreto desde configuración externa sin valor real por defecto en producción; permitir un valor ficticio aislado en el perfil de pruebas. Sustituir el patrón amplio de CORS por una lista allowlist configurable. Acordar el origen local/de despliegue permitido antes de fijar valores; no leer ni modificar otros componentes para inferirlos.

**Archivos a tocar:** `auth/src/main/resources/application.properties`; `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` o la clase de configuración de token que se establezca; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y configuración de tests solo si es necesaria.

**Prueba previa:** registrar el comportamiento CORS actual y la forma de respuesta de login. Después de acordar la allowlist, probar preflight/origen permitido y origen no permitido, además de verificar que los tests usan solo un secreto ficticio de entorno. El cambio de política CORS necesita decisión previa: `docs/ENDPOINTS.md` describe el comportamiento observado, pero no acuerda el origen permitido.

## H-AUTH-05 — Detalles internos en errores

**Hallazgo:** `HALLAZGOS.md` registra exposición de mensaje/clase de excepción y reflexión del username en error de credenciales (severidad media, Pendiente).

**Problema:** las respuestas pueden revelar detalles internos o reflejar entrada del cliente, facilitando diagnóstico externo del sistema.

**Cambio propuesto:** mantener los códigos HTTP observados y devolver mensajes genéricos; registrar detalles técnicos solo en logging del servidor, sin secretos ni contraseñas. El error de credenciales no debe incorporar el username. Antes de implementar, acordar el cuerpo de error: la respuesta actual observada incluye texto variable y, en algunos errores, el campo `exception`; eliminar esos datos cambia el comportamiento visible aunque se mantengan los status.

**Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`; posiblemente una clase dedicada de mapeo de errores si la separación lo requiere; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.

**Prueba previa:** caracterizar requests inválidos (`400`) y credenciales incorrectas (`401`), incluyendo status y estructura JSON actual. Añadir prueba de error interno controlado que exija mensaje genérico y ausencia de clase/mensaje de excepción en el cuerpo. No actualizar la expectativa del contrato observado hasta que se acuerde el nuevo cuerpo.

## H-AUTH-06 — Responsabilidades mezcladas en AuthResource

**Hallazgo:** `HALLAZGOS.md` registra validación, autenticación, acceso a datos, token y armado de respuesta en el resource (severidad baja, Resuelto).

**Problema:** el acoplamiento y el anidamiento dificultan probar reglas y mantener respuestas sin mezclar responsabilidades.

**Cambio propuesto:** seguir las capas de `docs/GOOD_PRACTICES.md`: `AuthResource` para HTTP, un `AuthService` para orquestar reglas y `LegacyDatabase` como repositorio. Usar retornos tempranos/validación clara, evitar duplicación y mantener cada método con una responsabilidad. Mantener el DTO de respuesta independiente de `UserRecord` y preservar los campos observados.

**Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`; nueva `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`; posiblemente nuevo DTO de respuesta en `auth/src/main/java/com/bcp/workshop/auth/`; pruebas `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y pruebas unitarias del servicio.

**Prueba previa:** ampliar la caracterización HTTP para éxito, request/campos requeridos e inválido, verificando status, estructura y campos de la respuesta. Ejecutar esa suite antes y después de la extracción para detectar cambios de contrato.

**Ejecuciones (auth, H-AUTH-06):**

- Caracterización HTTP antes de cablear el refactor: `mvn -Dtest=AuthResourceTest test`, 7 tests, 0 fallos; incluye `200`, campos de respuesta, `400` y `401`.
- Caracterización después de extraer `AuthService`/DTO y refactorizar `AuthResource`: 7 tests, 0 fallos.
- Se añadieron pruebas directas para request nulo, campos vacíos y ambas ramas del manejo de errores existente; la respuesta de error se verificó sin cambiarla.
- Verificación final compartida con H-AUTH-01/H-AUTH-02: `mvn verify`, 12 tests, 0 fallos. JaCoCo: `AuthResource` 16/16 líneas (100%), `AuthService` 10/10 (100%), `LoginResponse` y DTO anidado 100%.

## H-AUTH-07 — Versiones candidatas a revisión SCA

**Hallazgo:** `HALLAZGOS.md` registró Quarkus `3.27.4` y Commons Text `1.9` declarados. La revisión posterior encontró el advisory CVE-2022-42889 para Apache Commons Text en versiones `>=1.5.0` y `<1.10.0`; Apache describe impacto ligado al uso de `StringSubstitutor` con entrada no confiable. En `auth`, el uso encontrado es `StringEscapeUtils`, no `StringSubstitutor`. La matriz oficial de Quarkus confirma que 3.27 LTS terminó soporte el 2026-09-24 y que 3.40 es LTS. Fuentes: [advisory Apache Commons Text](https://commons.apache.org/proper/commons-text/security.html), [NVD CVE-2022-42889](https://nvd.nist.gov/vuln/detail/CVE-2022-42889) y [matriz oficial de releases Quarkus](https://quarkus.io/releases/), consultadas el 2026-10-26.

**Problema:** Commons Text `1.9` estaba en el rango del CVE y Quarkus `3.27.4` estaba en una línea LTS cuyo soporte terminó. La aplicabilidad de CVE-2022-42889 al código inspeccionado no se presume: requiere el uso afectado de `StringSubstitutor` con entrada no confiable.

**Cambio realizado:** actualizar Quarkus a `3.40.1.1` dentro de la línea LTS `3.40` y Commons Text a `1.15.0`, fuera de los rangos/ventana señalados. Alinear `quarkus-junit5` al artifactId actual `quarkus-junit`. No se añadieron dependencias ni se redujeron quality gates.

**Archivos tocados:** `auth/pom.xml`.

**Ejecuciones (auth, H-AUTH-07):**

- `mvn -Dincludes=org.apache.commons:commons-text dependency:tree`: BUILD SUCCESS; versión resuelta `org.apache.commons:commons-text:1.15.0`.
- `mvn verify` después de actualizar dependencias y artifactId de test: Quarkus `3.40.1.1`, 12 tests, 0 fallos; JaCoCo generado.
- Cobertura JaCoCo por líneas de clases productivas modificadas: `AuthResource` 16/16 (100%), `AuthService` 10/10 (100%), `LegacyDatabase` 59/65 (90.8%) y `PasswordHasher` 29/34 (85.3%). Todas alcanzan la meta mínima de 85%.
- CodeQL y Dependency Review remotos no ejecutados. El cierre no afirma un resultado positivo de esos scanners ni sustituye un análisis completo del árbol de dependencias.

## Decisiones pendientes y límites

- La sección auth de `docs/ENDPOINTS.md` registra el comportamiento observado de login; su aprobación humana y las decisiones de token, CORS y errores siguen pendientes.
- H-AUTH-03 tiene aprobación de alcance, pero su formato de token queda pendiente de coordinar con consumidores, que no se inspeccionaron por el límite de componente.
- H-AUTH-04 queda pendiente de acordar el origen CORS permitido antes de fijar la allowlist.
- H-AUTH-05 queda pendiente de acordar el cuerpo genérico de error antes de cambiar las respuestas observadas.
- H-AUTH-07 resuelto con actualización sustentada en advisory/lifecycle y árbol local de dependencia; scanners remotos no ejecutados.
- H-AUTH-01, H-AUTH-02 y H-AUTH-06 se implementaron y verificaron localmente. CodeQL y Dependency Review remotos no se ejecutaron.
