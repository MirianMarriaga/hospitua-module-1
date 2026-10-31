# Implementation Plan: Marcar Habitación en Limpieza

**Date**: 2026-10-08  
**Spec**: [spec-marcar-habitacion-en-limpieza.md](../SPEC/spec-marcar-habitacion-en-limpieza.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Marcar Habitación en Limpieza** para el actor Personal de limpieza:

- **Origen** (*Consultar Panel de Limpieza* FR-004, FR-005 y FR-007): la acción **Iniciar limpieza** del panel de limpieza, que implementa `plan-consultar-panel-limpieza.md`.
- **Iniciar limpieza** (FR-001 a FR-004, FR-006, FR-007, FR-008, FR-009): en una sola transacción, bloquea la fila de la habitación (regla 13 del plan base), la transiciona de `PendingCleaning` o `Available` a `InCleaning` con `TransitionRoomStateUseCase.transition(...)` (regla 8 del plan base), crea la `CleaningTask` del miembro con `StartDateTime` del servidor y registra el periodo en `room_state_history`. Al terminar, redirige a la vista de tarea activa (FR-005); si se rechaza, informa el motivo y vuelve al panel recargado (FR-010). La interfaz de la acción (botón, llamada, mensajes y navegación) la implementa `plan-consultar-panel-limpieza.md` con los textos de la tabla de errores de este plan.

Usa la tabla `cleaning_task` del plan base (T017), que también usan *Confirmar Fin de Limpieza de Habitación* (cierre y liberación) y *Consultar Panel de Limpieza* (última limpieza).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `cleaning_task`, `room` y `room_state_history` del plan base (T017 y T007).
- **Performance Goals**: sin metas propias; el tiempo de carga del panel lo fija `plan-consultar-panel-limpieza.md` (SC-001).
- **Constraints**:
  - Una sola tarea abierta por miembro y por habitación (FR-004, FR-007; regla 9 del plan base), garantizado con el bloqueo de la fila de `room` (`RoomRepositoryPort.lockById`, regla 13 del plan base) y la validación del miembro; los índices únicos parciales quedan como respaldo de integridad.
  - `StartDateTime` lo genera el servidor con `Clock` y coincide con el inicio del periodo en `room_state_history` (FR-006, FR-008, SC-002).
  - La reasignación o liberación de tareas de otros miembros se rige por *Confirmar Fin de Limpieza de Habitación* (FR-014 y caso límite 4); la liberación se implementa en `plan-confirmar-fin-de-limpieza.md`.
  - Sin restricción de tiempo desde que la habitación quedó pendiente (caso límite 2).

---

## Convenciones de los endpoints

- **Fechas**: ISO 8601 con zona de Colombia (ej. `2026-10-08T09:15:00-05:00`).
- **Errores**: esquema `ApiError` del plan base, con el campo adicional `details` cuando aporta contexto (ej. `currentStatus`).
- **Usuario actual**: el miembro del Personal de limpieza se toma del encabezado `X-User` (regla 14 del plan base).
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).

---

## POST /api/cleaning/tasks

**Descripción:** Inicia la limpieza de una habitación: transiciona `PendingCleaning` o `Available` a `InCleaning`, crea la `CleaningTask` del miembro que realiza la acción y registra el periodo en `room_state_history`, todo en una transacción (FR-001 a FR-004, FR-006, FR-007, FR-008, FR-009).

### Petición (Request)

**Headers:**

```http
X-User: jramirez
Content-Type: application/json
```

**Body (JSON):** solo el identificador de la habitación; cualquier fecha enviada por el cliente se ignora (FR-006, caso límite 3).

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

**Body (JSON):** el frontend solo redirige a la vista de tarea activa, que toma sus datos del panel.

