# Implementation Plan: Programar Bloqueo Técnico para Habitación

**Date**: 2026-10-08  
**Spec**: [spec-programar-bloqueo-tecnico-para-habitacion.md](../SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Programar Bloqueo Técnico para Habitación** para el Personal de mantenimiento, en dos pasos (FR-002):

1. **Programar** (FR-001, FR-003 a FR-008, FR-011 a FR-015, FR-017): desde el botón "Programar mantenimiento" de cualquier habitación del panel de mantenimiento (que no lista las `Inactive`), el miembro ingresa la justificación técnica, la fecha de inicio y la fecha estimada de fin. El sistema valida las fechas y el estado (si la fecha de inicio es hoy, la habitación debe estar `Available`) antes de cualquier consulta, verifica fuera de la transacción que *Consultar reservas* no devuelva reservas en el rango (Módulo 2 solo devuelve las vigentes que se cruzan) y, ya con la fila de la habitación bloqueada, que *Consultar Información de Mantenimientos* responda que la habitación está disponible y registra el `TechnicalBlockReport` en `Scheduled`; desde ese momento la consulta de mantenimientos reporta su rango como cruce (FR-007). Si la fecha de inicio es hoy, en la misma transacción aplica el bloqueo: la habitación pasa a `TechnicalBlock` y el informe a `Applied`.
2. **Aplicar** (FR-002, FR-016, FR-018): un trabajo autónomo, que corre después de la ingesta de la lista diaria de las 00:00 y, en todo caso, a las 01:00 (America/Bogota), aplica los informes `Scheduled` cuyo rango contiene la fecha actual si la habitación está `Available`; si no lo está, lo reintenta en cada ejecución hasta la fecha estimada de fin y luego marca el informe como `Expired` (caducado). También se aplica, en la misma transacción, cuando la habitación queda `Available` al confirmarse el fin de su limpieza dentro del rango (`ApplyScheduledTechnicalBlocksUseCase.applyForRoom`, que invoca *Confirmar Fin de Limpieza de Habitación* FR-020; la prioridad entre transiciones la fija la regla 6 de la máquina de estados); así no se pierde el día de inicio cuando a las 00:00 la habitación seguía ocupada por un huésped que sale ese día.

Publica avisos `SCHEDULED` a Módulo 2 (contrato en el plan base, T024) al registrar la programación (FR-023) y cada día a las 00:00 (FR-024). Además ofrece "Ver informe" (FR-009) para las habitaciones que indica el panel de mantenimiento. Usa la tabla `technical_block_report` del plan base (T017).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `technical_block_report`, `room` y `room_state_history` del plan base (T017 y T007).
- **Performance Goals**: validar, consultar y registrar la programación en menos de 2 segundos con Módulo 2 respondiendo en su tiempo normal (SC-001).
- **Constraints**:
  - Las validaciones de fechas (formato real de calendario, inicio no anterior a hoy, fin no anterior al inicio, rango máximo de 90 días y antelación máxima de 365 días, ambos configurables) se ejecutan antes de consultar reservas o mantenimientos (FR-011 a FR-015, SC-002).
  - Estado de la habitación: se rechaza `Inactive` y, si el bloqueo inicia hoy, cualquier estado distinto de `Available`; con inicio futuro se acepta desde cualquier otro estado (FR-001, FR-006).
  - La consulta a Módulo 2 se hace fuera de la transacción: si falla, no se registra nada y la habitación no cambia (caso límite 4). Si falla la persistencia dentro de la transacción, se revierte sin cambios (caso límite 5).
  - El trabajo autónomo y `applyForRoom` nunca sobrescriben un estado distinto de `Available`; el trabajo corre después de la ingesta de reservas y, en todo caso, a las 01:00 (FR-016; regla 10 del plan base; regla 6 de la máquina de estados).
  - Una programación no se cancela ni se modifica (FR-017); por eso este plan solo publica avisos `SCHEDULED` (FR-019, FR-020). `Completed` y `Expired` son estados definitivos: un informe `Expired` solo registra que el mantenimiento caducó sin aplicarse y no bloquea reservas.
  - La salida de `TechnicalBlock` solo ocurre por *Confirmar Fin de Reparación de Habitación* (tabla de transiciones de la máquina de estados), que marca el informe como `Completed`.
  - Casos límite sin implementación propia: 6 (una avería durante la intervención se resuelve con *Confirmar Fin de Reparación de Habitación* y un reporte de daño posterior) y 9 (la cancelación queda fuera de alcance; FR-017).

---

## Convenciones de los endpoints

- **Fechas**: las fechas del mantenimiento son de calendario en la API (`YYYY-MM-DD`); la interfaz las pide y muestra como DD-MM-YYYY (FR-011). `reportDateTime` es ISO 8601 con zona de Colombia.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Usuario actual**: el miembro del Personal de mantenimiento que programa se toma del encabezado `X-User` (regla 14 del plan base); solo lo usa `POST /api/rooms/{roomId}/technical-blocks`.
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).

---

## POST /api/rooms/{roomId}/technical-blocks

**Descripción:** Programa un bloqueo técnico sobre una habitación que no está `Inactive` (si inicia hoy, debe estar `Available` y el bloqueo se aplica de inmediato) (FR-001 a FR-006, FR-008, FR-011 a FR-015, FR-018).

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
| `status` | string | `Scheduled`, o `Applied` si inicia hoy (la habitación queda en `TechnicalBlock`) | `"Scheduled"` |

