# API de tareas y requerimientos

## Seccion api

### Estado y convenciones

Este contrato esta observado en `api/app/main.py`; no existe un contrato acordado en `docs/ENDPOINTS.md` previo ni se valido con consumidores. Los ejemplos muestran valores posibles: los IDs, marcas de tiempo y datos dependen del estado de SQLite.

Todas las rutas requieren el header `Authorization: Bearer <token>`. `legacy_identity` elimina el texto `Bearer `, decodifica el resto como Base64 y espera `username|role|expiry|secret`; compara el secreto fijo y verifica la expiracion. No verifica firma, emisor ni audiencia. La API no expone una ruta para emitir tokens. No se publica aqui un token utilizable; el mecanismo de emision y distribucion es una pregunta abierta para la integracion. Evidencia: `api/app/main.py:14-26`.

Para ejemplos `curl`, `$TOKEN` representa un token local vigente obtenido por un mecanismo de prueba ya configurado. No es un valor literal que la API pueda emitir.

---

## GET /api/tasks

### 1. Que hace
Lista las tareas del `owner` solicitado o, cuando se omite o esta vacio, las tareas del usuario indicado por el token.

### 2. Autenticacion
Requiere `Authorization: Bearer <token>` como se describe en las convenciones. Evidencia: `api/app/main.py:17-26,67-68`.

### 3. Request

| campo/parametro | tipo | obligatorio | descripcion |
|---|---|---|---|
| `owner` (query) | string | No | Owner usado para filtrar. Si se omite o es una cadena vacia, se usa `username` del token. Si se solicita un owner distinto, la API responde `403`. |

Ejemplo completo:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8000/api/tasks?owner=student"
```

### 4. Response exitosa

Codigo: `200 OK`.

| campo | tipo | descripcion |
|---|---|---|
| respuesta | array de objetos | Lista ordenada por `id`. |
| `id` | integer | Identificador de la tarea en SQLite. |
| `title` | string | Titulo. |
| `description` | string | Descripcion. |
| `status` | string | Estado guardado. |
| `owner` | string | Owner guardado. |
| `priority` | string | Prioridad guardada. |
| `createdAt` | string | Valor `created_at` de SQLite, expuesto con alias camelCase. |

Ejemplo de una lista con una tarea (IDs y timestamp ilustrativos):

```json
[
  {
    "id": 1,
    "title": "Refactor login",
    "description": "Separar responsabilidades en frontend",
    "status": "TODO",
    "owner": "student",
    "priority": "HIGH",
    "createdAt": "2026-11-02 12:34:56"
  }
]
```

### 5. Respuestas de error

| codigo | cuando ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token que no puede decodificarse/validarse. El texto exacto depende de la excepcion capturada. | `{"detail":"Invalid token: invalid token"}` |
| `403 Forbidden` | El `owner` solicitado es distinto del `username` del token. | `{"detail":"Not authorized to access this owner"}` |
| `500 Internal Server Error` | Falla no controlada al consultar SQLite. La respuesta por defecto de FastAPI/Starlette no incluye el detalle de la excepcion. | `Internal Server Error` (texto plano) |

### 6. Notas de seguridad

La consulta usa parametros SQL y la ruta solo permite consultar tareas del `username` autenticado. El token Base64 no tiene firma criptografica.

---

## POST /api/tasks

### 1. Que hace
Crea una tarea y devuelve la fila persistida.

### 2. Autenticacion
Requiere `Authorization: Bearer <token>` como se describe en las convenciones. Evidencia: `api/app/main.py:17-26,77-78`.

### 3. Request

Content-Type: `application/json`. El cuerpo debe ser un objeto JSON; los campos individuales son opcionales y el codigo convierte sus valores a string.

| campo/parametro | tipo | obligatorio | descripcion |
|---|---|---|---|
| `title` | string (otros valores se convierten a string) | No | Titulo. Valor por defecto si se omite: cadena vacia. |
| `description` | string (otros valores se convierten a string) | No | Descripcion. Valor por defecto si se omite: cadena vacia. |
| `status` | string (otros valores se convierten a string) | No | Estado. Valor por defecto: `TODO`. |
| `priority` | string (otros valores se convierten a string) | No | Prioridad. Valor por defecto: `MEDIUM`. |
| `owner` | string (otros valores se convierten a string) | No | Owner. Valor por defecto: `username` del token; si se envia, se usa el valor del cliente sin compararlo con la identidad. |

Ejemplo completo:

```bash
curl -X POST "http://localhost:8000/api/tasks" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Review requirements","description":"Check acceptance criteria","status":"TODO","priority":"MEDIUM","owner":"student"}'
```

### 4. Response exitosa

Codigo: `201 Created`.

| campo | tipo | descripcion |
|---|---|---|
| `id` | integer | Identificador asignado por SQLite. |
| `title` | string | Titulo persistido. |
| `description` | string | Descripcion persistida. |
| `status` | string | Estado persistido. |
| `owner` | string | Owner persistido. |
| `priority` | string | Prioridad persistida. |
| `createdAt` | string | Fecha/hora SQLite expuesta con alias camelCase. |

Ejemplo (ID y timestamp ilustrativos):

```json
{
  "id": 4,
  "title": "Review requirements",
  "description": "Check acceptance criteria",
  "status": "TODO",
  "owner": "student",
  "priority": "MEDIUM",
  "createdAt": "2026-11-02 12:34:56"
}
```

### 5. Respuestas de error

| codigo | cuando ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token invalido. | `{"detail":"Invalid token: invalid token"}` |
| `422 Unprocessable Entity` | Cuerpo ausente o que no es un objeto compatible con `dict`. El detalle exacto depende de la validacion de FastAPI/Pydantic. | `{"detail":[{"type":"missing","loc":["body"],"msg":"Field required","input":null}]}` |
| `500 Internal Server Error` | Falla no controlada al insertar, confirmar o leer SQLite. | `Internal Server Error` (texto plano) |

### 6. Notas de seguridad

Los valores SQL se pasan como parametros SQLite. El campo `owner`, sin embargo, es controlable por el cliente; el valor se almacena tal cual y no demuestra autorizacion sobre ese owner. Los campos no tienen validacion de dominio visible mas alla de convertirlos a string.

---

## GET /api/summary

### 1. Que hace
Devuelve el total de tareas, su conteo por estado y las tareas abiertas de prioridad alta para el owner solicitado.

### 2. Autenticacion
Requiere `Authorization: Bearer <token>` como se describe en las convenciones. Evidencia: `api/app/main.py:17-26,90-91`.

### 3. Request

| campo/parametro | tipo | obligatorio | descripcion |
|---|---|---|---|
| `owner` (query) | string | No | Owner usado para filtrar. Si se omite o esta vacio, se usa `username` del token. No se compara con esa identidad. |

Ejemplo completo:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8000/api/summary?owner=student"
```

