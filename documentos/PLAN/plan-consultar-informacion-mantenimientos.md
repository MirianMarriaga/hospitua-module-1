# Implementation Plan: Consultar Información de Mantenimientos

**Date**: 2026-10-08  
**Spec**: [spec-consultar-informacion-mantenimientos.md](../SPEC/spec-consultar-informacion-mantenimientos.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso de solo lectura **Consultar Información de Mantenimientos**, que responde si una habitación está disponible frente a los mantenimientos en un rango de fechas y, si no lo está, las fechas de cada mantenimiento que se cruza (FR-002, FR-003). La disponibilidad se refiere solo a mantenimientos: no evalúa reservas, ocupación ni bajas. Lo consumen:

1. **Módulo 2**, por REST, antes de asignar una habitación a una reserva (`GET /api/rooms/{roomId}/maintenance-availability`, ya listado en el plan base). Es el único consumidor REST (FR-001), con el formato de respuesta acordado con Módulo 2.
2. **Programar Bloqueo Técnico para Habitación**, de forma interna mediante el puerto `ConsultMaintenanceAvailabilityUseCase` (FR-004 de ese spec).

Reglas principales:

- **Días evaluados (FR-003):** el puerto recibe días inclusivos. Para Módulo 2, el controlador convierte llegada y salida en los días de la llegada al día anterior a la salida (el día de salida no se considera ocupado); *Programar Bloqueo Técnico* consulta con el último día del mantenimiento incluido.
- **Validación (FR-005 a FR-009):** fechas reales y fin no anterior al inicio; un rango que termina antes de hoy se rechaza y uno que empieza antes de hoy se evalúa desde hoy. No se aplican los límites de 90 y 365 días (FR-008).
- **Cruces (FR-010):** informes `Scheduled` o `Applied` cuyo rango `[inicio, fin estimado]` se solapa con los días evaluados, incluidos los extremos; un informe `Applied` vencido no bloquea fechas futuras.
- **Hoy (FR-014):** si la habitación está hoy en `DisabledForRepairs`, o en `TechnicalBlock` con el informe vencido, y los días evaluados incluyen hoy, se responde un cruce con inicio y fin en la fecha actual.
- **Errores:** `roomId` inválido (FR-013) y habitación inexistente (FR-012) se distinguen de la respuesta negativa. La consulta no modifica datos (FR-004, FR-011).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Solo lectura de `room` y `technical_block_report` (plan base, T018).
- **Performance Goals**: respuesta en menos de 200 ms (SC-001), con el índice `(room_id, status)` de `technical_block_report`. Cubre el requisito de Módulo 2 de menos de 1 segundo por consulta, con hasta 10 consultas por reserva.
- **Constraints**:
  - Las validaciones de fechas se ejecutan antes de leer la tabla de mantenimientos (FR-009, SC-002).
  - La respuesta solo incluye disponibilidad y fechas de los cruces; nunca la justificación ni otros datos del mantenimiento (FR-002).
  - Un bloqueo `Applied` vencido o un daño (`DisabledForRepairs`) no bloquean fechas futuras; solo impiden asignar la habitación para el día actual (FR-014; casos límite 6 y 8). La habitación sigue en su estado hasta que mantenimiento confirme la reparación, y el panel de mantenimiento marca el bloqueo como vencido.
  - No genera registros en `room_state_history` (FR-011).
  - Casos límite sin implementación propia: 2 (consulta y programación simultáneas: *Programar Bloqueo Técnico* verifica las reservas antes de registrar; la simultaneidad exacta es una limitación aceptada) y 9 (reservas, ocupación y bajas se verifican con sus propias consultas).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>` del usuario de servicio de Módulo 2, con el rol `MODULE_2` (plan base, T015) (FR-001).
- **Fechas**: parámetros y fechas de la respuesta de calendario en formato ISO `YYYY-MM-DD`.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto. Siempre se responde con un error controlado, nunca con un 500 (Módulo 2 bloquea la reserva si no recibe respuesta). La columna "Texto en la interfaz" es el `message` que recibe Módulo 2.
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Credenciales de servicio inválidas o vencidas." |
| 403 | `FORBIDDEN` | El token no tiene el rol `MODULE_2` (FR-001, caso límite 1) | "El consumidor no está autorizado para esta consulta." |

---

## GET /api/rooms/{roomId}/maintenance-availability

**Descripción:** Indica si la habitación está disponible frente a los mantenimientos para una estadía y, si no lo está, devuelve el inicio y el fin estimado de cada mantenimiento que se cruza (FR-001 a FR-014).
**Rol autorizado:** `MODULE_2`

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
| `startDate` | query | date | Sí | Fecha de llegada | `2026-10-22` |
| `endDate` | query | date | Sí | Fecha de salida; no se considera ocupada. Igual a la llegada es válido (se evalúa ese día) | `2026-10-26` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `roomId` | UUID | Habitación consultada | `"1a2b…"` |
| `available` | boolean | `true` si ningún mantenimiento se cruza con los días evaluados; `false` si al menos uno se cruza. Solo se refiere a mantenimientos (FR-002) | `false` |
| `conflicts` | array | Cruces ordenados por inicio; vacío si `available` es `true` (FR-002, FR-010, FR-014) | |
| `conflicts[].maintenanceStart` | string | Inicio del mantenimiento, o la fecha actual en el caso de FR-014 | `"2026-10-24"` |
| `conflicts[].maintenanceEnd` | string | Fin estimado del mantenimiento, o la fecha actual en el caso de FR-014 | `"2026-10-28"` |

**Body (JSON)**, con cruce:

```json
{
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "available": false,
  "conflicts": [
    { "maintenanceStart": "2026-10-24", "maintenanceEnd": "2026-10-28" }
  ]
}
```

**Body (JSON)**, sin cruce:

```json
{
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "available": true,
  "conflicts": []
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `roomId` sin formato de UUID, o falta `startDate` o `endDate`, o no tienen formato de fecha (FR-005, FR-013) | "La habitación y las fechas deben tener un formato válido (fechas AAAA-MM-DD)." |
| 400 | `INVALID_DATE_RANGE` | Fecha inexistente, salida anterior a la llegada o rango que termina antes de hoy (FR-005 a FR-007, esc. 4, caso límite 4); `details.rule` indica la regla | Mensaje de la regla (tabla siguiente) |
| 404 | `ROOM_NOT_FOUND` | El `roomId` no corresponde a una habitación registrada (FR-012, caso límite 7) | "No se encontró la habitación." |

**Mensajes por regla (`details.rule`)**, compartidos con *Programar Bloqueo Técnico para Habitación*:

| `rule` | Usada por | Texto en la interfaz |
| --- | --- | --- |
| `INVALID_DATE` | Ambos | "La fecha no existe en el calendario." |
| `END_BEFORE_START` | Ambos | "La fecha de fin no puede ser anterior a la fecha de inicio." |
| `RANGE_IN_PAST` | Esta consulta | "El rango consultado termina antes de hoy." |
| `START_IN_PAST` | *Programar Bloqueo Técnico para Habitación* | "La fecha de inicio no puede ser anterior a hoy." |
| `RANGE_TOO_LONG` | *Programar Bloqueo Técnico para Habitación* | "El rango no puede superar {max-range-days} días." |
| `START_TOO_FAR` | *Programar Bloqueo Técnico para Habitación* | "La fecha de inicio no puede estar a más de {max-advance-days} días de hoy." |

Los valores entre llaves salen de `MaintenanceProperties` (90 y 365 días por defecto).

```json
{
  "errorCode": "INVALID_DATE_RANGE",
  "message": "La fecha de fin no puede ser anterior a la fecha de inicio.",
  "timestamp": "2026-10-08T15:30:00-05:00",
  "path": "/api/rooms/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/maintenance-availability",
  "details": { "rule": "END_BEFORE_START" }
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
│   │   ├── MaintenanceDateRange.java              # Value Object: días evaluados (primer y último día, inclusivos)
│   │   └── MaintenanceConflict.java               # Value Object: inicio y fin de un cruce
│   ├── exception/
│   │   └── InvalidDateRangeException.java         # Con la regla incumplida
│   └── ports/in/
│       └── ConsultMaintenanceAvailabilityUseCase.java
├── application/
│   ├── service/
│   │   ├── MaintenanceAvailabilityService.java
│   │   └── MaintenanceDateRangeValidator.java     # Modos AVAILABILITY (esta consulta) y SCHEDULING (bloqueo técnico)
│   └── dto/
│       └── MaintenanceAvailabilityDto.java        # roomId, available, conflicts
└── infrastructure/adapters/in/web/
    └── MaintenanceAvailabilityController.java     # Convierte llegada y salida en días evaluados
```

**Structure Decision**: El puerto trabaja siempre con días inclusivos (`firstDay`, `lastDay`). El controlador REST convierte la estadía de Módulo 2 en `firstDay = startDate` y `lastDay = max(startDate, endDate − 1)`; *Programar Bloqueo Técnico para Habitación* llama al puerto con su inicio y su fin estimado. `MaintenanceDateRangeValidator` tiene dos modos: `AVAILABILITY` (esta consulta: rechaza rangos que terminan antes de hoy, recorta el inicio a hoy y no aplica límites) y `SCHEDULING` (bloqueo técnico: rechaza inicios anteriores a hoy y aplica los límites de `MaintenanceProperties`). No tiene frontend propio.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Implementar los dos modos de `MaintenanceDateRangeValidator`: `AVAILABILITY` para esta consulta (sin límites, recorte a hoy y rechazo de rangos pasados) y `SCHEDULING` para *Programar Bloqueo Técnico para Habitación* (rechazo de inicios pasados y límites de `MaintenanceProperties`, plan base T020) (FR-007, FR-008).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Implementar `MaintenanceDateRange`, `MaintenanceConflict` e `InvalidDateRangeException` (con `rule`: `INVALID_DATE`, `END_BEFORE_START`, `RANGE_IN_PAST`, `START_IN_PAST`, `RANGE_TOO_LONG`, `START_TOO_FAR`) con `Clock` (FR-005 a FR-008).
- [ ] T003 [P] Agregar a `TechnicalBlockReportRepositoryPort` la consulta de cruces: informes `Scheduled` o `Applied` de la habitación con `technical_block_start_date <= lastDay` y `estimated_technical_block_end_date >= firstDay`, devolviendo sus dos fechas ordenadas por inicio, y la consulta del informe `Applied` vigente de la habitación (FR-010, FR-014).
- [ ] T004 [P] Restringir el endpoint al rol de servicio `MODULE_2` (plan base, T015) (FR-001).

---

## Phase 3: User Story 1 - Consulta de mantenimientos por parte del módulo de reservas o personal de mantenimiento (Priority: P1)

**Goal**: Responder con certeza si una habitación está disponible frente a los mantenimientos en un rango y, si no, con qué mantenimientos se cruza, distinguiendo errores de validación y habitaciones inexistentes de la respuesta negativa.

**Independent Test**: Con mantenimientos `Scheduled`, `Applied` (vigentes y vencidos), `Completed` y `Expired` en la base y habitaciones en `DisabledForRepairs` y `TechnicalBlock`, consultar estadías con y sin cruce como Módulo 2 y rangos desde *Programar Bloqueo Técnico*; consultar rangos inválidos, un `roomId` inválido y una habitación inexistente.

### Tests for User Story 1

- [ ] T005 [P] [US1] Unit test en `MaintenanceAvailabilityServiceTest`: sin mantenimientos que se crucen responde `available = true` y `conflicts` vacío (esc. 1 y 2; FR-002, FR-003; SC-001).
- [ ] T006 [P] [US1] Unit test: un informe `Scheduled` del 24 al 28 de octubre hace no disponible la estadía del 22 al 26 y lo devuelve en `conflicts`; con dos cruces se devuelven ambos ordenados por inicio (esc. 3; FR-002, FR-010).
- [ ] T007 [P] [US1] Unit test con `Clock` fijo: un informe `Applied` vencido no bloquea fechas futuras; los informes `Completed` y `Expired` no generan cruce; el estado `DisabledForRepairs` no bloquea fechas futuras, pero un informe `Scheduled` de esa habitación sí (esc. 6; FR-010; casos límite 5, 6 y 8).
- [ ] T008 [P] [US1] Unit test en `MaintenanceDateRangeValidatorTest` con `Clock` fijo, modo `AVAILABILITY`: fecha inexistente, fin anterior al inicio y rango que termina antes de hoy se rechazan con su regla; un rango que empieza antes de hoy se evalúa desde hoy; inicio igual al fin es válido; rangos de más de 90 días o que inician a más de 365 días son válidos (esc. 4; FR-005 a FR-008; casos límite 3 y 4).
- [ ] T009 [P] [US1] Unit test: las validaciones fallidas no consultan el repositorio (FR-009, SC-002).
- [ ] T010 [P] [US1] Unit test: `roomId` inexistente lanza `ROOM_NOT_FOUND` sin evaluar mantenimientos (FR-012; caso límite 7).
- [ ] T011 [US1] Integration test: el endpoint responde al rol `MODULE_2`, rechaza cualquier otro rol con `403` y no modifica datos ni escribe en `room_state_history` (FR-001, FR-004, FR-011; caso límite 1).

### Implementation for User Story 1

- [ ] T012 [US1] Implementar `MaintenanceAvailabilityService` (`@Transactional(readOnly = true)`): validar los días con `MaintenanceDateRangeValidator` en modo `AVAILABILITY`, verificar que la habitación exista, consultar los cruces y, si la habitación está hoy en `DisabledForRepairs` o en `TechnicalBlock` con el informe `Applied` vencido (`TechnicalBlockReport.isOverdue(today)`, `plan-programar-bloqueo-tecnico-para-habitacion.md` T003) y los días incluyen hoy, agregar el cruce de la fecha actual; devolver `available` y `conflicts` sin la justificación (FR-002 a FR-014).
- [ ] T013 [US1] Implementar `MaintenanceAvailabilityController` con `GET /api/rooms/{roomId}/maintenance-availability` (`@PreAuthorize("hasRole('MODULE_2')")`): validar el formato UUID de `roomId`, convertir llegada y salida en `firstDay = startDate` y `lastDay = max(startDate, endDate − 1)` y mapear los errores de la tabla (FR-001, FR-003, FR-009, FR-013).

**Checkpoint**: Módulo 2 y *Programar Bloqueo Técnico para Habitación* pueden verificar la disponibilidad frente a mantenimientos y conocer las fechas de cada cruce.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T014 Integration test con Testcontainers: la consulta responde en menos de 200 ms con un volumen de prueba de informes (SC-001).
- [ ] T015 Integration test: un `roomId` sin formato de UUID responde `400 VALIDATION_ERROR` y la respuesta exitosa no contiene la justificación ni otros campos fuera de `roomId`, `available` y `conflicts` (FR-002, FR-013).
- [ ] T016 Integration test: un mantenimiento que termina el día anterior a la llegada no es cruce y la respuesta es `available = true` (esc. 5; FR-010).
- [ ] T017 Integration test: un mantenimiento que empieza el día de la salida no es cruce para la estadía, y el mismo rango consultado desde *Programar Bloqueo Técnico* (con su último día incluido) sí lo es (esc. 7; FR-003).
- [ ] T018 Integration test con `Clock` fijo: una habitación en `DisabledForRepairs`, o en `TechnicalBlock` con el informe vencido, con llegada hoy, responde `available = false` con un cruce de inicio y fin en la fecha actual; con llegada mañana responde `available = true` (esc. 8; FR-014).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base la tabla `technical_block_report` (T018), `MaintenanceProperties` (T020), el usuario de servicio con rol `MODULE_2` (T015), `GlobalExceptionHandler` y `ClockConfig`.
- **Planes relacionados**:
  - `plan-programar-bloqueo-tecnico-para-habitacion.md`: consume `ConsultMaintenanceAvailabilityUseCase` (días inclusivos) y `MaintenanceDateRangeValidator` en modo `SCHEDULING`.
  - `plan-confirmar-reparacion-finalizada.md`: marca en el panel de mantenimiento los bloqueos `Applied` vencidos, que esta consulta ya no reporta como cruce para fechas futuras.
- **Orden**: Phases 1 a 3 de este plan antes de la Phase 3 de `plan-programar-bloqueo-tecnico-para-habitacion.md`.
