# Hallazgos

## Contrato observado (no acordado)

`docs/ENDPOINTS.md` existe y describe los endpoints de `api/`, pero no se ha validado con consumidores. Lo siguiente combina el contrato documentado con el comportamiento observado:

- Todas las rutas dependen de `legacy_identity`. Lee el header `Authorization`, decodifica Base64 y espera `username|role|expiry|secret`; compara el secreto fijo y la expiracion. Los errores se devuelven como HTTP 401. Evidencia: api/app/main.py:17-26.
- `GET /api/tasks` admite `owner` opcional y devuelve una lista con `id`, `title`, `description`, `status`, `owner`, `priority` y `createdAt`. Evidencia: api/app/main.py:56-70.
- `POST /api/tasks` acepta los campos de tarea como objeto JSON y responde HTTP 201 con la tarea creada. Evidencia: api/app/main.py:77-88.
- `GET /api/summary` admite `owner` opcional y devuelve `owner`, `total`, `byStatus` y `highPriorityOpen`. Evidencia: api/app/main.py:90-106.
- `POST /api/requirements` recibe un archivo multipart, lo guarda y responde HTTP 201 con `owner`, `fileName`, `storedAt` y `size`. Evidencia: api/app/main.py:107-112.
- `GET /api/backend-health` consulta `AUTH_HEALTH_URL` (por defecto `http://localhost:8080/q/health`) y devuelve `requestedBy`, `status` y `body`. Evidencia: api/app/main.py:114-121.
- La persistencia observada es SQLite: `WORKSHOP_DB_PATH` o `./data/workshop.db`; `create_app` crea la tabla `tasks` y carga filas iniciales si esta vacia. Evidencia: api/app/main.py:29-42.

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-01 | api | Autorizacion por owner controlable por el cliente | Vulnerabilidad | `GET /api/tasks` y `GET /api/summary` usan el owner enviado como query y `POST /api/tasks` permite elegirlo en el cuerpo; una identidad autenticada puede consultar o crear tareas para otro owner. | api/app/main.py:68-70; api/app/main.py:83; api/app/main.py:91-93 | Alta | Pendiente |
| H-API-02 | api | Consultas SQL construidas por concatenacion | Vulnerabilidad | Valores de query y del cuerpo se concatenan en SQL. Una entrada con caracteres SQL puede alterar filtros o instrucciones de consulta/insercion. | api/app/main.py:70; api/app/main.py:84; api/app/main.py:93 | Alta | Pendiente |
| H-API-03 | api | Nombre de archivo subido se usa sin validacion visible | Vulnerabilidad | El nombre proporcionado por el cliente se une directamente al directorio y el contenido completo se lee antes de guardarlo; no se observa normalizacion, limite de extension ni limite de tamano, con riesgo de escritura fuera del directorio previsto o consumo excesivo de recursos. | api/app/main.py:108-110 | Alta | Pendiente |
| H-API-04 | api | Token Base64 con secreto fijo y sin firma | Vulnerabilidad | El token se decodifica como Base64 y se compara con un secreto embebido; solo se comprueba la estructura y la expiracion, sin firma criptografica ni validacion de emisor o audiencia. Base64 no aporta integridad al token. | api/app/main.py:14; api/app/main.py:17-25 | Alta | Pendiente |
| H-API-05 | api | Versiones de dependencias requieren verificacion SCA | Sugerencia | El archivo fija las versiones indicadas por el RETO. Su presencia queda confirmada, pero en este Discovery no se contrastaron advisories ni se confirma un CVE; evaluar el riesgo y dejar evidencia de la fuente pendiente. | api/requirements.txt:4-5 | Media | Pendiente |
| H-API-06 | api | Rutas, persistencia y transformacion concentradas en un modulo | Refactor | `main.py` crea la base y las tablas, inicializa datos, declara rutas, ejecuta SQL y transforma filas para las respuestas; esta concentracion dificulta aislar responsabilidades y probarlas. | api/app/main.py:29; api/app/main.py:35-42; api/app/main.py:56; api/app/main.py:67-123 | Media | Pendiente |
| H-API-07 | api | Tipado crudo, excepciones amplias y condiciones anidadas | Refactor | Se usan `dict`/`Any` para identidad, entradas y respuestas, se capturan excepciones amplias y `summary` contiene condiciones anidadas. La duplicacion mencionada en el RETO no se confirma como hallazgo independiente en esta revision. | api/app/main.py:17; api/app/main.py:25; api/app/main.py:56; api/app/main.py:78; api/app/main.py:101-103; api/app/main.py:120 | Baja | Pendiente |
| H-API-08 | api | Prueba de autorizacion por owner falla contra la implementacion actual | Vulnerabilidad | `tests/test_api.py::test_user_cannot_read_tasks_from_another_owner` espera `403` cuando `student` solicita `owner=trainer`, pero `list_tasks` no compara `identity["username"]` con el owner solicitado y responde `200` con datos de otro owner. `docs/ENDPOINTS.md` documenta explicitamente ese mismo comportamiento ("No se observa una respuesta 403 para un owner distinto"). Hay conflicto entre la prueba agregada (contrato deseado) y el contrato vigente/codigo (contrato real); la suite falla con `python tools/lab.py test api`. | api/app/main.py:67-71; api/tests/test_api.py:74-80; docs/ENDPOINTS.md (seccion GET /api/tasks, "Respuestas de error") | Alta | Candidato |
| H-API-09 | api | H-API-02 parece remediado en codigo pero sigue "Pendiente" en este archivo | Proceso/Documentacion | El commit `e4ea89a` reemplazo la concatenacion de SQL por consultas parametrizadas (`WHERE owner=?`, `INSERT ... VALUES (?,?,?,?,?,...)`) en las tres rutas citadas por H-API-02, y las pruebas `test_quoted_owner_filters_are_treated_as_data` y `test_quoted_task_text_is_inserted_as_data` verifican ese comportamiento y pasan. El estado de H-API-02 no refleja este cambio, lo que puede llevar a reabrir o duplicar trabajo ya hecho, o a dar por cerrado un riesgo sin verificar la evidencia actual. | api/app/main.py:70-71; api/app/main.py:84-85; api/app/main.py:97-98; api/tests/test_api.py:42-64; `git log -p -1 -- api/app/main.py` (commit e4ea89a) | Media | Candidato |
| H-API-10 | api | CORS con origen comodin y credenciales habilitadas simultaneamente | Configuracion | `CORSMiddleware` se registra con `allow_origins=["*"]` y `allow_credentials=True` a la vez; esta combinacion contradice la especificacion fetch/CORS (un origen comodin no deberia combinarse con credenciales) y suele senalarse en revisiones de seguridad como configuracion insegura o inconsistente, sin que se haya verificado aqui el comportamiento exacto del navegador/proxy en este despliegue. | api/app/main.py:48-54 | Media | Candidato |
| H-API-11 | api | El campo `role` del token se decodifica pero no se usa en ninguna decision de autorizacion | Vulnerabilidad/Diseno | `legacy_identity` extrae `role` del token (api/app/main.py:24) pero ninguna ruta lee `identity["role"]`; solo se usa `identity["username"]`. No hay diferenciacion de privilegios entre roles para listar/crear tareas, subir archivos o consultar `backend-health`, pese a que el token transporta esa informacion. | api/app/main.py:17-26; api/app/main.py:67-127 (ausencia de uso de `identity["role"]`) | Media | Candidato |

