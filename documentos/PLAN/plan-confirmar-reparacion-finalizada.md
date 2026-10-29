# Implementation Plan: Confirmar Fin de Reparación de Habitación

**Date**: 2026-10-08  
**Spec**: [spec-confirmar-reparacion-finalizada.md](../SPEC/spec-confirmar-reparacion-finalizada.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Confirmar Fin de Reparación de Habitación** para el Personal de mantenimiento, con dos piezas:

1. **Iniciar reparaciones** (HU-2; FR-011, FR-012): crea la `ReparationTask` del miembro con `StartDateTime` del servidor, sin cambiar el estado de la habitación. La interfaz de la acción (botón, llamada, retroalimentación, redirección y recarga) la implementa `plan-consultar-panel-mantenimiento.md` con los textos de la tabla de errores de este plan (*Consultar Panel de Mantenimiento* FR-001, FR-005 y FR-010).
2. **Vista de tarea activa** (HU-1; FR-001 a FR-010, FR-013 a FR-018): "Confirmar fin" cierra la tarea con `outcome = Completed`, pasa la habitación a `PendingCleaning` mediante *Marcar Pendiente a Limpieza* y, si estaba en `TechnicalBlock`, marca su `TechnicalBlockReport` como `Completed`. "Liberar tarea" la cierra con `outcome = Released` sin tocar la habitación.

Al confirmar el fin publica el aviso de mantenimiento a Módulo 2 (FR-023; contrato en el plan base, T024). Iniciar y liberar no publican avisos.

Además, el plan expone el puerto interno `ReleaseRepairTaskOnDeactivationUseCase`, sin endpoint, que libera la tarea abierta de un miembro cuando el Administrador desactiva su cuenta (FR-014, caso límite 5).

Usa la tabla `reparation_task` del plan base (T017).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `reparation_task`, `damage_report`, `technical_block_report`, `room` y `room_state_history` del plan base (T017 y T007).
- **Performance Goals**:
  - Confirmar el fin desde la vista de tarea activa en menos de 3 clics y sin redactar justificaciones (SC-001; verificación manual, UAT).
  - La habitación pasa a `PendingCleaning` en menos de 2 segundos tras la confirmación (SC-002; prueba de rendimiento fuera de CI, T026).
- **Constraints**:
  - Una sola tarea abierta por miembro y por habitación (FR-012; regla 9 del plan base): iniciar bloquea la fila de `room` con `RoomRepositoryPort.lockById` (regla 13 del plan base, T008) y comprueba la tarea de la habitación; los índices únicos parciales quedan como respaldo (el del miembro sigue siendo necesario, porque un miembro que inicia dos habitaciones distintas bloquea dos filas distintas).
  - Solo el titular confirma o libera su tarea, salvo la liberación por desactivación de su cuenta (FR-005, FR-014); la reasignación de tareas abandonadas queda fuera de alcance (caso límite 5).
  - Confirmar, liberar y la liberación por desactivación bloquean la fila de `room` con `lockById` (regla 13 del plan base); por eso la segunda de dos solicitudes simultáneas recibe siempre `TASK_ALREADY_CLOSED` (FR-018).
  - `StartDateTime` y `EndDateTime` los genera el servidor y no se modifican (FR-009, FR-011). Que `EndDateTime` no sea anterior a `StartDateTime` es una invariante del dominio que garantiza `ReparationTask.close` (FR-010).
  - Iniciar y liberar no cambian el estado de la habitación ni escriben en `room_state_history` (FR-015, FR-016).
  - Casos límite sin implementación propia: 1 (fallas que persisten tras la reparación: supervisión operativa con el historial de estados) y 2 (una avería nueva se reporta después, como flujo independiente).

---

## Convenciones de los endpoints

- **Fechas**: ISO 8601 con zona de Colombia; las fechas de mantenimiento son de calendario (`YYYY-MM-DD`) y la interfaz las muestra como DD-MM-YYYY.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Usuario actual**: el miembro del Personal de mantenimiento se toma del encabezado `X-User` (regla 14 del plan base).
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).

---

