# Implementation Plan: Programar Bloqueo Técnico para Habitación

**Date**: 2026-10-08  
**Spec**: [spec-programar-bloqueo-tecnico-para-habitacion.md](../SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Programar Bloqueo Técnico para Habitación** para el Personal de mantenimiento, en dos pasos (FR-002):

1. **Programar** (FR-001, FR-003 a FR-009, FR-012 a FR-017, FR-019): desde el botón "Programar mantenimiento" de cualquier habitación del panel de mantenimiento (que no lista las `Inactive`), el miembro ingresa la justificación técnica, la fecha de inicio y la fecha estimada de fin. El sistema valida las fechas y el estado (si la fecha de inicio es hoy, la habitación debe estar `Available`) antes de cualquier consulta, verifica que *Consultar reservas* no devuelva reservas en el rango (Módulo 2 solo devuelve las vigentes que se cruzan) y que *Consultar Información de Mantenimientos* responda que la habitación está disponible y registra el `TechnicalBlockReport` en `Scheduled`; desde ese momento la consulta de mantenimientos reporta su rango como cruce (FR-008). Si la fecha de inicio es hoy, en la misma transacción aplica el bloqueo: la habitación pasa a `TechnicalBlock` y el informe a `Applied`.
2. **Aplicar** (FR-002, FR-018, FR-020): un trabajo autónomo, que corre después de la ingesta de la lista diaria de las 00:00, aplica los informes `Scheduled` cuyo rango contiene la fecha actual si la habitación está `Available`; si no lo está, reintenta cada 00:00 hasta la fecha estimada de fin y luego marca el informe como `Expired`. También se aplica, en la misma transacción, cuando la habitación queda `Available` al confirmarse el fin de su limpieza dentro del rango, sin daño ni llegada pendiente (`ApplyScheduledTechnicalBlocksUseCase.applyForRoom`, que invoca *Confirmar Fin de Limpieza de Habitación* FR-020); así no se pierde el día de inicio cuando a las 00:00 la habitación seguía ocupada por un huésped que sale ese día.

Al registrar la programación publica el aviso de mantenimiento `SCHEDULED` a Módulo 2 (FR-021; cola `m2.habitacion.mantenimiento.queue`, pieza compartida del plan base T025), para que Booking deje de vender esas noches. Además ofrece "Ver informe" (FR-011) para habitaciones con un informe `Scheduled` o `Applied`. Usa la tabla `technical_block_report` del plan base (T018).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `technical_block_report`, `room` y `room_state_history` del plan base (T018 y T007).
- **Performance Goals**: validar, consultar y registrar la programación en menos de 2 segundos (SC-001).
- **Constraints**:
  - Las validaciones de fechas (formato real de calendario, inicio no anterior a hoy, fin no anterior al inicio, rango máximo de 90 días y antelación máxima de 365 días, ambos configurables) se ejecutan antes de consultar reservas o mantenimientos (FR-012 a FR-016, SC-005).
  - Estado de la habitación: se rechaza `Inactive` y, si el bloqueo inicia hoy, cualquier estado distinto de `Available`; con inicio futuro se acepta desde cualquier otro estado (FR-001, FR-007).
  - Si alguna consulta falla, no se registra nada y la habitación no cambia (casos límite 4 y 5).
  - El trabajo de las 00:00 y `applyForRoom` nunca sobrescriben un estado distinto de `Available`; el trabajo corre después de la ingesta de reservas (FR-018; regla 10 del plan base; regla 6 de la máquina de estados).
  - Una programación no se cancela ni se modifica (FR-019); por eso este plan solo publica avisos `SCHEDULED` (FR-021). `Completed` y `Expired` son estados definitivos: un informe `Expired` solo registra que el mantenimiento se venció sin aplicarse y no bloquea reservas.
  - La salida de `TechnicalBlock` solo ocurre por *Confirmar Fin de Reparación de Habitación* (FR-010), que marca el informe como `Completed`.
  - Casos límite sin implementación propia: 6 (una avería durante la intervención se resuelve con *Confirmar Fin de Reparación de Habitación* y un reporte de daño posterior) y 9 (la cancelación queda fuera de alcance; FR-019).

---

## Convenciones de los endpoints

- **Fechas**: las fechas del mantenimiento son de calendario en la API (`YYYY-MM-DD`); la interfaz las pide y muestra como DD-MM-YYYY (FR-012). `reportDateTime` es ISO 8601 con zona de Colombia.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Usuario actual**: el miembro del Personal de mantenimiento que programa se toma del encabezado `X-User` (regla 14 del plan base); solo lo usa `POST /api/rooms/{roomId}/technical-blocks`.
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).

