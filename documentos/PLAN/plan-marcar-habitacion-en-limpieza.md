# Implementation Plan: Marcar Habitación en Limpieza

**Date**: 2026-10-08  
**Spec**: [spec-marcar-habitacion-en-limpieza.md](../SPEC/spec-marcar-habitacion-en-limpieza.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Marcar Habitación en Limpieza** para el actor Personal de limpieza:

- **Origen** (FR-007, FR-008, FR-012): la acción **Iniciar limpieza** del panel de limpieza, que implementa `plan-consultar-panel-limpieza.md`.
- **Iniciar limpieza** (FR-001 a FR-006, FR-010, FR-011, FR-013, FR-014, FR-016): en una sola transacción, transiciona la habitación de `PendingCleaning` o `Available` a `InCleaning` con `TransitionRoomStateUseCase.transition(...)` (regla 8 del plan base), crea la `CleaningTask` del miembro con `StartDateTime` del servidor y registra el periodo en `room_state_history`. Al terminar, redirige a la vista de tarea activa (FR-009); si se rechaza, informa el motivo y vuelve al panel recargado (FR-017).

Usa la tabla `cleaning_task` del plan base (T018), que también usan *Confirmar Fin de Limpieza de Habitación* (cierre y liberación) y *Consultar Panel de Limpieza* (última limpieza).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `cleaning_task`, `room` y `room_state_history` del plan base (T018 y T007).
- **Performance Goals**:
  - Marcar una habitación en limpieza desde el panel en menos de 15 segundos de interacción (SC-001).
  - El cambio de estado se refleja en el inventario en menos de 2 segundos (SC-003).
- **Constraints**:
  - Una sola tarea abierta por miembro y por habitación (FR-006, FR-013; regla 9 del plan base), garantizado con índices únicos parciales además de la validación del servicio.
  - `StartDateTime` lo genera el servidor con `Clock` y coincide con el inicio del periodo en `room_state_history` (FR-010, FR-011, SC-004).
  - La reasignación o liberación de tareas de otros miembros queda fuera de alcance (FR-015, caso límite 5); la liberación propia se implementa en `plan-confirmar-fin-de-limpieza.md`.
  - Sin restricción de tiempo desde que la habitación quedó pendiente (caso límite 3).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `CLEANING_STAFF`. El miembro se toma del token, nunca del cuerpo de la petición.
- **Fechas**: ISO 8601 con zona de Colombia (ej. `2026-10-08T09:15:00-05:00`).
- **Errores**: esquema `ApiError` del plan base, con el campo adicional `details` cuando aporta contexto (ej. `currentStatus`).
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | `AuthenticationException` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." (caso límite 2) |
| 403 | `FORBIDDEN` | `AccessDeniedException` | El usuario no tiene el rol `CLEANING_STAFF` | "No tienes permiso para realizar esta acción." |

---

## POST /api/cleaning/tasks

**Descripción:** Inicia la limpieza de una habitación: transiciona `PendingCleaning` o `Available` a `InCleaning`, crea la `CleaningTask` del miembro autenticado y registra el periodo en `room_state_history`, todo en una transacción (FR-001 a FR-006, FR-010, FR-011, FR-013, FR-014, FR-016).
**Rol autorizado:** `CLEANING_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Body (JSON):** solo el identificador de la habitación; cualquier fecha enviada por el cliente se ignora (FR-010, caso límite 4).

```json
{
  "roomId": "3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60"
}
```

### Respuesta (Response)

**Status Code:** `201 Created`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `taskId` | UUID | Tarea de limpieza creada | `"c0ffee00-1234-4abc-9def-0123456789ab"` |
| `roomId` | UUID | Habitación | `"3f1c…"` |
| `roomNumber` | string | Número de habitación | `"101"` |
| `roomStatus` | string | Estado resultante (`InCleaning`) | `"InCleaning"` |
| `startDateTime` | string | Inicio de labores, generado por el servidor (FR-010, FR-011) | `"2026-10-08T09:15:00-05:00"` |

**Body (JSON):**

```json
{
  "taskId": "c0ffee00-1234-4abc-9def-0123456789ab",
  "roomId": "3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60",
  "roomNumber": "101",
  "roomStatus": "InCleaning",
  "startDateTime": "2026-10-08T09:15:00-05:00"
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Falta `roomId` o no es un UUID válido | "Selecciona una habitación válida." |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` | El `roomId` no corresponde a ninguna habitación | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación no está en `PendingCleaning` ni `Available` (FR-005, HU-1 esc. 3) | "La habitación está {estado}; ahora no se puede limpiar." |
| 409 | `ACTIVE_TASK_EXISTS` | `ActiveTaskExistsException` o `DataIntegrityViolationException` del índice único del miembro (T003) | El miembro ya tiene una `CleaningTask` abierta (FR-013, HU-1 esc. 4) | "Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra." |
| 409 | `CONCURRENT_UPDATE` | `ObjectOptimisticLockingFailureException` o `DataIntegrityViolationException` del índice único de la habitación (T003) | Otro miembro inició la limpieza de la misma habitación al mismo tiempo (FR-006, caso límite 1) | "Otro compañero ya está limpiando esta habitación." |

```json
{
  "errorCode": "ROOM_INVALID_STATE",
  "message": "La habitación está Ocupada; ahora no se puede limpiar.",
  "timestamp": "2026-10-08T09:15:02-05:00",
  "path": "/api/cleaning/tasks",
  "details": { "currentStatus": "Occupied" }
}
```

Si falla la persistencia o la conexión, la transacción se revierte completa y la interfaz muestra el error con la sugerencia de reintentar (FR-016).

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-marcar-habitacion-en-limpieza.md
│   ├── spec-confirmar-fin-de-limpieza.md
│   └── spec-marcar-habitacion-inhabilitada-por-reparaciones.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-marcar-habitacion-en-limpieza.md
    ├── plan-confirmar-fin-de-limpieza.md
    └── plan-marcar-habitacion-inhabilitada-por-reparaciones.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   └── CleaningTask.java                 # Definido en el plan base; aquí se implementa
│   │   └── ports/
│   │       ├── in/
│   │       │   └── StartCleaningUseCase.java
│   │       └── out/
│   │           └── CleaningTaskRepositoryPort.java
│   ├── application/
│   │   ├── service/
│   │   │   └── StartCleaningService.java
│   │   └── dto/
│   │       ├── StartCleaningCommand.java
│   │       └── CleaningTaskStartedDto.java
│   └── infrastructure/
│       └── adapters/
│           ├── in/web/
│           │   └── CleaningController.java
│           └── out/persistence/
│               ├── entity/CleaningTaskJpaEntity.java
│               └── adapter/JpaCleaningTaskRepositoryAdapter.java
└── main/resources/db/migration/
    └── (sin migración propia: `cleaning_task` se crea en `V3__cleaning_maintenance_schema.sql` del plan base)
frontend/src/
└── api/
    └── cleaningService.js
```

**Structure Decision**: `CleaningController` agrupa los endpoints `/api/cleaning/...`; `plan-consultar-panel-limpieza.md` le agrega el del panel y `plan-confirmar-fin-de-limpieza.md` los de la tarea activa. El panel y sus componentes de interfaz pertenecen a `plan-consultar-panel-limpieza.md`. `StartCleaningCommand` contiene `roomId` y `memberId` (tomado del token).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base (rol `CLEANING_STAFF`, layout de limpieza de T016, `ClockConfig`).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que la tabla `cleaning_task` y sus índices únicos parciales existan (plan base, T018) (FR-006, FR-013; regla 9 del plan base).
- [ ] T002 [P] Implementar `CleaningTask` (dominio), `CleaningTaskJpaEntity`, `CleaningTaskRepositoryPort` y `JpaCleaningTaskRepositoryAdapter`, con las consultas "tarea abierta por miembro" y "última tarea cerrada con `outcome` `Completed` o `DamageReported` por habitación".
- [ ] T003 [P] Registrar en `GlobalExceptionHandler` los códigos `ROOM_INVALID_STATE`, `ACTIVE_TASK_EXISTS`, `CONCURRENT_UPDATE` y `ROOM_NOT_FOUND`, y mapear las violaciones de los índices únicos y del bloqueo optimista a `409 CONCURRENT_UPDATE` o `ACTIVE_TASK_EXISTS` según el índice.

---

## Phase 3: User Story 1 - Marcar habitación en limpieza (Priority: P1)

**Goal**: Que un miembro del Personal de limpieza inicie desde el panel la limpieza de una habitación, quedando vinculado a la tarea y redirigido a la vista de tarea activa.

**Independent Test**: Con un miembro sin tareas abiertas, iniciar la limpieza de una habitación en `PendingCleaning` y otra vez (con otro miembro) en `Available`; verificar el estado `InCleaning`, la `CleaningTask` y el registro en `room_state_history`. Intentarlo sobre `Occupied` y con un miembro que ya tiene tarea, y verificar los rechazos.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `StartCleaningServiceTest`: inicio desde `PendingCleaning` crea la tarea y transiciona a `InCleaning` (HU-1 esc. 1; FR-002, FR-003, FR-004).
- [ ] T005 [P] [US1] Unit test: inicio desde `Available` sin exigir check-out previo (HU-1 esc. 2).
- [ ] T006 [P] [US1] Unit test: rechazo desde cualquier otro estado con `ROOM_INVALID_STATE` y el estado actual (HU-1 esc. 3; FR-005).
- [ ] T007 [P] [US1] Unit test: rechazo con `ACTIVE_TASK_EXISTS` si el miembro ya tiene una tarea abierta (HU-1 esc. 4; FR-013).
- [ ] T008 [P] [US1] Unit test con `Clock` fijo: `startDateTime` de la tarea y `start_date_time` del periodo en `room_state_history` son iguales y no se toman del cliente (FR-010, FR-011, FR-014; caso límite 4; SC-002).
- [ ] T009 [US1] Integration test con Testcontainers: dos miembros inician la misma habitación a la vez; solo uno tiene éxito y el otro recibe `409` con el estado `InCleaning` (FR-006; caso límite 1).
- [ ] T010 [US1] Integration test: un fallo forzado al guardar el registro del historial revierte la transición y la tarea (FR-016).
- [ ] T011 [P] [US1] Component test de la acción "Iniciar limpieza": muestra la retroalimentación de la acción, redirige a la vista de tarea activa al tener éxito y, si se rechaza, muestra el motivo y vuelve al panel recargado (FR-001, FR-009, FR-017).
- [ ] T012 [US1] Integration test: un usuario sin token recibe `401` y uno sin rol `CLEANING_STAFF` recibe `403` (FR-001; caso límite 2).

### Implementation for User Story 1

- [ ] T013 [US1] Implementar `StartCleaningService` (`@Transactional`) (FR-001 a FR-006, FR-010, FR-011, FR-013, FR-014, FR-016):
  - Rechazar si el miembro tiene una tarea abierta (`ACTIVE_TASK_EXISTS`).
  - Validar que la habitación exista y esté en `PendingCleaning` o `Available` (`ROOM_INVALID_STATE` con `currentStatus`).
  - Tomar la hora con `Clock`; invocar `TransitionRoomStateUseCase.transition(roomId, InCleaning, miembro, START_CLEANING, null)`, que registra el periodo en `room_state_history` (FR-014), y crear la `CleaningTask` con la marca de tiempo del periodo que devuelve la transición (FR-010, FR-011).
- [ ] T014 [US1] Implementar `CleaningController` con el endpoint `POST /api/cleaning/tasks` y `@PreAuthorize("hasRole('CLEANING_STAFF')")`, tomando el miembro del token. `plan-consultar-panel-limpieza.md` le agrega `GET /api/cleaning/panel`.
- [ ] T015 [US1] Mostrar la retroalimentación de "Iniciar limpieza" en el panel: éxito con redirección a la vista de tarea activa y errores con el texto de la tabla de errores (y, ante fallos de red, la sugerencia de reintentar), volviendo al panel recargado (FR-001, FR-009, FR-016, FR-017).

**Checkpoint**: El Personal de limpieza puede iniciar limpiezas desde el panel (`plan-consultar-panel-limpieza.md`); la vista de tarea activa a la que se redirige se completa con `plan-confirmar-fin-de-limpieza.md`.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T016 Integration test: tras iniciar la limpieza, `GET /api/rooms?status=InCleaning` incluye la habitación en menos de 2 segundos (SC-003).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), la tabla `cleaning_task` (T018), `SourceFlow` y `ActiveTaskExistsException` (T020), el rol `CLEANING_STAFF` (T015) y el layout de limpieza (T016).
- **Planes relacionados**:
  - `plan-consultar-panel-limpieza.md`: implementa el panel desde el que se inicia la limpieza.
  - `plan-confirmar-fin-de-limpieza.md`: implementa la vista de tarea activa a la que redirige este plan, y el cierre y la liberación de la `CleaningTask`.
  - `plan-marcar-pendiente-a-limpieza.md`: deja las habitaciones en `PendingCleaning` que se pueden limpiar.
- **Orden**: Phase 2 → Phase 3 → Phase 4. Este plan va antes de `plan-confirmar-fin-de-limpieza.md`.