### 4. Response exitosa

Codigo: `200 OK`.

| campo | tipo | descripcion |
|---|---|---|
| `owner` | string | Owner usado en el filtro. |
| `total` | integer | Numero total de tareas del owner. |
| `byStatus` | object de string a integer | Conteo de tareas agrupado por valor de `status`. |
| `highPriorityOpen` | integer | Tareas con `priority` igual a `HIGH` y `status` distinto de `DONE` y `ARCHIVED`. |

Ejemplo correspondiente a las filas semilla de un SQLite nuevo:

```json
{
  "owner": "student",
  "total": 2,
  "byStatus": {
    "TODO": 1,
    "IN_PROGRESS": 1
  },
  "highPriorityOpen": 1
}
```

### 5. Respuestas de error

| codigo | cuando ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token invalido. | `{"detail":"Invalid token: invalid token"}` |
| `500 Internal Server Error` | Falla no controlada al consultar SQLite. | `Internal Server Error` (texto plano) |

No se observa una respuesta `403` para un owner distinto.

### 6. Notas de seguridad

La consulta se parametriza, pero el filtro `owner` que envia el cliente prevalece sobre la identidad del token. El resultado puede incluir conteos de otro owner.

---

## POST /api/requirements

### 1. Que hace
Guarda el archivo de requerimientos recibido en el directorio de uploads configurado.

### 2. Autenticacion
Requiere `Authorization: Bearer <token>` como se describe en las convenciones. Evidencia: `api/app/main.py:17-26,107-108`.

### 3. Request

Content-Type: `multipart/form-data`.

| campo/parametro | tipo | obligatorio | descripcion |
|---|---|---|---|
| `file` | archivo multipart (`UploadFile`) | Si | Archivo que se va a guardar. El nombre multipart se usa para formar la ruta de destino. |

Ejemplo completo:

```bash
curl -X POST "http://localhost:8000/api/requirements" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@requirements.txt;type=text/plain"
```

### 4. Response exitosa

Codigo: `201 Created`.

