# Implementation Plan: Marcar Habitación como Disponible

**Date**: 2026-10-09  
**Spec**: [spec-marcar-habitacion-como-disponible.md](../SPEC/spec-marcar-habitacion-como-disponible.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Marcar Habitación como Disponible**, que centraliza toda transición de una habitación hacia `Available` (FR-001). Detalla el puerto transversal `MarkRoomAvailableUseCase` del plan base (T019), que atiende tres flujos:

1. **Reactivación directa del Administrador** (FR-002, FR-009, FR-010): desde la acción "Marcar como disponible" de una habitación `Inactive` en el inventario, el Administrador confirma la reactivación viendo la fecha, el motivo y el detalle de la baja; la habitación pasa de `Inactive` a `Available`. Es el único flujo con endpoints propios.
2. **Fin de limpieza** (FR-003): *Confirmar fin de limpieza* invoca el puerto para pasar de `InCleaning` a `Available`. En la misma transacción, ese flujo continúa a `Reserved` (*Marcar habitación como reservada*) o a `DisabledForRepairs` (si se reportó un daño); este plan no decide esa continuación (FR-004).
3. **Liberación de una reserva** (FR-004): la ingesta de reservas (*Consultar reservas*, FR-009) invoca el puerto para pasar de `Reserved` a `Available` cuando la reserva se retira (`REMOVED`, que incluye el no-show que decide Módulo 2), cuando un `UPDATED` quita la habitación o cuando la reserva no figura en la lista de las 00:00. Limpia `reserved_by_reservation_ref`.

La trazabilidad de los tres flujos es el periodo en `room_state_history` (FR-008, FR-011), que registra `TransitionRoomStateUseCase` una sola vez con el `sourceFlow` del invocador. No se registra en `room_audit_log`: esa tabla es para eventos que no son transiciones o que necesitan datos que `room_state_history` no guarda, y el periodo ya guarda el actor, el flujo y la hora que pide FR-008.

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `room`, `room_state_history` y `room_decommission` del plan base (T007); no crea tablas.
- **Performance Goals**: toda transición válida a `Available` en menos de 1 segundo (SC-001).
- **Constraints**:
  - Estado de origen según el flujo (FR-004, FR-006; SC-002): reactivación → `Inactive`; fin de limpieza → `InCleaning`; liberación → `Reserved` vinculada a la misma `reservationRef`. Cualquier otro origen se rechaza sin cambios.
  - Solo el rol `ADMINISTRATOR` usa los endpoints de reactivación (FR-002). Los otros dos flujos son internos y no tienen endpoint.
  - El puerto se ejecuta dentro de la transacción del invocador; si algo falla, el invocador hace rollback y la habitación queda en su estado de origen (casos borde 3 y 4).
  - Un solo periodo en el historial por transición: cuando invoca *Confirmar fin de limpieza*, el periodo queda con `source_flow = CONFIRM_CLEANING_END` (su FR-013) y este caso de uso no agrega otro (FR-011).
  - Reactivación idempotente: la petición envía el `decommissionId` que se mostró en la confirmación; si esa baja ya fue revertida, responde sin cambios y sin un segundo periodo en el historial (FR-010, caso borde 2).
  - La habitación queda visible en `Available` apenas se confirma la transacción, porque las consultas leen el estado persistido (FR-007; SC-003).

---

## Contrato del puerto `MarkRoomAvailableUseCase`

Lo invocan `ReactivateRoomService` (este plan), `CompleteCleaningService` (`plan-confirmar-fin-de-limpieza.md`), y `DailyReservationIngestionService` (`plan-consultar-reservas.md`, T012 y T013). Cada invocador bloquea la fila de `room` con `SELECT … FOR UPDATE` al inicio de su transacción (regla 13 del plan base):

```java
public interface MarkRoomAvailableUseCase {
    // Debe ejecutarse dentro de una transacción ya abierta por el flujo invocador.
    MarkRoomAvailableResult markAvailable(MarkRoomAvailableCommand command);
}

// sourceFlow (enum SourceFlow del plan base):
//   MARK_AVAILABLE       → origen Inactive (reactivación; actorId obligatorio)
//                          u origen Reserved (liberación; reservationRef obligatoria, actorId nulo)
//   CONFIRM_CLEANING_END → origen InCleaning (actorId = miembro del personal de limpieza)
public record MarkRoomAvailableCommand(UUID roomId, SourceFlow sourceFlow, String actorId, String reservationRef) {}

public record MarkRoomAvailableResult(UUID roomId, RoomStatus previousStatus, OffsetDateTime transitionDateTime) {}
```

Ante un origen no válido lanza `InvalidRoomTransitionException` (plan base) con el estado actual. El endpoint del invocador la traduce a `409`; la ingesta de reservas la registra como advertencia y continúa con la siguiente habitación.

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `ADMINISTRATOR`. El Administrador se toma del token.
- **Fechas**: ISO 8601 con zona de Colombia (ej. `2026-10-09T16:00:00-05:00`).
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | El usuario no tiene el rol `ADMINISTRATOR` (FR-002) | "No tienes permiso para realizar esta acción." |

---

## GET /api/rooms/{roomId}/decommission

**Descripción:** Devuelve los datos de la habitación `Inactive` y de su baja vigente, para la confirmación de la reactivación (FR-009, esc. 2).
**Rol autorizado:** `ADMINISTRATOR`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Habitación inactiva | `9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `roomId` | UUID | Habitación | `"9b2e…"` |
| `roomNumber` | string | Número | `"305"` |
| `floor` | integer | Piso | `3` |
| `categoryRoom` | string | Categoría (tipo) | `"SUITE"` |
| `status` | string | `Inactive` | `"Inactive"` |
| `decommissionId` | UUID \| null | Baja vigente; nulo si la habitación no tiene registro de baja (ej. cargada inactiva por la semilla) | `"7d6c…"` |
| `decommissionDateTime` | string \| null | Fecha de la baja | `"2026-10-09T15:00:00-05:00"` |
| `reasonCategory` | string \| null | Categoría del motivo | `"FLOOR_CLOSURE"` |
| `reasonDetail` | string \| null | Detalle, si existe | `"Cierre del piso 3…"` |

**Body (JSON):**

```json
{
  "roomId": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182",
  "roomNumber": "305",
  "floor": 3,
  "categoryRoom": "SUITE",
  "status": "Inactive",
  "decommissionId": "7d6c5b4a-3e2f-4a1b-9c8d-7e6f5a4b3c2d",
  "decommissionDateTime": "2026-10-09T15:00:00-05:00",
  "reasonCategory": "FLOOR_CLOSURE",
  "reasonDetail": "Cierre del piso 3 por reestructuración."
}
```

Si `decommissionId` es nulo, la confirmación muestra "Sin registro" en la fecha y el motivo.

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 404 | `ROOM_NOT_FOUND` | No existe la habitación (caso borde 1) | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | La habitación no está en `Inactive` (esc. 3) | "La habitación no está Inactiva (estado actual: {estado}); no es posible marcarla como disponible." |

---

## POST /api/rooms/{roomId}/reactivation

**Descripción:** Reactiva una habitación `Inactive`: pasa a `Available` y la baja queda revertida (FR-002, FR-005, FR-008, FR-010, FR-011).
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
| `roomId` | path | UUID | Sí | Habitación a reactivar | `9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182` |

**Body (JSON):** `decommissionId` es el que devolvió `GET /api/rooms/{roomId}/decommission`; nulo si la habitación no tenía registro de baja.

```json
{
  "decommissionId": "7d6c5b4a-3e2f-4a1b-9c8d-7e6f5a4b3c2d"
}
```

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `roomId` | UUID | Habitación | `"9b2e…"` |
| `roomNumber` | string | Número | `"305"` |
| `floor` | integer | Piso | `3` |
| `categoryRoom` | string | Categoría (tipo) | `"SUITE"` |
| `status` | string | `Available` | `"Available"` |
| `alreadyReactivated` | boolean | `true` si esa baja ya había sido revertida y no se hizo ningún cambio (FR-010, caso borde 2) | `false` |

**Body (JSON):**

```json
{
  "roomId": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182",
  "roomNumber": "305",
  "floor": 3,
  "categoryRoom": "SUITE",
  "status": "Available",
  "alreadyReactivated": false
}
```

El resultado indica que la habitación ya puede recibir reservas y estancias; con `alreadyReactivated: true` indica que no fue necesario hacer cambios (FR-010).

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 404 | `ROOM_NOT_FOUND` | No existe la habitación (caso borde 1) | "No se encontró la habitación." |
| 409 | `ROOM_INVALID_STATE` | La habitación no está en `Inactive` y no es un reintento de la misma baja (esc. 3; FR-006) | "La habitación no está Inactiva (estado actual: {estado}); no es posible marcarla como disponible." |

Si falla la persistencia o la conexión, la transacción se revierte completa y la habitación sigue `Inactive` (caso borde 3).

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-marcar-habitacion-como-disponible.md
│   ├── spec-dar-de-baja-habitacion.md
│   ├── spec-confirmar-fin-de-limpieza.md
│   └── spec-consultar-reservas.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-marcar-habitacion-como-disponible.md
    ├── plan-dar-de-baja-habitacion.md
    ├── plan-confirmar-fin-de-limpieza.md
    └── plan-consultar-reservas.md
```

### Source Code

```text
backend/src/main/java/com/hospitua/habitaciones/
├── domain/
│   └── ports/in/
│       ├── MarkRoomAvailableUseCase.java          # Puerto transversal (plan base, T019)
│       └── ReactivateRoomUseCase.java
├── application/
│   ├── service/
│   │   ├── MarkRoomAvailableService.java
│   │   ├── ReactivateRoomService.java
│   │   └── RoomDecommissionQueryService.java
│   └── dto/
│       ├── MarkRoomAvailableCommand.java
│       ├── MarkRoomAvailableResult.java
│       ├── RoomDecommissionInfoDto.java
│       └── ReactivationResultDto.java
└── infrastructure/adapters/in/web/
    └── RoomController.java                        # Se agregan GET /api/rooms/{roomId}/decommission y POST /api/rooms/{roomId}/reactivation
frontend/src/
├── pages/administracion/
│   ├── ReactivateRoomDialog.jsx                   # Confirmación con datos de la habitación y de la baja
│   └── ReactivationResult.jsx
└── api/
    └── roomService.js                             # Se agregan getDecommission() y reactivateRoom()
```

**Structure Decision**: `MarkRoomAvailableService` valida el origen, delega la transición y el periodo del historial en `TransitionRoomStateUseCase` (regla 8 del plan base) sin escribir en `room_audit_log`. En la liberación, además limpia `reserved_by_reservation_ref`. `ReactivateRoomService` agrega lo propio de la reactivación: la idempotencia por `decommissionId` y la marca de reversión en `room_decommission`.

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que la matriz de transiciones de `RoomStateTransitionService` (plan base, T010) incluya `Inactive`, `InCleaning` y `Reserved` → `Available`, y que `SourceFlow` (T020) incluya `MARK_AVAILABLE` y `CONFIRM_CLEANING_END`.
- [ ] T002 [P] Verificar la tabla `room_decommission` (plan base, T007) y `RoomDecommissionRepositoryPort` (`plan-dar-de-baja-habitacion.md`, T003).
- [ ] T003 [P] Crear `MarkRoomAvailableCommand`, `MarkRoomAvailableResult` y el puerto `MarkRoomAvailableUseCase` con el contrato de este plan.

---

## Phase 3: User Story 1 - Revertir la baja de una habitación inactiva (Priority: P1)

**Goal**: Que el Administrador reactive una habitación `Inactive` tras confirmar con los datos de su baja, y que cualquier otro estado se rechace.

**Independent Test**: Dar de baja la habitación "305" y luego reactivarla: verificar `Available`, la baja con `reactivation_date_time` y el periodo en `room_state_history` con `MARK_AVAILABLE` y el Administrador como actor. Repetir la petición con el mismo `decommissionId` y verificar `alreadyReactivated: true` sin periodo nuevo. Intentar reactivar una habitación `Available` con otra baja y verificar el `409`.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `ReactivateRoomServiceTest`: `Inactive` → `Available` vía el puerto con `MARK_AVAILABLE`, la baja queda con `reactivated_by` y `reactivation_date_time` y el periodo del historial queda con el Administrador como actor (esc. 1; FR-005, FR-008, FR-011; SC-004).
- [ ] T005 [P] [US1] Unit test: rechazo con `ROOM_INVALID_STATE` en `Available`, `Occupied`, `PendingCleaning`, `DisabledForRepairs` y `TechnicalBlock` (esc. 3; FR-006; SC-002).
- [ ] T006 [P] [US1] Unit test: un reintento con el `decommissionId` de una baja ya revertida devuelve `alreadyReactivated: true` sin transición ni periodo nuevo (FR-010, caso borde 2).
- [ ] T007 [P] [US1] Unit test en `RoomDecommissionQueryServiceTest`: devuelve la habitación con la fecha, la categoría y el detalle de su baja vigente; sin registro, devuelve esos campos nulos (esc. 2; FR-009).
- [ ] T008 [US1] Integration test con Testcontainers: dos reactivaciones simultáneas; con el bloqueo de la fila, una aplica la transición y la otra espera y responde `alreadyReactivated: true`, con un solo periodo en el historial (caso borde 2; regla 13 del plan base).
- [ ] T009 [P] [US1] Component test en `ReactivateRoomDialog` y `ReactivationResult`:
  - La confirmación muestra número, piso, tipo y estado, más la fecha, el motivo y el detalle de la baja, o "Sin registro" si no hay.
  - Cancelar no envía nada.
  - El resultado indica que la habitación ya puede recibir reservas y estancias, o que no fue necesario hacer cambios.
  - (esc. 2; FR-009, FR-010)

### Implementation for User Story 1

- [ ] T010 [US1] Implementar `MarkRoomAvailableService` con `@Transactional(propagation = Propagation.MANDATORY)` (FR-001, FR-004 a FR-006, FR-008, FR-011):
  - Validar el origen según `sourceFlow` y los datos del comando: `MARK_AVAILABLE` acepta `Inactive` con `actorId`, o `Reserved` con `reservationRef` igual a `reserved_by_reservation_ref`; `CONFIRM_CLEANING_END` acepta solo `InCleaning`.
  - Invocar `TransitionRoomStateUseCase.transition(roomId, Available, actorId, sourceFlow, reservationRef)`.
  - En la liberación, limpiar `reserved_by_reservation_ref`.
  - Ante un origen no válido, lanzar `InvalidRoomTransitionException` con el estado actual.
- [ ] T011 [US1] Implementar `ReactivateRoomService` (`@Transactional`): bloquear la fila de `room` con `SELECT … FOR UPDATE` (regla 13 del plan base), resolver la idempotencia por `decommissionId`, invocar el puerto con `MARK_AVAILABLE` y marcar la baja como revertida (FR-002, FR-010).
- [ ] T012 [US1] Implementar `RoomDecommissionQueryService` y agregar a `RoomController` `GET /api/rooms/{roomId}/decommission` y `POST /api/rooms/{roomId}/reactivation`, restringidos a `ADMINISTRATOR` (FR-002, FR-009).
- [ ] T013 [US1] Construir en el frontend `ReactivateRoomDialog.jsx` y `ReactivationResult.jsx`, abiertos desde la acción "Marcar como disponible" del inventario (FR-009, FR-010).

**Checkpoint**: El Administrador puede revertir bajas.

---

## Phase 4: User Story 2 - Reintegración operativa de la habitación tras fin de limpieza (Priority: P1)

**Goal**: Que *Confirmar fin de limpieza* y la ingesta de reservas usen el mismo puerto para volver a `Available`, con un solo periodo en el historial por transición.

**Independent Test**: Con una transacción abierta desde una prueba:
- Invocar el puerto con `CONFIRM_CLEANING_END` sobre una habitación `InCleaning` y verificar `Available` con un solo periodo con ese `source_flow`.
- Invocarlo con `MARK_AVAILABLE` y la `reservationRef` vinculada sobre una habitación `Reserved` y verificar `Available` con `reserved_by_reservation_ref` nulo.
- Invocarlo con `CONFIRM_CLEANING_END` sobre `Occupied` y verificar el rechazo sin cambios.

### Tests for User Story 2

- [ ] T014 [P] [US2] Unit test en `MarkRoomAvailableServiceTest`: `InCleaning` → `Available` con `CONFIRM_CLEANING_END`; el periodo del historial queda con ese `source_flow` y el miembro como actor, y no se crea un segundo periodo (esc. 4; FR-003, FR-011).
- [ ] T015 [P] [US2] Unit test: `Reserved` → `Available` con `MARK_AVAILABLE`, `actorId` nulo y la `reservationRef` vinculada; limpia `reserved_by_reservation_ref`. Con una `reservationRef` distinta, rechazo sin cambios (FR-004, FR-011).
- [ ] T016 [P] [US2] Unit test: rechazo con `CONFIRM_CLEANING_END` desde cualquier estado distinto de `InCleaning`, con el estado actual en la excepción (esc. 8; FR-006).
- [ ] T017 [P] [US2] Unit test: en los tres flujos, el periodo del historial registra el actor (nulo si es autónoma), el `sourceFlow` del invocador y la hora del servidor, y no se escribe en `room_audit_log` (FR-008; SC-004).
- [ ] T018 [US2] Integration test con Testcontainers: si la transacción del invocador falla después de invocar el puerto, la habitación queda en su estado de origen sin periodo ni evento nuevos (casos borde 3 y 4).

### Implementation for User Story 2

- [ ] T019 [US2] Verificar con los dueños de los planes invocadores que llamen al puerto con este contrato: `CompleteCleaningService` con `CONFIRM_CLEANING_END` (`plan-confirmar-fin-de-limpieza.md`, T015) y `DailyReservationIngestionService` con `MARK_AVAILABLE`, `actorId` nulo y la `reservationRef` liberada (`plan-consultar-reservas.md`, T012 y T013). T013 ya invoca este puerto; T012 todavía describe "transiciones automáticas" sin nombrarlo, aunque *Consultar reservas* FR-009 exige invocar este caso de uso.

La continuación a `Reserved` (esc. 5) o a `DisabledForRepairs` (esc. 6) la decide *Confirmar fin de limpieza* después de este puerto; sus pruebas están en `plan-confirmar-fin-de-limpieza.md` (T007, T008) y en `plan-marcar-habitacion-reservada.md`.

**Checkpoint**: Las tres vías hacia `Available` usan una sola implementación.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T020 Integration test: tras cada flujo, la habitación aparece en `GET /api/rooms?status=Available` sin retraso (esc. 7; FR-007; SC-003).
- [ ] T021 Medir en las pruebas de integración que la transición con su periodo y su evento tome menos de 1 segundo (SC-001).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8, T010 y T017), `SourceFlow` (T020), el rol `ADMINISTRATOR` (T015) y `ClockConfig`. Detalla el puerto `MarkRoomAvailableUseCase` de T019.
- **Planes de los que depende**:
  - `plan-dar-de-baja-habitacion.md`: `RoomDecommissionRepositoryPort` y las bajas que se revierten.
  - `plan-consultar-inventario-habitaciones.md`: acción "Marcar como disponible".
- **Planes que invocan el puerto** (FR-003, FR-004; ninguno debe reimplementar la transición):
  - `plan-confirmar-fin-de-limpieza.md`: `CompleteCleaningService` con `CONFIRM_CLEANING_END`.
  - `plan-consultar-reservas.md`: `DailyReservationIngestionService` con `MARK_AVAILABLE` al procesar un `REMOVED` o una reserva ausente de la lista de las 00:00, antes de apartar las habitaciones de la nueva lista (T012 y T013).
- **Orden**: Phase 2 → Phase 3 → Phase 4 → Phase 5. El puerto (T003, T010) debe estar listo antes de que los planes invocadores implementen sus servicios.
