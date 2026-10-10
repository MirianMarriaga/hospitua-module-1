# Implementation Plan: Dar de Baja Habitación

**Date**: 2026-10-09  
**Spec**: [spec-dar-de-baja-habitacion.md](../SPEC/spec-dar-de-baja-habitacion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Dar de Baja Habitación** para el Administrador, desde la acción "Dar de baja" de una habitación `Available` en el inventario (*Consultar inventario de habitaciones*, FR-008). El flujo tiene cuatro pasos en la interfaz:

1. **Motivo** (FR-008, FR-010): con los datos de la habitación, el Administrador elige la categoría (obligatoria) y escribe el detalle (opcional, obligatorio con "Otro", máximo 500 caracteres).
2. **Confirmación** (FR-005, FR-010): muestra la habitación, el motivo y el detalle.
3. **Verificación de reservas** (FR-002, FR-004, FR-009, FR-010): el sistema consulta a Módulo 2, mediante *Consultar reservas*, las reservas vigentes de la habitación desde hoy, sin límite superior de fechas.
   - Si Módulo 2 devuelve alguna (solo devuelve las vigentes: `PENDING`, `ACTIVE` o `IN_PROGRESS`), rechaza la baja y lista cada reserva en conflicto.
   - Si Módulo 2 no responde, cancela la operación y ofrece "Reintentar", que repite la verificación sin volver a pedir el motivo ni la confirmación.
4. **Resultado** (FR-006, FR-007, FR-010, FR-011): en una sola transacción, la habitación pasa a `Inactive`, se registra la baja (motivo, Administrador y fecha) y el periodo en `room_state_history`.

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `room`, `room_state_history` y `room_decommission` del plan base (T007); no crea tablas. No usa `room_audit_log` (plan base): la transición queda en `room_state_history` y el motivo, el Administrador y la fecha de la baja en `room_decommission` (FR-007, FR-008, FR-011).
- **Performance Goals**: dar de baja una habitación elegible en menos de 1 minuto de interacción (SC-001). La consulta a Módulo 2 tiene timeout de lectura de 2 segundos (plan base, regla 5).
- **Constraints**:
  - Solo el rol `ADMINISTRATOR` da de baja habitaciones (FR-001).
  - La consulta de reservas es obligatoria y ocurre dentro del mismo endpoint de la baja, de modo que no se puede omitir (FR-002).
  - La consulta a Módulo 2 se hace **fuera** de la transacción de base de datos, para no mantenerla abierta durante la llamada remota. Después, la transacción de la baja bloquea la fila de `room` con `SELECT … FOR UPDATE` y vuelve a validar que la habitación siga `Available` (regla 13 del plan base).
  - Cualquier reserva que devuelva Módulo 2 bloquea la baja, sin importar qué tan lejana sea su fecha (FR-004, caso borde "Reservas lejanas"). Módulo 2 solo devuelve las vigentes (`PENDING`, `ACTIVE` o `IN_PROGRESS`), así que Módulo 1 no filtra por estado.
  - Si la consulta falla o Módulo 2 no responde, no se modifica nada y la habitación sigue `Available` (FR-004, FR-009).
  - Si falla la persistencia tras la verificación, la transacción se revierte completa (caso borde 3).
  - La reactivación de una habitación dada de baja (esc. 4, caso borde 2) se implementa en `plan-marcar-habitacion-como-disponible.md`.

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `ADMINISTRATOR`. El Administrador se toma del token.
- **Fechas**: fechas de reserva como fecha de calendario (`YYYY-MM-DD`); la fecha y hora de la baja es ISO 8601 con zona de Colombia.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | `AuthenticationException` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | `AccessDeniedException` | El usuario no tiene el rol `ADMINISTRATOR` (FR-001) | "No tienes permiso para realizar esta acción." |

Los datos de la habitación del paso del motivo se cargan con `GET /api/rooms/{roomId}` (`plan-consultar-inventario-habitaciones.md`).

---

## POST /api/rooms/{roomId}/decommission

**Descripción:** Verifica las reservas vigentes desde hoy y, si no hay ninguna, da de baja la habitación (FR-001 a FR-011). Es el mismo endpoint para el primer intento y para "Reintentar" (FR-009).
**Rol autorizado:** `ADMINISTRATOR`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Habitación a dar de baja | `9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182` |

**Body (JSON):**

| Campo | Tipo | Regla | Ejemplo |
| --- | --- | --- | --- |
| `reasonCategory` | string | Obligatorio: `PERMANENT_REMODELING` (Remodelación permanente), `FLOOR_CLOSURE` (Cierre definitivo de piso), `ADMINISTRATIVE_DECISION` (Decisión administrativa) u `OTHER` (Otro) (FR-008) | `"FLOOR_CLOSURE"` |
| `reasonDetail` | string \| null | Opcional, máximo 500 caracteres; obligatorio y no solo espacios cuando la categoría es `OTHER` (FR-008) | `"Cierre del piso 3 por reestructuración."` |

```json
{
  "reasonCategory": "FLOOR_CLOSURE",
  "reasonDetail": "Cierre del piso 3 por reestructuración."
}
```

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:** los que muestra el resultado (FR-010).

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `decommissionId` | UUID | Registro de la baja | `"7d6c5b4a-…"` |
| `roomId` | UUID | Habitación | `"9b2e…"` |
| `roomNumber` | string | Número | `"305"` |
| `floor` | integer | Piso | `3` |
| `categoryRoom` | string | Categoría (tipo) | `"SUITE"` |
| `status` | string | `Inactive` (FR-006) | `"Inactive"` |
| `reasonCategory` | string | Categoría del motivo | `"FLOOR_CLOSURE"` |
| `reasonDetail` | string \| null | Detalle, si existe | `"Cierre del piso 3…"` |
| `decommissionDateTime` | string | Fecha y hora de la baja, generada por el servidor (FR-007) | `"2026-10-09T15:00:00-05:00"` |

**Body (JSON):**

```json
{
  "decommissionId": "7d6c5b4a-3e2f-4a1b-9c8d-7e6f5a4b3c2d",
  "roomId": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182",
  "roomNumber": "305",
  "floor": 3,
  "categoryRoom": "SUITE",
  "status": "Inactive",
  "reasonCategory": "FLOOR_CLOSURE",
  "reasonDetail": "Cierre del piso 3 por reestructuración.",
  "decommissionDateTime": "2026-10-09T15:00:00-05:00"
}
```

El resultado indica que la habitación ya no aparece entre las habitaciones disponibles (FR-010). El Administrador que hizo la baja no se muestra, pero queda registrado (FR-007).

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Falta la categoría o no es válida (FR-008, caso borde "Motivo sin categoría") | "Selecciona el motivo de la baja." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Categoría `OTHER` con detalle vacío o solo espacios (FR-008, caso borde "Otro sin descripción") | "Escribe el motivo de la baja." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Detalle de más de 500 caracteres (FR-008, caso borde "Detalle demasiado largo") | "El detalle admite máximo 500 caracteres." |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` | No existe la habitación | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación no está en `Available`, antes de la consulta o al ejecutar la baja (FR-003, esc. 2) | "No es posible dar de baja la habitación: está en estado {estado} y debe estar Disponible." (con `Occupied`: "La habitación tiene un huésped activo; debe estar Disponible para darla de baja.") |
| 409 | `RESERVATION_CONFLICT` | `ReservationConflictException` | Módulo 2 devolvió al menos una reserva vigente desde hoy (FR-004, esc. 3); `details.reservations` lista `reservationRef`, `startDate`, `endDate` y `status` de cada una | "La habitación tiene reservas vigentes. Deben reasignarse antes de darla de baja." |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación cambió de estado mientras se verificaban las reservas (se detecta al bloquear la fila) | "La habitación cambió de estado (estado actual: {estado}); no se dio de baja." |
| 503 | `MODULE_2_UNAVAILABLE` | `ExternalModule2UnavailableException` | Módulo 2 no respondió, superó el timeout o respondió con error (FR-004, FR-009, caso borde "Fallo de Módulo 2") | "No fue posible verificar las reservas de la habitación; no se dio de baja." con el botón "Reintentar" |

```json
{
  "errorCode": "RESERVATION_CONFLICT",
  "message": "La habitación tiene reservas vigentes. Deben reasignarse antes de darla de baja.",
  "timestamp": "2026-10-09T15:00:02-05:00",
  "path": "/api/rooms/9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182/decommission",
  "details": {
    "reservations": [
      { "reservationRef": "RES-10502", "startDate": "2026-10-24", "endDate": "2026-10-27", "status": "ACTIVE" }
    ]
  }
}
```

"Reintentar" vuelve a enviar la misma petición con el motivo ya registrado en el frontend, sin volver a los pasos del motivo ni de la confirmación (FR-009).

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-dar-de-baja-habitacion.md
│   ├── spec-consultar-reservas.md
│   └── spec-marcar-habitacion-como-disponible.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-dar-de-baja-habitacion.md
    ├── plan-consultar-reservas.md
    ├── plan-registrar-habitacion.md
    └── plan-marcar-habitacion-como-disponible.md
```

### Source Code

```text
backend/src/main/java/com/hospitua/habitaciones/
├── domain/
│   ├── model/
│   │   ├── RoomDecommission.java
│   │   └── DecommissionReason.java                # Enum: PERMANENT_REMODELING, FLOOR_CLOSURE, ADMINISTRATIVE_DECISION, OTHER
│   ├── exception/
│   │   └── ReservationConflictException.java      # 409 RESERVATION_CONFLICT (o la de plan-programar-bloqueo-tecnico-para-habitacion.md)
│   └── ports/
│       ├── in/
│       │   └── DecommissionRoomUseCase.java
│       └── out/
│           └── RoomDecommissionRepositoryPort.java
├── application/
│   ├── service/
│   │   └── DecommissionRoomService.java
│   └── dto/
│       ├── DecommissionRoomCommand.java
│       └── RoomDecommissionDto.java
└── infrastructure/adapters/
    ├── in/web/
    │   └── RoomController.java                    # Se agrega POST /api/rooms/{roomId}/decommission
    └── out/persistence/
        ├── entity/RoomDecommissionJpaEntity.java
        └── adapter/JpaRoomDecommissionAdapter.java
frontend/src/
├── pages/administracion/
│   └── DecommissionRoomPage.jsx                   # Pasos: motivo, confirmación, verificación y resultado
├── components/
│   └── ReservationConflictList.jsx                # Reservas en conflicto: referencia, fechas y estado
└── api/
    └── roomService.js                             # Se agrega decommissionRoom()
```

**Structure Decision**: La consulta a Módulo 2 reutiliza `CheckRoomReservationConflictsUseCase.checkDecommissionConflict(roomId)` de `plan-consultar-reservas.md` (T026), que consulta desde hoy sin límite superior (`dateTo = 9999-12-31`, *Consultar reservas* FR-007) y lanza `ExternalModule2UnavailableException` (`503 MODULE_2_UNAVAILABLE`) si Módulo 2 falla. La verificación ocurre dentro de `POST /api/rooms/{roomId}/decommission`; ese plan no expone un endpoint de verificación previa (T027). La transición `Available` → `Inactive` y su periodo en el historial los hace `TransitionRoomStateUseCase` con `sourceFlow = DECOMMISSION_ROOM` (regla 8 del plan base). `DecommissionRoomCommand` contiene `roomId`, `reasonCategory`, `reasonDetail` y `administratorId` (del token).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar la tabla `room_decommission` en la migración inicial (plan base, T007).
- [ ] T002 [P] Verificar `CheckRoomReservationConflictsUseCase.checkDecommissionConflict` y `ExternalModule2UnavailableException` (`plan-consultar-reservas.md`, T008 y T026), y que el resultado incluya `reservationRef`, `startDate`, `endDate` y `status` de cada reserva. La ruta y el formato de la respuesta de Módulo 2 siguen pendientes de confirmación (`[NEEDS_CONFIRMATION_MODULO_2]` en *Consultar reservas*, FR-007); este plan solo depende del puerto.
- [ ] T003 [P] Implementar `RoomDecommission`, `DecommissionReason`, el puerto `RoomDecommissionRepositoryPort`, `RoomDecommissionJpaEntity` y `JpaRoomDecommissionAdapter` sobre la tabla `room_decommission` del plan base.
- [ ] T004 [P] Registrar en `GlobalExceptionHandler` `409 RESERVATION_CONFLICT` con `details.reservations`, reutilizando la excepción de `plan-programar-bloqueo-tecnico-para-habitacion.md` si ya existe.

---

## Phase 3: User Story 1 - Retirar una habitación del inventario activo (Priority: P1)

**Goal**: Que el Administrador dé de baja una habitación `Available` sin reservas que lo impidan, con motivo y trazabilidad, y que la baja se rechace o se cancele en los demás casos sin modificar la habitación.

**Independent Test**: Con `MockRestServiceServer`:
- Módulo 2 responde sin reservas: la habitación pasa a `Inactive` con su registro en `room_decommission` y su periodo en `room_state_history`.
- Módulo 2 responde con una reserva vigente, aunque sea dentro de meses: `409 RESERVATION_CONFLICT` con la reserva listada.
- Módulo 2 supera el timeout: `503 MODULE_2_UNAVAILABLE`.

En los dos últimos casos la habitación sigue `Available`.

### Tests for User Story 1

- [ ] T005 [P] [US1] Unit test en `DecommissionRoomServiceTest`: sin reservas bloqueantes, la habitación pasa a `Inactive` vía `TransitionRoomStateUseCase` con `DECOMMISSION_ROOM`, se crea `RoomDecommission` con motivo, Administrador y hora de `Clock` (esc. 1; FR-006, FR-007, FR-011; SC-004).
- [ ] T006 [P] [US1] Unit test: rechazo con `ROOM_INVALID_STATE` en los 7 estados distintos de `Available`, sin consultar a Módulo 2 (esc. 2; FR-003; SC-002).
- [ ] T007 [P] [US1] Unit test: invoca `checkDecommissionConflict` con el `roomId`; cualquier reserva devuelta rechaza la baja con todas las reservas en conflicto y la habitación sigue `Available` (esc. 3; FR-002, FR-004; SC-005).
- [ ] T008 [P] [US1] Unit test: una reserva vigente con `startDate` a varios meses también rechaza la baja (FR-004, caso borde "Reservas lejanas").
- [ ] T009 [P] [US1] Unit test: con `ExternalModule2UnavailableException` la operación se cancela sin cambios y responde `503`; repetir la petición con el mismo motivo vuelve a verificar (FR-004, FR-009, caso borde "Fallo de Módulo 2").
- [ ] T010 [P] [US1] Unit test: rechazo sin categoría, con `OTHER` y detalle vacío o solo espacios, y con detalle de más de 500 caracteres; con otra categoría el detalle es opcional (FR-008, casos borde de motivo).
- [ ] T011 [US1] Integration test con Testcontainers y `MockRestServiceServer`: si la habitación cambia de estado mientras Módulo 2 responde, la transacción de la baja lo detecta al bloquear la fila, responde `409 ROOM_INVALID_STATE` y no crea `RoomDecommission` (regla 13 del plan base).
- [ ] T012 [US1] Integration test: un fallo forzado al registrar `RoomDecommission` revierte la transición y el periodo del historial; la habitación sigue `Available` (caso borde 3).
- [ ] T013 [P] [US1] Component test en `DecommissionRoomPage`:
  - El paso del motivo muestra número, piso, tipo y estado.
  - "Otro" exige texto y el contador limita a 500 caracteres.
  - La confirmación muestra habitación, motivo y detalle, y cancelar no envía nada.
  - Durante la petición se muestra "Comprobando las reservas de la habitación…".
  - El resultado muestra los datos, el motivo, el detalle y que la habitación ya no aparece entre las disponibles.
  - (esc. 5; FR-005, FR-008, FR-010)
- [ ] T014 [P] [US1] Component test: `RESERVATION_CONFLICT` muestra `ReservationConflictList` con referencia, fechas y estado de cada reserva; `MODULE_2_UNAVAILABLE` muestra "Reintentar", que reenvía la petición sin volver al motivo ni a la confirmación (esc. 3; FR-004, FR-009).

### Implementation for User Story 1

- [ ] T015 [US1] Implementar `DecommissionRoomService` (FR-001 a FR-011):
  - Validar el motivo y que la habitación exista y esté `Available`.
  - Fuera de transacción, invocar `checkDecommissionConflict(roomId)`; si devuelve alguna reserva, lanzar `ReservationConflictException` con la lista.
  - En una transacción (`@Transactional`): bloquear la fila de `room` con `SELECT … FOR UPDATE` y volver a validar `Available` (regla 13 del plan base); invocar `TransitionRoomStateUseCase.transition(roomId, Inactive, administratorId, DECOMMISSION_ROOM, null)`; crear `RoomDecommission`.
- [ ] T016 [US1] Agregar a `RoomController` el endpoint `POST /api/rooms/{roomId}/decommission`, restringido a `ADMINISTRATOR` (FR-001).
- [ ] T017 [US1] Construir en el frontend `DecommissionRoomPage.jsx` con los cuatro pasos y `ReservationConflictList.jsx`; conservar el motivo en el estado de la página para "Reintentar" (FR-005, FR-008 a FR-010).

**Checkpoint**: La baja funciona de forma independiente; la reactivación queda para `plan-marcar-habitacion-como-disponible.md`.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T018 Integration test: una habitación dada de baja no aparece en `GET /api/rooms?status=Available` y sí en `GET /api/rooms?status=Inactive` (SC-003).
- [ ] T019 Integration test con `MockRestServiceServer`: la petición a Módulo 2 lleva `dateFrom` = hoy y `dateTo` = `9999-12-31`, y una reserva a varios meses devuelta por Módulo 2 impide la baja (FR-002; caso borde "Reservas lejanas").

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8), `SourceFlow` con `DECOMMISSION_ROOM` (T020), el cliente REST de Módulo 2 con timeouts (regla 5, T013), la tabla `room_decommission` (T007), el rol `ADMINISTRATOR` (T015) y `ClockConfig`.
- **Planes de los que depende**:
  - `plan-consultar-reservas.md`: `CheckRoomReservationConflictsUseCase.checkDecommissionConflict` y `ExternalModule2UnavailableException` (Phase 5 de ese plan).
  - `plan-consultar-inventario-habitaciones.md`: acción "Dar de baja" y `GET /api/rooms/{roomId}`.
- **Plan que depende de este**: `plan-marcar-habitacion-como-disponible.md` lee `room_decommission` para la confirmación de la reactivación.
- **Orden**: Phase 2 → Phase 3 → Phase 4. La Phase 5 de `plan-consultar-reservas.md` debe estar lista.