## POST /api/maintenance/tasks

**Descripción:** Acción "Iniciar reparaciones": crea la `ReparationTask` del miembro sobre una habitación `DisabledForRepairs` o `TechnicalBlock`, sin cambiar su estado (FR-011, FR-012).

### Petición (Request)

**Headers:**

```http
X-User: mtorres
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

**Body (JSON):** el frontend solo redirige a la vista de tarea activa, que toma sus datos del panel.

```json
{
  "taskId": "7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6"
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Falta `roomId` o no es un UUID válido | "Selecciona una habitación válida." |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` (`lockById` no encuentra la habitación) | El `roomId` no existe | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación no está en `DisabledForRepairs` ni `TechnicalBlock` (HU-2 esc. 9) | "La habitación está {estado}; no necesita reparación." |
| 409 | `ROOM_TASK_EXISTS` | `RoomTaskExistsException` (el servicio, o el adaptador al traducir el índice único de tarea abierta por habitación, T002) | La habitación ya tiene una tarea abierta de otro miembro, incluido el miembro que llega segundo tras el bloqueo (HU-2 esc. 7) | "Otro compañero ya está reparando esta habitación." |
| 409 | `ACTIVE_TASK_EXISTS` | `ActiveTaskExistsException` (el servicio, o el adaptador al traducir el índice único del miembro, T002) | El miembro ya tiene una tarea abierta (HU-2 esc. 8) | "Ya estás reparando otra habitación. Termínala o déjala antes de empezar otra." |

```json
{
  "errorCode": "ROOM_TASK_EXISTS",
  "message": "Otro compañero ya está reparando esta habitación.",
  "timestamp": "2026-10-08T13:00:01-05:00",
  "path": "/api/maintenance/tasks"
}
```

---

## POST /api/maintenance/tasks/{taskId}/complete

**Descripción:** Confirma el fin de las reparaciones: cierra la tarea con `outcome = Completed`, pasa la habitación a `PendingCleaning` mediante *Marcar Pendiente a Limpieza* y, si estaba en `TechnicalBlock`, marca su informe como `Completed` (FR-004 a FR-007, FR-008 a FR-010, FR-013, FR-015, FR-017, FR-018, FR-019).

### Petición (Request)

**Headers:**

```http
X-User: mtorres
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `taskId` | path | UUID | Sí | Tarea activa del miembro | `7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `204 No Content`

**Body:** No aplica; el frontend solo redirige al panel.

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |
| 403 | `TASK_NOT_OWNED` | `TaskNotOwnedException` | La tarea pertenece a otro miembro; se valida antes que el cierre (FR-005, HU-1 esc. 3) | "Esta reparación la está haciendo otro compañero." |
| 404 | `TASK_NOT_FOUND` | `TaskNotFoundException` | No existe la tarea | "No encontramos esta reparación. Vuelve al panel." |
| 409 | `TASK_ALREADY_CLOSED` | `TaskAlreadyClosedException` | La tarea ya fue confirmada o liberada, incluida la solicitud que llega segunda tras el bloqueo (FR-005, FR-018, HU-1 esc. 2) | "Esta tarea ya fue cerrada." |

```json
{
  "errorCode": "TASK_ALREADY_CLOSED",
  "message": "Esta tarea ya fue cerrada.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/maintenance/tasks/7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6/complete"
}
```

Si falla la persistencia o la conexión, la transacción se revierte completa (habitación y tarea sin cambios) y la interfaz sugiere reintentar (caso límite 3).

---

## POST /api/maintenance/tasks/{taskId}/release

**Descripción:** Acción "Liberar tarea": cierra la tarea con `outcome = Released` sin cambiar la habitación ni su informe, para que otro miembro la retome (FR-014, FR-016, FR-018).

### Petición (Request)

**Headers:**

```http
X-User: mtorres
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `taskId` | path | UUID | Sí | Tarea activa del miembro | `7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `204 No Content`

**Body:** No aplica; el frontend solo redirige al panel.

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |
| 403 | `TASK_NOT_OWNED` | `TaskNotOwnedException` | La tarea pertenece a otro miembro; se valida antes que el cierre (FR-014) | "Esta reparación la está haciendo otro compañero." |
| 404 | `TASK_NOT_FOUND` | `TaskNotFoundException` | No existe la tarea | "No encontramos esta reparación. Vuelve al panel." |
| 409 | `TASK_ALREADY_CLOSED` | `TaskAlreadyClosedException` | La tarea ya fue confirmada o liberada, incluida la solicitud que llega segunda tras el bloqueo (FR-018, HU-1 esc. 2) | "Esta tarea ya fue cerrada." |

```json
{
  "errorCode": "TASK_NOT_OWNED",
  "message": "Esta reparación la está haciendo otro compañero.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/maintenance/tasks/7e6d5c4b-3a2f-4e1d-9c0b-a1b2c3d4e5f6/release"
}
```

---

## Puerto interno: liberación por desactivación

No tiene endpoint. Lo invoca *Gestionar personal* al desactivar la cuenta de un miembro, dentro de su transacción (FR-014, caso límite 5):

```java
public interface ReleaseRepairTaskOnDeactivationUseCase {
    // Debe ejecutarse dentro de la transacción de la desactivación (propagation = MANDATORY).
    void releaseOpenTaskOf(String memberId); // memberId = username de la cuenta desactivada
}
```

No registra actor: la liberación de una reparación no cambia la habitación ni escribe en `room_state_history`. Si el miembro no tiene tarea abierta, o si al releerla tras el bloqueo ya está cerrada, no hace nada y no lanza excepción. Si la liberación falla, la desactivación se revierte.

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
│   │   └── model/
│   │       └── ReparationTask.java               # Declarada en el árbol del plan base; aquí se define e implementa con ensureClosableBy y close
│   ├── application/
│   │   ├── ports/
│   │   │   ├── in/
│   │   │   │   ├── StartRepairUseCase.java
│   │   │   │   ├── CompleteRepairUseCase.java
│   │   │   │   ├── ReleaseRepairTaskUseCase.java
│   │   │   │   └── ReleaseRepairTaskOnDeactivationUseCase.java
│   │   │   └── out/
│   │   │       └── ReparationTaskRepositoryPort.java
│   │   ├── service/
│   │   │   ├── StartRepairService.java
│   │   │   ├── CompleteRepairService.java
│   │   │   └── ReleaseRepairTaskService.java          # Implementa los dos puertos de liberación
│   │   └── dto/
│   │       └── RepairTaskStartedDto.java              # taskId
│   └── infrastructure/adapters/
│       ├── in/web/
│       │   └── MaintenanceController.java
│       └── out/persistence/
│           ├── entity/ReparationTaskJpaEntity.java
│           └── adapter/ReparationTaskRepositoryAdapter.java
└── main/resources/db/migration/
    └── (sin migración propia: `reparation_task` se crea en `V3__cleaning_maintenance_schema.sql` del plan base)
frontend/src/
├── pages/mantenimiento/
│   ├── ActiveRepairTaskPage.tsx
│   ├── ConfirmRepairEndDialog.tsx
│   └── ReleaseRepairTaskDialog.tsx
└── api/
    └── maintenanceService.ts
```

**Structure Decision**: `MaintenanceController` agrupa `/api/maintenance/...`. Este plan lo crea vacío en su Phase 2 y le agrega los endpoints de las tareas; `plan-consultar-panel-mantenimiento.md` le agrega el endpoint del panel y construye el panel con sus diálogos. Las excepciones de tareas (`TaskNotOwnedException`, `TaskNotFoundException`, `TaskAlreadyClosedException`) son las del plan base (T019). Confirmar y liberar responden `204` sin cuerpo. `ReleaseRepairTaskService` implementa `ReleaseRepairTaskUseCase` (endpoint) y `ReleaseRepairTaskOnDeactivationUseCase` (desactivación) con un mismo flujo de liberación. La vista de tarea activa toma sus datos de `GET /api/maintenance/panel` (`activeTask`, `plan-consultar-panel-mantenimiento.md`).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base (layout de mantenimiento de T015).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que la tabla `reparation_task` y sus índices únicos parciales existan (plan base, T017) (FR-012); verificar `RoomRepositoryPort.lockById` (plan base, T008; regla 13); que exista `MarkPendingCleaningUseCase`, con su resultado `MarkPendingCleaningResult` (`plan-marcar-pendiente-a-limpieza.md`, T003); y que `TechnicalBlockReport` tenga la transición `Applied` → `Completed` y `TechnicalBlockReportRepositoryPort` tenga `findAppliedByRoomIds`, que este plan usa con un solo id (`plan-programar-bloqueo-tecnico-para-habitacion.md`, T003); que `DamageReportRepositoryPort` tenga `findLatestByRoom(UUID roomId)` (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`, T002); y que exista `MaintenanceNoticePublisher` (plan base, T024) (FR-006, FR-013, FR-019).
- [ ] T002 [P] Implementar `ReparationTask` (dominio), `ReparationTaskJpaEntity`, `ReparationTaskRepositoryPort` y su adaptador JPA (FR-009, FR-010, FR-012, FR-018):
  - `ReparationTask.ensureClosableBy(String memberId)`: primero el dueño (`TaskNotOwnedException`) y después que siga abierta (`TaskAlreadyClosedException`).
  - `ReparationTask.close(outcome, endDateTime)`: si el fin es anterior al inicio lanza `IllegalStateException` (error interno, regla 7 del plan base); liberar es `close(Released, marca)`.
  - Consultas: `save`, `findById(UUID taskId)`, `findRoomIdByTaskId(UUID taskId)`, `findOpenByMember(String memberId)`, `existsOpenByRoom(UUID roomId)` y `findRoomIdsWithOpenTask()`; las dos últimas las usan `StartRepairService` y `plan-consultar-panel-mantenimiento.md` (etiqueta "En reparación").
  - El adaptador guarda con `saveAndFlush` y traduce la violación de los índices únicos: el de la habitación a `RoomTaskExistsException` y el del miembro a `ActiveTaskExistsException`.
- [ ] T003 [P] Verificar en `GlobalExceptionHandler` el registro de `RoomTaskExistsException` (`ROOM_TASK_EXISTS`) y `ActiveTaskExistsException` (`ACTIVE_TASK_EXISTS`) (catálogo del plan base, T019).
- [ ] T004 [P] Crear `MaintenanceController` (`@RestController`, `@RequestMapping("/api/maintenance")`) sin endpoints, para que `plan-consultar-panel-mantenimiento.md` le agregue el suyo aunque este plan termine después.

---

## Phase 3: User Story 2 - Iniciar reparaciones desde el panel de mantenimiento (Priority: P1)

Se implementa primero porque HU-1 necesita una tarea abierta.

**Goal**: Que el miembro inicie desde el panel las reparaciones de una habitación inhabilitada o en bloqueo técnico, quedando en la vista de tarea activa.

**Independent Test**: Iniciar reparaciones en una habitación `DisabledForRepairs` y verificar la tarea, el estado sin cambios y la redirección; intentar con una habitación con tarea de otro miembro, con un miembro con tarea activa y sobre una habitación `Available`.

### Tests for User Story 2

- [ ] T005 [P] [US2] Unit test en `StartRepairServiceTest`: inicio exitoso sobre `DisabledForRepairs` y sobre `TechnicalBlock` crea la tarea con hora del servidor y no cambia el estado ni escribe en el historial (HU-2 esc. 6; FR-011, FR-015).
- [ ] T006 [P] [US2] Unit test: rechazo `ROOM_TASK_EXISTS` si la habitación tiene tarea abierta de otro miembro (HU-2 esc. 7; FR-012).
- [ ] T007 [P] [US2] Unit test: rechazo `ACTIVE_TASK_EXISTS` si el miembro ya tiene tarea (HU-2 esc. 8; FR-012).
- [ ] T008 [P] [US2] Unit test: rechazo `ROOM_INVALID_STATE` en cualquier otro estado (HU-2 esc. 9; FR-012).
- [ ] T009 [US2] Integration test con Testcontainers: dos inicios simultáneos sobre la misma habitación; solo uno tiene éxito y el otro recibe `ROOM_TASK_EXISTS` (FR-012).

### Implementation for User Story 2

- [ ] T010 [US2] Implementar `StartRepairService` (`@Transactional`) (FR-011, FR-012), en este orden:
  - Bloquear la habitación con `RoomRepositoryPort.lockById(roomId)` como primera carga de la transacción (regla 13 del plan base); si no existe, `RoomNotFoundException` (`ROOM_NOT_FOUND`).
  - Rechazar si el miembro tiene una tarea abierta (`ActiveTaskExistsException`, `ACTIVE_TASK_EXISTS`).
  - Validar que la habitación esté en `DisabledForRepairs` o `TechnicalBlock` (`ROOM_INVALID_STATE` con `currentStatus`).
  - Rechazar si la habitación tiene una tarea abierta (`existsOpenByRoom`; `RoomTaskExistsException`, `ROOM_TASK_EXISTS`). Con el bloqueo, dos inicios sobre la misma habitación se serializan y el segundo recibe este error; los índices únicos quedan como respaldo (T002).
  - Tomar la hora con `Clock` (no hay transición de estado) y crear la `ReparationTask`.
- [ ] T011 [US2] Agregar a `MaintenanceController` (T004) `POST /api/maintenance/tasks`, que responde `201` con `taskId` y toma como miembro al usuario actual, con los textos de su tabla de errores (FR-011, FR-012). La interfaz de la acción (botón, llamada, retroalimentación, redirección y recarga) la implementa `plan-consultar-panel-mantenimiento.md`.

**Checkpoint**: El endpoint de inicio está listo para la acción del panel de mantenimiento (`plan-consultar-panel-mantenimiento.md`).

---

## Phase 4: User Story 1 - Finalización de reparación y pase a limpieza (Priority: P1)

**Goal**: Que el miembro con una tarea activa confirme el fin de la reparación (la habitación pasa a limpieza) o libere la tarea, y vuelva al panel.

**Independent Test**: Con una tarea activa sobre `DisabledForRepairs`, confirmar y verificar `PendingCleaning`, `outcome = Completed` y el periodo en el historial; repetir sobre `TechnicalBlock` y verificar el informe en `Completed`; liberar otra tarea y verificar que la habitación no cambia; intentar con la tarea de otro miembro.

### Tests for User Story 1

- [ ] T012 [P] [US1] Unit test en `CompleteRepairServiceTest`: confirmación sobre `DisabledForRepairs` invoca `MarkPendingCleaningUseCase` con `CONFIRM_REPAIR_END`, cierra con `outcome = Completed` y hora del servidor, y publica el aviso `CANCELLED` con `maintenanceId` = id del último `DamageReport` de la habitación y `startDate` = `endDate` = fecha de cierre (HU-1 esc. 1; FR-006, FR-007, FR-009, FR-017, FR-019).
- [ ] T013 [P] [US1] Unit test con `Clock` fijo: confirmación sobre `TechnicalBlock` además marca el `TechnicalBlockReport` `Applied` como `Completed`; si la fecha de cierre es anterior o posterior a la estimada publica el aviso `UPDATED` (`endDate` = fecha de cierre, `previousStartDate` y `previousEndDate` = rango original), y si coincide no publica ningún aviso (FR-013, FR-019).
- [ ] T014 [P] [US1] Unit test: rechazo `TASK_NOT_OWNED` en confirmar y liberar tareas de otro miembro, también si la tarea ajena ya está cerrada (el dueño se valida antes que el cierre); rechazo `TASK_ALREADY_CLOSED` sobre una tarea propia ya cerrada (HU-1 esc. 2 y 3; FR-005, FR-014).
- [ ] T015 [P] [US1] Unit test en `ReleaseRepairTaskServiceTest`: cierra con `outcome = Released`, no cambia la habitación, no escribe en el historial, deja el informe en `Applied` y no publica avisos de mantenimiento (HU-1 esc. 4; FR-016, FR-019).
- [ ] T016 [P] [US1] Unit test con `Clock` fijo: al confirmar, `endDateTime` es la marca del periodo `PendingCleaning` que devuelve `MarkPendingCleaningUseCase`; al liberar, la hora de `Clock` (FR-009). Unit test de `ReparationTask`: `close` con un fin anterior al inicio lanza `IllegalStateException` (FR-010; caso límite 4).
- [ ] T017 [US1] Integration test con Testcontainers: confirmación y liberación simultáneas sobre la misma tarea; solo una tiene éxito y la otra recibe `TASK_ALREADY_CLOSED` (FR-018).
- [ ] T018 [US1] Integration test: un fallo forzado en `MarkPendingCleaningUseCase` revierte el cierre de la tarea y el informe, y no deja ningún aviso de mantenimiento en `outbox_notification` (caso límite 3; FR-019).
- [ ] T019 [P] [US1] Component test en `ActiveRepairTaskPage`: solo "Confirmar fin" y "Liberar tarea"; el diálogo de confirmación muestra "¿Está seguro que desea finalizar las reparaciones de la habitación X?" con la advertencia de la cola de limpieza; tras éxito redirige al panel; si `GET /api/maintenance/panel` devuelve `activeTask` nulo, redirige al panel (FR-001, FR-004, FR-008, FR-016).
- [ ] T020 [P] [US1] Unit test en `ReleaseRepairTaskServiceTest`: `releaseOpenTaskOf` sin tarea abierta no hace nada; con tarea abierta la cierra con `outcome = Released` sin validar el dueño, sin cambiar la habitación, sin escribir en el historial y sin publicar avisos; si al releerla tras el bloqueo ya está cerrada, no hace nada ni lanza excepción (FR-014, FR-016; caso límite 5).
- [ ] T021 [US1] Integration test con Testcontainers: invocar `releaseOpenTaskOf` dentro de una transacción abierta por la prueba (como la desactivación de *Gestionar personal*) para un miembro con tarea abierta deja la tarea con `outcome = Released` y la habitación y su informe sin cambios; si la transacción falla después, todo se revierte (FR-014; caso límite 5).

### Implementation for User Story 1

- [ ] T022 [US1] Implementar `CompleteRepairService` (`@Transactional`) (FR-005 a FR-007, FR-009, FR-010, FR-013, FR-015, FR-017, FR-019), en este orden:
  1. Leer solo el `roomId` de la tarea con `ReparationTaskRepositoryPort.findRoomIdByTaskId`; si no existe, `TaskNotFoundException`.
  2. Bloquear la fila con `RoomRepositoryPort.lockById(roomId)` (regla 13 del plan base).
  3. Releer la tarea completa con `findById`, ya fresca tras el bloqueo.
  4. `task.ensureClosableBy(memberId)`: primero el dueño (`TASK_NOT_OWNED`) y después que siga abierta (`TASK_ALREADY_CLOSED`).
  5. Hacer la transición y cerrar la tarea:
     - Si la habitación está en `TechnicalBlock`, marcar su informe `Applied` (`findAppliedByRoomIds` con un solo id) como `Completed`.
     - Invocar `MarkPendingCleaningUseCase` con `sourceFlow = CONFIRM_REPAIR_END` y el miembro como actor (registra el historial). Si la transición se rechaza (no debería ocurrir con la fila bloqueada), `InvalidRoomTransitionException` se propaga, la transacción se revierte y `GlobalExceptionHandler` responde `409 ROOM_INVALID_STATE`.
     - Cerrar la tarea con `task.close(Completed, MarkPendingCleaningResult.transitionDateTime)`, de modo que `EndDateTime` coincide con el inicio del periodo `PendingCleaning` (FR-009, FR-010).
     - Publicar el aviso con `MaintenanceNoticePublisher` (plan base, T024), con la fecha de cierre (fecha de `MarkPendingCleaningResult.transitionDateTime`): en `TechnicalBlock`, `publishUpdated(informe.id(), habitación, inicio del informe, fecha de cierre, inicio del informe, fecha estimada de fin)` solo si la fecha de cierre es distinta de la estimada; en `DisabledForRepairs`, `publishCancelled(reporte.id(), habitación, fecha de cierre, fecha de cierre)`, con el último `DamageReport` de la habitación (`findLatestByRoom`) (FR-019).
- [ ] T023 [US1] Implementar `ReleaseRepairTaskService`, un servicio compartido por el endpoint de liberar y por el puerto de desactivación (FR-014, FR-016, FR-018); ninguno cambia el estado de la habitación:
  - Endpoint (`ReleaseRepairTaskUseCase`, `@Transactional`): los pasos 1 a 4 de T022 (`findRoomIdByTaskId`, `lockById`, releer la tarea, `ensureClosableBy`) y `task.close(Released, hora de Clock)`.
  - Desactivación (`ReleaseRepairTaskOnDeactivationUseCase.releaseOpenTaskOf(String memberId)`, `@Transactional(propagation = MANDATORY)`, sin actor): `findOpenByMember(memberId)` (por miembro, sin filtrar por área); si no hay tarea, no hace nada. Si la hay, `lockById(roomId)` y releer la tarea; si ya está cerrada, no hace nada (no lanza excepción); si sigue abierta, `task.close(Released, hora de Clock)`. No valida el dueño, no escribe historial, deja el informe en `Applied` y no publica avisos.
- [ ] T024 [US1] Agregar a `MaintenanceController` `POST /api/maintenance/tasks/{taskId}/complete` y `POST /api/maintenance/tasks/{taskId}/release`, que responden `204` sin cuerpo (FR-001, FR-008).
- [ ] T025 [US1] Construir `ActiveRepairTaskPage.tsx`, que toma sus datos de `GET /api/maintenance/panel` (`activeTask` con la fila del panel) y redirige al panel si `activeTask` es nulo (habitación con número, tipo y estado; botones "Confirmar fin" y "Liberar tarea"), `ConfirmRepairEndDialog.tsx` y `ReleaseRepairTaskDialog.tsx` ("¿Está seguro que desea liberar la reparación de la habitación X?", advirtiendo que otro miembro podrá retomarla); tras éxito, redirigir al panel; mostrar los errores con el texto de las tablas y sugerir reintentar ante fallos de red (FR-001, FR-004, FR-005, FR-008, FR-016).

**Checkpoint**: El ciclo de mantenimiento queda completo: tomar la habitación, confirmar o liberar.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T026 Prueba de rendimiento (etiquetada según la estrategia de testing del plan base): tras confirmar, la habitación aparece en `PendingCleaning` en menos de 2 segundos (SC-002).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `RoomRepositoryPort.lockById` (regla 13, T008), las tablas `reparation_task` y `technical_block_report` (T017), las excepciones de tareas y `SourceFlow` (T019), `MaintenanceNoticePublisher` (T024) y el layout de mantenimiento (T015).
- **Planes de los que depende**:
  - `plan-consultar-panel-mantenimiento.md`: el panel desde el que se inician las reparaciones (dueño de la interfaz de la acción), que entrega `activeTask` a la vista de tarea activa. Ese plan necesita la Phase 2 de este (T002 y T004); su Phase 3 va antes de la Phase 3 de este plan.
  - `plan-marcar-pendiente-a-limpieza.md`: `MarkPendingCleaningUseCase`.
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: los registros de `damage_report`, creados al reportar un daño sobre la tabla de V3 (plan base, T017), y `DamageReportRepositoryPort` con `findLatestByRoom` (para el aviso `CANCELLED`); los reportes dejan en `DisabledForRepairs` las habitaciones que se reparan.
  - `plan-programar-bloqueo-tecnico-para-habitacion.md`: `TechnicalBlockReport` (este plan lo marca como `Completed` sobre la tabla del plan base) y `findAppliedByRoomIds`.
  - `plan-gestionar-personal.md`: invoca `ReleaseRepairTaskOnDeactivationUseCase` al desactivar una cuenta.
- **Orden**: Phase 2 → (`plan-consultar-panel-mantenimiento.md`) → Phase 3 (HU-2) → Phase 4 (HU-1) → Phase 5.