## Candidatos detallados de esta revision

### H-API-08 — Prueba y comportamiento de autorizacion por owner en conflicto
- Estado: CANDIDATO
- Evidencia: `api/app/main.py:67-71` filtra por el `owner` recibido sin compararlo con la identidad; `api/tests/test_api.py:74-80` espera un `403` para `student` que pide `owner=trainer`; la seccion GET `/api/tasks` de `docs/ENDPOINTS.md` dice que no se observa un `403`. `python tools/lab.py test api` fallo en ese test (1 failed, 24 passed).
- Impacto: una identidad puede obtener tareas de otro owner y la suite no pasa; además, codigo, prueba y contrato documentado discrepan sobre la respuesta esperada.
- Prioridad propuesta: Alta.
- Contrato potencialmente afectado: `GET /api/tasks` y la semantica de `owner`; el comportamiento que se debe preservar o cambiar requiere decision humana.
- Como validarlo: ejecutar `python tools/lab.py test api` y contrastar la respuesta real con el test y la seccion de respuestas de error de `docs/ENDPOINTS.md`.

### H-API-09 — Estado de H-API-02 no refleja las consultas SQL actuales
- Estado: CANDIDATO
- Evidencia: `api/app/main.py:70-71,84-85,96-99` pasa valores mediante parametros SQLite; `api/tests/test_api.py:42-64` comprueba entradas con comillas; `git log -p -1 -- api/app/main.py` muestra el cambio en `e4ea89a`. Sin embargo, H-API-02 sigue como `Pendiente` en esta tabla.
- Impacto: el registro puede provocar trabajo duplicado o una evaluacion incorrecta del riesgo si se usa sin verificar el estado del codigo.
- Prioridad propuesta: Media.
- Contrato potencialmente afectado: ninguno de los contratos HTTP observados; afecta la trazabilidad y el estado del hallazgo H-API-02.
- Como validarlo: revisar el diff de `e4ea89a`, ejecutar `python tools/lab.py test api` y confirmar el resultado de las dos pruebas de entradas entrecomilladas.

### H-API-10 — Politica CORS con comodin y credenciales
- Estado: CANDIDATO
- Evidencia: `api/app/main.py:48-54` configura `allow_origins=["*"]` junto con `allow_credentials=True`.
- Impacto: un navegador puede rechazar solicitudes cross-origin con credenciales segun los headers de respuesta; el comportamiento efectivo no se comprobo en un navegador de este despliegue.
- Prioridad propuesta: Media.
- Contrato potencialmente afectado: consumo cross-origin de la API desde `web/`; no se identifica un contrato CORS formal en `docs/ENDPOINTS.md`.
- Como validarlo: enviar una solicitud preflight y una solicitud con `Origin` y credenciales desde un navegador, inspeccionar `Access-Control-Allow-Origin` y `Access-Control-Allow-Credentials`, y comprobar si el navegador permite la respuesta.