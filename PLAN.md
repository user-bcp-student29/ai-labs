# Plan de correccion

## Alcance y contratos

- Este documento conserva las propuestas de `H-API-01` a `H-API-07` de `HALLAZGOS.md`; la seleccion para este ciclo es `H-API-02` y `H-API-04`. Los otros apartados se mantienen como historial y trabajo pendiente, no como alcance seleccionado.
- `docs/ENDPOINTS.md` no existe. Se toman como referencia los endpoints, metodos, estados y formas de respuesta observados en `api/app/main.py`. Se preservaran las rutas y los formatos de respuestas de los flujos autorizados; los accesos a datos de otro usuario y las entradas inseguras se restringiran deliberadamente.
- El header `Authorization: Bearer ...` y la identidad `username`/`role` se conservaran. Pasar del token Base64 actual a JWT cambia el formato de credencial; antes de implementarlo se debe coordinar con quien emite el token para acordar algoritmo, clave, `iss`, `aud` y claims. No se inspeccionaran ni modificaran otros componentes en este alcance. No se asumira compatibilidad ni se mantendra aceptacion del token legado sin aprobacion explicita.
- No se definiran aqui extensiones ni tamano maximo de archivo: producto/consumidor debe acordar esos limites antes de implementar. Se usaran constantes o configuracion con nombre, no valores magicos.
- Las pruebas propuestas son trabajo futuro. No se han escrito ni ejecutado en este paso.

## Seleccion para este ciclo

La matriz confirma que ambos IDs existen y estan `Pendiente`. Se selecciona `H-API-02` por su severidad alta y porque la parametrizacion SQL no depende de acuerdos externos; `H-API-04` se selecciona por su alcance acotado. Para este ultimo, la implementacion sigue condicionada a acordar con el emisor algoritmo, clave de configuracion, `iss`, `aud` y claims. `H-API-01`, `H-API-03`, `H-API-05`, `H-API-06` y `H-API-07` se conservan como propuestas previas pendientes fuera de esta seleccion.

**Aprobacion humana:** el usuario confirmo `APRUEBO` el 2026-11-02 para la seleccion `H-API-02` y `H-API-04`. Para `H-API-04`, esta aprobacion no reemplaza los acuerdos tecnicos pendientes con el emisor JWT.

## H-API-01 — Autorizacion por owner controlable por el cliente

- **Hallazgo:** `H-API-01`, Pendiente. `GET /api/tasks`, `GET /api/summary` y `POST /api/tasks` permiten seleccionar `owner` desde la solicitud.
- **Problema:** una identidad autenticada puede consultar o crear tareas asociadas a otro usuario.
- **Cambio propuesto:** derivar siempre el owner efectivo de la identidad verificada. Mantener el query/body `owner` por compatibilidad de forma; si se proporciona un valor distinto del usuario autenticado, responder con error de autorizacion generico. No introducir privilegios por `role` hasta que exista una politica acordada.
- **Archivos previstos:** `api/app/main.py` o el modulo de rutas que lo reemplace; `api/tests/test_api.py`.
- **Caracterizacion antes de tocar codigo:** agregar pruebas de que las consultas y creaciones propias mantienen status y campos actuales, y pruebas de regresion para owner ajeno en query y body. Ejecutar contra la implementacion actual para registrar el comportamiento previo; las aserciones de denegacion quedaran inicialmente fallidas y se volveran gate de la correccion.

## H-API-02 — Consultas SQL construidas por concatenacion

- **Hallazgo:** `H-API-02`, Resuelto. Filtros e inserts concatenaban valores recibidos del cliente.
- **Problema:** los valores pueden alterar la expresion SQL en vez de tratarse como datos; el insert tambien puede fallar o producir persistencia no prevista.
- **Cambio propuesto:** usar placeholders de SQLite y pasar todos los valores como parametros en listados, resumen e inserciones. Mantener las columnas, orden, valores por defecto y mapeo de respuesta observados. El SELECT posterior al insert debe seguir usando `lastrowid` como parametro.
- **Archivos previstos:** `api/app/main.py` o el repositorio que se introduzca para tareas; `api/tests/test_api.py`.
- **Caracterizacion antes del cambio:** las pruebas de lectura y resumen con `owner="O'Brien"` y de creacion con apóstrofos registraron HTTP 500 en el comportamiento previo. Se conservaron las pruebas y se cambiaron sus aserciones tras corregir SQL para exigir el comportamiento esperado.
- **Ejecuciones (componente api, H-API-02):**
	- Antes del cambio: `python -m pytest -q tests/test_api.py -k quoted` desde `api/`: 2 passed; ambas pruebas constataron HTTP 500 con el SQL concatenado.
	- Despues del cambio: el mismo comando: 2 passed; los filtros devolvieron HTTP 200 y resultado vacio para ese owner, y el insert devolvio HTTP 201 conservando apóstrofos en titulo y descripcion.
	- Regresion y cobertura: `python -m pytest -q --cov=app --cov-report=term-missing` desde `api/`: 6 passed; cobertura total de `app/main.py` 91% (supera el minimo 85%). Hubo avisos deprecados de FastAPI/Starlette ajenos a este cambio.
