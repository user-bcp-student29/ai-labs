# Salida del crew (CrewAI) — H-AUTH-04

Material generado tal cual por el flujo S1 CrewAI. Está aislado de `auth/` y `web/` y **no forma parte del build**.

- `backend-quarkus/`: propuesta de `LoginService` (paquete `pe.bcp.login`) con errores públicos fijos y pruebas unitarias. `LegacyDatabase.java` no contiene código válido y debe rehacerse.
- `frontend-angular/`: componentes y servicios Angular de login. Contiene archivos duplicados (`auth-login.*` y `login.*`).

Estado: pruebas **no ejecutadas**. El Chapter emitió el veredicto **RECHAZADO**. Requiere integración manual en `auth/` (`com.bcp.workshop.auth`) y `web/` antes de cualquier uso.