```json
{
  "taskId": "c0ffee00-1234-4abc-9def-0123456789ab"
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Falta `roomId` o no es un UUID válido | "Selecciona una habitación válida." |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` (`lockById` no encuentra la habitación) | El `roomId` no corresponde a ninguna habitación | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` (la lanza la transición a `InCleaning`) | La habitación no está en `PendingCleaning` ni `Available`, incluido el miembro que llega segundo tras el bloqueo y la encuentra en `InCleaning` (FR-003, FR-004, HU-1 esc. 3, caso límite 1) | "La habitación está {estado}; ahora no se puede limpiar." |
| 409 | `ACTIVE_TASK_EXISTS` | `ActiveTaskExistsException` (el servicio, o el adaptador al traducir el índice único del miembro, T002) | El miembro ya tiene una `CleaningTask` abierta (FR-007, HU-1 esc. 4) | "Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra." |
| 409 | `ROOM_TASK_EXISTS` | `RoomTaskExistsException` (el adaptador al traducir el índice único de tarea abierta por habitación, T002) | Respaldo del índice; con el bloqueo de la habitación no se alcanza en flujo normal | "Otro compañero ya está limpiando esta habitación." |

```json
{
  "errorCode": "ROOM_INVALID_STATE",
  "message": "La habitación está Ocupada; ahora no se puede limpiar.",
  "timestamp": "2026-10-08T09:15:02-05:00",
  "path": "/api/cleaning/tasks",
  "details": { "currentStatus": "Occupied" }
}
```

`message` es el texto de respaldo del catálogo del plan base; la interfaz (`plan-consultar-panel-limpieza.md`) muestra el "Texto en la interfaz" de esta tabla según el `errorCode`.

Si falla la persistencia o la conexión, la transacción se revierte completa y la interfaz muestra el error con la sugerencia de reintentar (FR-009).

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
│   │   └── model/
│   │       └── CleaningTask.java                 # Definido en el plan base; aquí se implementa con start, ensureClosableBy y close
│   ├── application/
│   │   ├── ports/
│   │   │   ├── in/
│   │   │   │   └── StartCleaningUseCase.java
│   │   │   └── out/
│   │   │       └── CleaningTaskRepositoryPort.java
│   │   ├── service/
│   │   │   └── StartCleaningService.java
│   │   └── dto/
│   │       ├── StartCleaningCommand.java
│   │       └── CleaningTaskStartedDto.java        # {taskId}
│   └── infrastructure/
│       └── adapters/
│           ├── in/web/
│           │   └── CleaningController.java
│           └── out/persistence/
│               ├── entity/CleaningTaskJpaEntity.java
│               └── adapter/CleaningTaskRepositoryAdapter.java
└── main/resources/db/migration/
    └── (sin migración propia: `cleaning_task` se crea en `V3__cleaning_maintenance_schema.sql` del plan base)
frontend/src/
└── api/
    └── cleaningService.ts
```

**Structure Decision**: `CleaningController` agrupa los endpoints `/api/cleaning/...`. Este plan lo crea vacío en su Phase 2 y le agrega `POST /api/cleaning/tasks`; `plan-consultar-panel-limpieza.md` le agrega el del panel y `plan-confirmar-fin-de-limpieza.md` los de la tarea activa. El panel, sus componentes y toda la interfaz de la acción "Iniciar limpieza" pertenecen a `plan-consultar-panel-limpieza.md`. `StartCleaningCommand` contiene `roomId` y `memberId` (usuario actual).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base (layout de limpieza de T015, `ClockConfig`).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [x] T001 Verificar que la tabla `cleaning_task` con sus índices únicos parciales (respaldo de integridad) y el índice parcial de última limpieza existan (plan base, T017), y verificar `RoomRepositoryPort.lockById` (plan base, T008) (FR-004, FR-007; reglas 9 y 13 del plan base).
  - Hecho (L17): la migración V3 ya tenía `uk_cleaning_task_open_by_member`, `uk_cleaning_task_open_by_room` e `idx_cleaning_task_last_cleaning`, y `lockById` existe desde el plan base; `CleaningTaskRepositoryAdapterTest` comprueba los dos índices únicos.