---

## POST /api/rooms/{roomId}/technical-blocks

**Descripción:** Programa un bloqueo técnico sobre una habitación que no está `Inactive` (si inicia hoy, debe estar `Available` y el bloqueo se aplica de inmediato) (FR-001 a FR-007, FR-009, FR-012 a FR-017, FR-020).

### Petición (Request)

**Headers:**

```http
X-User: mtorres
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

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException`, `ConstraintViolationException` o `HttpMessageNotReadableException` | Justificación vacía, solo espacios o de más de 500 caracteres, o falta una fecha, no tiene formato de fecha o no existe en el calendario (ej. 31-02-2026) (FR-006, FR-012, esc. 4, caso límite 8) | "Escribe el motivo del mantenimiento (máximo 500 caracteres) y elige las dos fechas." |
| 400 | `INVALID_DATE_RANGE` | `InvalidDateRangeException` | Inicio anterior a hoy, fin anterior al inicio, rango mayor a 90 días o antelación mayor a 365 días (FR-012 a FR-016, esc. 5); `details.rule` indica la regla | Mensaje de la regla (tabla "Mensajes por regla" de `plan-consultar-informacion-mantenimientos.md`) |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` | El `roomId` no existe | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación está `Inactive`, o el bloqueo inicia hoy y la habitación no está en `Available` (FR-001, FR-007, esc. 10) | "Hoy la habitación está {estado}. Para empezar hoy debe estar disponible; elige una fecha de inicio posterior." |
| 409 | `RESERVATION_CONFLICT` | `ReservationConflictException` | *Consultar reservas* devuelve al menos una reserva en el rango (FR-005, esc. 2, caso límite 1); `details.reservations` lista cada reserva con `reservationRef`, `startDate`, `endDate` y `status` (la misma estructura y la misma `ReservationConflictException` que usa `plan-dar-de-baja-habitacion.md`) | "La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas.", seguido de cada reserva con su código y sus fechas de llegada y salida |
| 409 | `MAINTENANCE_CONFLICT` | `MaintenanceConflictException` | *Consultar Información de Mantenimientos* responde `available = false`, incluida una programación simultánea (FR-005, esc. 3, caso límite 2); `details.conflicts` lista el inicio y el fin estimado de cada mantenimiento que se cruza | "Ya hay un mantenimiento programado del {inicio} al {fin}. Elige otras fechas." |
| 503 | `MODULE_2_UNAVAILABLE` | `ExternalModule2UnavailableException` | Módulo 2 no respondió, superó el timeout o respondió con error al consultar las reservas (`plan-consultar-reservas.md`; caso límite 4); no se registra nada y la habitación no cambia | "Servicio de reservas temporalmente no disponible. No se programó el mantenimiento; inténtalo de nuevo." |

```json
{
  "errorCode": "INVALID_DATE_RANGE",
  "message": "La fecha de fin no puede ser anterior a la fecha de inicio.",
  "timestamp": "2026-10-08T15:00:01-05:00",
  "path": "/api/rooms/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/technical-blocks",
  "details": { "rule": "END_BEFORE_START" }
}
```

```json
{
  "errorCode": "RESERVATION_CONFLICT",
  "message": "La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas.",
  "timestamp": "2026-10-08T15:00:01-05:00",
  "path": "/api/rooms/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/technical-blocks",
  "details": {
    "reservations": [
      { "reservationRef": "RES-10502", "startDate": "2026-10-21", "endDate": "2026-10-24", "status": "ACTIVE" }
    ]
  }
}
```

Si Módulo 2 no responde, se usa el manejo de `ExternalModule2UnavailableException` (`MODULE_2_UNAVAILABLE`) definido en `plan-consultar-reservas.md`: no se registra nada, la habitación no cambia y se informa la imposibilidad de verificar reservas (caso límite 4).

---

## GET /api/rooms/{roomId}/technical-blocks/current

**Descripción:** Acción "Ver informe": devuelve el informe `Applied` de la habitación o, si no tiene, su informe `Scheduled` de fecha de inicio más temprana (FR-011, FR-011.1).

### Petición (Request)

**Headers:**

```http
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

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` | El `roomId` no existe | "No se encontró la habitación." |
| 404 | `TECHNICAL_BLOCK_NOT_FOUND` | `TechnicalBlockNotFoundException` | La habitación no tiene un informe `Scheduled` ni `Applied` | "Esta habitación no tiene un mantenimiento programado." |

```json
{
  "errorCode": "TECHNICAL_BLOCK_NOT_FOUND",
  "message": "Esta habitación no tiene un mantenimiento programado.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/rooms/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/technical-blocks/current"
}
```

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
│   │   └── exception/
│   │       ├── ReservationConflictException.java   # Definida en plan-consultar-reservas.md (T028); aquí se reutiliza
│   │       ├── MaintenanceConflictException.java
│   │       └── TechnicalBlockNotFoundException.java # 404 TECHNICAL_BLOCK_NOT_FOUND
│   ├── application/
│   │   ├── ports/
│   │   │   ├── in/
│   │   │   │   ├── ScheduleTechnicalBlockUseCase.java
│   │   │   │   ├── ApplyScheduledTechnicalBlocksUseCase.java  # Trabajo de las 00:00 y applyForRoom (fin de limpieza)
│   │   │   │   └── GetCurrentTechnicalBlockUseCase.java
│   │   │   └── out/
│   │   │       └── TechnicalBlockReportRepositoryPort.java
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
│       │       └── adapter/TechnicalBlockReportRepositoryAdapter.java
│       └── config/
│           └── MaintenanceProperties.java         # Definido en el plan base (T020)
└── main/resources/db/migration/
    └── (sin migración propia: `technical_block_report` se crea en `V3__cleaning_maintenance_schema.sql` del plan base)
frontend/src/
├── components/
│   ├── ScheduleTechnicalBlockDialog.tsx           # Lo abre "Programar mantenimiento" del panel de mantenimiento
│   └── TechnicalBlockViewDialog.tsx               # Lo abre "Ver informe" (TB, o cualquier habitación con programación salvo DFR, que muestra su reporte de daño)
└── api/
    └── technicalBlockService.ts
```

