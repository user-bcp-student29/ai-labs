# Hallazgos

## Contrato observado (no acordado)

`docs/ENDPOINTS.md` no existe. Lo siguiente se deriva del codigo de `api/` y no representa un contrato validado con consumidores:

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