- **Resolucion:** se parametrizaron los filtros de listado/resumen, el insert y la lectura por `lastrowid`. Columnas, orden, valores por defecto, status y formato de respuesta se mantienen.

## H-API-03 — Carga de archivos sin validacion visible

- **Hallazgo:** `H-API-03`, Pendiente. Se usa el nombre del archivo enviado y se lee todo su contenido antes de guardarlo.
- **Problema:** una ruta manipulada puede escapar del directorio de uploads y un archivo grande puede consumir memoria/disco; no hay politica visible de extension.
- **Cambio propuesto:** acordar allowlist de extensiones y maximo de bytes; normalizar el nombre, resolver y comprobar que el destino queda dentro del directorio configurado, y leer en bloques con limite antes de escribir. Mantener `POST /api/requirements`, status 201 y las claves de respuesta (`owner`, `fileName`, `storedAt`, `size`) para cargas validas; definir errores genericos para nombre, extension o tamano no permitidos.
- **Archivos previstos:** `api/app/main.py` o un servicio de archivos nuevo bajo `api/app/`; `api/tests/test_api.py`; configuracion bajo `api/` solo si los limites acordados se externalizan.
- **Caracterizacion antes de tocar codigo:** ampliar la prueba de carga valida para fijar status, claves de respuesta, nombre almacenado y contenido. Agregar pruebas de regresion para nombre que intenta salir del directorio, extension no permitida y archivo que supera el limite acordado. La prueba de tamano requiere decidir primero ese limite.

## H-API-04 — Token Base64 con secreto fijo y sin firma

- **Hallazgo:** `H-API-04`, Pendiente. `legacy_identity` decodifica un token Base64 y compara un secreto embebido.
- **Problema:** Base64 no autentica el contenido; no se verifican firma, emisor ni audiencia. La migracion puede rechazar tokens que hoy acepta el servicio.
- **Cambio propuesto:** verificar JWT firmado con expiracion, emisor y audiencia usando una biblioteca mantenida. No hay biblioteca JWT declarada actualmente; justificar y fijar una dependencia como PyJWT solo tras revisar soporte/advisories. Leer la clave y los valores de emisor/audiencia desde configuracion, nunca incorporar secretos reales. Mapear los claims acordados a `username` y `role`; responder 401 generico ante token invalido sin exponer excepciones.
- **Archivos previstos:** `api/app/main.py` o un modulo de seguridad nuevo bajo `api/app/`; `api/requirements.txt`; `api/tests/test_api.py`; configuracion de ejemplo bajo `api/` si se necesita documentar variables sin valores secretos.
- **Caracterizacion antes de tocar codigo:** conservar una prueba del flujo autorizado actual y de 401 para token invalido; agregar pruebas de aceptacion para JWT firmado y rechazo de firma, expiracion, emisor o audiencia invalidos. Las pruebas usaran clave y claims ficticios. La prueba de JWT requiere primero acordar el contrato con el emisor externo; no se cambiara ese componente en este trabajo.
- **Estado de ejecucion:** aprobado por el usuario, pero bloqueado hasta recibir el contrato JWT acordado (algoritmo, claims, emisor y audiencia). No se modificaron autenticacion ni dependencias y no se ejecutaron pruebas JWT.

## H-API-05 — Versiones de dependencias requieren verificacion SCA