**Structure Decision**: La consulta de reservas reutiliza `CheckRoomReservationConflictsUseCase.checkMaintenanceConflict` (`plan-consultar-reservas.md`, T026) y la de mantenimientos reutiliza `ConsultMaintenanceAvailabilityUseCase` y su validador de rangos en modo `SCHEDULING` (rechaza inicios anteriores a hoy y aplica los límites de 90 y 365 días, que la consulta para Módulo 2 no aplica). El puerto se llama con días inclusivos: el inicio y el fin estimado del mantenimiento (`plan-consultar-informacion-mantenimientos.md`). El trabajo autónomo se dispara con el evento que publica la ingesta de la lista diaria (regla 10 del plan base). `ScheduleTechnicalBlockCommand` contiene `roomId`, `memberId` (usuario actual), `reason`, `startDate` y `estimatedEndDate`.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar los límites `hospitua.maintenance.max-range-days` (90) y `max-advance-days` (365) de `MaintenanceProperties` y el bean `MaintenanceLimits` que expone (plan base, T020) (FR-015).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Verificar que la tabla `technical_block_report` y su índice por `(room_id, status)` existan (plan base, T018) y que `StaffAccountRepositoryPort` tenga `findByUsername` (plan base, T023) y exista `MaintenanceNoticePublisher` (plan base, T025) (FR-009, FR-011.1, FR-021).
- [ ] T003 [P] Implementar `TechnicalBlockReport` (dominio, con transiciones de estado `Scheduled` → `Applied` → `Completed` y `Scheduled` → `Expired`; `Completed` y `Expired` son definitivos, sin transiciones de salida; sin edición de campos, y con el método `isOverdue(today)`: `Applied` con fecha estimada de fin anterior a `today`), su entidad JPA, `TechnicalBlockReportRepositoryPort` y su adaptador (FR-019). El puerto incluye, además de las consultas propias de este plan:
  - La consulta de cruces: informes `Scheduled` o `Applied` de la habitación con `technical_block_start_date <= lastDay` y `estimated_technical_block_end_date >= firstDay`, devolviendo sus dos fechas ordenadas por inicio, y la del informe `Applied` vigente de la habitación (las usa `plan-consultar-informacion-mantenimientos.md`, FR-010 y FR-014 de su spec; y `plan-confirmar-reparacion-finalizada.md` para pasar el informe a `Completed`).
  - `findAppliedByRoomIds(Collection<UUID>)` y `findEarliestScheduledByRoomIds(Collection<UUID>)`: el informe `Applied` y el `Scheduled` de inicio más temprano de cada habitación (las usa `plan-consultar-panel-mantenimiento.md` para `overdue` y `scheduledMaintenance`).
