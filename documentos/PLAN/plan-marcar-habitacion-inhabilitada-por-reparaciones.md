# Implementation Plan: Marcar Habitación Inhabilitada por Reparaciones

**Date**: 2026-10-08  
**Spec**: [spec-marcar-habitacion-inhabilitada-por-reparaciones.md](../SPEC/spec-marcar-habitacion-inhabilitada-por-reparaciones.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Marcar Habitación Inhabilitada por Reparaciones** para el Personal de limpieza y el Personal de mantenimiento:

1. **Reportar daño** (FR-001 a FR-006, FR-008 a FR-012): desde el botón "Reportar daño" de una habitación `Available` (panel de limpieza o panel de mantenimiento), un diálogo pide la descripción del daño (obligatoria, máximo 500 caracteres). En una sola transacción la habitación pasa a `DisabledForRepairs`, se crea el `DamageReport` inmutable con autor y fecha del servidor, y se registra el periodo en `room_state_history`.
2. **Ver informe** (FR-007): el Personal de mantenimiento consulta el reporte de una habitación en `DisabledForRepairs` (descripción, autor y fecha y hora).

El mismo puerto `ReportDamageUseCase` lo invoca *Confirmar Fin de Limpieza de Habitación* cuando el miembro describe un daño al terminar (caso límite 3), dentro de la transacción de ese flujo.

Usa la tabla `damage_report` del plan base (T018).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `damage_report`, `room` y `room_state_history` del plan base (T018 y T007).
- **Performance Goals**:
  - Completar el reporte, incluida la redacción, en un máximo de 45 segundos (SC-001).
  - La habitación deja de listarse en el panel de limpieza y aparece como inhabilitada en el panel de mantenimiento en un máximo de 3 segundos (SC-003).
- **Constraints**:
  - Solo desde `Available` (FR-002); la transición, el reporte y el historial van en la misma transacción (SC-002, FR-012).
  - `ReportDateTime` lo genera el servidor y coincide con el inicio del periodo en el historial (FR-008, FR-009); el reporte no se modifica ni se elimina (FR-010).
  - "Ver informe" solo lo usa el Personal de mantenimiento; el panel de limpieza no lista habitaciones en `DisabledForRepairs` (FR-007.2).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; los roles se indican por endpoint. El autor se toma del token.
- **Fechas**: ISO 8601 con zona de Colombia; la interfaz las muestra como DD-MM-YYYY HH:MM.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | El usuario no tiene un rol autorizado para el endpoint | "No tienes permiso para realizar esta acción." |

---

## POST /api/rooms/{roomId}/damage-reports

**Descripción:** Reporta un daño en una habitación `Available` y la inhabilita por reparaciones (FR-001 a FR-006, FR-008 a FR-012).
**Rol autorizado:** `CLEANING_STAFF`, `MAINTENANCE_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Habitación a inhabilitar | `3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60` |

**Body (JSON):** cualquier fecha enviada por el cliente se ignora (caso límite 4).

```json
{
  "damageDescription": "Ventana con el vidrio fisurado."
}
```

### Respuesta (Response)

**Status Code:** `201 Created`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `damageReportId` | UUID | Reporte creado | `"d4e5f6a7-…"` |
| `roomId` | UUID | Habitación | `"3f1c…"` |
| `roomNumber` | string | Número de habitación | `"101"` |
| `roomStatus` | string | `DisabledForRepairs` | `"DisabledForRepairs"` |
| `reportDateTime` | string | Fecha y hora del reporte, generada por el servidor | `"2026-10-08T11:20:00-05:00"` |

**Body (JSON):**

```json
{
  "damageReportId": "d4e5f6a7-0b1c-4d2e-8f30-415263748596",
  "roomId": "3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60",
  "roomNumber": "101",
  "roomStatus": "DisabledForRepairs",
  "reportDateTime": "2026-10-08T11:20:00-05:00"
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Descripción nula, vacía, solo espacios o de más de 500 caracteres (FR-003, HU-1 esc. 2, caso límite 2) | "La descripción del daño es obligatoria (máximo 500 caracteres)." |
| 404 | `ROOM_NOT_FOUND` | El `roomId` no existe | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | La habitación no está en `Available` (FR-002, HU-1 esc. 3) | "La habitación está en estado {estado}; no es posible reportar un daño." |
| 409 | `CONCURRENT_UPDATE` | Otra solicitud cambió el estado de la habitación justo antes (FR-006, caso límite 1) | "La habitación cambió de estado (estado actual: {estado})." |

```json
{
  "errorCode": "ROOM_INVALID_STATE",
  "message": "La habitación está en estado Ocupada; no es posible reportar un daño.",
  "timestamp": "2026-10-08T11:20:01-05:00",
  "path": "/api/rooms/3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60/damage-reports",
  "details": { "currentStatus": "Occupied" }
}
```

Si falla la persistencia o la conexión, la transacción se revierte, la habitación sigue en `Available` y el diálogo conserva la descripción escrita (FR-012).

---

## GET /api/rooms/{roomId}/damage-reports/latest

**Descripción:** Acción "Ver informe": devuelve el reporte de daño que originó el estado `DisabledForRepairs` actual; solo responde mientras la habitación está en ese estado (FR-007, FR-007.1).
**Rol autorizado:** `MAINTENANCE_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Habitación inhabilitada | `3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `damageReportId` | UUID | Reporte | `"d4e5f6a7-…"` |
| `roomNumber` | string | Número de habitación | `"101"` |
| `damageDescription` | string | Descripción del daño | `"Ventana con el vidrio fisurado."` |
| `authorName` | string | Nombre del autor del reporte | `"Ana Gómez"` |
| `reportDateTime` | string | Fecha y hora del reporte | `"2026-10-08T11:20:00-05:00"` |

**Body (JSON):**

```json
{
  "damageReportId": "d4e5f6a7-0b1c-4d2e-8f30-415263748596",
  "roomNumber": "101",
  "damageDescription": "Ventana con el vidrio fisurado.",
  "authorName": "Ana Gómez",
  "reportDateTime": "2026-10-08T11:20:00-05:00"
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 404 | `ROOM_NOT_FOUND` | El `roomId` no existe | "No se encontró la habitación." |
| 404 | `DAMAGE_REPORT_NOT_FOUND` | La habitación no está en `DisabledForRepairs` (FR-007) | "Esta habitación no tiene un reporte de daño vigente." |

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-marcar-habitacion-inhabilitada-por-reparaciones.md
│   ├── spec-marcar-habitacion-en-limpieza.md
│   ├── spec-confirmar-fin-de-limpieza.md
│   └── spec-confirmar-reparacion-finalizada.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-marcar-habitacion-inhabilitada-por-reparaciones.md
    ├── plan-marcar-habitacion-en-limpieza.md
    ├── plan-confirmar-fin-de-limpieza.md
    └── plan-confirmar-reparacion-finalizada.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   └── DamageReport.java                 # Definido en el plan base; aquí se implementa
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── ReportDamageUseCase.java
│   │       │   └── GetLatestDamageReportUseCase.java
│   │       └── out/
│   │           └── DamageReportRepositoryPort.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── ReportDamageService.java
│   │   │   └── DamageReportQueryService.java
│   │   └── dto/
│   │       ├── ReportDamageCommand.java
│   │       ├── DamageReportCreatedDto.java
│   │       └── DamageReportViewDto.java
│   └── infrastructure/adapters/
│       ├── in/web/
│       │   └── DamageReportController.java
│       └── out/persistence/
│           ├── entity/DamageReportJpaEntity.java
│           └── adapter/JpaDamageReportRepositoryAdapter.java
└── main/resources/db/migration/
    └── (sin migración propia: `damage_report` se crea en `V3__cleaning_maintenance_schema.sql` del plan base)
frontend/src/
├── components/
│   ├── DamageReportDialog.jsx                     # Reportar daño; lo usan los paneles de limpieza y mantenimiento
│   └── DamageReportViewDialog.jsx                 # Ver informe; lo usa el panel de mantenimiento
└── api/
    └── damageReportService.js
```

**Structure Decision**: Los diálogos van en `components/` porque los usan dos paneles. `ReportDamageService` se ejecuta con `@Transactional` de propagación `REQUIRED`: abre su transacción cuando se llama desde el endpoint y se une a la de *Confirmar Fin de Limpieza de Habitación* cuando se invoca desde ese flujo. `ReportDamageCommand` contiene `roomId`, `damageDescription`, `authorId` (del token o del flujo invocador) y `sourceFlow` (`REPORT_DAMAGE` desde el endpoint, `CONFIRM_CLEANING_END` desde *Confirmar Fin de Limpieza de Habitación*).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que la tabla `damage_report` y su índice por `(room_id, report_date_time DESC)` existan (plan base, T018).
- [ ] T002 [P] Implementar `DamageReport` (dominio, sin métodos de modificación), `DamageReportJpaEntity`, `DamageReportRepositoryPort` (solo insertar y consultar el último por habitación) y su adaptador JPA (FR-010).
- [ ] T003 [P] Registrar en `GlobalExceptionHandler` el código `DAMAGE_REPORT_NOT_FOUND` y la validación de la descripción como `VALIDATION_ERROR`.

---

## Phase 3: User Story 1 - Reporte de daño en habitación (Priority: P1)

**Goal**: Que el Personal de limpieza o de mantenimiento reporte un daño sobre una habitación disponible, dejándola inhabilitada con su reporte, y que el Personal de mantenimiento pueda consultar ese reporte.

**Independent Test**: Reportar un daño con descripción válida sobre una habitación `Available` (con un usuario de cada rol) y verificar `DisabledForRepairs`, el `DamageReport` y el periodo en el historial; intentar sin descripción y sobre una habitación `Occupied`, y verificar los rechazos; consultar "Ver informe" con un usuario de mantenimiento.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `ReportDamageServiceTest`: reporte válido transiciona a `DisabledForRepairs`, crea el reporte con autor del token y fecha del servidor (HU-1 esc. 1; FR-004, FR-005, FR-008).
- [ ] T005 [P] [US1] Unit test: descripción nula, vacía, solo espacios o de más de 500 caracteres se rechaza sin cambios (HU-1 esc. 2; FR-003; caso límite 2).
- [ ] T006 [P] [US1] Unit test: rechazo desde cualquier estado distinto de `Available`, con el estado actual (HU-1 esc. 3; FR-002).
- [ ] T007 [P] [US1] Unit test con `Clock` fijo: `reportDateTime` coincide con el inicio del periodo en `room_state_history` y se ignora cualquier fecha del cliente (FR-008, FR-009, FR-011; caso límite 4; SC-004).
- [ ] T008 [US1] Integration test con Testcontainers: dos reportes simultáneos sobre la misma habitación; solo uno tiene éxito y el otro recibe `409` con el estado actual (FR-006; caso límite 1).
- [ ] T009 [US1] Integration test: un fallo forzado al guardar el reporte revierte la transición; invocado desde una transacción externa, el rollback abarca también la de ese flujo (FR-012; SC-002).
- [ ] T010 [P] [US1] Unit test en `DamageReportQueryServiceTest`: "Ver informe" devuelve el reporte más reciente con descripción, autor y fecha (FR-007, FR-007.1; responde `DAMAGE_REPORT_NOT_FOUND` si la habitación no está en `DisabledForRepairs`).
- [ ] T011 [US1] Integration test: `POST` funciona con `CLEANING_STAFF` y `MAINTENANCE_STAFF`; `GET .../latest` responde `403` a `CLEANING_STAFF` (FR-001, FR-007.2).
- [ ] T012 [P] [US1] Component test en `DamageReportDialog`: la descripción es obligatoria, contador de 500 caracteres, botón de confirmar deshabilitado si no es válida y conservación del texto ante un error (FR-001, FR-003, FR-012).

### Implementation for User Story 1

- [ ] T013 [US1] Implementar `ReportDamageService` (`@Transactional`, propagación `REQUIRED`) (FR-002 a FR-006, FR-008, FR-009, FR-011, FR-012):
  - Validar la descripción y que la habitación exista y esté en `Available`.
  - Invocar `TransitionRoomStateUseCase.transition(roomId, DisabledForRepairs, autor, sourceFlow, null)` con `sourceFlow = REPORT_DAMAGE`, o `CONFIRM_CLEANING_END` cuando el comando viene de ese flujo; crear el `DamageReport` usando como `reportDateTime` la marca de tiempo del periodo que devuelve la transición (FR-008, FR-009, FR-011).
- [ ] T014 [US1] Implementar `DamageReportQueryService` y `DamageReportController` con `POST /api/rooms/{roomId}/damage-reports` (`CLEANING_STAFF`, `MAINTENANCE_STAFF`) y `GET /api/rooms/{roomId}/damage-reports/latest` (`MAINTENANCE_STAFF`) (FR-001, FR-007).
- [ ] T015 [US1] Construir `DamageReportDialog.jsx` (modal con campo de texto obligatorio de máximo 500 caracteres; mensaje de éxito; ante error de estado, mensaje con el estado actual y actualización de la vista) y `DamageReportViewDialog.jsx` (descripción, autor y fecha en DD-MM-YYYY HH:MM) (FR-001 a FR-004, FR-007.1).
- [ ] T016 [US1] Conectar el botón "Reportar daño" del panel de limpieza (`plan-marcar-habitacion-en-limpieza.md`, T017) con `DamageReportDialog.jsx` y refrescar el panel al terminar, para que la habitación desaparezca de él.

**Checkpoint**: El reporte de daño funciona desde el panel de limpieza y queda disponible para el panel de mantenimiento y para *Confirmar Fin de Limpieza de Habitación*.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T017 Integration test: tras el reporte, la habitación deja de aparecer en `GET /api/cleaning/panel` y aparece con `DisabledForRepairs` en `GET /api/maintenance/panel` en menos de 3 segundos (SC-003).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), la tabla `damage_report` (T018), `SourceFlow` (T020), los roles `CLEANING_STAFF` y `MAINTENANCE_STAFF` y `ClockConfig`.
- **Planes relacionados**:
  - `plan-marcar-habitacion-en-limpieza.md`: panel de limpieza con el botón "Reportar daño".
  - `plan-confirmar-fin-de-limpieza.md`: invoca `ReportDamageUseCase` en el fin de limpieza con daño.
  - `plan-confirmar-reparacion-finalizada.md`: panel de mantenimiento, que usa `DamageReportDialog.jsx` y `DamageReportViewDialog.jsx`.
- **Orden**: Phase 2 → Phase 3 → Phase 4. Su Phase 2 debe estar lista antes del camino con daño de `plan-confirmar-fin-de-limpieza.md`.
