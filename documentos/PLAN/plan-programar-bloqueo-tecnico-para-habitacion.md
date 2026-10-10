# Implementation Plan: Programar Bloqueo Técnico para Habitación

**Date**: 2026-10-08  
**Spec**: [spec-programar-bloqueo-tecnico-para-habitacion.md](../SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Programar Bloqueo Técnico para Habitación** para el Personal de mantenimiento, en dos pasos (FR-002):

1. **Programar** (FR-001, FR-003 a FR-009, FR-012 a FR-017, FR-019): desde el botón "Programar mantenimiento" de cualquier habitación del panel de mantenimiento (que no lista las `Inactive`), el miembro ingresa la justificación técnica, la fecha de inicio y la fecha estimada de fin. El sistema valida las fechas y el estado (si la fecha de inicio es hoy, la habitación debe estar `Available`) antes de cualquier consulta, verifica que *Consultar reservas* no devuelva reservas en el rango (Módulo 2 solo devuelve las vigentes que se cruzan) y que *Consultar Información de Mantenimientos* responda que la habitación está disponible y registra el `TechnicalBlockReport` en `Scheduled`; desde ese momento la consulta de mantenimientos reporta su rango como cruce (FR-008). Si la fecha de inicio es hoy, en la misma transacción aplica el bloqueo: la habitación pasa a `TechnicalBlock` y el informe a `Applied`.
2. **Aplicar** (FR-002, FR-018, FR-020): un trabajo autónomo, que corre después de la ingesta de la lista diaria de las 00:00, aplica los informes `Scheduled` cuyo rango contiene la fecha actual si la habitación está `Available`; si no lo está, reintenta cada 00:00 hasta la fecha estimada de fin y luego marca el informe como `Expired`.

Además ofrece "Ver informe" (FR-011) para habitaciones con un informe `Scheduled` o `Applied`. Usa la tabla `technical_block_report` del plan base (T018).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `technical_block_report`, `room` y `room_state_history` del plan base (T018 y T007).
- **Performance Goals**: validar, consultar y registrar la programación en menos de 2 segundos (SC-001).
- **Constraints**:
  - Las validaciones de fechas (formato real de calendario, inicio no anterior a hoy, fin no anterior al inicio, rango máximo de 90 días y antelación máxima de 365 días, ambos configurables) se ejecutan antes de consultar reservas o mantenimientos (FR-012 a FR-016, SC-005).
  - Estado de la habitación: se rechaza `Inactive` y, si el bloqueo inicia hoy, cualquier estado distinto de `Available`; con inicio futuro se acepta desde cualquier otro estado (FR-001, FR-007).
  - Si alguna consulta falla, no se registra nada y la habitación no cambia (casos límite 4 y 5).
  - El trabajo de las 00:00 nunca sobrescribe un estado distinto de `Available` y corre después de la ingesta de reservas (FR-018; regla 10 del plan base).
  - Una programación no se cancela ni se modifica (FR-019).
  - La salida de `TechnicalBlock` solo ocurre por *Confirmar Fin de Reparación de Habitación* (FR-010), que marca el informe como `Completed`.
  - Casos límite sin implementación propia: 6 (una avería durante la intervención se resuelve con *Confirmar Fin de Reparación de Habitación* y un reporte de daño posterior) y 9 (la cancelación queda fuera de alcance; FR-019).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `MAINTENANCE_STAFF`. El responsable se toma del token.
- **Fechas**: las fechas del mantenimiento son de calendario en la API (`YYYY-MM-DD`); la interfaz las pide y muestra como DD-MM-YYYY (FR-012). `reportDateTime` es ISO 8601 con zona de Colombia.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | El usuario no tiene el rol `MAINTENANCE_STAFF` (FR-003) | "No tienes permiso para realizar esta acción." |

---

## POST /api/rooms/{roomId}/technical-blocks

**Descripción:** Programa un bloqueo técnico sobre una habitación que no está `Inactive` (si inicia hoy, debe estar `Available` y el bloqueo se aplica de inmediato) (FR-001 a FR-007, FR-009, FR-012 a FR-017, FR-020).
**Rol autorizado:** `MAINTENANCE_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Habitación a bloquear | `1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d` |

**Body (JSON):**

```json
{
  "reason": "Cambio preventivo del sistema de aire acondicionado.",
  "startDate": "2026-10-20",
  "estimatedEndDate": "2026-10-22"
}
```

### Respuesta (Response)

**Status Code:** `201 Created`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `technicalBlockId` | UUID | Informe creado | `"5c4b3a2f-…"` |
| `roomId` | UUID | Habitación | `"1a2b…"` |
| `roomNumber` | string | Número de habitación | `"106"` |
| `status` | string | `Scheduled`, o `Applied` si inicia hoy | `"Scheduled"` |
| `roomStatus` | string | Estado actual de la habitación; `TechnicalBlock` si inicia hoy | `"Available"` |
| `startDate` | string | Fecha de inicio | `"2026-10-20"` |
| `estimatedEndDate` | string | Fecha estimada de fin | `"2026-10-22"` |
| `reportDateTime` | string | Fecha y hora de la programación, generada por el servidor (FR-017) | `"2026-10-08T15:00:00-05:00"` |

**Body (JSON):**

```json
{
  "technicalBlockId": "5c4b3a2f-1e0d-4c9b-8a7f-6e5d4c3b2a10",
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "roomNumber": "106",
  "status": "Scheduled",
  "roomStatus": "Available",
  "startDate": "2026-10-20",
  "estimatedEndDate": "2026-10-22",
  "reportDateTime": "2026-10-08T15:00:00-05:00"
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Justificación vacía, solo espacios o de más de 500 caracteres, o falta una fecha o no tiene formato de fecha (FR-006, FR-012, esc. 4) | "Escribe el motivo del mantenimiento (máximo 500 caracteres) y elige las dos fechas." |
| 400 | `INVALID_DATE_RANGE` | Fecha inexistente, inicio anterior a hoy, fin anterior al inicio, rango mayor a 90 días o antelación mayor a 365 días (FR-012 a FR-016, esc. 5); `details.rule` indica la regla | Mensaje de la regla (tabla "Mensajes por regla" de `plan-consultar-informacion-mantenimientos.md`) |
| 404 | `ROOM_NOT_FOUND` | El `roomId` no existe | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | La habitación está `Inactive`, o el bloqueo inicia hoy y la habitación no está en `Available` (FR-001, FR-007, esc. 10) | "Hoy la habitación está {estado}. Para empezar hoy debe estar disponible; elige una fecha de inicio posterior." |
| 409 | `RESERVATION_CONFLICT` | *Consultar reservas* devuelve al menos una reserva en el rango (FR-005, esc. 2, caso límite 1); `details.reservations` lista referencia y fechas | "La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas." |
| 409 | `MAINTENANCE_CONFLICT` | *Consultar Información de Mantenimientos* responde `available = false`, incluida una programación simultánea (FR-005, esc. 3, caso límite 2); `details.conflicts` lista el inicio y el fin estimado de cada mantenimiento que se cruza | "Ya hay un mantenimiento programado del {inicio} al {fin}. Elige otras fechas." |

```json
{
  "errorCode": "INVALID_DATE_RANGE",
  "message": "La fecha estimada de finalización no puede ser anterior a la fecha de inicio.",
  "timestamp": "2026-10-08T15:00:01-05:00",
  "path": "/api/rooms/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/technical-blocks",
  "details": { "rule": "END_BEFORE_START" }
}
```

Si Módulo 2 no responde, se usa el manejo de `ExternalModule2UnavailableException` (`MODULE_2_UNAVAILABLE`) definido en `plan-consultar-reservas.md`: no se registra nada, la habitación no cambia y se informa la imposibilidad de verificar reservas (caso límite 4).

---

## GET /api/rooms/{roomId}/technical-blocks/current

**Descripción:** Acción "Ver informe": devuelve el informe `Applied` de la habitación o, si no tiene, su informe `Scheduled` de fecha de inicio más temprana (FR-011, FR-011.1).
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
| `roomId` | path | UUID | Sí | Habitación | `1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `technicalBlockId` | UUID | Informe | `"5c4b3a2f-…"` |
| `roomNumber` | string | Número de habitación | `"106"` |
| `reason` | string | Justificación técnica | `"Cambio preventivo del sistema de aire acondicionado."` |
| `responsibleName` | string | Miembro que programó el mantenimiento | `"Luis Pérez"` |
| `startDate` | string | Fecha de inicio | `"2026-10-20"` |
| `estimatedEndDate` | string | Fecha estimada de fin | `"2026-10-22"` |
| `reportDateTime` | string | Fecha y hora de la programación | `"2026-10-08T15:00:00-05:00"` |
| `status` | string | `Scheduled` o `Applied` | `"Scheduled"` |

**Body (JSON):**

```json
{
  "technicalBlockId": "5c4b3a2f-1e0d-4c9b-8a7f-6e5d4c3b2a10",
  "roomNumber": "106",
  "reason": "Cambio preventivo del sistema de aire acondicionado.",
  "responsibleName": "Luis Pérez",
  "startDate": "2026-10-20",
  "estimatedEndDate": "2026-10-22",
  "reportDateTime": "2026-10-08T15:00:00-05:00",
  "status": "Scheduled"
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 404 | `ROOM_NOT_FOUND` | El `roomId` no existe | "No se encontró la habitación." |
| 404 | `TECHNICAL_BLOCK_NOT_FOUND` | La habitación no tiene un informe `Scheduled` ni `Applied` | "Esta habitación no tiene un mantenimiento programado." |

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-programar-bloqueo-tecnico-para-habitacion.md
│   ├── spec-consultar-informacion-mantenimientos.md
│   ├── spec-consultar-reservas.md
│   └── spec-confirmar-reparacion-finalizada.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-programar-bloqueo-tecnico-para-habitacion.md
    ├── plan-consultar-informacion-mantenimientos.md
    ├── plan-consultar-reservas.md
    └── plan-confirmar-reparacion-finalizada.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   └── TechnicalBlockReport.java          # Definido en el plan base; aquí se implementa
│   │   ├── exception/
│   │   │   ├── ReservationConflictException.java
│   │   │   └── MaintenanceConflictException.java
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── ScheduleTechnicalBlockUseCase.java
│   │       │   ├── ApplyScheduledTechnicalBlocksUseCase.java
│   │       │   └── GetCurrentTechnicalBlockUseCase.java
│   │       └── out/
│   │           └── TechnicalBlockReportRepositoryPort.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── ScheduleTechnicalBlockService.java
│   │   │   ├── ApplyScheduledTechnicalBlocksService.java
│   │   │   └── TechnicalBlockQueryService.java
│   │   └── dto/
│   │       ├── ScheduleTechnicalBlockCommand.java
│   │       ├── TechnicalBlockCreatedDto.java
│   │       └── TechnicalBlockViewDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/web/
│       │   │   └── TechnicalBlockController.java
│       │   ├── in/scheduling/
│       │   │   └── TechnicalBlockScheduler.java
│       │   └── out/persistence/
│       │       ├── entity/TechnicalBlockReportJpaEntity.java
│       │       └── adapter/JpaTechnicalBlockReportRepositoryAdapter.java
│       └── config/
│           └── MaintenanceProperties.java         # Definido en el plan base (T020)
└── main/resources/db/migration/
    └── (sin migración propia: `technical_block_report` se crea en `V3__cleaning_maintenance_schema.sql` del plan base)
frontend/src/
├── components/
│   ├── ScheduleTechnicalBlockDialog.jsx           # Lo abre "Programar mantenimiento" del panel de mantenimiento
│   └── TechnicalBlockViewDialog.jsx               # Lo abre "Ver informe" (TB, o cualquier habitación con programación salvo DFR, que muestra su reporte de daño)
└── api/
    └── technicalBlockService.js
```

**Structure Decision**: La consulta de reservas reutiliza `CheckRoomReservationConflictsUseCase.checkMaintenanceConflict` (`plan-consultar-reservas.md`, T025) y la de mantenimientos reutiliza `ConsultMaintenanceAvailabilityUseCase` y su validador de rangos en modo `SCHEDULING` (rechaza inicios anteriores a hoy y aplica los límites de 90 y 365 días, que la consulta para Módulo 2 no aplica). El puerto se llama con días inclusivos: el inicio y el fin estimado del mantenimiento (`plan-consultar-informacion-mantenimientos.md`). El trabajo autónomo se dispara con el evento que publica la ingesta de la lista diaria (regla 10 del plan base). `ScheduleTechnicalBlockCommand` contiene `roomId`, `memberId` (del token), `reason`, `startDate` y `estimatedEndDate`.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar los límites `hospitua.maintenance.max-range-days` (90) y `max-advance-days` (365) de `MaintenanceProperties` (plan base, T020) (FR-015).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Verificar que la tabla `technical_block_report` y su índice por `(room_id, status)` existan (plan base, T018) (FR-009).
- [ ] T003 [P] Implementar `TechnicalBlockReport` (dominio, con transiciones de estado `Scheduled` → `Applied` → `Completed` y `Scheduled` → `Expired`, sin edición de campos, y con el método `isOverdue(today)`: `Applied` con fecha estimada de fin anterior a `today`), su entidad JPA, `TechnicalBlockReportRepositoryPort` y su adaptador (FR-019).
- [ ] T004 [P] Registrar en `GlobalExceptionHandler` los códigos `INVALID_DATE_RANGE`, `RESERVATION_CONFLICT`, `MAINTENANCE_CONFLICT` y `TECHNICAL_BLOCK_NOT_FOUND`.
- [ ] T005 Verificar que la ingesta de la lista diaria publique `DailyReservationListIngestedEvent` (regla 10 del plan base).

---

## Phase 3: User Story 1 - Programación de bloqueo preventivo sobre habitación disponible (Priority: P1)

**Goal**: Que el miembro programe un mantenimiento validado y sin conflictos, y que el sistema aplique el bloqueo en su fecha de inicio respetando las reservas.

**Independent Test**: Programar con inicio futuro y verificar el informe `Scheduled` sin cambio de estado, también sobre una habitación `Occupied`; programar con inicio hoy y verificar `TechnicalBlock` y `Applied`; simular conflictos de reservas y de mantenimientos, justificación vacía y fechas inválidas; ejecutar el trabajo con `Clock` fijo con la habitación `Available` y con la habitación `Occupied`.

### Tests for User Story 1

- [ ] T006 [P] [US1] Unit test en `ScheduleTechnicalBlockServiceTest`: inicio hoy aplica en la misma transacción (`Applied`, `TechnicalBlock`, periodo en el historial con el miembro como actor) (esc. 1; FR-002, FR-020).
- [ ] T007 [P] [US1] Unit test: inicio futuro registra `Scheduled` sin cambiar el estado de la habitación, también si está `Occupied` o `Reserved`, y sin registro en el historial (esc. 6 y 9; FR-001, FR-002, FR-020).
- [ ] T008 [P] [US1] Unit test: rechazo `RESERVATION_CONFLICT` si *Consultar reservas* devuelve al menos una reserva, con sus referencias y fechas en `details.reservations`; una lista vacía no bloquea (esc. 2; FR-005; caso límite 1; SC-002).
- [ ] T009 [P] [US1] Unit test: rechazo `MAINTENANCE_CONFLICT` con `details.conflicts` si *Consultar Información de Mantenimientos* responde `available = false` (esc. 3; FR-004, FR-005; SC-002).
- [ ] T010 [P] [US1] Unit test: justificación vacía, solo espacios o de más de 500 caracteres se rechaza sin consultar (esc. 4; FR-006; caso límite 3; SC-004).
- [ ] T011 [P] [US1] Unit test con `Clock` fijo: fechas inexistentes, inicio pasado, fin anterior al inicio, rango > 90 días y antelación > 365 días se rechazan sin consultar, con la regla en `details`; fin igual al inicio es válido (esc. 5; FR-012 a FR-016; casos límite 7 y 8; SC-005).
- [ ] T012 [P] [US1] Unit test: rechazo `ROOM_INVALID_STATE`, sin consultar, si la habitación está `Inactive` o si el bloqueo inicia hoy y la habitación no está `Available` (esc. 10; FR-001, FR-007; SC-002).
- [ ] T013 [US1] Integration test: si la consulta a Módulo 2 o la de mantenimientos falla, no se crea el informe y la habitación no cambia (casos límite 4 y 5).
- [ ] T014 [US1] Integration test con Testcontainers: dos programaciones simultáneas con rangos solapados; solo una se registra (caso límite 2).
- [ ] T015 [P] [US1] Unit test en `ApplyScheduledTechnicalBlocksServiceTest` con `Clock` fijo: aplica informes cuyo rango contiene hoy con la habitación `Available` (esc. 7); no toca habitaciones en otro estado y las reintenta el día siguiente; marca `Expired` si hoy supera la fecha estimada de fin sin aplicarse o si la habitación está `Inactive` (esc. 8; FR-018); el periodo se registra con actor nulo (FR-020).
- [ ] T016 [US1] Integration test: tras la ingesta de las 00:00, una habitación apartada como `Reserved` no recibe el bloqueo programado para ese día (regla 10 del plan base).
- [ ] T017 [P] [US1] Unit test en `TechnicalBlockQueryServiceTest`: "Ver informe" devuelve justificación, responsable, rango y fecha de programación del informe `Applied` o, si no hay, del `Scheduled` de inicio más temprano (FR-011, FR-011.1).
- [ ] T018 [P] [US1] Component test en `ScheduleTechnicalBlockDialog`: campos obligatorios, fechas DD-MM-YYYY, validación previa y mensaje de la regla incumplida, alerta de conflicto con reservas o mantenimientos (FR-006, FR-012 a FR-016).

### Implementation for User Story 1

- [ ] T019 [US1] Implementar `ScheduleTechnicalBlockService` (`@Transactional`) (FR-001 a FR-007, FR-009, FR-012 a FR-017, FR-020):
  - Validar la justificación y las fechas con `MaintenanceDateRangeValidator` en modo `SCHEDULING` (`plan-consultar-informacion-mantenimientos.md`), que aplica los límites de `MaintenanceProperties`.
  - Bloquear la fila de `room` (`SELECT … FOR UPDATE`, regla 13 del plan base) antes de validar el estado y de consultar, para que dos programaciones simultáneas no registren rangos cruzados (caso límite 2).
  - Validar que la habitación exista, que no esté `Inactive` y, si `startDate` es hoy, que esté `Available` (después de las validaciones de fechas, porque la regla depende de `startDate`; FR-007).
  - Consultar reservas con `checkMaintenanceConflict` (rechazar si devuelve alguna reserva) y mantenimientos con `ConsultMaintenanceAvailabilityUseCase` (rechazar si `available = false`, pasando sus `conflicts` al error).
  - Crear el informe en `Scheduled` con `reportDateTime` de `Clock` y el miembro del token.
  - Si `startDate` es hoy: `TransitionRoomStateUseCase.transition(roomId, TechnicalBlock, miembro, TECHNICAL_BLOCK, null)` (registra el periodo) e informe `Applied`.
- [ ] T020 [US1] Implementar `ApplyScheduledTechnicalBlocksService` (`@Transactional` por informe): recorrer los informes `Scheduled`; aplicar los que contienen hoy si la habitación está `Available` (con `TransitionRoomStateUseCase`, `sourceFlow = TECHNICAL_BLOCK` y actor nulo); marcar `Expired` los vencidos y los de habitaciones `Inactive`; dejar el resto para el día siguiente (FR-002, FR-018, FR-020).
- [ ] T021 [US1] Implementar `TechnicalBlockScheduler`: escuchar `DailyReservationListIngestedEvent` y ejecutar `ApplyScheduledTechnicalBlocksUseCase` (FR-002, FR-018; regla 10 del plan base).
- [ ] T022 [US1] Implementar `TechnicalBlockQueryService` y `TechnicalBlockController` con `POST /api/rooms/{roomId}/technical-blocks` y `GET /api/rooms/{roomId}/technical-blocks/current` (`MAINTENANCE_STAFF`) (FR-003, FR-011).
- [ ] T023 [US1] Construir `ScheduleTechnicalBlockDialog.jsx` (justificación de máximo 500 caracteres, fecha de inicio y fecha estimada de fin en DD-MM-YYYY, mensajes de la regla incumplida y de conflictos, éxito con el estado resultante) y `TechnicalBlockViewDialog.jsx` (justificación, responsable, rango y fecha de programación), conectados a los botones del panel de mantenimiento (`plan-consultar-panel-mantenimiento.md`, T011) (FR-006, FR-011.1, FR-016).

**Checkpoint**: Los mantenimientos preventivos se programan, se aplican en su fecha y caducan si no pueden aplicarse; el panel de mantenimiento muestra su rango y su informe.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T024 Integration test: desde que se registra un informe `Scheduled`, `GET /api/rooms/{roomId}/maintenance-availability` reporta su rango como cruce (FR-008, SC-003).
- [ ] T025 Integration test: la programación válida completa (validación, consultas y registro) toma menos de 2 segundos con Módulo 2 simulado (SC-001).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), la tabla `technical_block_report` (T018), `SourceFlow` y `MaintenanceProperties` (T020), el evento de la regla 10, la carpeta `scheduling/`, `ClockConfig` y el rol `MAINTENANCE_STAFF`.
- **Planes de los que depende**:
  - `plan-consultar-reservas.md`: `CheckRoomReservationConflictsUseCase` (T025) y `ExternalModule2UnavailableException`.
  - `plan-consultar-informacion-mantenimientos.md`: `ConsultMaintenanceAvailabilityUseCase` y el validador de rangos. Debe implementarse antes de la Phase 3 de este plan.
  - `plan-confirmar-reparacion-finalizada.md`: panel de mantenimiento con los botones que abren los diálogos de este plan y cierre del informe como `Completed`.
- **Orden**: Phase 1 → Phase 2 → Phase 3 → Phase 4, con la Phase 3 después de las Phases 1 a 3 de `plan-consultar-informacion-mantenimientos.md`.