- [ ] T004 [P] Registrar en `GlobalExceptionHandler` los códigos `MAINTENANCE_CONFLICT` y `TECHNICAL_BLOCK_NOT_FOUND` (`TechnicalBlockNotFoundException`), y verificar `INVALID_DATE_RANGE` (`plan-consultar-informacion-mantenimientos.md`, T001) y `RESERVATION_CONFLICT` (`plan-consultar-reservas.md`, T028).
- [ ] T005 Verificar que la ingesta de la lista diaria publique `DailyReservationListIngestedEvent` (regla 10 del plan base).

---

## Phase 3: User Story 1 - Programación de bloqueo preventivo sobre habitación disponible (Priority: P1)

**Goal**: Que el miembro programe un mantenimiento validado y sin conflictos, y que el sistema aplique el bloqueo en su fecha de inicio respetando las reservas.

**Independent Test**: Programar con inicio futuro y verificar el informe `Scheduled` sin cambio de estado, también sobre una habitación `Occupied`; programar con inicio hoy y verificar `TechnicalBlock` y `Applied`; simular conflictos de reservas y de mantenimientos, justificación vacía y fechas inválidas; ejecutar el trabajo con `Clock` fijo con la habitación `Available` y con la habitación `Occupied`.

### Tests for User Story 1