**Body (JSON):**

```json
{
  "technicalBlockId": "5c4b3a2f-1e0d-4c9b-8a7f-6e5d4c3b2a10",
  "status": "Scheduled"
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException`, `ConstraintViolationException` o `HttpMessageNotReadableException` | Justificación vacía, solo espacios o de más de 500 caracteres, o falta una fecha, no tiene formato de fecha o no existe en el calendario (ej. 31-02-2026) (FR-005, FR-011, esc. 4, caso límite 8) | Texto por campo según `details.field` (plan base, T009): `reason` → "Escribe el motivo del mantenimiento (máximo 500 caracteres)."; `startDate` o `estimatedEndDate` → "Elige una fecha válida (DD-MM-YYYY)." |
| 400 | `INVALID_DATE_RANGE` | `InvalidDateRangeException` | Inicio anterior a hoy, fin anterior al inicio, rango mayor a `{max-range-days}` días o antelación mayor a `{max-advance-days}` días (FR-011 a FR-015, esc. 5); `details.rule` indica la regla | Mensaje de la regla (tabla "Mensajes por regla" de `plan-consultar-informacion-mantenimientos.md`) |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` | El `roomId` no existe | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación está `Inactive`, o el bloqueo inicia hoy y la habitación no está en `Available` (FR-001, FR-006, esc. 10) | Según `details.currentStatus`: si es `Inactive`, "La habitación está dada de baja; no se puede programar mantenimiento."; si no, "Habitación {estado} hoy. Elige otra fecha de inicio.", con el estado escrito para ir en la frase: reservada, ocupada, pendiente de limpieza, en limpieza, inhabilitada por reparaciones o en bloqueo técnico |
| 409 | `RESERVATION_CONFLICT` | `ReservationConflictException` | *Consultar reservas* devuelve al menos una reserva en el rango (FR-005, esc. 2, caso límite 1); `details.reservations` lista cada reserva con `reservationRef`, `startDate`, `endDate` y `status` (la misma estructura y la misma `ReservationConflictException` que usa `plan-dar-de-baja-habitacion.md`) | "La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas.", seguido de cada reserva con su código y sus fechas de llegada y salida |
| 409 | `MAINTENANCE_CONFLICT` | `MaintenanceConflictException` | *Consultar Información de Mantenimientos* responde `available = false`, incluida una programación simultánea (FR-005, esc. 3, caso límite 2); `details.conflicts` lista el inicio y el fin estimado de cada mantenimiento que se cruza | "Ya hay un mantenimiento programado o en curso del {inicio} al {fin}. Elige otras fechas." |
| 424 | `MODULE_2_UNAVAILABLE` | `ExternalModule2UnavailableException` | Módulo 2 no respondió, superó el timeout o respondió con error al consultar las reservas (`plan-consultar-reservas.md`; caso límite 4); no se registra nada y la habitación no cambia | "Servicio de reservas temporalmente no disponible. No se programó el mantenimiento; inténtalo de nuevo." |

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

**Descripción:** Acción "Ver informe": devuelve el informe `Applied` de la habitación o, si no tiene, su informe `Scheduled` de fecha de inicio más temprana (FR-009, FR-010).

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
| `roomNumber` | string | Número de habitación | `"106"` |
| `reason` | string | Justificación técnica | `"Cambio preventivo del sistema de aire acondicionado."` |
| `responsibleName` | string | Miembro que programó el mantenimiento | `"Luis Pérez"` |
| `startDate` | string | Fecha de inicio | `"2026-10-20"` |
| `estimatedEndDate` | string | Fecha estimada de fin | `"2026-10-22"` |
| `reportDateTime` | string | Fecha y hora de la programación | `"2026-10-08T15:00:00-05:00"` |

**Body (JSON):**

```json
{
  "roomNumber": "106",
  "reason": "Cambio preventivo del sistema de aire acondicionado.",
  "responsibleName": "Luis Pérez",
  "startDate": "2026-10-20",
  "estimatedEndDate": "2026-10-22",
  "reportDateTime": "2026-10-08T15:00:00-05:00"
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` | El `roomId` no existe | "No se encontró la habitación." |
| 404 | `TECHNICAL_BLOCK_NOT_FOUND` | `TechnicalBlockNotFoundException` | La habitación no tiene un informe `Scheduled` ni `Applied` | "Esta habitación no tiene un mantenimiento programado ni en curso." |

```json
{
  "errorCode": "TECHNICAL_BLOCK_NOT_FOUND",
  "message": "Esta habitación no tiene un mantenimiento programado ni en curso.",
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
│   │   │   ├── TechnicalBlockReport.java          # Declarado en el árbol del plan base; aquí se define e implementa
│   │   │   └── TechnicalBlockStatus.java          # Scheduled, Applied, Completed, Expired (ACTIVE: los dos primeros)
│   │   └── exception/
│   │       ├── ReservationConflictException.java   # Definida en plan-consultar-reservas.md (T029); aquí se reutiliza
│   │       ├── MaintenanceConflictException.java
│   │       └── TechnicalBlockNotFoundException.java # 404 TECHNICAL_BLOCK_NOT_FOUND
│   ├── application/
│   │   ├── ports/
│   │   │   ├── in/
│   │   │   │   ├── ScheduleTechnicalBlockUseCase.java
│   │   │   │   ├── ApplyScheduledTechnicalBlocksUseCase.java  # runDaily (tras la ingesta y a las 01:00) y applyForRoom (también desde el fin de limpieza)
│   │   │   │   ├── GetCurrentTechnicalBlockUseCase.java
│   │   │   │   └── PublishDailyMaintenanceNoticesUseCase.java # publishDaily(today): lo dispara TechnicalBlockScheduler a las 00:00
│   │   │   └── out/
│   │   │       └── TechnicalBlockReportRepositoryPort.java
│   │   ├── service/
│   │   │   ├── ScheduleTechnicalBlockService.java     # Sin transacción: valida y consulta Módulo 2
│   │   │   ├── TechnicalBlockRegistration.java        # Bean @Transactional: bloquea, revalida y registra
│   │   │   ├── ApplyScheduledTechnicalBlocksService.java
│   │   │   ├── DailyMaintenanceNoticeService.java     # Aviso diario de las 00:00 (FR-020)
│   │   │   └── TechnicalBlockQueryService.java
│   │   └── dto/
│   │       ├── ScheduleTechnicalBlockCommand.java
│   │       ├── TechnicalBlockCreatedDto.java          # technicalBlockId y status
│   │       └── TechnicalBlockViewDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/web/
│       │   │   └── TechnicalBlockController.java
│       │   ├── in/scheduling/
│       │   │   └── TechnicalBlockScheduler.java   # Solo dispara runDaily y el aviso diario
│       │   └── out/persistence/
│       │       ├── entity/TechnicalBlockReportJpaEntity.java
│       │       ├── mapper/TechnicalBlockReportMapper.java
│       │       ├── repository/SpringDataTechnicalBlockReportJpaRepository.java
│       │       └── adapter/TechnicalBlockReportRepositoryAdapter.java
│       └── config/
│           └── MaintenanceProperties.java         # Definido en el plan base (T019)
└── main/resources/db/migration/
    └── (sin migración propia: `technical_block_report` se crea en `V3__cleaning_maintenance_schema.sql` del plan base)
frontend/src/
├── components/
│   ├── ScheduleTechnicalBlockDialog.tsx           # Lo abre "Programar mantenimiento" del panel de mantenimiento
│   ├── ScheduleTechnicalBlockDialog.test.tsx
│   ├── TechnicalBlockViewDialog.tsx               # Lo abre "Ver informe" según la regla de `plan-consultar-panel-mantenimiento.md` (FR-005 de su spec)
│   ├── TechnicalBlockViewDialog.test.tsx
│   ├── scheduleTechnicalBlockSchema.ts            # Esquema Zod de la validación previa, con cada regla en su campo
│   └── technicalBlockErrors.ts                    # Aviso de éxito, textos de error y campo de cada rechazo
├── hooks/
│   └── useTechnicalBlock.ts                       # Programar y ver el informe
└── api/
    └── technicalBlockService.ts
```

**Structure Decision**: La programación sigue el patrón de `plan-dar-de-baja-habitacion.md`: `ScheduleTechnicalBlockService` (sin transacción) valida el motivo, las fechas y el estado, y consulta las reservas con `CheckRoomReservationConflictsUseCase.checkMaintenanceConflict` (`plan-consultar-reservas.md`, T027) fuera de la transacción, para no mantener la fila bloqueada durante la llamada HTTP; después invoca el bean aparte `TechnicalBlockRegistration` (`@Transactional`; una llamada interna al mismo objeto no pasaría por el proxy de Spring), que bloquea la fila con `RoomRepositoryPort.lockById`, revalida el estado, consulta los cruces locales con `ConsultMaintenanceAvailabilityUseCase`, registra, aplica si inicia hoy y publica el aviso. Las fechas se validan con `MaintenanceDateRange.forScheduling(today, limits, …)` (rechaza inicios anteriores a hoy y aplica los límites de `MaintenanceLimits`, que la consulta para Módulo 2 no aplica). El puerto de mantenimientos se llama con días inclusivos: el inicio y el fin estimado del mantenimiento (`plan-consultar-informacion-mantenimientos.md`). `TechnicalBlockScheduler` solo dispara `ApplyScheduledTechnicalBlocksService.runDaily` (tras el evento de la ingesta y a las 01:00, regla 10 del plan base) y `DailyMaintenanceNoticeService` (a las 00:00); la lógica vive en los servicios. `ScheduleTechnicalBlockCommand` contiene `roomId`, `memberId` (usuario actual, `String` con el `username`), `reason`, `startDate` y `estimatedEndDate`.

---

## Phase 1: Setup (Shared Infrastructure)

- [x] T001 Verificar los límites `hospitua.maintenance.max-range-days` (90) y `max-advance-days` (365) de `MaintenanceProperties` y el bean `MaintenanceLimits` (record de `domain/model`) que expone (plan base, T019) (FR-014).
  - Hecho (L22): `MaintenanceProperties` (90 y 365 por defecto, `@Positive`) y el bean `MaintenanceLimits` de `MaintenanceConfig`.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [x] T002 Verificar que la tabla `technical_block_report` exista sin columna `version`, con sus índices por `(room_id, status)`, parcial de `Scheduled` por `technical_block_start_date` y único parcial de `Applied` por `room_id`, y sus `CHECK` de fechas y de `status` (plan base, T017); que `RoomRepositoryPort` tenga `lockById` (plan base, T008); que `StaffAccountRepositoryPort` tenga `findByUsername` (plan base, T022); que exista `MaintenanceNoticePublisher` (plan base, T024), y que `DamageReportRepositoryPort` tenga `findLatestByRoomIds(Collection<UUID>)` (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`) (FR-008, FR-010, FR-019, FR-020).
  - Hecho (L22): existen la tabla con sus índices y `CHECK` (V3), `lockById`, `StaffAccountRepositoryPort.findByUsername`, `MaintenanceNoticePublisher` y `DamageReportRepositoryPort.findLatestByRoomIds`.
- [x] T003 [P] Implementar `TechnicalBlockReport` (dominio, con transiciones de estado `Scheduled` → `Applied` → `Completed` y `Scheduled` → `Expired`; `Completed` y `Expired` son definitivos, sin transiciones de salida; sin edición de campos; con el método `covers(LocalDate)`: el rango `[inicio, fin estimado]` contiene el día, que usan la programación con inicio hoy, `runDaily` y `applyForRoom`, y con el método `isOverdue(today)`: `Applied` con fecha estimada de fin anterior a `today`), su entidad JPA (sin `version`: todas sus escrituras ocurren con la fila de la habitación bloqueada, regla 13 del plan base), `TechnicalBlockReportRepositoryPort` y su adaptador (FR-017). El puerto:
  - `List<TechnicalBlockReport> findActiveOverlapping(UUID roomId, LocalDate from, LocalDate to)`: informes `Scheduled` o `Applied` de la habitación con `technical_block_start_date <= to` y `estimated_technical_block_end_date >= from`, ordenados por inicio (la usa `plan-consultar-informacion-mantenimientos.md`, FR-010 de su spec).
  - `Map<UUID, TechnicalBlockReport> findAppliedByRoomIds(Collection<UUID>)` y `Map<UUID, TechnicalBlockReport> findEarliestScheduledByRoomIds(Collection<UUID>)`: el informe `Applied` y el `Scheduled` de inicio más temprano de cada habitación (las usan `plan-consultar-panel-mantenimiento.md` para `overdue` y `maintenance`, `plan-consultar-informacion-mantenimientos.md` para FR-014 de su spec y el aviso diario de este plan, T025; "Ver informe" y `plan-confirmar-reparacion-finalizada.md`, para pasar el informe a `Completed`, las llaman con un solo id).
  - `List<UUID> findRoomIdsWithScheduledCovering(LocalDate today)` e `int expireScheduledEndedBefore(LocalDate today)` (un solo `UPDATE … WHERE status = 'Scheduled' AND estimated_technical_block_end_date < :today`): las usa `runDaily`.
  - Hecho (L22): `TechnicalBlockReport` (estado mutable con `apply`, `complete` y `expire`; una transición inválida es `IllegalStateException`), `TechnicalBlockStatus` (con `ACTIVE`), la entidad, `TechnicalBlockReportMapper`, `SpringDataTechnicalBlockReportJpaRepository` y el adaptador. El `Applied` y el primer `Scheduled` de cada habitación salen de una sola consulta ordenada por inicio; `save` usa `saveAndFlush`, así un segundo `Applied` en la habitación falla dentro del caso de uso (índice único parcial).
- [x] T004 [P] Registrar en `GlobalExceptionHandler` los códigos `MAINTENANCE_CONFLICT` y `TECHNICAL_BLOCK_NOT_FOUND` (`TechnicalBlockNotFoundException`), y verificar `INVALID_DATE_RANGE` (`plan-consultar-informacion-mantenimientos.md`, T001) y `RESERVATION_CONFLICT` (`plan-consultar-reservas.md`, T029).
  - Avance (L22): `INVALID_DATE_RANGE` y `RESERVATION_CONFLICT` existen; L23 agrega a `InvalidDateRangeException` las reglas del bloqueo técnico (`START_IN_PAST`, `RANGE_TOO_LONG`, `START_TOO_FAR`). `MaintenanceConflictException` se crea en L25, su primer consumidor, porque arma `details.conflicts` con `MaintenanceConflict` (`plan-consultar-informacion-mantenimientos.md` T001, L23); `TechnicalBlockNotFoundException`, en L26, con `GET …/technical-blocks/current`.
  - Avance (L25): `MAINTENANCE_CONFLICT` (`MaintenanceConflictException`, con `details.conflicts` y el primer cruce en el mensaje en DD-MM-YYYY) registrado y probado en `GlobalExceptionHandlerTest`. Falta `TECHNICAL_BLOCK_NOT_FOUND`, que llega con `GET …/technical-blocks/current` (L26).
  - Hecho (L26): `TECHNICAL_BLOCK_NOT_FOUND` (`TechnicalBlockNotFoundException`, 404) registrado y probado en `GlobalExceptionHandlerTest`.
- [x] T005 Verificar que la ingesta de la lista diaria publique `DailyReservationListIngestedEvent` dentro de su transacción (`plan-consultar-reservas.md`, T012; regla 10 del plan base).
  - Hecho (L22): `DailyReservationIngestionService` publica `DailyReservationListIngestedEvent` dentro de la transacción de la lista de las 00:00 (`plan-consultar-reservas.md` T012).

---

## Phase 3: User Story 1 - Programación de bloqueo preventivo sobre habitación disponible (Priority: P1)

**Goal**: Que el miembro programe un mantenimiento validado y sin conflictos, y que el sistema aplique el bloqueo en su fecha de inicio respetando las reservas.

**Independent Test**: Programar con inicio futuro y verificar el informe `Scheduled` sin cambio de estado, también sobre una habitación `Occupied`; programar con inicio hoy y verificar `TechnicalBlock` y `Applied`; simular conflictos de reservas y de mantenimientos, justificación vacía y fechas inválidas; ejecutar el trabajo con `Clock` fijo con la habitación `Available` y con la habitación `Occupied`.

### Tests for User Story 1

- [x] T006 [P] [US1] Unit test en `ScheduleTechnicalBlockServiceTest`: inicio hoy aplica en la misma transacción (`Applied`, `TechnicalBlock`, periodo en el historial con el miembro como actor) y publica un solo aviso `SCHEDULED` con el inicio y el fin estimado (esc. 1; FR-002, FR-018, FR-019).
  - Hecho (L25): `ScheduleTechnicalBlockServiceTest`, con `TechnicalBlockRegistration` real; también en `TechnicalBlockSchedulingIntegrationTest`.
- [x] T007 [P] [US1] Unit test: inicio futuro registra `Scheduled` sin cambiar el estado de la habitación, también si está `Occupied` o `Reserved`, y sin registro en el historial; publica el aviso `SCHEDULED` con `maintenanceId` = id del informe, `categoryRoom` de la habitación y el rango programado (esc. 6 y 9; FR-001, FR-002, FR-018, FR-019).
- [x] T008 [P] [US1] Unit test: rechazo `RESERVATION_CONFLICT` si *Consultar reservas* devuelve al menos una reserva, con sus referencias y fechas en `details.reservations`; una lista vacía no bloquea; no se abre la transacción de registro (esc. 2; FR-004; caso límite 1).
- [x] T009 [P] [US1] Unit test: rechazo `MAINTENANCE_CONFLICT` con `details.conflicts` si *Consultar Información de Mantenimientos* responde `available = false` (esc. 3; FR-003, FR-004).
- [x] T010 [P] [US1] Unit test: justificación vacía, solo espacios o de más de 500 caracteres se rechaza sin consultar (esc. 4; FR-005; caso límite 3).
  - Hecho (L25): la justificación la valida `ScheduleTechnicalBlockRequest` y, otra vez, `TechnicalBlockReport.normalizeReason` (`InvalidTechnicalBlockReasonException`, `VALIDATION_ERROR` con `details.field = reason`), que también la recorta.
- [x] T011 [P] [US1] Unit test con `Clock` fijo: inicio pasado, fin anterior al inicio, rango > 90 días y antelación > 365 días se rechazan sin consultar, con la regla en `details`; fin igual al inicio es válido; una fecha inexistente en el cuerpo responde `VALIDATION_ERROR` (esc. 5; FR-011 a FR-015; casos límite 7 y 8; SC-002).
- [x] T012 [P] [US1] Unit test: rechazo `ROOM_INVALID_STATE`, sin consultar, si la habitación está `Inactive` o si el bloqueo inicia hoy y la habitación no está `Available`; el mismo rechazo si el estado cambia entre la consulta a Módulo 2 y la revalidación con la fila bloqueada (esc. 10; FR-001, FR-006).
- [x] T013 [US1] Integration test: si la consulta a Módulo 2 falla (fuera de la transacción), si falla la persistencia (fallo simulado del puerto de mantenimientos o del registro) o si la programación se rechaza, no se crea el informe, la habitación no cambia y no queda ningún aviso de mantenimiento en `outbox_notification` (casos límite 4 y 5; FR-019).
  - Hecho (L25): `TechnicalBlockSchedulingIntegrationTest` (reservas en conflicto, Módulo 2 caído y falla forzada al guardar el informe con el bloqueo de hoy ya aplicado).
- [x] T014 [US1] Integration test con Testcontainers: dos programaciones simultáneas con rangos solapados; solo una se registra, porque los cruces locales se consultan con la fila de la habitación bloqueada (caso límite 2).
- [x] T015 [P] [US1] Unit test en `ApplyScheduledTechnicalBlocksServiceTest` con `Clock` fijo: `runDaily(today)` marca `Expired` los informes `Scheduled` cuya fecha estimada de fin ya pasó y llama a `applyForRoom` por cada habitación con un informe `Scheduled` que cubre hoy (esc. 7 y 8; FR-016); ejecutarlo dos veces el mismo día no cambia nada más (idempotente por estado). `applyForRoom(roomId, today)`: bloquea la fila, aplica el informe `Scheduled` de la habitación que cubre hoy si está `Available`, con el periodo registrado con actor nulo (FR-018); no hace nada si no hay informe o la habitación está en otro estado, incluida `Inactive`, y el informe sigue `Scheduled` para la siguiente ejecución; una habitación `Inactive` reactivada dentro del rango se bloquea en la siguiente ejecución (FR-002, FR-016). En `TechnicalBlockScheduler`: un evento con el `operationalDate` de hoy dispara `runDaily` con esa fecha y uno con un `operationalDate` anterior a hoy no lo dispara (regla 10 del plan base).
  - Avance (L25): hecha la parte de los servicios (`ApplyScheduledTechnicalBlocksServiceTest` y `runDaily` contra la base en `TechnicalBlockSchedulingIntegrationTest`). La parte de `TechnicalBlockScheduler` llega con él (L26).
  - Hecho (L26): `TechnicalBlockSchedulerTest` con `Clock` fijo a las 00:05 de Bogotá (en UTC ya es el día siguiente): el evento de hoy dispara `runDaily(today)`, uno anterior no, y los dos `@Scheduled` usan la fecha del `Clock`.
- [x] T016 [US1] Integration test: tras la ingesta de las 00:00, una habitación apartada como `Reserved` no recibe el bloqueo programado para ese día, y un fallo al aplicar un bloqueo no revierte la ingesta (regla 10 del plan base).
  - Hecho (L26): `TechnicalBlockSchedulerIntegrationTest` invoca la ingesta por su puerto con el día operativo de hoy; el espía de `runDaily` le da su propio contexto y su propia base, sin las listas de días futuros de las pruebas de la cola, que harían descartar la de hoy como atrasada. El listener atrapa y registra el fallo de `runDaily`: la ingesta ya se confirmó y la excepción no debe llegar al listener de RabbitMQ, que reintentaría el mensaje; el respaldo de las 01:00 lo vuelve a intentar.
- [x] T017 [P] [US1] Unit test en `TechnicalBlockQueryServiceTest`: "Ver informe" devuelve justificación, responsable (`responsibleName`: `full_name` de la cuenta o, si no existe, su `username`), rango y fecha de programación del informe `Applied` o, si no hay, del `Scheduled` de inicio más temprano (FR-009, FR-010).
  - Hecho (L26): también cubre la respuesta 404 sin informe y la habitación inexistente.
- [x] T018 [P] [US1] Component test en `ScheduleTechnicalBlockDialog`: campos obligatorios, fechas DD-MM-YYYY, validación previa y mensaje de la regla incumplida, alerta de conflicto con reservas o mantenimientos (FR-005, FR-011 a FR-015).
  - Hecho (F07): `ScheduleTechnicalBlockDialog.test.tsx` y `TechnicalBlockViewDialog.test.tsx`.
  - Ajuste (decisiones abiertas tras RF4): cada regla incumplida se busca debajo de su campo, que la anuncia como descripción accesible. Casos nuevos: varias reglas incumplidas a la vez muestran todos sus errores; `VALIDATION_ERROR` e `INVALID_DATE_RANGE` del backend se marcan en su campo y no en el aviso; cambiar la fecha de inicio vuelve a validar la de fin.
- [x] T019 [US1] Integration test con `Clock` fijo: `DailyMaintenanceNoticeService` deja en `outbox_notification` un aviso `SCHEDULED` con inicio = fin = hoy por cada habitación `DisabledForRepairs` (con `maintenanceId` = id de su último `DamageReport`) y por cada informe `Applied` vencido (con `maintenanceId` = id del informe), y ninguno para un `Applied` vigente ni un `Scheduled` (FR-020).
  - Hecho (L26): en `TechnicalBlockSchedulerIntegrationTest`, con dos reportes de daño (usa el último). `publishDaily` recibe la fecha, que `TechnicalBlockScheduler` toma del `Clock`, así que la prueba fija el día sin reemplazar el `Clock` de la aplicación; `DailyMaintenanceNoticeServiceTest` cubre además la reparación sin reporte (se omite con un `log.warn`).

### Implementation for User Story 1

- [x] T020 [US1] Implementar `ScheduleTechnicalBlockService` (sin transacción) y el bean `TechnicalBlockRegistration` (`@Transactional`) (FR-001 a FR-006, FR-008, FR-011 a FR-015, FR-018, FR-019), en tres pasos:
  - Hecho (L25): `ensureSchedulable` (estado) la comparten la validación previa y la revalidación con la fila bloqueada. Los cruces de `ConsultMaintenanceAvailabilityUseCase` (su `ConflictDto`) se pasan a `MaintenanceConflict` para el error.
  1. Sin transacción: validar la justificación y las fechas con `MaintenanceDateRange.forScheduling(today, limits, startDate, estimatedEndDate)` (`plan-consultar-informacion-mantenimientos.md`; `today` de `Clock`), y validar el estado leyendo la habitación: que exista, que no esté `Inactive` y, si `startDate` es hoy, que esté `Available` (después de las validaciones de fechas, porque la regla depende de `startDate`; FR-006).
  2. Fuera de transacción: consultar reservas con `checkMaintenanceConflict`; si devuelve alguna reserva, `RESERVATION_CONFLICT`, y si Módulo 2 no responde, `424 MODULE_2_UNAVAILABLE`. En ninguno de los dos casos se registra nada.
  3. En `TechnicalBlockRegistration` (bean aparte, para que la transacción se abra a través del proxy de Spring): `RoomRepositoryPort.lockById(roomId)` (regla 13 del plan base); volver a validar la existencia, que no esté `Inactive` y, si inicia hoy, que esté `Available`; consultar mantenimientos con `ConsultMaintenanceAvailabilityUseCase` (rechazar si `available = false`, pasando sus `conflicts` al error), solo dentro del bloqueo, para que dos programaciones simultáneas no registren rangos cruzados (caso límite 2); crear el informe en `Scheduled` con `reportDateTime` de `Clock` y el miembro que lo programa (usuario actual); si el informe `covers(today)`: `TransitionRoomStateUseCase.transition(roomId, TechnicalBlock, miembro, TECHNICAL_BLOCK, null)` (registra el periodo) e informe `Applied`; publicar el aviso con `MaintenanceNoticePublisher.publishScheduled(informe.id(), habitación, startDate, estimatedEndDate)` (plan base, T024), después de registrar, en el orden que acordó Módulo 2: consultar reservas, decidir, registrar y publicar.
  - La precedencia de errores no cambia: reservas antes que mantenimientos.
- [x] T021 [US1] Implementar `ApplyScheduledTechnicalBlocksService` (FR-002, FR-016, FR-018):
  - `applyForRoom(UUID roomId, LocalDate today)` (`@Transactional`, propagación `REQUIRED`): `RoomRepositoryPort.lockById(roomId)` (regla 13 del plan base) y, si la habitación está `Available` y tiene un informe `Scheduled` que `covers(today)`, aplicarlo con `TransitionRoomStateUseCase` (`sourceFlow = TECHNICAL_BLOCK` y actor nulo) y marcarlo `Applied`; si no, no hace nada. Lo invoca también `CompleteCleaningService` (`plan-confirmar-fin-de-limpieza.md`, T016): se une a su transacción y vuelve a bloquear la fila sin esperar.
  - `runDaily(LocalDate today)` (sin transacción; idempotente por estado, sin marcador de día): primero `expireScheduledEndedBefore(today)` en su propia transacción; después `findRoomIdsWithScheduledCovering(today)` y `applyForRoom` por cada habitación, cada una en su propia transacción a través del proxy. Recibe el día que le pasa el disparador (T022).
  - No publica avisos de mantenimiento (convenciones de mensajería del plan base); el aviso diario lo publica `DailyMaintenanceNoticeService` (T025, FR-020).
  - Hecho (L25): `applyForRoom` vive en el bean `ScheduledTechnicalBlockApplier`, así cada habitación de `runDaily` pasa por el proxy y tiene su propia transacción (la capa de aplicación no puede usar `@Lazy` ni `TransactionTemplate`, regla 1 de `ArchitectureTest`). El informe que se aplica sale de `findActiveOverlapping(roomId, hoy, hoy)` filtrado a `Scheduled`, no del primer `Scheduled`, por si queda uno vencido sin caducar. Si una habitación falla, `runDaily` lo registra en el log y sigue con las demás; su informe sigue `Scheduled`.
- [x] T022 [US1] Implementar `TechnicalBlockScheduler`, que solo dispara `runDaily`: un listener de `DailyReservationListIngestedEvent` con `@TransactionalEventListener(phase = AFTER_COMMIT)` y `@Transactional(propagation = NOT_SUPPORTED)`, para correr después de confirmar la ingesta sin que un fallo la revierta, que invoca `runDaily(operationalDate)` solo si el `operationalDate` del evento coincide con la fecha de hoy según `Clock` (si no, lista atrasada o reentregada, no hace nada); y como respaldo `@Scheduled(cron = "0 0 1 * * *", zone = "America/Bogota")`, que invoca `runDaily` con la fecha de `Clock`. Repetir la ejecución no tiene efecto, porque solo toca informes `Scheduled` (FR-002, FR-016; regla 10 del plan base).
- [x] T023 [US1] Implementar `TechnicalBlockQueryService`, que obtiene `responsibleName` con `StaffAccountRepositoryPort.findByUsername(maintenanceStaffMemberId)` (el `username` si la cuenta no existe), y `TechnicalBlockController` con `POST /api/rooms/{roomId}/technical-blocks` y `GET /api/rooms/{roomId}/technical-blocks/current` (FR-009, FR-018).
  - Avance (L25): `TechnicalBlockController` con `POST /api/rooms/{roomId}/technical-blocks` (201, `technicalBlockId` y `status`). `TechnicalBlockQueryService` y `GET …/current` llegan con L26.
  - Hecho (L26): `GetCurrentTechnicalBlockUseCase` y `GET …/current`, probado en `TechnicalBlockSchedulingIntegrationTest` (Scheduled más temprano, luego el Applied, 404 sin informe y habitación inexistente).
- [x] T024 [US1] Construir `ScheduleTechnicalBlockDialog.tsx` (justificación de máximo 500 caracteres, fecha de inicio y fecha estimada de fin en DD-MM-YYYY, mensajes de la regla incumplida, de `VALIDATION_ERROR` por campo según `details.field`, de `ROOM_INVALID_STATE` según `details.currentStatus` y de conflictos, éxito con el estado resultante según `status`) y `TechnicalBlockViewDialog.tsx` (justificación, responsable, rango y fecha de programación), conectados a los botones del panel de mantenimiento (`plan-consultar-panel-mantenimiento.md`, T011) (FR-005, FR-010, FR-015).
  - Hecho (F07): la validación previa (`validateSchedule` en `technicalBlockErrors.ts`) repite las reglas de `MaintenanceDateRange.forScheduling` y del estado en el orden del backend, con los límites de `MAINTENANCE_LIMITS` (90 y 365 días, copia de `application.yml`). Las fechas usan el selector nativo (`type="date"`), que el navegador muestra en su formato regional; las fechas que se muestran (avisos e informe) van en DD-MM-YYYY. "Reintentar" ante `MODULE_2_UNAVAILABLE` reenvía los mismos datos.
  - Ajuste (decisiones abiertas tras RF4): el diálogo pasa a React Hook Form + Zod, como el resto de los formularios (regla de UI 2 del plan de fases del frontend). `validateSchedule` se reemplaza por `scheduleTechnicalBlockSchema(today, status)` (`scheduleTechnicalBlockSchema.ts`), que marca cada regla en su campo: el motivo; el formato, `START_IN_PAST`, `START_TOO_FAR` y el estado en la fecha de inicio; el formato, `END_BEFORE_START` y `RANGE_TOO_LONG` en la fecha de fin. Antes solo se veía la primera regla incumplida, en un aviso general. Los rechazos del backend con campo (`VALIDATION_ERROR` por `details.field` e `INVALID_DATE_RANGE` por `details.rule`, `scheduleFieldError`) se marcan con `setError`; `RESERVATION_CONFLICT`, `MAINTENANCE_CONFLICT`, `ROOM_INVALID_STATE`, `MODULE_2_UNAVAILABLE` y la red siguen en el aviso del diálogo. Los diálogos de confirmación con un único campo (`DamageReportDialog`, `ConfirmCleaningEndDialog`) quedan como excepción a la regla de UI 2.
  - Ajuste (F20, 12-11-2026): el rechazo por estado con inicio hoy dice "Habitación {estado} hoy. Elige otra fecha de inicio." (texto elegido por el usuario). Antes ponía la etiqueta de las listas en medio de la frase ("Hoy la habitación está En limpieza…") y con `TechnicalBlock` quedaba "está Bloqueo técnico". `STATUS_IN_SENTENCE` de `technicalBlockErrors.ts` escribe cada estado para la frase; lo prueban `technicalBlockErrors.test.ts` (todos los estados) y `ScheduleTechnicalBlockDialog.test.tsx`. El de dada de baja no cambia.
- [x] T025 [US1] Implementar `DailyMaintenanceNoticeService` (`@Transactional`, sin bloquear filas, porque `MaintenanceNoticePublisher` exige una transacción abierta) y su disparo a las 00:00 en `TechnicalBlockScheduler` (`@Scheduled(cron = "0 0 0 * * *", zone = "America/Bogota")`), separado de `runDaily` para que el aviso no salga dos veces al día: con `findByStatusIn` (plan base, T008), `findAppliedByRoomIds` con `isOverdue(today)` y `DamageReportRepositoryPort.findLatestByRoomIds` (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`), publicar `publishScheduled(maintenanceId, habitación, today, today)` por cada habitación `DisabledForRepairs` (id del último `DamageReport`) y por cada informe `Applied` vencido (id del informe) (FR-020).
  - Ajuste (revisión de L26): una habitación `TechnicalBlock` sin informe `Applied` (dato inconsistente) no recibe aviso, porque no hay `maintenanceId` que enviar, y deja un `log.warn`, como la `DisabledForRepairs` sin reporte de daño. Módulo 2 no la asigna igual: la consulta de mantenimientos la responde no disponible hoy (`plan-consultar-informacion-mantenimientos.md` T011).

**Checkpoint**: Los mantenimientos preventivos se programan, se aplican en su fecha y caducan si no pueden aplicarse; el panel de mantenimiento muestra su rango y su informe.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T026 Prueba de rendimiento (etiquetada, fuera del CI por defecto; estrategia de testing del plan base): la programación válida completa (validación, consultas y registro) toma menos de 2 segundos con Módulo 2 simulado respondiendo en su tiempo normal (SC-001).
  - Pendiente (fuera de los lotes; `@Tag("performance")` a pedido).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), `RoomRepositoryPort.lockById` (T008, regla 13), la tabla `technical_block_report` (T017), `SourceFlow`, `MaintenanceProperties` y `MaintenanceLimits` (T019), `MaintenanceNoticePublisher` (T024), el evento de la regla 10, la carpeta `scheduling/` y `ClockConfig`.
- **Planes de los que depende**:
  - `plan-consultar-reservas.md`: `CheckRoomReservationConflictsUseCase` (T027), `ExternalModule2UnavailableException` y `ReservationConflictException` (T029), y la publicación de `DailyReservationListIngestedEvent` dentro de la transacción de la ingesta (T012).
  - `plan-consultar-informacion-mantenimientos.md`: `ConsultMaintenanceAvailabilityUseCase` y `MaintenanceDateRange.forScheduling`. Debe implementarse antes de la Phase 3 de este plan.
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: `DamageReportRepositoryPort.findLatestByRoomIds` para el aviso diario (T025).
  - `plan-consultar-panel-mantenimiento.md`: panel de mantenimiento con los botones que abren los diálogos de este plan.
  - `plan-confirmar-reparacion-finalizada.md`: cierre del informe como `Completed`.
  - `plan-confirmar-fin-de-limpieza.md`: invoca `applyForRoom` (T020) al confirmar el fin de limpieza de una habitación con un mantenimiento programado que contiene hoy; se une a su transacción y vuelve a bloquear la fila sin esperar (regla 13 del plan base).
- **Orden**: Phase 1 → Phase 2 → (Phases 2 y 3 de `plan-consultar-informacion-mantenimientos.md`, que usan T003 de este plan) → Phase 3 → Phase 4.
