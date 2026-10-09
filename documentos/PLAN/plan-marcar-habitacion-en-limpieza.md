# Implementation Plan: Marcar Habitación en Limpieza

**Date**: 2026-10-08  
**Spec**: [spec-marcar-habitacion-en-limpieza.md](../SPEC/spec-marcar-habitacion-en-limpieza.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Marcar Habitación en Limpieza** para el actor Personal de limpieza, con dos piezas:

1. **Panel de limpieza** (FR-007, FR-008, FR-012, FR-017): lista las habitaciones en `PendingCleaning` y `Available` con número, tipo, estado, última limpieza y acciones (`PC`: Iniciar limpieza; `AVB`: Reportar daño, Iniciar limpieza), con búsqueda por número exacto. Si el miembro tiene una tarea activa, el panel lo redirige a la vista de tarea activa (FR-009), que define *Confirmar Fin de Limpieza de Habitación*.
2. **Iniciar limpieza** (FR-001 a FR-006, FR-010, FR-011, FR-013, FR-014, FR-016): en una sola transacción, transiciona la habitación de `PendingCleaning` o `Available` a `InCleaning` con `Room.transitionTo()`, crea la `CleaningTask` del miembro con `StartDateTime` del servidor y registra el periodo en `room_state_history`.

Usa la tabla `cleaning_task` del plan base (T018), que también usan *Confirmar Fin de Limpieza de Habitación* (cierre y liberación) y el panel (última limpieza).

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

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." (caso límite 2) |
| 403 | `FORBIDDEN` | El usuario no tiene el rol `CLEANING_STAFF` | "No tienes permiso para realizar esta acción." |

---

## GET /api/cleaning/panel

**Descripción:** Devuelve el panel de limpieza del miembro autenticado: las habitaciones en `PendingCleaning` y `Available` con su última limpieza, o la tarea activa del miembro para redirigirlo a su vista (FR-007, FR-008, FR-009, FR-012, FR-017).
**Rol autorizado:** `CLEANING_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomNumber` | query | string | No | Filtra por número de habitación con coincidencia exacta (FR-008) | `101` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `activeTaskId` | UUID \| null | Tarea abierta del miembro; si no es nula, el frontend redirige a la vista de tarea activa y `rooms` llega vacío (FR-009) | `null` |
| `rooms` | array | Habitaciones en `PendingCleaning` o `Available`, ordenadas por número (FR-007) | |
| `rooms[].roomId` | UUID | Identificador de la habitación | `"3f1c…"` |
| `rooms[].roomNumber` | string | Número de habitación | `"101"` |
| `rooms[].roomType` | string | Tipo: Sencilla, Doble, Suite o Boutique | `"Doble"` |
| `rooms[].status` | string | `PendingCleaning` o `Available` | `"PendingCleaning"` |
| `rooms[].lastCleaningDateTime` | string \| null | `EndDateTime` de la última `CleaningTask` con `outcome` `Completed` o `DamageReported`; nulo si no existe (el frontend muestra "Sin registro", FR-012) | `"2026-10-07T16:40:00-05:00"` |

**Body (JSON):**

```json
{
  "activeTaskId": null,
  "rooms": [
    {
      "roomId": "3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60",
      "roomNumber": "101",
      "roomType": "Doble",
      "status": "PendingCleaning",
      "lastCleaningDateTime": "2026-10-07T16:40:00-05:00"
    },
    {
      "roomId": "8a7b6c5d-4e3f-4a1b-8c9d-0e1f2a3b4c5d",
      "roomNumber": "102",
      "roomType": "Sencilla",
      "status": "Available",
      "lastCleaningDateTime": null
    }
  ]
}
```

Una búsqueda sin coincidencias o un panel sin habitaciones responde `200` con `rooms` vacío; el frontend muestra "No se encontró la habitación X" o "No hay habitaciones pendientes de limpieza ni disponibles" (FR-017).

### Respuestas de error

Solo los errores comunes (401, 403).

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

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Falta `roomId` o no es un UUID válido | "Selecciona una habitación válida." |
| 404 | `ROOM_NOT_FOUND` | El `roomId` no corresponde a ninguna habitación | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | La habitación no está en `PendingCleaning` ni `Available` (FR-005, HU-1 esc. 3) | "La habitación está en estado {estado}; no es posible iniciar la limpieza." |
| 409 | `ACTIVE_TASK_EXISTS` | El miembro ya tiene una `CleaningTask` abierta (FR-013, HU-1 esc. 4) | "Ya tienes una tarea de limpieza activa." |
| 409 | `CONCURRENT_UPDATE` | Otro miembro inició la limpieza de la misma habitación al mismo tiempo (FR-006, caso límite 1) | "Otro miembro acaba de tomar esta habitación (estado actual: En limpieza)." |

```json
{
  "errorCode": "ROOM_INVALID_STATE",
  "message": "La habitación está en estado Occupied; no es posible iniciar la limpieza.",
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
│   │       │   ├── StartCleaningUseCase.java
│   │       │   └── GetCleaningPanelUseCase.java
│   │       └── out/
│   │           └── CleaningTaskRepositoryPort.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── StartCleaningService.java
│   │   │   └── CleaningPanelQueryService.java
│   │   └── dto/
│   │       ├── StartCleaningCommand.java
│   │       ├── CleaningTaskStartedDto.java
│   │       └── CleaningPanelDto.java
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
├── pages/limpieza/
│   ├── CleaningPanelPage.jsx
│   ├── CleaningRoomTable.jsx
│   └── RoomNumberSearch.jsx
└── api/
    └── cleaningService.js
```

**Structure Decision**: `CleaningController` agrupa los endpoints `/api/cleaning/...`; `plan-confirmar-fin-de-limpieza.md` le agrega los de la tarea activa. El botón "Reportar daño" del panel abre el diálogo que define `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`. `StartCleaningCommand` contiene `roomId` y `memberId` (tomado del token).

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

**Goal**: Que un miembro del Personal de limpieza vea su panel, busque una habitación e inicie su limpieza, quedando vinculado a la tarea y redirigido a la vista de tarea activa.

**Independent Test**: Con un miembro sin tareas abiertas, cargar el panel, iniciar la limpieza de una habitación en `PendingCleaning` y otra vez (con otro miembro) en `Available`; verificar el estado `InCleaning`, la `CleaningTask` y el registro en `room_state_history`. Intentarlo sobre `Occupied` y con un miembro que ya tiene tarea, y verificar los rechazos.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `StartCleaningServiceTest`: inicio desde `PendingCleaning` crea la tarea y transiciona a `InCleaning` (HU-1 esc. 1; FR-002, FR-003, FR-004).
- [ ] T005 [P] [US1] Unit test: inicio desde `Available` sin exigir check-out previo (HU-1 esc. 2).
- [ ] T006 [P] [US1] Unit test: rechazo desde cualquier otro estado con `ROOM_INVALID_STATE` y el estado actual (HU-1 esc. 3; FR-005).
- [ ] T007 [P] [US1] Unit test: rechazo con `ACTIVE_TASK_EXISTS` si el miembro ya tiene una tarea abierta (HU-1 esc. 4; FR-013).
- [ ] T008 [P] [US1] Unit test con `Clock` fijo: `startDateTime` de la tarea y `start_date_time` del periodo en `room_state_history` son iguales y no se toman del cliente (FR-010, FR-011, FR-014; caso límite 4; SC-002).
- [ ] T009 [US1] Integration test con Testcontainers: dos miembros inician la misma habitación a la vez; solo uno tiene éxito y el otro recibe `409` con el estado `InCleaning` (FR-006; caso límite 1).
- [ ] T010 [US1] Integration test: un fallo forzado al guardar el registro del historial revierte la transición y la tarea (FR-016).
- [ ] T011 [P] [US1] Unit test en `CleaningPanelQueryServiceTest`: lista solo `PendingCleaning` y `Available`; `lastCleaningDateTime` ignora tareas `Released` y es nulo sin registro; con tarea abierta devuelve `activeTaskId` y `rooms` vacío; búsqueda por número exacto (FR-007, FR-008, FR-009, FR-012).
- [ ] T012 [P] [US1] Component test en `CleaningPanelPage`: botones por estado (`PC`: Iniciar limpieza; `AVB`: Reportar daño, Iniciar limpieza), "Sin registro", mensajes de búsqueda vacía y panel vacío, y redirección cuando hay `activeTaskId` (FR-007, FR-009, FR-012, FR-017).
- [ ] T013 [US1] Integration test: un usuario sin token recibe `401` y uno sin rol `CLEANING_STAFF` recibe `403` (FR-001; caso límite 2).

### Implementation for User Story 1

- [ ] T014 [US1] Implementar `StartCleaningService` (`@Transactional`) (FR-001 a FR-006, FR-010, FR-011, FR-013, FR-014, FR-016):
  - Rechazar si el miembro tiene una tarea abierta (`ACTIVE_TASK_EXISTS`).
  - Validar que la habitación exista y esté en `PendingCleaning` o `Available` (`ROOM_INVALID_STATE` con `currentStatus`).
  - Tomar la hora con `Clock`; invocar `TransitionRoomStateUseCase.transition(roomId, InCleaning, miembro, START_CLEANING, null)`, que registra el periodo en `room_state_history` (FR-014), y crear la `CleaningTask` con la marca de tiempo del periodo que devuelve la transición (FR-010, FR-011).
- [ ] T015 [US1] Implementar `CleaningPanelQueryService` con el filtro de estados, la búsqueda exacta por número, el cálculo de la última limpieza y la detección de tarea activa (FR-007, FR-008, FR-009, FR-012).
- [ ] T016 [US1] Implementar en `CleaningController` los endpoints `GET /api/cleaning/panel` y `POST /api/cleaning/tasks` con `@PreAuthorize("hasRole('CLEANING_STAFF')")`, tomando el miembro del token.
- [ ] T017 [US1] Construir en el frontend `CleaningPanelPage.jsx`, `CleaningRoomTable.jsx` y `RoomNumberSearch.jsx`: tabla con número, tipo, estado ("Disponible" / "Pendiente de limpieza"), última limpieza (DD-MM-YYYY HH:MM o "Sin registro") y botones por estado; búsqueda exacta; mensajes de FR-017; redirección a la vista de tarea activa al iniciar la limpieza o si `activeTaskId` no es nulo; el botón "Reportar daño" abre el diálogo de `plan-marcar-habitacion-inhabilitada-por-reparaciones.md` (FR-007, FR-008, FR-009, FR-012, FR-017).
- [ ] T018 [US1] Mostrar los errores de `POST /api/cleaning/tasks` con el texto de la tabla de errores y, ante fallos de red, la sugerencia de reintentar (FR-001, FR-016).

**Checkpoint**: El Personal de limpieza puede ver su panel, buscar e iniciar limpiezas de forma aislada; la vista de tarea activa a la que se redirige se completa con `plan-confirmar-fin-de-limpieza.md`.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T019 Integration test: tras iniciar la limpieza, `GET /api/rooms?status=InCleaning` incluye la habitación en menos de 2 segundos (SC-003).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), la tabla `cleaning_task` (T018), `SourceFlow` y `ActiveTaskExistsException` (T020), el rol `CLEANING_STAFF` (T015) y el layout de limpieza (T016).
- **Planes relacionados**:
  - `plan-confirmar-fin-de-limpieza.md`: implementa la vista de tarea activa a la que redirige este plan, y el cierre y la liberación de la `CleaningTask`.
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: implementa el diálogo "Reportar daño" que abre el botón del panel.
  - `plan-marcar-pendiente-a-limpieza.md`: deja las habitaciones en `PendingCleaning` que este panel lista.
- **Orden**: Phase 2 → Phase 3 → Phase 4. Este plan va antes de `plan-confirmar-fin-de-limpieza.md`.