- [ ] T006 [P] [US1] Unit test en `ScheduleTechnicalBlockServiceTest`: inicio hoy aplica en la misma transacción (`Applied`, `TechnicalBlock`, periodo en el historial con el miembro como actor) y publica un solo aviso `SCHEDULED` con el inicio y el fin estimado (esc. 1; FR-002, FR-020, FR-021).
- [ ] T007 [P] [US1] Unit test: inicio futuro registra `Scheduled` sin cambiar el estado de la habitación, también si está `Occupied` o `Reserved`, y sin registro en el historial; publica el aviso `SCHEDULED` con `maintenanceId` = id del informe, `categoryRoom` de la habitación y el rango programado (esc. 6 y 9; FR-001, FR-002, FR-020, FR-021).
- [ ] T008 [P] [US1] Unit test: rechazo `RESERVATION_CONFLICT` si *Consultar reservas* devuelve al menos una reserva, con sus referencias y fechas en `details.reservations`; una lista vacía no bloquea (esc. 2; FR-005; caso límite 1; SC-002).
- [ ] T009 [P] [US1] Unit test: rechazo `MAINTENANCE_CONFLICT` con `details.conflicts` si *Consultar Información de Mantenimientos* responde `available = false` (esc. 3; FR-004, FR-005; SC-002).
- [ ] T010 [P] [US1] Unit test: justificación vacía, solo espacios o de más de 500 caracteres se rechaza sin consultar (esc. 4; FR-006; caso límite 3; SC-004).
- [ ] T011 [P] [US1] Unit test con `Clock` fijo: inicio pasado, fin anterior al inicio, rango > 90 días y antelación > 365 días se rechazan sin consultar, con la regla en `details`; fin igual al inicio es válido; una fecha inexistente en el cuerpo responde `VALIDATION_ERROR` (esc. 5; FR-012 a FR-016; casos límite 7 y 8; SC-005).
- [ ] T012 [P] [US1] Unit test: rechazo `ROOM_INVALID_STATE`, sin consultar, si la habitación está `Inactive` o si el bloqueo inicia hoy y la habitación no está `Available` (esc. 10; FR-001, FR-007; SC-002).
- [ ] T013 [US1] Integration test: si la consulta a Módulo 2 o la de mantenimientos falla, o la programación se rechaza, no se crea el informe, la habitación no cambia y no queda ningún aviso de mantenimiento en `outbox_notification` (casos límite 4 y 5; FR-021).
- [ ] T014 [US1] Integration test con Testcontainers: dos programaciones simultáneas con rangos solapados; solo una se registra (caso límite 2).
- [ ] T015 [P] [US1] Unit test en `ApplyScheduledTechnicalBlocksServiceTest` con `Clock` fijo: aplica informes cuyo rango contiene hoy con la habitación `Available` (esc. 7); no toca habitaciones en otro estado y las reintenta el día siguiente; marca `Expired` si hoy supera la fecha estimada de fin sin aplicarse o si el rango contiene hoy y la habitación está `Inactive` (esc. 8; FR-018); un informe con fecha de inicio futura de una habitación `Inactive` sigue `Scheduled` y, si la habitación se reactiva antes de esa fecha, se aplica con normalidad (FR-018); el periodo se registra con actor nulo (FR-020). `applyForRoom(roomId, today)`: aplica el informe `Scheduled` de la habitación cuyo rango contiene hoy si está `Available`, no hace nada si no hay informe o la habitación está en otro estado, y exige una transacción abierta (FR-002).
- [ ] T016 [US1] Integration test: tras la ingesta de las 00:00, una habitación apartada como `Reserved` no recibe el bloqueo programado para ese día (regla 10 del plan base).
- [ ] T017 [P] [US1] Unit test en `TechnicalBlockQueryServiceTest`: "Ver informe" devuelve justificación, responsable (`responsibleName`: `full_name` de la cuenta o, si no existe, su `username`), rango y fecha de programación del informe `Applied` o, si no hay, del `Scheduled` de inicio más temprano (FR-011, FR-011.1).
- [ ] T018 [P] [US1] Component test en `ScheduleTechnicalBlockDialog`: campos obligatorios, fechas DD-MM-YYYY, validación previa y mensaje de la regla incumplida, alerta de conflicto con reservas o mantenimientos (FR-006, FR-012 a FR-016).

### Implementation for User Story 1

- [ ] T019 [US1] Implementar `ScheduleTechnicalBlockService` (`@Transactional`) (FR-001 a FR-007, FR-009, FR-012 a FR-017, FR-020, FR-021):
  - Validar la justificación y las fechas con `MaintenanceDateRangeValidator` en modo `SCHEDULING` (`plan-consultar-informacion-mantenimientos.md`), que aplica los límites de `MaintenanceLimits`.
  - Bloquear la fila de `room` (`SELECT … FOR UPDATE`, regla 13 del plan base) antes de validar el estado y de consultar, para que dos programaciones simultáneas no registren rangos cruzados (caso límite 2).
  - Validar que la habitación exista, que no esté `Inactive` y, si `startDate` es hoy, que esté `Available` (después de las validaciones de fechas, porque la regla depende de `startDate`; FR-007).
  - Consultar reservas con `checkMaintenanceConflict` (rechazar si devuelve alguna reserva) y mantenimientos con `ConsultMaintenanceAvailabilityUseCase` (rechazar si `available = false`, pasando sus `conflicts` al error).
  - Crear el informe en `Scheduled` con `reportDateTime` de `Clock` y el miembro que lo programa (usuario actual).
  - Si `startDate` es hoy: `TransitionRoomStateUseCase.transition(roomId, TechnicalBlock, miembro, TECHNICAL_BLOCK, null)` (registra el periodo) e informe `Applied`.
  - Publicar el aviso con `MaintenanceNoticePublisher.publishScheduled(informe, habitación, startDate, estimatedEndDate)` (plan base, T025), en la misma transacción y después de registrar, en el orden que acordó Módulo 2: consultar reservas, decidir, registrar y publicar.
