# Implementation Plan: Confirmar Fin de Limpieza de Habitación

**Date**: 2026-10-08  
**Spec**: [spec-confirmar-fin-de-limpieza.md](../SPEC/spec-confirmar-fin-de-limpieza.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Confirmar Fin de Limpieza de Habitación** para el Personal de limpieza, sobre la **vista de tarea activa** (FR-006), que tiene dos acciones:

1. **Confirmar fin** (FR-001 a FR-005, FR-007 a FR-012, FR-015, FR-017, FR-018): tras un diálogo de confirmación con un campo opcional de daño, cierra la `CleaningTask` y, en una sola transacción:
   - delega `InCleaning` → `Available` en *Marcar Habitación como Disponible* (FR-003);
   - si se describió un daño, invoca *Marcar Habitación Inhabilitada por Reparaciones* y la habitación termina en `DisabledForRepairs` (FR-011); aunque tenga llegada hoy no se aparta, y Recepción la ve con la alerta de su panel (FR-019);
   - si no hay daño y la habitación tiene una llegada pendiente hoy (ver *Llegada pendiente* en el diccionario), invoca *Marcar Habitación como Reservada* (FR-011);
   - si no hay daño ni llegada pendiente y la habitación tiene un mantenimiento programado (`Scheduled`) cuyo rango contiene hoy, se lo aplica mediante *Programar Bloqueo Técnico para Habitación* y la habitación termina en `TechnicalBlock` (FR-020). La prioridad es según la regla 6 de la máquina de estados.
2. **Liberar tarea** (FR-013, FR-014, FR-016): cierra la tarea con `outcome = Released` y devuelve la habitación a `PendingCleaning` mediante *Marcar Pendiente a Limpieza*.

En ambos casos el miembro queda desvinculado y vuelve al panel de limpieza. Además, el plan expone el puerto interno `ReleaseCleaningTaskOnDeactivationUseCase`, sin endpoint, que libera la tarea abierta de un miembro cuando el Administrador desactiva su cuenta (FR-014, HU-1 esc. 8).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa las tablas `cleaning_task` y `damage_report` (V3, plan base T017), cuyos registros crean *Marcar habitación en limpieza* (`plan-marcar-habitacion-en-limpieza.md`) y *Marcar habitación inhabilitada por reparaciones* (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`); `room` y `room_state_history`; y `daily_reservation_room` (V1, plan base T007), cuyos registros mantiene `plan-consultar-reservas.md`.
- **Performance Goals**: no define metas propias.
- **Constraints**:
  - Solo el miembro dueño de la tarea puede confirmarla o liberarla, salvo la liberación por desactivación de su cuenta (FR-002, FR-005, FR-013, FR-014).
  - Confirmar y liberar bloquean la fila de `room` con `RoomRepositoryPort.lockById` (regla 13 del plan base, T008); por eso la segunda de dos solicitudes simultáneas recibe siempre `TASK_ALREADY_CLOSED` (FR-016).
  - `EndDateTime` lo genera el servidor: es la marca de la primera transición del flujo (`InCleaning` → `Available` al confirmar, `InCleaning` → `PendingCleaning` al liberar), de modo que coincide con el inicio del periodo en `room_state_history`. Que no sea anterior a `StartDateTime` es una invariante del dominio que garantiza `CleaningTask.close` (FR-008); no se modifica después (FR-007, FR-009).
  - Todas las transiciones del flujo, el cierre de la tarea y los registros en el historial van en una sola transacción; cualquier fallo deja la habitación en `InCleaning` con la tarea activa (FR-011, FR-017).
  - El paso a `Reserved` es autónomo y no lo elige el usuario (FR-010).
  - La alerta operativa a Recepción (FR-019) no es un componente de este plan: la produce el panel de recepción a partir del estado `DisabledForRepairs` (regla 11 del plan base).
  - Caso límite sin implementación propia: 1 (que la habitación no haya quedado limpia lo supervisa Gerencia con el historial de estados).
  - Riesgo aceptado: si el miembro inicia una tarea en el mismo instante en que el Administrador desactiva su cuenta, las dos operaciones pueden confirmarse, porque ninguna bloquea a la otra.

---

## Convenciones de los endpoints

- **Fechas**: ISO 8601 con zona de Colombia (ej. `2026-10-08T10:05:00-05:00`).
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Usuario actual**: el miembro del Personal de limpieza se toma del encabezado `X-User` (regla 14 del plan base).
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).

---

## POST /api/cleaning/tasks/{taskId}/complete

**Descripción:** Confirma el fin de la limpieza. Cierra la tarea y pasa la habitación a `Available`; en la misma transacción, y según la regla 6 de la máquina de estados, termina en `DisabledForRepairs` si se describió un daño, en `Reserved` si tiene llegada pendiente hoy, en `TechnicalBlock` si tiene un mantenimiento programado que contiene hoy, o se queda en `Available` (FR-002 a FR-005, FR-007 a FR-012, FR-015 a FR-018, FR-020).

### Petición (Request)

**Headers:**

```http
X-User: jramirez
Content-Type: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `taskId` | path | UUID | Sí | Tarea activa del miembro | `c0ffee00-1234-4abc-9def-0123456789ab` |

**Body (JSON):** `damageDescription` es opcional (máximo 500 caracteres); vacío o solo espacios equivale a no reportar daño (FR-018); `CompleteCleaningCommand` lo normaliza.

```json
{
  "damageDescription": "Grifo del lavamanos con fuga constante."
}
```

### Respuesta (Response)

**Status Code:** `204 No Content`

**Body:** No aplica; el frontend solo redirige al panel.

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | `damageDescription` supera 500 caracteres | "La descripción del daño admite máximo 500 caracteres." |
| 403 | `TASK_NOT_OWNED` | `TaskNotOwnedException` | La tarea pertenece a otro miembro (FR-005, HU-1 esc. 2) | "Esta habitación la está limpiando otro compañero." |
| 404 | `TASK_NOT_FOUND` | `TaskNotFoundException` | No existe la tarea | "No encontramos esta limpieza. Vuelve al panel." |
| 409 | `TASK_ALREADY_CLOSED` | `TaskAlreadyClosedException` | La tarea ya fue confirmada o liberada por otra solicitud simultánea (FR-016) | "Esta tarea ya fue cerrada." |

```json
{
  "errorCode": "TASK_NOT_OWNED",
  "message": "Esta habitación la está limpiando otro compañero.",
  "timestamp": "2026-10-08T10:05:01-05:00",
  "path": "/api/cleaning/tasks/c0ffee00-1234-4abc-9def-0123456789ab/complete"
}
```

Si falla la persistencia o la conexión, la transacción se revierte completa (habitación en `InCleaning`, tarea activa) y la interfaz sugiere reintentar (FR-017).

---

## POST /api/cleaning/tasks/{taskId}/release

**Descripción:** Libera la tarea activa sin terminarla: la cierra con `outcome = Released` y devuelve la habitación a `PendingCleaning` mediante *Marcar Pendiente a Limpieza* (FR-013, FR-014, FR-016, FR-017).

### Petición (Request)

**Headers:**

```http
X-User: jramirez
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `taskId` | path | UUID | Sí | Tarea activa del miembro | `c0ffee00-1234-4abc-9def-0123456789ab` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `204 No Content`

**Body:** No aplica; el frontend solo redirige al panel.

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |
| 403 | `TASK_NOT_OWNED` | `TaskNotOwnedException` | La tarea pertenece a otro miembro (FR-013, FR-014) | "Esta habitación la está limpiando otro compañero." |
| 404 | `TASK_NOT_FOUND` | `TaskNotFoundException` | No existe la tarea | "No encontramos esta limpieza. Vuelve al panel." |
| 409 | `TASK_ALREADY_CLOSED` | `TaskAlreadyClosedException` | La tarea ya fue confirmada o liberada (FR-016) | "Esta tarea ya fue cerrada." |

```json
{
  "errorCode": "TASK_ALREADY_CLOSED",
  "message": "Esta tarea ya fue cerrada.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/cleaning/tasks/c0ffee00-1234-4abc-9def-0123456789ab/release"
}
```

---

## Puerto interno: liberación por desactivación

No tiene endpoint. Lo invoca *Gestionar personal* al desactivar la cuenta de un miembro, dentro de su transacción (FR-014, HU-1 esc. 9):

```java
public interface ReleaseCleaningTaskOnDeactivationUseCase {
    // Debe ejecutarse dentro de la transacción de la desactivación (propagation = MANDATORY).
    void releaseOpenTaskOf(String memberId, String actorId); // ambos = username (memberId: dueño de la tarea; actorId: Administrador)
}
```

Si el miembro no tiene tarea abierta, o si al releerla tras el bloqueo ya está cerrada, no hace nada y no lanza excepción. Si la liberación falla, la desactivación se revierte.

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-confirmar-fin-de-limpieza.md
│   ├── spec-marcar-habitacion-como-disponible.md
│   ├── spec-marcar-habitacion-reservada.md
│   ├── spec-marcar-habitacion-inhabilitada-por-reparaciones.md
│   └── spec-marcar-pendiente-a-limpieza.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-confirmar-fin-de-limpieza.md
    ├── plan-marcar-habitacion-en-limpieza.md
    ├── plan-marcar-habitacion-inhabilitada-por-reparaciones.md
    ├── plan-marcar-pendiente-a-limpieza.md
    └── plan-consultar-reservas.md
```

### Source Code

```text
backend/src/main/java/com/hospitua/habitaciones/
├── application/
│   ├── ports/in/
│   │   ├── CompleteCleaningUseCase.java
│   │   ├── ReleaseCleaningTaskUseCase.java
│   │   └── ReleaseCleaningTaskOnDeactivationUseCase.java
│   ├── service/
│   │   ├── CompleteCleaningService.java
│   │   └── ReleaseCleaningTaskService.java          # Implementa los dos puertos de liberación
│   └── dto/
│       └── CompleteCleaningCommand.java
└── infrastructure/adapters/in/web/
    └── CleaningController.java                    # Se agregan los 2 endpoints de este plan
frontend/src/
├── pages/limpieza/
│   ├── ActiveCleaningTaskPage.tsx
│   ├── ConfirmCleaningEndDialog.tsx               # Confirmación con campo opcional de daño
│   └── ReleaseCleaningTaskDialog.tsx
└── api/
    └── cleaningService.ts                         # Se agregan las 2 llamadas de este plan
```

**Structure Decision**: Los servicios de este plan no reimplementan transiciones: usan los puertos `MarkRoomAvailableUseCase` (*Marcar Habitación como Disponible*), `MarkRoomReservedUseCase` (*Marcar Habitación como Reservada*), `ReportDamageUseCase` (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`) y `MarkPendingCleaningUseCase` (`plan-marcar-pendiente-a-limpieza.md`), todos dentro de la misma transacción. `CompleteCleaningCommand` contiene `taskId`, `memberId` (usuario actual) y `damageDescription` (opcional). Los dos endpoints responden `204` sin cuerpo. `ReleaseCleaningTaskService` implementa `ReleaseCleaningTaskUseCase` (endpoint) y `ReleaseCleaningTaskOnDeactivationUseCase` (desactivación) con un mismo flujo de liberación. La vista de tarea activa toma sus datos de `GET /api/cleaning/panel` (`activeTask`, `plan-consultar-panel-limpieza.md`).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar los puertos que invoca este plan: `MarkRoomAvailableUseCase` (`plan-marcar-habitacion-como-disponible.md`, T002), `MarkRoomReservedUseCase` (`plan-marcar-habitacion-reservada.md`, T003), `MarkPendingCleaningUseCase` con su resultado `MarkPendingCleaningResult` (`plan-marcar-pendiente-a-limpieza.md`, T003) `ReportDamageUseCase` (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`, T012) y `ApplyScheduledTechnicalBlocksUseCase.applyForRoom` (`plan-programar-bloqueo-tecnico-para-habitacion.md`, T021).
- [ ] T002 [P] Verificar las excepciones de tareas del plan base (T019): `TaskNotOwnedException` (403), `TaskNotFoundException` (404), y `TaskAlreadyClosedException` (409).
- [ ] T003 [P] Verificar que `CleaningTaskRepositoryPort` tenga `save`, `findById`, `findRoomIdByTaskId` y `findOpenByMember` (`plan-marcar-habitacion-en-limpieza.md`, T002), que la entidad `CleaningTask` tenga `ensureClosableBy` y `close` (mismo plan), verificar `RoomRepositoryPort.lockById` (plan base, T008) y que `DailyReservationRepositoryPort` tenga `findPendingArrivalReservationRef` (`plan-consultar-reservas.md`, T007 y T011).

---

## Phase 3: User Story 1 - Finalización de limpieza y disponibilidad de habitación (Priority: P1)

**Goal**: Que el miembro con una tarea activa pueda confirmar el fin (con o sin daño) o liberar la tarea desde su vista, dejando la habitación en el estado correcto y volviendo al panel.

**Independent Test**: Con una tarea activa sobre una habitación en `InCleaning`, confirmar el fin y verificar `Available`; repetir con una reserva de hoy en la copia local y verificar `Reserved`; repetir con descripción de daño y verificar `DisabledForRepairs` con su `DamageReport`; liberar otra tarea y verificar `PendingCleaning`. Intentar con la tarea de otro miembro y verificar el rechazo.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `CompleteCleaningServiceTest`: fin sin llegada pendiente deja la habitación en `Available` vía `MarkRoomAvailableUseCase` y la tarea con `outcome = Completed` (HU-1 esc. 1; FR-003, FR-004, FR-015).
- [ ] T005 [P] [US1] Unit test: rechazo con `TASK_NOT_OWNED` si la tarea es de otro miembro (HU-1 esc. 2; FR-002, FR-005).
- [ ] T006 [P] [US1] Unit test: con una llegada pendiente hoy, la habitación termina en `Reserved` vía `MarkRoomReservedUseCase`; si la reserva del día ya tiene `Stay` para esa habitación, no hay llegada pendiente y termina en `Available`. Sin daño ni llegada pendiente invoca `applyForRoom`; con llegada pendiente o con daño no lo invoca (HU-1 esc. 3 y 7; FR-010, FR-020).
- [ ] T007 [P] [US1] Unit test: con descripción de daño, invoca `ReportDamageUseCase`, la habitación termina en `DisabledForRepairs` y `outcome = DamageReported`; aunque la habitación tenga una llegada pendiente hoy, no se invoca `MarkRoomReservedUseCase` (HU-1 esc. 4 y 6; caso límite 2; FR-011, FR-015, FR-019).
- [ ] T008 [P] [US1] Unit test en `ReleaseCleaningTaskServiceTest`: cierra con `outcome = Released`, invoca `MarkPendingCleaningUseCase` con `RELEASE_CLEANING_TASK` y el miembro como `actorId`, no verifica llegadas ni daño y rechaza tareas de otros miembros (HU-1 esc. 5; FR-013).
- [ ] T009 [P] [US1] Unit test con `Clock` fijo: `endDateTime` es la marca de la primera transición del flujo (igual al inicio del periodo `Available` o `PendingCleaning`) (FR-007; SC-001).
- [ ] T010 [US1] Integration test con Testcontainers: confirmación y liberación simultáneas sobre la misma tarea; solo una tiene éxito y la otra recibe `TASK_ALREADY_CLOSED` (FR-016).
- [ ] T011 [US1] Integration test: un fallo forzado en `ReportDamageUseCase` revierte todo y deja la habitación en `InCleaning` con la tarea activa (FR-011, FR-017).
- [ ] T012 [US1] Integration test: los periodos en `room_state_history` quedan registrados en cada camino, sin duplicados (FR-012; FR-009 de *Marcar Habitación Inhabilitada por Reparaciones*):

  | Camino | Periodo 1 | Periodo 2 |
  | --- | --- | --- |
  | Sin daño ni llegada pendiente | `Available` (`CONFIRM_CLEANING_END`, actor = miembro) | — |
  | Llegada pendiente hoy | `Available` (`CONFIRM_CLEANING_END`, actor = miembro) | `Reserved` (`MARK_RESERVED`, actor nulo, `reservation_ref` de la llegada) |
  | Con daño | `Available` (`CONFIRM_CLEANING_END`, actor = miembro) | `DisabledForRepairs` (`CONFIRM_CLEANING_END`, actor = miembro) |
  | Mantenimiento programado que contiene hoy, sin daño ni llegada pendiente | `Available` (`CONFIRM_CLEANING_END`, actor = miembro) | `TechnicalBlock` (`TECHNICAL_BLOCK`, actor nulo), con el informe en `Applied` |
  | Liberar tarea | `PendingCleaning` (`RELEASE_CLEANING_TASK`, actor = miembro) | — |

  En los caminos de dos periodos, el periodo `Available` dura lo que separa ambas transiciones (cero con el reloj fijo de las pruebas) y es válido.
- [ ] T013 [P] [US1] Component test en `ActiveCleaningTaskPage`: solo muestra "Confirmar fin" y "Liberar tarea"; el diálogo de confirmación tiene el campo opcional de daño (máximo 500), advierte la inhabilitación si se escribe un daño y cancelar no envía nada; tras éxito redirige al panel; si `GET /api/cleaning/panel` devuelve `activeTask` nulo, redirige al panel (FR-006, FR-013, FR-018).
- [ ] T014 [P] [US1] Unit test en `ReleaseCleaningTaskServiceTest`: `releaseOpenTaskOf` sin tarea abierta no hace nada; con tarea abierta invoca `MarkPendingCleaningUseCase` con `RELEASE_CLEANING_TASK` y el Administrador como `actorId` y cierra la tarea con `outcome = Released` sin validar el dueño; si al releerla tras el bloqueo ya está cerrada, no hace nada ni lanza excepción (HU-1 esc. 8; FR-014).
- [ ] T015 [US1] Integration test con Testcontainers: invocar `releaseOpenTaskOf` dentro de una transacción abierta por la prueba (como la desactivación de *Gestionar personal*) para un miembro con tarea abierta deja la habitación en `PendingCleaning`, la tarea con `outcome = Released` y el periodo con actor = Administrador; si la transacción falla después, todo se revierte (HU-1 esc. 8; FR-014).

### Implementation for User Story 1

- [ ] T016 [US1] Implementar `CompleteCleaningService` (`@Transactional`) (FR-002 a FR-005, FR-007 a FR-012, FR-015, FR-017, FR-020), en este orden:
  1. Leer solo el `roomId` de la tarea con `CleaningTaskRepositoryPort.findRoomIdByTaskId`; si no existe, `TaskNotFoundException`.
  2. Bloquear la fila con `RoomRepositoryPort.lockById(roomId)` (regla 13 del plan base), que es la primera carga de la habitación en la transacción, antes de consultar la copia local, para que un `ADDED` simultáneo no deje la llegada sin apartar.
  3. Releer la tarea completa con `findById`, ya fresca tras el bloqueo.
  4. `task.ensureClosableBy(memberId)`: primero el dueño (`TASK_NOT_OWNED`) y después que siga abierta (`TASK_ALREADY_CLOSED`).
  5. Ejecutar las transiciones:
     - Invocar `MarkRoomAvailableUseCase` (`InCleaning` → `Available`) con `sourceFlow = CONFIRM_CLEANING_END` y el miembro como `actorId`. "Hoy" es `markAvailableResult.transitionDateTime().toLocalDate()`, la fecha de esa transición con su desfase −05:00 (regla 6 del plan base).
     - Si hay daño: invocar `ReportDamageUseCase` con `sourceFlow = CONFIRM_CLEANING_END` y asignar `outcome = DamageReported`; no se consulta la copia local ni se invoca `MarkRoomReservedUseCase` (FR-019).
     - Si no hay daño: si la habitación tiene una llegada pendiente hoy (ver *Llegada pendiente* en el diccionario; consultada con `DailyReservationRepositoryPort.findPendingArrivalReservationRef`), invocar `MarkRoomReservedUseCase` con esa `reservationRef` y `trigger = CLEANING_END`, y aceptar su resultado sin revertir (con la habitación bloqueada y en `Available`, siempre es `RESERVED`); si no la tiene, invocar `ApplyScheduledTechnicalBlocksUseCase.applyForRoom(roomId, hoy)`, que aplica el mantenimiento programado que contiene hoy si existe; se une a la transacción y vuelve a bloquear la fila sin esperar (regla 13) (FR-020; regla 6 de la máquina de estados). Asignar `outcome = Completed`.
  6. Cerrar la tarea con `task.close(outcome, markAvailableResult.transitionDateTime())` y guardarla (FR-007, FR-008).
  - Cada transición registra su periodo a través de `TransitionRoomStateUseCase` (regla 8 del plan base); este servicio no invoca `RoomStateHistoryRecorder`.
- [ ] T017 [US1] Implementar `ReleaseCleaningTaskService`, un servicio compartido por el endpoint de liberar y por el puerto de desactivación (FR-013, FR-014, FR-016, FR-017):
  - Endpoint (`ReleaseCleaningTaskUseCase`, `@Transactional`): los pasos 1 a 4 de T016 (`findRoomIdByTaskId`, `lockById`, releer la tarea, `ensureClosableBy`); invocar `MarkPendingCleaningUseCase` con `RELEASE_CLEANING_TASK` y el miembro como `actorId`; cerrar con `task.close(Released, MarkPendingCleaningResult.transitionDateTime)`.
  - Desactivación (`ReleaseCleaningTaskOnDeactivationUseCase.releaseOpenTaskOf(memberId, actorId)`, `@Transactional(propagation = MANDATORY)`): `findOpenByMember(memberId)`; si no hay tarea, no hace nada. Si la hay, `lockById(roomId)` y releer la tarea; si ya está cerrada, no hace nada (no lanza excepción); si sigue abierta, invocar `MarkPendingCleaningUseCase` con `RELEASE_CLEANING_TASK` y el Administrador como `actorId`, y `task.close(Released, marca de la transición)`. No valida el dueño.
- [ ] T018 [US1] Agregar a `CleaningController` los endpoints `POST /api/cleaning/tasks/{taskId}/complete` y `POST /api/cleaning/tasks/{taskId}/release`, que responden `204` sin cuerpo (FR-001, FR-006).
- [ ] T019 [US1] Construir en el frontend `ActiveCleaningTaskPage.tsx`, que toma sus datos de `GET /api/cleaning/panel` (`activeTask` con la fila del panel) al cargar, tras iniciar una tarea o al recargar, y redirige al panel si `activeTask` es nulo (habitación con el formato del panel y botones "Confirmar fin" y "Liberar tarea"), `ConfirmCleaningEndDialog.tsx` ("¿Está seguro que desea finalizar la limpieza de la habitación X?", campo "Describir daño encontrado" opcional de máximo 500 caracteres y advertencia de inhabilitación) y `ReleaseCleaningTaskDialog.tsx` ("¿Está seguro que desea liberar la limpieza de la habitación X?"); al terminar, redirigir al panel (FR-001, FR-006, FR-013, FR-018).
- [ ] T020 [US1] Mostrar los errores de los endpoints con el texto de las tablas de este plan según el `errorCode` (el `message` de la respuesta es solo un texto de respaldo) y, ante fallos de red, la sugerencia de reintentar (FR-017).

**Checkpoint**: El ciclo de limpieza queda completo: iniciar (plan anterior), confirmar con o sin daño y liberar.

---

## Phase 4: Polish & Cross-Cutting Concerns

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), `RoomRepositoryPort.lockById` (regla 13, T008), las excepciones de tareas y `SourceFlow` (T019) y `ClockConfig`.
- **Planes de los que depende**:
  - `plan-marcar-habitacion-en-limpieza.md`: los registros de `cleaning_task` (creados al iniciar la limpieza), `CleaningTaskRepositoryPort` con `findRoomIdByTaskId`, `findById`, `findOpenByMember` y `save`, la entidad `CleaningTask` con `ensureClosableBy` y `close`, y `CleaningController`.
  - `plan-consultar-panel-limpieza.md`: el panel al que se vuelve, que entrega `activeTask` a la vista de tarea activa.
  - `plan-marcar-pendiente-a-limpieza.md`: `MarkPendingCleaningUseCase` para la liberación.
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: `ReportDamageUseCase`, que crea el registro de `damage_report` en el fin con daño.
  - `plan-consultar-reservas.md`: `DailyReservationRepositoryPort.findPendingArrivalReservationRef` sobre la copia local, para detectar la llegada pendiente de hoy.
  - `plan-programar-bloqueo-tecnico-para-habitacion.md`: `ApplyScheduledTechnicalBlocksUseCase.applyForRoom` (T021), para aplicar el mantenimiento programado que contiene hoy.
  - `plan-marcar-habitacion-como-disponible.md` y `plan-marcar-habitacion-reservada.md`: los puertos transversales `MarkRoomAvailableUseCase` y `MarkRoomReservedUseCase` (plan base T018 los delega en esos planes).
- **Plan que lo invoca**: `plan-gestionar-personal.md` invoca `ReleaseCleaningTaskOnDeactivationUseCase` al desactivar una cuenta.
- **Orden**: Phase 2 → Phase 3 → Phase 4. Para el camino con daño, la Phase 2 de `plan-marcar-habitacion-inhabilitada-por-reparaciones.md` debe estar lista. El camino del mantenimiento programado (la invocación a `applyForRoom` en T016, y sus casos en T006 y T012) se completa cuando exista T021 de `plan-programar-bloqueo-tecnico-para-habitacion.md`, que va después en el orden del plan base (paso 6).