- [x] T002 [P] Implementar `CleaningTask` (dominio), `CleaningTaskJpaEntity`, `CleaningTaskRepositoryPort` y `CleaningTaskRepositoryAdapter`:
  - Hecho (L17): `CleaningTask` y `CleaningTaskOutcome` (`countsAsCleaning()` devuelve `Completed` y `DamageReported`); el constructor exige fecha de fin y resultado juntos, como el `CHECK` de V3, y `close` rechaza también una tarea ya cerrada. `findLastCleaningEndByRoomIds` no consulta si no recibe habitaciones o resultados. La búsqueda del nombre de la restricción violada pasó a `ConstraintViolations`, que comparte con `RoomRepositoryAdapter`.
  - Dominio, con las reglas de la tarea:
    - `static CleaningTask start(roomId, memberId, startDateTime)`.
    - `void ensureClosableBy(memberId)`: lanza `TaskNotOwnedException` si la tarea no es del miembro y después `TaskAlreadyClosedException` si ya está cerrada, en ese orden.
    - `void close(outcome, endDateTime)`: valida `endDateTime ≥ startDateTime` (si no, `IllegalStateException`, que atiende el manejador genérico de la regla 7 del plan base) y fija ambos campos. Liberar es `close(Released, marca)`.
  - Puerto:

    ```java
    CleaningTask save(CleaningTask task);
    Optional<CleaningTask> findById(UUID taskId);
    Optional<UUID> findRoomIdByTaskId(UUID taskId);
    Optional<CleaningTask> findOpenByMember(String memberId); // username
    Map<UUID, OffsetDateTime> findLastCleaningEndByRoomIds(Collection<UUID> roomIds, Set<CleaningTaskOutcome> countedOutcomes);
    ```

  - `findLastCleaningEndByRoomIds` es una sola consulta por lotes (`SELECT room_id, MAX(end_date_time) … WHERE outcome IN (:countedOutcomes) AND room_id IN (:roomIds) GROUP BY room_id`); los outcomes los da `CleaningTaskOutcome.countsAsCleaning()` del dominio.
  - El adaptador guarda con `saveAndFlush` y traduce la violación de cada índice único a `ActiveTaskExistsException` (miembro) o `RoomTaskExistsException` (habitación).

  El cierre y la última limpieza los usan `plan-confirmar-fin-de-limpieza.md` y `plan-consultar-panel-limpieza.md`.
- [x] T003 [P] Verificar en `GlobalExceptionHandler` que las excepciones de los códigos comunes `ROOM_INVALID_STATE`, `ACTIVE_TASK_EXISTS`, `ROOM_TASK_EXISTS` y `ROOM_NOT_FOUND` estén registradas (plan base, T009 y T019); la traducción de los índices la hace el adaptador (T002).
  - Hecho (L17): `GlobalExceptionHandler` atiende toda `DomainException` por su `DomainError`, así que `ROOM_INVALID_STATE`, `ACTIVE_TASK_EXISTS`, `ROOM_TASK_EXISTS` y `ROOM_NOT_FOUND` ya responden con su código; no hizo falta un `@ExceptionHandler` nuevo.
- [x] T004 [P] Crear `CleaningController` (`@RestController`, `@RequestMapping("/api/cleaning")`) sin endpoints, para que `plan-consultar-panel-limpieza.md` y `plan-confirmar-fin-de-limpieza.md` le agreguen los suyos aunque este plan termine después de ellos.
  - Hecho (L18): `CleaningController` se creó junto con su primer endpoint (T013).

---

## Phase 3: User Story 1 - Marcar habitación en limpieza (Priority: P1)

**Goal**: Que un miembro del Personal de limpieza inicie desde el panel la limpieza de una habitación, quedando vinculado a la tarea y redirigido a la vista de tarea activa.

**Independent Test**: Con un miembro sin tareas abiertas, iniciar la limpieza de una habitación en `PendingCleaning` y otra vez (con otro miembro) en `Available`; verificar el estado `InCleaning`, la `CleaningTask` y el registro en `room_state_history`. Intentarlo sobre `Occupied` y con un miembro que ya tiene tarea, y verificar los rechazos.

### Tests for User Story 1

- [x] T005 [P] [US1] Unit test en `StartCleaningServiceTest`: inicio desde `PendingCleaning` crea la tarea y transiciona a `InCleaning` (HU-1 esc. 1; FR-002, FR-003, FR-008).
  - Hecho (L18): `StartCleaningServiceTest`, con el orden bloqueo → tarea del miembro → transición → guardado.
- [x] T006 [P] [US1] Unit test: inicio desde `Available` sin exigir check-out previo (HU-1 esc. 2).
  - Hecho (L18): `StartCleaningServiceTest` y `StartCleaningIntegrationTest`, que además envía una fecha y comprueba que se ignora.
- [x] T007 [P] [US1] Unit test: rechazo desde cualquier otro estado con `ROOM_INVALID_STATE` y el estado actual (HU-1 esc. 3; FR-003).
  - Hecho (L18): unitario e integración (`409` con `currentStatus` y el mensaje del plan).
- [x] T008 [P] [US1] Unit test: rechazo con `ACTIVE_TASK_EXISTS` si el miembro ya tiene una tarea abierta (HU-1 esc. 4; FR-007).
  - Hecho (L18): unitario e integración; no se llama a la transición y la habitación no cambia.