- [ ] T020 [US1] Implementar `ApplyScheduledTechnicalBlocksService` (`@Transactional` por informe, idempotente por `operationalDate`): recorrer los informes `Scheduled`; por cada uno, bloquear la fila de `room` (`SELECT … FOR UPDATE`, regla 13 del plan base) antes de leer su estado; aplicar los que contienen hoy si la habitación está `Available` (con `TransitionRoomStateUseCase`, `sourceFlow = TECHNICAL_BLOCK` y actor nulo); marcar `Expired` los que ya pasaron su fecha estimada de fin sin aplicarse y los que contienen hoy si la habitación está `Inactive` (un informe con inicio futuro no se toca hasta que su rango llegue, FR-018); dejar el resto para el día siguiente. Implementar también `applyForRoom(roomId, today)` (`@Transactional(propagation = Propagation.MANDATORY)`, sin bloquear la fila porque el invocador ya la bloqueó): con la misma regla de aplicación, aplica el informe `Scheduled` de esa habitación cuyo rango contiene hoy si está `Available`; lo invoca `CompleteCleaningService` (`plan-confirmar-fin-de-limpieza.md`, T015). No publica avisos de mantenimiento: aplicar o marcar `Expired` no cambia las fechas que ya conoce Módulo 2 (FR-002, FR-018, FR-020).
- [ ] T021 [US1] Implementar `TechnicalBlockScheduler`: escuchar `DailyReservationListIngestedEvent` y ejecutar `ApplyScheduledTechnicalBlocksUseCase`; como respaldo, ejecutarlo a las 01:00 si ese día todavía no se aplicó; un segundo evento del mismo `operationalDate` no repite el trabajo (FR-002, FR-018; regla 10 del plan base).
- [ ] T022 [US1] Implementar `TechnicalBlockQueryService`, que obtiene `responsibleName` con `StaffAccountRepositoryPort.findByUsername(maintenanceStaffMemberId)` (el `username` si la cuenta no existe), y `TechnicalBlockController` con `POST /api/rooms/{roomId}/technical-blocks` y `GET /api/rooms/{roomId}/technical-blocks/current` (FR-003, FR-011).
- [ ] T023 [US1] Construir `ScheduleTechnicalBlockDialog.tsx` (justificación de máximo 500 caracteres, fecha de inicio y fecha estimada de fin en DD-MM-YYYY, mensajes de la regla incumplida y de conflictos, éxito con el estado resultante) y `TechnicalBlockViewDialog.tsx` (justificación, responsable, rango y fecha de programación), conectados a los botones del panel de mantenimiento (`plan-consultar-panel-mantenimiento.md`, T011) (FR-006, FR-011.1, FR-016).

**Checkpoint**: Los mantenimientos preventivos se programan, se aplican en su fecha y caducan si no pueden aplicarse; el panel de mantenimiento muestra su rango y su informe.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T024 Integration test: desde que se registra un informe `Scheduled`, `GET /api/rooms/{roomId}/maintenances` reporta su rango como cruce (FR-008, SC-003).
- [ ] T025 Integration test: la programación válida completa (validación, consultas y registro) toma menos de 2 segundos con Módulo 2 simulado (SC-001).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), la tabla `technical_block_report` (T018), `SourceFlow`, `MaintenanceProperties` y `MaintenanceLimits` (T020), `MaintenanceNoticePublisher` (T025), el evento de la regla 10, la carpeta `scheduling/` y `ClockConfig`.
- **Planes de los que depende**:
  - `plan-consultar-reservas.md`: `CheckRoomReservationConflictsUseCase` (T026), `ExternalModule2UnavailableException` y `ReservationConflictException` (T028).
  - `plan-consultar-informacion-mantenimientos.md`: `ConsultMaintenanceAvailabilityUseCase` y el validador de rangos. Debe implementarse antes de la Phase 3 de este plan.
  - `plan-consultar-panel-mantenimiento.md`: panel de mantenimiento con los botones que abren los diálogos de este plan.
  - `plan-confirmar-reparacion-finalizada.md`: cierre del informe como `Completed`.
  - `plan-confirmar-fin-de-limpieza.md`: invoca `applyForRoom` (T020) al confirmar el fin de limpieza de una habitación con un mantenimiento programado que contiene hoy.
- **Orden**: Phase 1 → Phase 2 → (Phases 2 y 3 de `plan-consultar-informacion-mantenimientos.md`, que usan T003 de este plan) → Phase 3 → Phase 4.
