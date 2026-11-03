# Pruebas de API

## Alcance

Esta matriz cubre los cinco endpoints de la seccion `api` de `docs/ENDPOINTS.md`. Los tests ejecutables estan en `api/tests/test_api.py`; ese archivo es la fuente de verdad del codigo de pruebas y no se copia aqui para evitar que la documentacion y el test diverjan. La prueba de acceso entre owners se conserva fallida para reportar el bug existente, no como `xfail`.

## Matriz de casos

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /api/tasks` | Listar tareas semilla | Camino feliz | `200`; lista de tareas del owner autenticado con las claves documentadas. |
| `GET /api/tasks` | `owner` vacio | Borde | `200`; se usa `username` del token. |
| `GET /api/tasks` | Header ausente | Error `401` | `401` con `detail` de token invalido. |
| `GET /api/tasks` | SQLite cerrado | Error `500` | `500 Internal Server Error`. |
| `GET /api/tasks` | Usuario student consulta owner trainer | Seguridad / IDOR | Esperado `403` generico, sin datos de trainer. **Falla actualmente:** responde `200` e incluye las tareas de trainer. |
| `POST /api/tasks` | Crear tarea completa | Camino feliz | `201`; fila persistida con las claves documentadas. |
| `POST /api/tasks` | Crear con `{}` | Borde | `201`; valores por defecto: title/description vacios, `TODO`, `MEDIUM` y owner autenticado. |
| `POST /api/tasks` | Title y description con apostrofos | Borde / SQL | `201`; los caracteres se conservan literalmente. |
| `POST /api/tasks` | Header ausente | Error `401` | `401` con `detail` de token invalido. |
| `POST /api/tasks` | Cuerpo ausente | Error `422` | `422`; ubicacion del error en `body`. |
| `POST /api/tasks` | SQLite cerrado | Error `500` | `500 Internal Server Error`. |
| `GET /api/summary` | Conteo de tareas student | Camino feliz | `200`; total y conteos de las filas semilla. |
| `GET /api/summary` | `owner` vacio | Borde | `200`; se usa `username` del token. |
| `GET /api/summary` | Header ausente | Error `401` | `401` con `detail` de token invalido. |
| `GET /api/summary` | SQLite cerrado | Error `500` | `500 Internal Server Error`. |
| `POST /api/requirements` | Upload de archivo de texto | Camino feliz | `201`; archivo guardado y metadatos de respuesta. |
| `POST /api/requirements` | Archivo vacio sin extension | Borde | `201`; archivo de cero bytes guardado, `size` igual a `0`. El codigo no define extension obligatoria. |
| `POST /api/requirements` | Header ausente | Error `401` | `401` con `detail` de token invalido. |
| `POST /api/requirements` | Campo file ausente | Error `422` | `422`; ubicacion del error en `body/file`. |
| `POST /api/requirements` | Escritura de almacenamiento falla | Error `500` | `500 Internal Server Error`. |
| `GET /api/backend-health` | Upstream sano (respuesta HTTP 200) | Camino feliz | `200` de la API con `requestedBy`, status remoto `200` y body como string. |
| `GET /api/backend-health` | Upstream no disponible | Borde/error remoto capturado | `200` de la API, `status` igual a `0` y texto de excepcion en `body`. |
| `GET /api/backend-health` | Header ausente | Error `401` | `401` con `detail` de token invalido. |
| `GET /api/backend-health` | Identidad inutilizable inyectada en prueba | Error `500` | `500 Internal Server Error` al fallar tambien el bloque de recuperacion de la ruta. |

## Codigo ejecutable de pruebas

La implementacion completa de los casos esta en [api/tests/test_api.py](../api/tests/test_api.py). Casos agregados:

```text
test_quoted_owner_filters_are_treated_as_data
test_quoted_task_text_is_inserted_as_data
test_lists_student_tasks_when_owner_is_empty
test_user_cannot_read_tasks_from_another_owner
test_creates_task_from_empty_object_using_defaults
test_summary_uses_identity_when_owner_is_empty
test_uploads_empty_extensionless_file
test_backend_health_returns_upstream_response
test_backend_health_maps_upstream_exception_to_status_zero
test_all_endpoints_reject_missing_authentication [5 parametros]
test_create_task_rejects_missing_body
test_upload_rejects_missing_file
test_task_endpoints_return_500_when_database_is_unavailable [3 parametros]
test_upload_returns_500_when_storage_fails
test_backend_health_returns_500_for_unusable_identity
```

La prueba IDOR no se marca como esperada/fallida permitida: su fallo mantiene visible el comportamiento que se debe corregir bajo `H-API-01`.

## Ejecuciones realizadas

Entorno: Windows, Python 3.13.15, dependencias de `api/.venv`. Todas las pruebas se lanzaron con cwd `api/`; cobertura medida sobre `app` con pytest-cov/coverage.py. Las salidas se resumen literalmente; los avisos de deprecacion de FastAPI/Starlette no cambian el resultado.

| componente / IDs | comando (desde `api/`) | codigo de salida | resultado |
|---|---|---:|---|
| api / baseline antes de ampliar tests | `python -m pytest -q --cov=app --cov-report=term-missing` | 0 | 6 passed; cobertura global `app/main.py`: 96/96 sentencias, 91% combinado segun coverage.py. |
| api / matriz ampliada, incluyendo IDOR | `python -m pytest -q tests/test_api.py --tb=short` | 1 | 24 passed, 1 failed. Falla `test_user_cannot_read_tasks_from_another_owner`: real `200`, esperado `403`. |
| api / matriz ampliada y cobertura | `python -m pytest -q --cov=app --cov-report=term-missing --tb=short` | 1 | 24 passed, 1 failed; `app/main.py`: 96/96 sentencias; 14/16 ramas cubiertas, 2 parciales; coverage.py informa 98% combinado. |

No se modifico codigo de produccion en esta etapa; por tanto, la cobertura del diff de produccion es no aplicable. La cobertura global de la aplicacion bajo prueba se informa por separado: 96/96 sentencias, 14/16 ramas, 98% combinado. El umbral de 85% se supera para esa medicion global, pero no se declara que el IDOR este corregido.

## Fallos, pendientes y pruebas propuestas

### Fallo observado: H-API-01 (IDOR)

`test_user_cannot_read_tasks_from_another_owner` confirma que una solicitud autenticada como `student` a `GET /api/tasks?owner=trainer` devuelve `200` y datos del owner `trainer`, no `403`. La ruta usa el parametro `owner` directamente en el filtro (`api/app/main.py:69-71`). El defecto ya esta registrado como `H-API-01` en `HALLAZGOS.md`; el test se deja fallando deliberadamente, sin acomodarlo al bug. No se modifico codigo porque este paso es de pruebas/documentacion.

### Pendientes

- Definir y corregir la politica de autorizacion por owner antes de esperar que pase la prueba IDOR; no ocultar el caso con `xfail`.
- No existe un limite maximo de bytes ni allowlist de extensiones en el contrato o codigo; por ello no se invento un caso de limite de tamano. El borde de archivo sin extension y vacio si se ejercita y actualmente es aceptado.
- Los ejemplos de errores `422` pueden variar con versiones de FastAPI/Pydantic; las pruebas verifican status y ubicacion esencial, no todos los textos internos.
- El caso de `500` de backend-health usa un override de dependencia invalido para cubrir una excepcion no recuperable; fallos normales de red se convierten en `200` con `status: 0`.

### Pruebas propuestas

Tras corregir `H-API-01`, repetir `test_user_cannot_read_tasks_from_another_owner` y exigir `403` sin filtrar tareas. Cuando producto acuerde limites de archivo, agregar pruebas de maximo permitido y excedido, y extensiones admitidas/rechazadas. No se reportan como ejecutadas.
