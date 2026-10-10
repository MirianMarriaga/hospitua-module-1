# Implementation Plan: Confirmar Fin de Reparación de Habitación

**Date**: 2026-10-08  
**Spec**: [spec-confirmar-reparacion-finalizada.md](../SPEC/spec-confirmar-reparacion-finalizada.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Confirmar Fin de Reparación de Habitación** para el Personal de mantenimiento, con dos piezas:

1. **Iniciar reparaciones** (HU-2; FR-011 a FR-013, FR-020): desde la acción del panel de mantenimiento, que implementa `plan-consultar-panel-mantenimiento.md`, crea la `ReparationTask` del miembro con `StartDateTime` del servidor, sin cambiar el estado de la habitación.
2. **Vista de tarea activa** (HU-1; FR-001 a FR-010, FR-014 a FR-019): "Confirmar fin" cierra la tarea con `outcome = Completed`, pasa la habitación a `PendingCleaning` mediante *Marcar Pendiente a Limpieza* y, si estaba en `TechnicalBlock`, marca su `TechnicalBlockReport` como `Completed`. "Liberar tarea" la cierra con `outcome = Released` sin tocar la habitación.

Usa la tabla `reparation_task` del plan base (T018).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `reparation_task`, `damage_report`, `technical_block_report`, `room` y `room_state_history` del plan base (T018 y T007).
- **Performance Goals**:
  - Confirmar el fin desde la vista de tarea activa en menos de 3 clics y sin redactar justificaciones (SC-001).
  - La habitación pasa a `PendingCleaning` en menos de 2 segundos tras la confirmación (SC-002).
- **Constraints**:
  - Una sola tarea abierta por miembro y por habitación, con índices únicos parciales (FR-012; regla 9 del plan base).
  - Solo el titular confirma o libera su tarea (FR-003, FR-015); la reasignación de tareas abandonadas queda fuera de alcance (caso límite 5).
  - `StartDateTime` y `EndDateTime` los genera el servidor y no se modifican (FR-008 a FR-011).
  - Iniciar y liberar no cambian el estado de la habitación ni escriben en `room_state_history` (FR-016, FR-017).
  - Casos límite sin implementación propia: 1 (fallas que persisten tras la reparación: supervisión operativa con el historial de estados) y 2 (una avería nueva se reporta después, como flujo independiente).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `MAINTENANCE_STAFF`. El miembro se toma del token.
- **Fechas**: ISO 8601 con zona de Colombia; las fechas de mantenimiento programado son de calendario (`YYYY-MM-DD`) y la interfaz las muestra como DD-MM-YYYY.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | `AuthenticationException` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | `AccessDeniedException` | El usuario no tiene el rol `MAINTENANCE_STAFF` | "No tienes permiso para realizar esta acción." |

---

## POST /api/maintenance/tasks

**Descripción:** Acción "Iniciar reparaciones": crea la `ReparationTask` del miembro sobre una habitación `DisabledForRepairs` o `TechnicalBlock`, sin cambiar su estado (FR-011, FR-012).
**Rol autorizado:** `MAINTENANCE_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Body (JSON):**

```json
{
  "roomId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"
}
```

### Respuesta (Response)

**Status Code:** `201 Created`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `taskId` | UUID | Tarea de reparación creada | `"7e6d5c4b-…"` |
| `roomId` | UUID | Habitación | `"9b8a…"` |
| `roomNumber` | string | Número de habitación | `"105"` |
| `roomStatus` | string | Estado de la habitación, sin cambios | `"DisabledForRepairs"` |
| `startDateTime` | string | Inicio de reparaciones, generado por el servidor | `"2026-10-08T13:00:00-05:00"` |

**Body (JSON):**

```json
{
  "taskId": "7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6",
  "roomId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
  "roomNumber": "105",
  "roomStatus": "DisabledForRepairs",
  "startDateTime": "2026-10-08T13:00:00-05:00"
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Falta `roomId` o no es un UUID válido | "Selecciona una habitación válida." |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` | El `roomId` no existe | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación no está en `DisabledForRepairs` ni `TechnicalBlock` (HU-2 esc. 4) | "La habitación está {estado}; no necesita reparación." |
| 409 | `ROOM_TASK_EXISTS` | `DataIntegrityViolationException` del índice único de tarea abierta por habitación (T003) | La habitación ya tiene una tarea abierta de otro miembro, incluida una solicitud simultánea (HU-2 esc. 2) | "Otro compañero ya está reparando esta habitación." |
| 409 | `ACTIVE_TASK_EXISTS` | `ActiveTaskExistsException` o `DataIntegrityViolationException` del índice único del miembro (T003) | El miembro ya tiene una tarea abierta (HU-2 esc. 3) | "Ya estás reparando otra habitación. Termínala o déjala antes de empezar otra." |

```json
{
  "errorCode": "ROOM_TASK_EXISTS",
  "message": "Otro compañero ya está reparando esta habitación.",
  "timestamp": "2026-10-08T13:00:01-05:00",
  "path": "/api/maintenance/tasks",
  "details": { "currentStatus": "DisabledForRepairs" }
}
```

---

## GET /api/maintenance/tasks/active

**Descripción:** Devuelve la tarea de reparación activa del miembro para la vista de tarea activa (FR-001, FR-001.1).
**Rol autorizado:** `MAINTENANCE_STAFF`

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
| `taskId` | UUID | Tarea activa | `"7e6d5c4b-…"` |
| `startDateTime` | string | Inicio de reparaciones | `"2026-10-08T13:00:00-05:00"` |
| `room.roomId` | UUID | Habitación | `"9b8a…"` |
| `room.roomNumber` | string | Número de habitación | `"105"` |
| `room.categoryRoom` | string | Código del tipo: `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE` (la interfaz muestra Sencilla, Doble, Suite o Boutique) | `"SUITE"` |
| `room.status` | string | `DisabledForRepairs` o `TechnicalBlock` | `"DisabledForRepairs"` |

**Body (JSON):**

```json
{
  "taskId": "7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6",
  "startDateTime": "2026-10-08T13:00:00-05:00",
  "room": {
    "roomId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
    "roomNumber": "105",
    "categoryRoom": "SUITE",
    "status": "DisabledForRepairs"
  }
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 404 | `NO_ACTIVE_TASK` | `NoActiveTaskException` | El miembro no tiene una tarea abierta; el frontend redirige al panel | (sin mensaje; redirección al panel) |

```json
{
  "errorCode": "NO_ACTIVE_TASK",
  "message": "No tienes una reparación en curso.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/maintenance/tasks/active"
}
```

---

## POST /api/maintenance/tasks/{taskId}/complete

**Descripción:** Confirma el fin de las reparaciones: cierra la tarea con `outcome = Completed`, pasa la habitación a `PendingCleaning` mediante *Marcar Pendiente a Limpieza* y, si estaba en `TechnicalBlock`, marca su informe como `Completed` (FR-002 a FR-010, FR-014, FR-016, FR-018, FR-019).
**Rol autorizado:** `MAINTENANCE_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `taskId` | path | UUID | Sí | Tarea activa del miembro | `7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `taskId` | UUID | Tarea cerrada | `"7e6d5c4b-…"` |
| `outcome` | string | `Completed` | `"Completed"` |
| `endDateTime` | string | Fin de reparaciones, generado por el servidor | `"2026-10-08T16:30:00-05:00"` |
| `roomId` | UUID | Habitación | `"9b8a…"` |
| `roomStatus` | string | `PendingCleaning` | `"PendingCleaning"` |

**Body (JSON):**

```json
{
  "taskId": "7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6",
  "outcome": "Completed",
  "endDateTime": "2026-10-08T16:30:00-05:00",
  "roomId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
  "roomStatus": "PendingCleaning"
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 403 | `TASK_NOT_OWNED` | `TaskNotOwnedException` | La tarea pertenece a otro miembro (FR-003, HU-1 esc. 3) | "Esta reparación la está haciendo otro compañero." |
| 404 | `TASK_NOT_FOUND` | `TaskNotFoundException` | No existe la tarea | "No encontramos esta reparación. Vuelve al panel." |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación no está en `DisabledForRepairs` ni `TechnicalBlock` (FR-003, HU-1 esc. 2) | "La habitación está {estado}; ya no se puede terminar esta reparación." |
| 409 | `TASK_ALREADY_CLOSED` | `TaskAlreadyClosedException` | La tarea ya fue confirmada o liberada por otra solicitud simultánea (FR-019) | "Esta reparación ya se había terminado o dejado." |

```json
{
  "errorCode": "ROOM_INVALID_STATE",
  "message": "La habitación está Disponible; ya no se puede terminar esta reparación.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/maintenance/tasks/7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6/complete",
  "details": { "currentStatus": "Available" }
}
```

Si falla la persistencia o la conexión, la transacción se revierte completa (habitación y tarea sin cambios) y la interfaz sugiere reintentar (caso límite 3).

---

## POST /api/maintenance/tasks/{taskId}/release

**Descripción:** Acción "Liberar tarea": cierra la tarea con `outcome = Released` sin cambiar la habitación ni su informe, para que otro miembro la retome (FR-015, FR-017, FR-019).
**Rol autorizado:** `MAINTENANCE_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `taskId` | path | UUID | Sí | Tarea activa del miembro | `7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `taskId` | UUID | Tarea liberada | `"7e6d5c4b-…"` |
| `outcome` | string | `Released` | `"Released"` |
| `endDateTime` | string | Momento de la liberación | `"2026-10-08T14:10:00-05:00"` |
| `roomId` | UUID | Habitación | `"9b8a…"` |
| `roomStatus` | string | Estado de la habitación, sin cambios | `"DisabledForRepairs"` |

**Body (JSON):**

```json
{
  "taskId": "7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6",
  "outcome": "Released",
  "endDateTime": "2026-10-08T14:10:00-05:00",
  "roomId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
  "roomStatus": "DisabledForRepairs"
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 403 | `TASK_NOT_OWNED` | `TaskNotOwnedException` | La tarea pertenece a otro miembro (FR-015) | "Esta reparación la está haciendo otro compañero." |
| 404 | `TASK_NOT_FOUND` | `TaskNotFoundException` | No existe la tarea | "No encontramos esta reparación. Vuelve al panel." |
| 409 | `TASK_ALREADY_CLOSED` | `TaskAlreadyClosedException` | La tarea ya fue confirmada o liberada (FR-019) | "Esta reparación ya se había terminado o dejado." |

```json
{
  "errorCode": "TASK_NOT_OWNED",
  "message": "Esta reparación la está haciendo otro compañero.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/maintenance/tasks/7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6/release"
}
```

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-confirmar-reparacion-finalizada.md
│   ├── spec-marcar-pendiente-a-limpieza.md
│   ├── spec-marcar-habitacion-inhabilitada-por-reparaciones.md
│   └── spec-programar-bloqueo-tecnico-para-habitacion.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-confirmar-reparacion-finalizada.md
    ├── plan-marcar-pendiente-a-limpieza.md
    ├── plan-marcar-habitacion-inhabilitada-por-reparaciones.md
    └── plan-programar-bloqueo-tecnico-para-habitacion.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   └── ReparationTask.java               # Definido en el plan base; aquí se implementa
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── StartRepairUseCase.java
│   │       │   ├── GetActiveRepairTaskUseCase.java
│   │       │   ├── CompleteRepairUseCase.java
│   │       │   └── ReleaseRepairTaskUseCase.java
│   │       └── out/
│   │           └── ReparationTaskRepositoryPort.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── StartRepairService.java
│   │   │   ├── CompleteRepairService.java
│   │   │   └── ReleaseRepairTaskService.java
│   │   └── dto/
│   │       ├── RepairTaskStartedDto.java
│   │       ├── RepairTaskClosedDto.java
│   │       └── ActiveRepairTaskDto.java
│   └── infrastructure/adapters/
│       ├── in/web/
│       │   └── MaintenanceController.java
│       └── out/persistence/
│           ├── entity/ReparationTaskJpaEntity.java
│           └── adapter/JpaReparationTaskRepositoryAdapter.java
└── main/resources/db/migration/
    └── (sin migración propia: `reparation_task` se crea en `V3__cleaning_maintenance_schema.sql` del plan base)
frontend/src/
├── pages/mantenimiento/
│   ├── ActiveRepairTaskPage.jsx
│   ├── ConfirmRepairEndDialog.jsx
│   └── ReleaseRepairTaskDialog.jsx
└── api/
    └── maintenanceService.js
```

**Structure Decision**: `MaintenanceController` agrupa `/api/maintenance/...`; `plan-consultar-panel-mantenimiento.md` le agrega el endpoint del panel y construye el panel con sus diálogos. Las excepciones de tareas (`TaskNotOwnedException`, `TaskNotFoundException`, `TaskAlreadyClosedException`, `NoActiveTaskException`) son las del plan base (T020).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base (rol `MAINTENANCE_STAFF`, layout de mantenimiento de T016).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que la tabla `reparation_task` y sus índices únicos parciales existan (plan base, T018) (FR-012).
- [ ] T002 [P] Implementar `ReparationTask` (dominio), `ReparationTaskJpaEntity`, `ReparationTaskRepositoryPort` y su adaptador JPA, con el cierre que falla si la tarea ya está cerrada (FR-010, FR-019).
- [ ] T003 [P] Registrar en `GlobalExceptionHandler` el código `ROOM_TASK_EXISTS` y mapear cada índice único a `ROOM_TASK_EXISTS` o `ACTIVE_TASK_EXISTS`.

---

## Phase 3: User Story 2 - Iniciar reparaciones desde el panel de mantenimiento (Priority: P1)

Se implementa primero porque HU-1 necesita una tarea abierta.

**Goal**: Que el miembro inicie desde el panel las reparaciones de una habitación inhabilitada o en bloqueo técnico, quedando en la vista de tarea activa.

**Independent Test**: Iniciar reparaciones en una habitación `DisabledForRepairs` y verificar la tarea, el estado sin cambios y la redirección; intentar con una habitación con tarea de otro miembro, con un miembro con tarea activa y sobre una habitación `Available`.

### Tests for User Story 2

- [ ] T004 [P] [US2] Unit test en `StartRepairServiceTest`: inicio exitoso sobre `DisabledForRepairs` y sobre `TechnicalBlock` crea la tarea con hora del servidor y no cambia el estado ni escribe en el historial (HU-2 esc. 1; FR-011, FR-016).
- [ ] T005 [P] [US2] Unit test: rechazo `ROOM_TASK_EXISTS` si la habitación tiene tarea abierta de otro miembro (HU-2 esc. 2; FR-012).
- [ ] T006 [P] [US2] Unit test: rechazo `ACTIVE_TASK_EXISTS` si el miembro ya tiene tarea (HU-2 esc. 3; FR-012).
- [ ] T007 [P] [US2] Unit test: rechazo `ROOM_INVALID_STATE` en cualquier otro estado (HU-2 esc. 4; FR-012).
- [ ] T008 [US2] Integration test con Testcontainers: dos inicios simultáneos sobre la misma habitación; solo uno tiene éxito (FR-012).
- [ ] T009 [P] [US2] Component test de la acción "Iniciar reparaciones": redirige a la vista de tarea activa al tener éxito y, si se rechaza, muestra el motivo y vuelve al panel recargado (FR-011, FR-013, FR-020).

### Implementation for User Story 2

- [ ] T010 [US2] Implementar `StartRepairService` (`@Transactional`): validar estado y tareas abiertas, tomar la hora con `Clock` y crear la `ReparationTask` (FR-011, FR-012).
- [ ] T011 [US2] Implementar en `MaintenanceController` `POST /api/maintenance/tasks` con `@PreAuthorize("hasRole('MAINTENANCE_STAFF')")`, y mostrar en el panel la retroalimentación de "Iniciar reparaciones" (FR-011, FR-013, FR-020). `plan-consultar-panel-mantenimiento.md` agrega `GET /api/maintenance/panel` al mismo controlador.

**Checkpoint**: Desde el panel de mantenimiento (`plan-consultar-panel-mantenimiento.md`) se pueden tomar habitaciones para reparar.

---

## Phase 4: User Story 1 - Finalización de reparación y pase a limpieza (Priority: P1)

**Goal**: Que el miembro con una tarea activa confirme el fin de la reparación (la habitación pasa a limpieza) o libere la tarea, y vuelva al panel.

**Independent Test**: Con una tarea activa sobre `DisabledForRepairs`, confirmar y verificar `PendingCleaning`, `outcome = Completed` y el periodo en el historial; repetir sobre `TechnicalBlock` y verificar el informe en `Completed`; liberar otra tarea y verificar que la habitación no cambia; intentar con la tarea de otro miembro.

### Tests for User Story 1

- [ ] T012 [P] [US1] Unit test en `CompleteRepairServiceTest`: confirmación sobre `DisabledForRepairs` invoca `MarkPendingCleaningUseCase` con `CONFIRM_REPAIR_END`, cierra con `outcome = Completed` y hora del servidor (HU-1 esc. 1; FR-004, FR-005, FR-008, FR-018).
- [ ] T013 [P] [US1] Unit test: confirmación sobre `TechnicalBlock` además marca el `TechnicalBlockReport` `Applied` como `Completed` (FR-014).
- [ ] T014 [P] [US1] Unit test: rechazo si la habitación no está en `DisabledForRepairs` ni `TechnicalBlock` (HU-1 esc. 2; FR-003, FR-006).
- [ ] T015 [P] [US1] Unit test: rechazo `TASK_NOT_OWNED` en confirmar y liberar tareas de otro miembro (HU-1 esc. 3; FR-003, FR-015).
- [ ] T016 [P] [US1] Unit test en `ReleaseRepairTaskServiceTest`: cierra con `outcome = Released`, no cambia la habitación, no escribe en el historial y deja el informe en `Applied` (HU-1 esc. 4; FR-017).
- [ ] T017 [P] [US1] Unit test con `Clock` fijo: `endDateTime` del servidor; si fuera anterior a `startDateTime` se rechaza sin cambios (FR-008, FR-009; caso límite 4).
- [ ] T018 [US1] Integration test con Testcontainers: confirmación y liberación simultáneas sobre la misma tarea; solo una tiene éxito (FR-019).
- [ ] T019 [US1] Integration test: un fallo forzado en `MarkPendingCleaningUseCase` revierte el cierre de la tarea y el informe (caso límite 3).
- [ ] T020 [P] [US1] Component test en `ActiveRepairTaskPage`: solo "Confirmar fin" y "Liberar tarea"; el diálogo de confirmación muestra "¿Está seguro que desea finalizar las reparaciones de la habitación X?" con la advertencia de la cola de limpieza; tras éxito redirige al panel (FR-001, FR-002, FR-007, FR-017).

### Implementation for User Story 1

- [ ] T021 [US1] Implementar `CompleteRepairService` (`@Transactional`) (FR-003 a FR-010, FR-014, FR-016, FR-018):
  - Validar la tarea, su titular y el estado de la habitación.
  - Cerrar la tarea con la hora de `Clock` y `outcome = Completed`.
  - Si la habitación está en `TechnicalBlock`, marcar su informe `Applied` como `Completed`.
  - Invocar `MarkPendingCleaningUseCase` con `sourceFlow = CONFIRM_REPAIR_END` y el miembro como actor (registra el historial).
- [ ] T022 [US1] Implementar `ReleaseRepairTaskService` (`@Transactional`): validar la tarea y su titular y cerrarla con `outcome = Released` (FR-015, FR-017).
- [ ] T023 [US1] Agregar a `MaintenanceController` `GET /api/maintenance/tasks/active`, `POST /api/maintenance/tasks/{taskId}/complete` y `POST /api/maintenance/tasks/{taskId}/release` (FR-001).
- [ ] T024 [US1] Construir `ActiveRepairTaskPage.jsx` (habitación con número, tipo y estado; botones "Confirmar fin" y "Liberar tarea"), `ConfirmRepairEndDialog.jsx` y `ReleaseRepairTaskDialog.jsx` ("¿Está seguro que desea liberar la reparación de la habitación X?", advirtiendo que otro miembro podrá retomarla); tras éxito, redirigir al panel; mostrar los errores con el texto de las tablas y sugerir reintentar ante fallos de red (FR-001, FR-002, FR-006, FR-007, FR-017).

**Checkpoint**: El ciclo de mantenimiento queda completo: tomar la habitación, confirmar o liberar.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T025 Integration test: tras confirmar, la habitación aparece en `PendingCleaning` en menos de 2 segundos (SC-002).
- [ ] T026 Integration test: ninguna `ReparationTask` cerrada tiene `end_date_time` anterior a `start_date_time` ni posterior a la hora del servidor (SC-003, SC-004).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base las tablas `reparation_task` y `technical_block_report` (T018), las excepciones de tareas y `SourceFlow` (T020), el rol `MAINTENANCE_STAFF` y el layout de mantenimiento (T016).
- **Planes de los que depende**:
  - `plan-consultar-panel-mantenimiento.md`: el panel desde el que se inician las reparaciones.
  - `plan-marcar-pendiente-a-limpieza.md`: `MarkPendingCleaningUseCase`.
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: tabla `damage_report`.
  - `plan-programar-bloqueo-tecnico-para-habitacion.md`: `TechnicalBlockReport` (este plan lo marca como `Completed` sobre la tabla del plan base).
- **Orden**: Phase 2 → Phase 3 (HU-2) → Phase 4 (HU-1) → Phase 5.