- [x] T009 [P] [US1] Unit test con `Clock` fijo: el servicio pasa a `CleaningTask.start` la marca que devuelve la transición, sin tomar fechas del cliente (FR-006, FR-008; caso límite 3; SC-001).
  - Hecho (L18): `StartCleaningServiceTest` comprueba que la tarea recibe la marca que devuelve la transición; la integración, que coincide con el inicio del periodo en `room_state_history`.
- [x] T010 [US1] Integration test con Testcontainers: dos miembros inician la misma habitación a la vez; solo uno tiene éxito y el otro recibe `409 ROOM_INVALID_STATE` con `currentStatus = InCleaning` (FR-004; caso límite 1).
  - Hecho (L18): `StartCleaningIntegrationTest` lanza los dos inicios en paralelo contra el caso de uso: uno crea la tarea y el otro recibe `ROOM_INVALID_STATE` con `InCleaning`.
- [x] T011 [US1] Integration test: un fallo forzado al guardar el registro del historial revierte la transición y la tarea (FR-009).
  - Hecho (L18): el fallo se fuerza en el guardado de la tarea, la última escritura de la transacción (una tarea abierta previa hace saltar el índice único por habitación), en vez de en el historial: así no hace falta reemplazar un bean; la habitación sigue `PendingCleaning` y no queda ningún periodo en `room_state_history`.

### Implementation for User Story 1

- [x] T012 [US1] Implementar `StartCleaningService` (`@Transactional`) (FR-001 a FR-004, FR-006, FR-007, FR-008, FR-009):
  - Hecho (L18): `StartCleaningService`. La transición valida el estado; el servicio solo vuelve a lanzar su `InvalidRoomTransitionException` con el mensaje del plan ("La habitación está {estado}; ahora no se puede limpiar.") y el mismo `currentStatus`, y `ACTIVE_TASK_EXISTS` lleva el texto de la tabla.
  - Bloquear la habitación con `RoomRepositoryPort.lockById(roomId)` como primera carga de la transacción (regla 13 del plan base); si no existe, `RoomNotFoundException` (`ROOM_NOT_FOUND`).
  - Rechazar si el miembro tiene una tarea abierta (`findOpenByMember`; `ActiveTaskExistsException`, `ACTIVE_TASK_EXISTS`).
  - Invocar `TransitionRoomStateUseCase.transition(roomId, InCleaning, miembro, START_CLEANING, null)`, que registra el periodo en `room_state_history` (FR-008) y lanza `InvalidRoomTransitionException` (`ROOM_INVALID_STATE` con `currentStatus`) si la habitación no está en `PendingCleaning` ni `Available`; el servicio no repite esa validación.
  - Crear la tarea con `CleaningTask.start(roomId, miembro, marca)`, usando la marca del periodo que devuelve la transición (FR-006, FR-008), y guardarla con `save` (devuelve `{taskId}`).
- [x] T013 [US1] Agregar a `CleaningController` (T004) el endpoint `POST /api/cleaning/tasks`, tomando como miembro al usuario actual y respondiendo `201 {taskId}`.
  - Hecho (L18): `POST /api/cleaning/tasks` con `StartCleaningRequest` (`roomId` obligatorio); el miembro sale de `X-User` y responde `201 {taskId}`.

**Checkpoint**: El Personal de limpieza puede iniciar limpiezas desde el panel (`plan-consultar-panel-limpieza.md`, dueño de la interfaz de la acción); la vista de tarea activa a la que se redirige se completa con `plan-confirmar-fin-de-limpieza.md`.

---

## Phase 4: Polish & Cross-Cutting Concerns

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), `RoomRepositoryPort.lockById` (T008, regla 13), la tabla `cleaning_task` (T017), `SourceFlow` y `ActiveTaskExistsException` (T019) y el layout de limpieza (T015).
- **Planes relacionados**:
  - `plan-consultar-panel-limpieza.md`: implementa el panel desde el que se inicia la limpieza y toda la interfaz de la acción "Iniciar limpieza", con los textos de la tabla de errores de este plan.
  - `plan-confirmar-fin-de-limpieza.md`: implementa la vista de tarea activa a la que redirige este plan, y el cierre y la liberación de la `CleaningTask`.
  - `plan-marcar-pendiente-a-limpieza.md`: deja las habitaciones en `PendingCleaning` que se pueden limpiar.
- **Orden**: Phase 2 → Phase 3 → Phase 4. Este plan va antes de `plan-confirmar-fin-de-limpieza.md`.