| campo | tipo | descripcion |
|---|---|---|
| `owner` | string | `username` del token. |
| `fileName` | string o null | Nombre recibido en el upload. |
| `storedAt` | string | Ruta del archivo guardado, como texto; puede depender de la configuracion del directorio. |
| `size` | integer | Cantidad de bytes leidos. |

Ejemplo (ruta ilustrativa con `WORKSHOP_UPLOAD_DIR=./uploads`):

```json
{
  "owner": "student",
  "fileName": "requirements.txt",
  "storedAt": "uploads/requirements.txt",
  "size": 24
}
```

### 5. Respuestas de error

| codigo | cuando ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token invalido. | `{"detail":"Invalid token: invalid token"}` |
| `422 Unprocessable Entity` | Falta el campo `file` o el cuerpo no es un multipart valido. | `{"detail":[{"type":"missing","loc":["body","file"],"msg":"Field required","input":null}]}` |
| `500 Internal Server Error` | Falla no controlada al leer/escribir el archivo. | `Internal Server Error` (texto plano) |

### 6. Notas de seguridad

El codigo usa el nombre recibido directamente para construir el destino, no valida extension ni limita tamaño y lee el archivo completo en memoria antes de escribirlo. Si el mismo nombre ya existe, `write_bytes` lo sobrescribe. No se debe tratar esta ruta como almacenamiento seguro de archivos.

---

## GET /api/backend-health

### 1. Que hace
Consulta el endpoint de salud de autenticacion configurado y devuelve el estado y cuerpo recibidos para diagnostico.

### 2. Autenticacion
Requiere `Authorization: Bearer <token>` como se describe en las convenciones. Evidencia: `api/app/main.py:17-26,114-115`.

### 3. Request

No recibe parametros ni cuerpo. El destino se configura con la variable de entorno `AUTH_HEALTH_URL`; por defecto es `http://localhost:8080/q/health`.

| campo/parametro | tipo | obligatorio | descripcion |
|---|---|---|---|
| ninguno | — | — | La URL de destino es configuracion del servidor, no un parametro de la solicitud. |

Ejemplo completo:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8000/api/backend-health"
```

### 4. Response exitosa

Codigo: `200 OK` para cualquier respuesta HTTP recibida del servicio remoto; su status se incluye en el cuerpo de esta API.

| campo | tipo | descripcion |
|---|---|---|
| `requestedBy` | string | `username` del token. |
| `status` | integer | Codigo HTTP recibido del servicio remoto; vale `0` si la solicitud remota lanza una excepcion. |
| `body` | string | Cuerpo remoto decodificado como UTF-8 o texto de la excepcion capturada. |

Ejemplo de respuesta remota exitosa (el `body` se devuelve como string):

```json
{
  "requestedBy": "student",
  "status": 200,
  "body": "{\"status\":\"UP\"}"
}
```

Ejemplo ilustrativo cuando falla la llamada remota; `body` contiene el texto concreto producido por urllib3 y puede variar:

```json
{
  "requestedBy": "student",
  "status": 0,
  "body": "<texto de la excepcion de urllib3>"
}
```

### 5. Respuestas de error

| codigo | cuando ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token invalido. | `{"detail":"Invalid token: invalid token"}` |

Los errores de red y otras excepciones dentro de la llamada remota se capturan; no producen un status HTTP de error en esta ruta, sino `200 OK` con `status: 0` y el texto de la excepcion en `body`. Un error no capturado fuera de ese bloque podria producir `500 Internal Server Error` con el cuerpo por defecto.

### 6. Notas de seguridad

El destino remoto se controla desde configuracion del servidor. La respuesta incluye el cuerpo remoto y, en caso de excepcion, su texto; no se filtra ese contenido. El token sigue siendo Base64 sin firma. La API no comprueba que la respuesta remota indique salud correcta: el status HTTP de esta ruta permanece `200` aunque el remoto falle.

---

## Preguntas abiertas

- Que servicio emite los tokens de integracion y cual es el mecanismo local aprobado para obtener un token vigente? El codigo actual solo verifica el formato Base64 legado y no define una ruta de emision.
- Cual sera el contrato acordado para JWT (algoritmo, claims, emisor y audiencia)? El codigo actual no lo implementa.
- El acceso por `owner` debe restringirse a la identidad del token o hay una politica de roles acordada? El comportamiento observado permite consultar y crear con un owner enviado por el cliente.
- Que tipos, extensiones y limites de tamaño se aceptaran para archivos? El codigo actual no define restricciones.
- Los ejemplos de errores `422` corresponden al esquema generado por FastAPI/Pydantic instalado; mensajes y campos detallados pueden cambiar con versiones de dependencias.