- **Hallazgo:** `H-API-05`, Pendiente. `api/requirements.txt` fija `urllib3==2.5.0` y `python-multipart==0.0.21`; no se ha confirmado un CVE en este Discovery.
- **Problema:** sin contrastar advisories no se puede determinar si las versiones requieren actualizacion; actualizarlas sin revisar compatibilidad tambien puede afectar las rutas.
- **Cambio propuesto:** consultar fuentes de advisory/SCA para las versiones fijadas y sus dependencias transitivas. Actualizar solo paquetes con riesgo confirmado a versiones corregidas compatibles, mantener pins y no silenciar alertas. Si no hay advisory aplicable, no cambiar versiones y actualizar el estado del hallazgo con evidencia.
- **Archivos previstos:** `api/requirements.txt` solo si el analisis justifica cambios; `api/tests/test_api.py` solo si una incompatibilidad lo requiere.
- **Caracterizacion antes de tocar codigo:** ampliar las comprobaciones existentes de carga (`test_uploads_requirement`) y agregar una prueba aislada de `/api/backend-health` con respuesta HTTP simulada para fijar status y claves de respuesta sin red. Ejecutar ambas antes de actualizar versiones; despues repetirlas y ejecutar el analisis SCA. El resultado SCA no se considera prueba funcional.

## H-API-06 — Rutas, persistencia y transformacion concentradas

- **Hallazgo:** `H-API-06`, Pendiente. `main.py` configura SQLite, define rutas, ejecuta SQL y serializa respuestas.
- **Problema:** un cambio de transporte, reglas o persistencia queda acoplado y las responsabilidades son dificiles de probar por separado.
- **Cambio propuesto:** separar gradualmente routers, servicio de tareas y repositorio SQLite; dejar `create_app` como composition root compatible con los parametros `db_path` y `upload_dir`, y mantener `app.main:app`/`create_app` para no romper arranque ni pruebas. El repositorio conservara consultas parametrizadas; el servicio concentrara reglas, y el router adaptara HTTP a los modelos.
- **Archivos previstos:** `api/app/main.py`; nuevos modulos bajo `api/app/` para rutas, servicio y repositorio; `api/tests/test_api.py`. Los nombres definitivos se ajustaran a la estructura que exista al iniciar la implementacion.
- **Caracterizacion antes de tocar codigo:** agregar una prueba de contrato para las cinco rutas observadas (incluido `/api/backend-health` con dependencia simulada), y una prueba de inicializacion SQLite que fije filas semilla, lectura/creacion y cierre. Ejecutarlas antes de mover responsabilidades y repetirlas sin cambios en status, campos y valores del flujo normal.

## H-API-07 — Tipado crudo, excepciones amplias y condiciones anidadas

- **Hallazgo:** `H-API-07`, Pendiente. Hay `dict`/`Any` en entradas y salidas, capturas amplias y condiciones anidadas en `summary`; la duplicacion no se confirmo como problema independiente.
- **Problema:** los datos invalidos pueden atravesar la API sin validacion de esquema, los errores internos se filtran o se confunden con errores esperados y la logica de resumen es mas dificil de leer.
- **Cambio propuesto:** definir modelos Pydantic para request/response usando aliases que mantengan las claves JSON actuales; tipar la identidad; validar entradas sin alterar respuestas validas; reemplazar capturas amplias por excepciones esperadas y mensajes genericos, conservando el esquema de error HTTP requerido; extraer el calculo de resumen a una funcion y aplanar condiciones con constantes con nombre para estados. No extraer duplicacion sin evidencia concreta.
- **Archivos previstos:** `api/app/main.py` o los routers del H-API-06; nuevo `api/app/schemas.py` si hace falta; `api/tests/test_api.py`.
- **Caracterizacion antes de tocar codigo:** agregar pruebas de esquemas y respuestas actuales para crear/listar tareas y resumen, incluyendo valores por defecto y estados; comprobar status y mensaje generico en errores de validacion/autenticacion. Las pruebas fijaran las claves JSON existentes antes de introducir modelos y aliases.

## Secuencia y verificacion futura

1. Acordar primero el contrato JWT con el emisor y la politica de owner/roles; acordar tambien extensiones y tamano maximo de archivos. Registrar decisiones antes de implementar esos cambios.
2. Escribir las pruebas de caracterizacion indicadas y ejecutar baseline desde `api/` con `python -m pytest`. Registrar resultados reales por componente e IDs en `docs/TESTING.md` cuando ese artefacto se cree; no inventar resultados.
3. Implementar cada hallazgo en cambios revisables, comenzando por controles de seguridad y despues separacion de capas/modelos. Ejecutar pruebas enfocadas y la suite tras cada cambio; no reducir gates. Meta de cobertura del codigo modificado: 85% o superior.
4. Verificar nuevamente rutas, status y formatos de respuesta frente al contrato observado. Coordinar cambios de formato de credencial con su emisor; no modificar otros componentes ni mantener una validacion insegura como fallback sin aprobacion.