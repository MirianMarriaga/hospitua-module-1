# Implementation Plan: Consultar Información de Mantenimientos

**Date**: 2026-10-08  
**Spec**: [spec-consultar-informacion-mantenimientos.md](../SPEC/spec-consultar-informacion-mantenimientos.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso de solo lectura **Consultar Información de Mantenimientos**, que responde si un rango de fechas de una habitación es factible frente a los mantenimientos programados o aplicados (FR-002, FR-003). Lo consumen:

1. **Módulo 2**, por REST, antes de asignar una habitación a una reserva (`GET /api/rooms/{roomId}/maintenance-availability`, ya listado en el plan base).
2. **Programar bloqueo técnico**, de forma interna mediante el puerto `ConsultMaintenanceAvailabilityUseCase` (FR-004 de ese spec).

Antes de consultar, valida el rango (fechas reales, fin no anterior al inicio, inicio no anterior a hoy, rango máximo de 90 días y antelación máxima de 365 días) y devuelve un error de validación distinto de la respuesta negativa (FR-005 a FR-009). Hay solapamiento con cualquier informe `Scheduled` que intersecte el rango, o con un informe `Applied` desde su inicio y mientras la reparación no se confirme (FR-010). Una habitación inexistente responde "habitación no encontrada" (FR-012). La consulta no modifica datos (FR-004, FR-011).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Solo lectura de `room` y `technical_block_report` (plan base, T018).
- **Performance Goals**: respuesta en menos de 200 ms (SC-001), con el índice `(room_id, status)` de `technical_block_report`.
- **Constraints**:
  - Las validaciones de fechas se ejecutan antes de leer la tabla de mantenimientos (FR-009, SC-002).
  - Los límites de 90 y 365 días son las mismas propiedades configurables de `MaintenanceProperties` que usa *Programar bloqueo técnico* (FR-008).
  - Sin sugerencias adicionales en la respuesta (FR-002).
  - No genera registros en `room_state_history` (FR-011).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`. Roles autorizados: `MAINTENANCE_STAFF` y el rol de servicio de Módulo 2 (`MODULE_2`) para la comunicación entre módulos (FR-001).
- **Fechas**: parámetros de calendario en formato ISO `YYYY-MM-DD`; la interfaz de Módulo 1 los muestra como DD-MM-YYYY.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto. Módulo 2 recibe errores, no mensajes de interfaz (caso límite 1); la columna "Texto en la interfaz" aplica solo al Personal de mantenimiento.
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | El consumidor no es `MAINTENANCE_STAFF` ni Módulo 2 autorizado (FR-001, caso límite 1) | "No tienes permiso para realizar esta acción." |

---

## GET /api/rooms/{roomId}/maintenance-availability

**Descripción:** Indica si el rango consultado es factible para la habitación frente a los mantenimientos programados o aplicados (FR-001 a FR-012).
**Rol autorizado:** `MAINTENANCE_STAFF`, `MODULE_2`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Habitación consultada (una sola por petición) | `1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d` |
| `startDate` | query | date | Sí | Inicio del rango | `2026-10-22` |
| `endDate` | query | date | Sí | Fin del rango (igual al inicio es válido) | `2026-10-26` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `roomId` | UUID | Habitación consultada | `"1a2b…"` |
| `startDate` | string | Inicio del rango | `"2026-10-22"` |
| `endDate` | string | Fin del rango | `"2026-10-26"` |
| `feasible` | boolean | `true` si no hay solapamiento; `false` si lo hay (FR-002) | `false` |

**Body (JSON):**

```json
{
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "startDate": "2026-10-22",
  "endDate": "2026-10-26",
  "feasible": false
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Falta `startDate` o `endDate`, o no tienen formato de fecha (FR-005) | "Ingresa fechas válidas (DD-MM-YYYY)." |
| 400 | `INVALID_DATE_RANGE` | Fecha inexistente, fin anterior al inicio, inicio anterior a hoy, rango mayor a 90 días o antelación mayor a 365 días (FR-005 a FR-008, esc. 4, caso límite 4); `details.rule` indica la regla | "{regla incumplida}." |
| 404 | `ROOM_NOT_FOUND` | El `roomId` no corresponde a una habitación registrada (FR-012, caso límite 7) | "No se encontró la habitación." |

```json
{
  "errorCode": "INVALID_DATE_RANGE",
  "message": "El rango consultado inicia en una fecha anterior a la fecha actual.",
  "timestamp": "2026-10-08T15:30:00-05:00",
  "path": "/api/rooms/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/maintenance-availability",
  "details": { "rule": "START_IN_PAST" }
}
```

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-consultar-informacion-mantenimientos.md
│   └── spec-programar-bloqueo-tecnico-para-habitacion.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-consultar-informacion-mantenimientos.md
    └── plan-programar-bloqueo-tecnico-para-habitacion.md
```

### Source Code

```text
backend/src/main/java/com/hospitua/habitaciones/
├── domain/
│   ├── model/
│   │   └── MaintenanceDateRange.java              # Value Object: rango validado
│   ├── exception/
│   │   └── InvalidDateRangeException.java         # Con la regla incumplida
│   └── ports/in/
│       └── ConsultMaintenanceAvailabilityUseCase.java
├── application/
│   ├── service/
│   │   ├── MaintenanceAvailabilityService.java
│   │   └── MaintenanceDateRangeValidator.java     # Lo reutiliza Programar bloqueo técnico
│   └── dto/
│       └── MaintenanceAvailabilityDto.java
└── infrastructure/adapters/in/web/
    └── MaintenanceAvailabilityController.java
```

**Structure Decision**: El validador de rangos vive en este plan y lo reutiliza `plan-programar-bloqueo-tecnico-para-habitacion.md`, para que ambos casos de uso apliquen exactamente las mismas reglas. No tiene frontend propio: el Personal de mantenimiento lo usa a través de "Programar mantenimiento".

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Usar los límites de `MaintenanceProperties` (plan base, T020) (FR-008).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Implementar `MaintenanceDateRange`, `InvalidDateRangeException` (con `rule`: `INVALID_DATE`, `END_BEFORE_START`, `START_IN_PAST`, `RANGE_TOO_LONG`, `START_TOO_FAR`) y `MaintenanceDateRangeValidator` con `Clock` (FR-005 a FR-008).
- [ ] T003 [P] Agregar a `TechnicalBlockReportRepositoryPort` la consulta de solapamiento: informes `Scheduled` que intersectan el rango y `Applied` con inicio no posterior al fin del rango (FR-010).
- [ ] T004 [P] Restringir el endpoint a `MAINTENANCE_STAFF` y al rol de servicio `MODULE_2` (plan base, T015) (FR-001).

---

## Phase 3: User Story 1 - Consulta de mantenimientos por parte del módulo de reservas o personal de mantenimiento (Priority: P1)

**Goal**: Responder con certeza si un rango es factible para una habitación, distinguiendo errores de validación y habitaciones inexistentes de la respuesta negativa.

**Independent Test**: Con mantenimientos `Scheduled`, `Applied`, `Completed` y `Expired` en la base, consultar rangos con y sin solapamiento como Módulo 2 y como Personal de mantenimiento; consultar rangos inválidos y una habitación inexistente.

### Tests for User Story 1

- [ ] T005 [P] [US1] Unit test en `MaintenanceAvailabilityServiceTest`: sin mantenimientos en el rango responde `feasible = true` (esc. 1 y 2; FR-002, FR-003).
- [ ] T006 [P] [US1] Unit test: un informe `Scheduled` del 24 al 28 de octubre hace no factible el rango del 22 al 26; la coincidencia en un día extremo también cuenta (esc. 3; FR-010).
- [ ] T007 [P] [US1] Unit test: un informe `Applied` con fecha estimada de fin vencida sigue bloqueando rangos posteriores; los informes `Completed` y `Expired` no bloquean (FR-010; casos límite 5 y 6).
- [ ] T008 [P] [US1] Unit test en `MaintenanceDateRangeValidatorTest` con `Clock` fijo: fecha inexistente, fin anterior al inicio, inicio pasado, rango > 90 días y antelación > 365 días se rechazan con su regla; inicio igual al fin es válido (esc. 4; FR-005 a FR-008; casos límite 3 y 4).
- [ ] T009 [P] [US1] Unit test: las validaciones fallidas no consultan el repositorio (FR-009, SC-002).
- [ ] T010 [P] [US1] Unit test: `roomId` inexistente lanza `ROOM_NOT_FOUND` sin evaluar mantenimientos (FR-012; caso límite 7).
- [ ] T011 [US1] Integration test: el endpoint responde a `MAINTENANCE_STAFF` y a `MODULE_2`, rechaza otros roles con `403` y no modifica datos ni escribe en `room_state_history` (FR-001, FR-004, FR-011; caso límite 1).

### Implementation for User Story 1

- [ ] T012 [US1] Implementar `MaintenanceAvailabilityService` (`@Transactional(readOnly = true)`): validar el rango con `MaintenanceDateRangeValidator`, verificar que la habitación exista y consultar el solapamiento; devolver `feasible` (FR-002 a FR-012).
- [ ] T013 [US1] Implementar `MaintenanceAvailabilityController` con `GET /api/rooms/{roomId}/maintenance-availability` (`@PreAuthorize("hasAnyRole('MAINTENANCE_STAFF','MODULE_2')")`) y el mapeo de errores de la tabla (FR-001, FR-009).

**Checkpoint**: Módulo 2 y *Programar bloqueo técnico* pueden verificar la factibilidad de un rango con las mismas reglas.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T014 Integration test con Testcontainers: la consulta responde en menos de 200 ms con un volumen de prueba de informes (SC-001).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base la tabla `technical_block_report` (T018), `MaintenanceProperties` (T020), el rol `MODULE_2` (T015), `GlobalExceptionHandler` y `ClockConfig`.
- **Planes relacionados**:
  - `plan-programar-bloqueo-tecnico-para-habitacion.md`: consume `ConsultMaintenanceAvailabilityUseCase` y `MaintenanceDateRangeValidator`.
- **Orden**: Phases 1 a 3 de este plan antes de la Phase 3 de `plan-programar-bloqueo-tecnico-para-habitacion.md`.
