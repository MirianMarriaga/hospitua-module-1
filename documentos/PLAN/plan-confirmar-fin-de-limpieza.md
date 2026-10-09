# Implementation Plan: Confirmar Fin de Limpieza de Habitación

**Date**: 2026-10-08  
**Spec**: [spec-confirmar-fin-de-limpieza.md](../SPEC/spec-confirmar-fin-de-limpieza.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Confirmar Fin de Limpieza de Habitación** para el Personal de limpieza, sobre la **vista de tarea activa** (FR-007), que tiene dos acciones:

1. **Confirmar fin** (FR-001 a FR-006, FR-008 a FR-013, FR-015, FR-017, FR-018): tras un diálogo de confirmación con un campo opcional de daño, cierra la `CleaningTask` y, en una sola transacción:
   - delega `InCleaning` → `Available` en *Marcar habitación como disponible* (FR-003);
   - si se describió un daño, invoca *Marcar habitación inhabilitada por reparaciones* y la habitación termina en `DisabledForRepairs` (FR-012);
   - si no hay daño y la copia local de la lista del día contiene una reserva asignada a la habitación, invoca *Marcar habitación como reservada* (FR-011).
2. **Liberar tarea** (FR-014, FR-016): cierra la tarea con `outcome = Released` y devuelve la habitación a `PendingCleaning` mediante *Marcar pendiente a limpieza*.

En ambos casos el miembro queda desvinculado y vuelve al panel de limpieza.

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `cleaning_task` (de `plan-marcar-habitacion-en-limpieza.md`), `room`, `room_state_history`, `damage_report` (de `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`) y `daily_reservation_room` (de `plan-consultar-reservas.md`).
- **Performance Goals**:
  - Confirmar el fin de limpieza en menos de 15 segundos de interacción (SC-001).
  - El cambio de estado se refleja en el inventario en menos de 2 segundos (SC-003).
- **Constraints**:
  - Solo el miembro dueño de la tarea puede confirmarla o liberarla (FR-002, FR-005, FR-014).
  - `EndDateTime` lo genera el servidor con `Clock`, no puede ser anterior a `StartDateTime` y no se modifica después (FR-008 a FR-010).
  - Todas las transiciones del flujo, el cierre de la tarea y los registros en el historial van en una sola transacción; cualquier fallo deja la habitación en `InCleaning` con la tarea activa (FR-012, FR-017).
  - El paso a `Reserved` es autónomo y no lo elige el usuario (FR-011).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `CLEANING_STAFF`. El miembro se toma del token.
- **Fechas**: ISO 8601 con zona de Colombia (ej. `2026-10-08T10:05:00-05:00`).
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | El usuario no tiene el rol `CLEANING_STAFF` | "No tienes permiso para realizar esta acción." |

---

## GET /api/cleaning/tasks/active

**Descripción:** Devuelve la tarea de limpieza activa del miembro autenticado, para la vista de tarea activa (FR-007).
**Rol autorizado:** `CLEANING_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `taskId` | UUID | Tarea activa | `"c0ffee00-1234-4abc-9def-0123456789ab"` |
| `startDateTime` | string | Inicio de labores | `"2026-10-08T09:15:00-05:00"` |
| `room.roomId` | UUID | Habitación de la tarea | `"3f1c…"` |
| `room.roomNumber` | string | Número de habitación | `"101"` |
| `room.roomType` | string | Tipo de habitación | `"Doble"` |
| `room.status` | string | Estado actual (`InCleaning`) | `"InCleaning"` |
| `room.lastCleaningDateTime` | string \| null | Última limpieza, con el mismo formato del panel | `"2026-10-07T16:40:00-05:00"` |

**Body (JSON):**

```json
{
  "taskId": "c0ffee00-1234-4abc-9def-0123456789ab",
  "startDateTime": "2026-10-08T09:15:00-05:00",
  "room": {
    "roomId": "3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60",
    "roomNumber": "101",
    "roomType": "Doble",
    "status": "InCleaning",
    "lastCleaningDateTime": "2026-10-07T16:40:00-05:00"
  }
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 404 | `NO_ACTIVE_TASK` | El miembro no tiene una tarea abierta; el frontend redirige al panel | (sin mensaje; redirección al panel) |

---

## POST /api/cleaning/tasks/{taskId}/complete

**Descripción:** Confirma el fin de la limpieza. Cierra la tarea y pasa la habitación a `Available` y, en la misma transacción, a `Reserved` si tiene llegada pendiente hoy o a `DisabledForRepairs` si se describió un daño (FR-002 a FR-006, FR-008 a FR-013, FR-015 a FR-018).
**Rol autorizado:** `CLEANING_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `taskId` | path | UUID | Sí | Tarea activa del miembro | `c0ffee00-1234-4abc-9def-0123456789ab` |

**Body (JSON):** `damageDescription` es opcional (máximo 500 caracteres); vacío o solo espacios equivale a no reportar daño (FR-012, FR-018).

```json
{
  "damageDescription": "Grifo del lavamanos con fuga constante."
}
```

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `taskId` | UUID | Tarea cerrada | `"c0ffee00-…"` |
| `outcome` | string | `Completed` o `DamageReported` (FR-015) | `"DamageReported"` |
| `endDateTime` | string | Fin de labores, generado por el servidor | `"2026-10-08T10:05:00-05:00"` |
| `roomId` | UUID | Habitación | `"3f1c…"` |
| `roomStatus` | string | `Available`, `Reserved` o `DisabledForRepairs` | `"DisabledForRepairs"` |
| `reservationRef` | string \| null | Reserva que apartó la habitación, si pasó a `Reserved` | `null` |
| `damageReportId` | UUID \| null | Reporte creado, si se describió un daño | `"d4e5f6a7-…"` |

**Body (JSON):**

```json
{
  "taskId": "c0ffee00-1234-4abc-9def-0123456789ab",
  "outcome": "DamageReported",
  "endDateTime": "2026-10-08T10:05:00-05:00",
  "roomId": "3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60",
  "roomStatus": "DisabledForRepairs",
  "reservationRef": null,
  "damageReportId": "d4e5f6a7-0b1c-4d2e-8f30-415263748596"
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `damageDescription` supera 500 caracteres | "La descripción del daño admite máximo 500 caracteres." |
| 403 | `TASK_NOT_OWNED` | La tarea pertenece a otro miembro (FR-005, HU-1 esc. 3) | "Esta tarea pertenece a otro miembro del personal de limpieza." |
| 404 | `TASK_NOT_FOUND` | No existe la tarea | "No se encontró la tarea." |
| 409 | `ROOM_INVALID_STATE` | La habitación no está en `InCleaning` (FR-005, HU-1 esc. 2) | "La habitación está en estado {estado}; no es posible confirmar el fin de limpieza." |
| 409 | `TASK_ALREADY_CLOSED` | La tarea ya fue confirmada o liberada por otra solicitud simultánea (FR-006, FR-016) | "Esta tarea ya fue cerrada (estado actual de la habitación: {estado})." |

```json
{
  "errorCode": "TASK_NOT_OWNED",
  "message": "Esta tarea pertenece a otro miembro del personal de limpieza.",
  "timestamp": "2026-10-08T10:05:01-05:00",
  "path": "/api/cleaning/tasks/c0ffee00-1234-4abc-9def-0123456789ab/complete",
  "details": { "currentStatus": "InCleaning" }
}
```

Si falla la persistencia o la conexión, la transacción se revierte completa (habitación en `InCleaning`, tarea activa) y la interfaz sugiere reintentar (FR-017).

---

## POST /api/cleaning/tasks/{taskId}/release

**Descripción:** Libera la tarea activa sin terminarla: la cierra con `outcome = Released` y devuelve la habitación a `PendingCleaning` mediante *Marcar pendiente a limpieza* (FR-014, FR-016, FR-017).
**Rol autorizado:** `CLEANING_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `taskId` | path | UUID | Sí | Tarea activa del miembro | `c0ffee00-1234-4abc-9def-0123456789ab` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `taskId` | UUID | Tarea liberada | `"c0ffee00-…"` |
| `outcome` | string | `Released` | `"Released"` |
| `endDateTime` | string | Momento de la liberación | `"2026-10-08T09:40:00-05:00"` |
| `roomId` | UUID | Habitación | `"3f1c…"` |
| `roomStatus` | string | `PendingCleaning` | `"PendingCleaning"` |

**Body (JSON):**

```json
{
  "taskId": "c0ffee00-1234-4abc-9def-0123456789ab",
  "outcome": "Released",
  "endDateTime": "2026-10-08T09:40:00-05:00",
  "roomId": "3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60",
  "roomStatus": "PendingCleaning"
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 403 | `TASK_NOT_OWNED` | La tarea pertenece a otro miembro (FR-014) | "Esta tarea pertenece a otro miembro del personal de limpieza." |
| 404 | `TASK_NOT_FOUND` | No existe la tarea | "No se encontró la tarea." |
| 409 | `ROOM_INVALID_STATE` | La habitación no está en `InCleaning` | "La habitación está en estado {estado}; no es posible liberar la tarea." |
| 409 | `TASK_ALREADY_CLOSED` | La tarea ya fue confirmada o liberada (FR-016) | "Esta tarea ya fue cerrada (estado actual de la habitación: {estado})." |

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
├── domain/
│   └── ports/in/
│       ├── GetActiveCleaningTaskUseCase.java
│       ├── CompleteCleaningUseCase.java
│       └── ReleaseCleaningTaskUseCase.java
├── application/
│   ├── service/
│   │   ├── ActiveCleaningTaskQueryService.java
│   │   ├── CompleteCleaningService.java
│   │   └── ReleaseCleaningTaskService.java
│   └── dto/
│       ├── CompleteCleaningCommand.java
│       ├── CleaningTaskClosedDto.java
│       └── ActiveCleaningTaskDto.java
└── infrastructure/adapters/in/web/
    └── CleaningController.java                    # Se agregan los 3 endpoints de este plan
frontend/src/
├── pages/limpieza/
│   ├── ActiveCleaningTaskPage.jsx
│   ├── ConfirmCleaningEndDialog.jsx               # Confirmación con campo opcional de daño
│   └── ReleaseCleaningTaskDialog.jsx
└── api/
    └── cleaningService.js                         # Se agregan las 3 llamadas de este plan
```

**Structure Decision**: Los servicios de este plan no reimplementan transiciones: usan los puertos `MarkRoomAvailableUseCase` (*Marcar habitación como disponible*), `MarkRoomReservedUseCase` (*Marcar habitación como reservada*), `ReportDamageUseCase` (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`) y `MarkPendingCleaningUseCase` (`plan-marcar-pendiente-a-limpieza.md`), todos dentro de la misma transacción.

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar los puertos transversales `MarkRoomAvailableUseCase` y `MarkRoomReservedUseCase` (plan base, T019).
- [ ] T002 [P] Verificar las excepciones de tareas del plan base (T020): `TaskNotOwnedException` (403), `TaskNotFoundException` (404), `TaskAlreadyClosedException` (409) y `NoActiveTaskException` (404).
- [ ] T003 [P] Agregar a `CleaningTaskRepositoryPort` el cierre de la tarea con `end_date_time` y `outcome`, que falle si la tarea ya está cerrada (`end_date_time` no nulo), y la consulta de reserva pendiente de hoy por habitación sobre `daily_reservation_room`.

---

## Phase 3: User Story 1 - Finalización de limpieza y disponibilidad de habitación (Priority: P1)

**Goal**: Que el miembro con una tarea activa pueda confirmar el fin (con o sin daño) o liberar la tarea desde su vista, dejando la habitación en el estado correcto y volviendo al panel.

**Independent Test**: Con una tarea activa sobre una habitación en `InCleaning`, confirmar el fin y verificar `Available`; repetir con una reserva de hoy en la copia local y verificar `Reserved`; repetir con descripción de daño y verificar `DisabledForRepairs` con su `DamageReport`; liberar otra tarea y verificar `PendingCleaning`. Intentar con la tarea de otro miembro y verificar el rechazo.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `CompleteCleaningServiceTest`: fin sin llegada pendiente deja la habitación en `Available` vía `MarkRoomAvailableUseCase` y la tarea con `outcome = Completed` (HU-1 esc. 1; FR-003, FR-004, FR-015).
- [ ] T005 [P] [US1] Unit test: rechazo si la habitación no está en `InCleaning` (HU-1 esc. 2; FR-005).
- [ ] T006 [P] [US1] Unit test: rechazo con `TASK_NOT_OWNED` si la tarea es de otro miembro (HU-1 esc. 3; FR-002, FR-005).
- [ ] T007 [P] [US1] Unit test: con reserva pendiente de hoy, la habitación termina en `Reserved` vía `MarkRoomReservedUseCase` (HU-1 esc. 4; FR-011).
- [ ] T008 [P] [US1] Unit test: con descripción de daño, invoca `ReportDamageUseCase`, la habitación termina en `DisabledForRepairs`, no se aparta aunque haya reserva y `outcome = DamageReported` (HU-1 esc. 5; FR-012, FR-015).
- [ ] T009 [P] [US1] Unit test en `ReleaseCleaningTaskServiceTest`: cierra con `outcome = Released`, invoca `MarkPendingCleaningUseCase` con `RELEASE_CLEANING_TASK`, no verifica llegadas ni daño y rechaza tareas de otros miembros (HU-1 esc. 6; FR-014).
- [ ] T010 [P] [US1] Unit test con `Clock` fijo: `endDateTime` lo genera el servidor; si fuera anterior a `startDateTime` se rechaza sin cambios (FR-008, FR-009; caso límite 3).
- [ ] T011 [US1] Integration test con Testcontainers: confirmación y liberación simultáneas sobre la misma tarea; solo una tiene éxito y la otra recibe `TASK_ALREADY_CLOSED` (FR-006, FR-016).
- [ ] T012 [US1] Integration test: un fallo forzado en `ReportDamageUseCase` revierte todo y deja la habitación en `InCleaning` con la tarea activa (FR-012, FR-017).
- [ ] T013 [US1] Integration test: los periodos en `room_state_history` quedan registrados con `source_flow` correcto en cada camino (`Available`, `Reserved`, `DisabledForRepairs`, `PendingCleaning`) y sin duplicados (FR-013).
- [ ] T014 [P] [US1] Component test en `ActiveCleaningTaskPage`: solo muestra "Confirmar fin" y "Liberar tarea"; el diálogo de confirmación tiene el campo opcional de daño (máximo 500), advierte la inhabilitación si se escribe un daño y cancelar no envía nada; tras éxito redirige al panel (FR-007, FR-014, FR-018).

### Implementation for User Story 1

- [ ] T015 [US1] Implementar `CompleteCleaningService` (`@Transactional`) (FR-002 a FR-006, FR-008 a FR-013, FR-015, FR-017):
  - Validar que la tarea exista, esté abierta, sea del miembro y que la habitación esté en `InCleaning`.
  - Cerrar la tarea con la hora de `Clock`.
  - Invocar `MarkRoomAvailableUseCase` (`InCleaning` → `Available`) con `sourceFlow = CONFIRM_CLEANING_END`.
  - Si hay daño: invocar `ReportDamageUseCase` con `sourceFlow = CONFIRM_CLEANING_END` y asignar `outcome = DamageReported`.
  - Si no hay daño: si la copia local de la lista del día (`daily_reservation_room`) contiene una reserva asignada a la habitación, invocar `MarkRoomReservedUseCase`. Asignar `outcome = Completed`.
  - Cada transición registra su periodo a través de `TransitionRoomStateUseCase` (regla 8 del plan base); este servicio no invoca `RoomStateHistoryRecorder`.
- [ ] T016 [US1] Implementar `ReleaseCleaningTaskService` (`@Transactional`): validar la tarea y su dueño, cerrarla con `outcome = Released` e invocar `MarkPendingCleaningUseCase` con `RELEASE_CLEANING_TASK` (FR-014, FR-016, FR-017).
- [ ] T017 [US1] Implementar `ActiveCleaningTaskQueryService` y agregar a `CleaningController` los endpoints `GET /api/cleaning/tasks/active`, `POST /api/cleaning/tasks/{taskId}/complete` y `POST /api/cleaning/tasks/{taskId}/release` (FR-001, FR-007).
- [ ] T018 [US1] Construir en el frontend `ActiveCleaningTaskPage.jsx` (habitación con el formato del panel y botones "Confirmar fin" y "Liberar tarea"), `ConfirmCleaningEndDialog.jsx` ("¿Está seguro que desea finalizar la limpieza de la habitación X?", campo "Describir daño encontrado" opcional de máximo 500 caracteres y advertencia de inhabilitación) y `ReleaseCleaningTaskDialog.jsx` ("¿Está seguro que desea liberar la limpieza de la habitación X?"); al terminar, redirigir al panel (FR-001, FR-007, FR-014, FR-018).
- [ ] T019 [US1] Mostrar los errores de los endpoints con el texto de sus tablas y, ante fallos de red, la sugerencia de reintentar (FR-017).

**Checkpoint**: El ciclo de limpieza queda completo: iniciar (plan anterior), confirmar con o sin daño y liberar.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T020 Integration test: tras confirmar el fin, `GET /api/rooms?status=Available` (o `Reserved`) refleja la habitación en menos de 2 segundos (SC-003).
- [ ] T021 Integration test: ninguna `CleaningTask` cerrada tiene `end_date_time` anterior a `start_date_time` ni posterior a la hora del servidor (SC-004).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), los puertos `MarkRoomAvailableUseCase` y `MarkRoomReservedUseCase` (T019), las excepciones de tareas y `SourceFlow` (T020) y `ClockConfig`.
- **Planes de los que depende**:
  - `plan-marcar-habitacion-en-limpieza.md`: tabla `cleaning_task`, `CleaningController` y el panel al que se vuelve.
  - `plan-marcar-pendiente-a-limpieza.md`: `MarkPendingCleaningUseCase` para la liberación.
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: `ReportDamageUseCase` y tabla `damage_report` para el fin con daño.
  - `plan-consultar-reservas.md`: copia local `daily_reservation_room` para detectar la llegada de hoy.
  - *Marcar habitación como disponible* y *Marcar habitación como reservada*: puertos transversales del plan base (T019).
- **Orden**: Phase 2 → Phase 3 → Phase 4. Para el camino con daño, la Phase 2 de `plan-marcar-habitacion-inhabilitada-por-reparaciones.md` debe estar lista.
