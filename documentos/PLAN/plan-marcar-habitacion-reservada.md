# Implementation Plan: Marcar Habitación como Reservada

**Date**: 2026-10-09  
**Spec**: [spec-marcar-habitacion-reservada.md](../SPEC/spec-marcar-habitacion-reservada.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso interno **Marcar Habitación como Reservada**, que aparta una habitación de `Available` a `Reserved` para una reserva con `startDate` = hoy. No tiene interfaz ni endpoint y ningún actor humano lo invoca (FR-001, FR-009). Detalla el puerto transversal `MarkRoomReservedUseCase` del plan base (T019), que invocan, dentro de su propia transacción, dos flujos:

1. **Ingesta de reservas** (*Consultar reservas*, FR-009): por cada habitación de una reserva de hoy, (a) al ingerir la lista diaria de las 00:00 y (b) al recibir una actualización `ADDED` con `startDate` = hoy.
2. **Confirmar fin de limpieza** (literal c del Disparador): cuando la habitación acaba de pasar de `InCleaning` a `Available` sin daño reportado y su reserva del día sigue en la copia local (transición encadenada).

El servicio resuelve cuatro caminos sin lanzar errores hacia el invocador:

- **Éxito**: `Available` → `Reserved`, con `reserved_by_reservation_ref` asignado y el periodo en `room_state_history` (FR-004, FR-005, FR-011).
- **Idempotente**: ya está `Reserved` para la misma reserva; no hace nada (FR-006).
- **Conflicto**: cualquier otro estado, o `Reserved` para otra reserva; no modifica nada y registra `RESERVATION_STATE_CONFLICT` en la bitácora (FR-007, FR-008).
- **Habitación inexistente**: no modifica nada y deja una advertencia en el log del sistema (esc. 10).

La alerta operativa a Recepción (FR-012) no es un componente de este plan: el panel de recepción la calcula a partir del estado de la habitación (regla 11 del plan base). Este caso de uso la cumple al no apartar la habitación. Tampoco se envían confirmaciones ni rechazos a Módulo 2 (regla 3 de la máquina de estados).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `room`, `room_state_history` y `room_audit_log` del plan base (tablas en T007; puerto de la bitácora en T017); no crea tablas.
- **Performance Goals**: procesar cada invocación y apartar la habitación en menos de 200 ms (NFR-001; SC-001).
- **Constraints**:
  - La única transición es `Available` → `Reserved`, también en la transición encadenada (FR-004).
  - Sin polling: ningún trabajo programado de este plan consulta a Módulo 2; solo reacciona a las invocaciones de la ingesta y del fin de limpieza (FR-002).
  - Nunca sobrescribe un estado distinto de `Available` ni cambia la reserva vinculada de una habitación ya `Reserved` (FR-007; SC-003).
  - Los conflictos no son errores: el puerto devuelve un resultado y la ingesta continúa con la siguiente habitación. Los datos mal estructurados se registran como error controlado (NFR-002, NFR-003).
  - Eventos concurrentes: cada invocador bloquea la fila de `room` con `SELECT … FOR UPDATE` al inicio de su transacción (regla 13 del plan base). El segundo evento espera, lee el estado que dejó el primero y queda como idempotente o conflicto (caso borde 3).
  - La marca de tiempo de la transición la genera el servidor con `Clock` (FR-003).

---

## Contratos REST

No aplica: es un caso de uso interno sin endpoint. Su contrato es el puerto de entrada `MarkRoomReservedUseCase`:

```java
public interface MarkRoomReservedUseCase {
    // Debe ejecutarse dentro de una transacción ya abierta por el flujo invocador.
    MarkRoomReservedResult markReserved(MarkRoomReservedCommand command);
}

// trigger: DAILY_LIST (lista de las 00:00) | ADDED_UPDATE (actualización ADDED con startDate = hoy)
//          | CLEANING_END (transición encadenada desde Confirmar fin de limpieza)
public record MarkRoomReservedCommand(UUID roomId, String reservationRef, ReservationTrigger trigger) {}

// outcome: RESERVED | ALREADY_RESERVED | CONFLICT | ROOM_NOT_FOUND
public record MarkRoomReservedResult(MarkRoomReservedOutcome outcome, RoomStatus currentStatus, String linkedReservationRef) {}
```

`roomId` y `reservationRef` son obligatorios (FR-003); si faltan, el puerto lanza una excepción de validación que el invocador registra como error controlado (NFR-003).

### Registro de conflicto en `room_audit_log`

| Campo | Valor |
| --- | --- |
| `event_type` | `RESERVATION_STATE_CONFLICT` |
| `actor_id` | Nulo (autónomo) |
| `event_date_time` | Hora del servidor |
| `details` | `currentStatus`, `incomingReservationRef`, `linkedReservationRef` (si la habitación está `Reserved`) y `trigger` |

```json
{
  "currentStatus": "Reserved",
  "incomingReservationRef": "RES-202",
  "linkedReservationRef": "RES-101",
  "trigger": "DAILY_LIST"
}
```

### Qué pasa después de un conflicto

| Estado de la habitación | Qué pasa después (fuera de este plan) |
| --- | --- |
| `Occupied`, `PendingCleaning`, `InCleaning` | Se aparta cuando se confirme el fin de su limpieza (literal c), mediante `plan-confirmar-fin-de-limpieza.md` |
| `DisabledForRepairs`, `TechnicalBlock`, `Inactive` | El panel de recepción muestra la alerta "No disponible: [Estado]" en la fila de esa llegada (FR-012; regla 11 del plan base) |
| `Reserved` con otra reserva | Queda en la bitácora para que Recepción y Módulo 2 lo resuelvan (esc. 9) |

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-marcar-habitacion-reservada.md
│   ├── spec-consultar-reservas.md
│   ├── spec-confirmar-fin-de-limpieza.md
│   ├── spec-consultar-panel-recepcion.md
│   └── spec-registrar-check-in.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-marcar-habitacion-reservada.md
    ├── plan-consultar-reservas.md
    ├── plan-confirmar-fin-de-limpieza.md
    └── plan-consultar-panel-recepcion.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── ReservationTrigger.java            # Enum: DAILY_LIST, ADDED_UPDATE, CLEANING_END
│   │   │   └── MarkRoomReservedOutcome.java       # Enum: RESERVED, ALREADY_RESERVED, CONFLICT, ROOM_NOT_FOUND
│   │   └── ports/in/
│   │       └── MarkRoomReservedUseCase.java       # Puerto transversal (plan base, T019)
│   └── application/
│       ├── service/
│       │   └── MarkRoomReservedService.java
│       └── dto/
│           ├── MarkRoomReservedCommand.java
│           └── MarkRoomReservedResult.java
└── test/java/com/hospitua/habitaciones/
    ├── application/
    │   └── MarkRoomReservedServiceTest.java
    └── infrastructure/persistence/
        └── MarkRoomReservedIntegrationTest.java
```

**Structure Decision**: Al no tener interfaz, el caso de uso vive solo en las capas de dominio y aplicación. La transición y su periodo en el historial los hace `TransitionRoomStateUseCase.transition(roomId, Reserved, null, MARK_RESERVED, reservationRef)` (regla 8 del plan base), con `actorId` nulo porque es autónoma. En la transición encadenada, el periodo `InCleaning` → `Available` lo registra *Confirmar fin de limpieza* y el `Available` → `Reserved` este plan, con estado previo `Available` (esc. 3).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que la matriz de transiciones de `RoomStateTransitionService` (plan base, T010) permita `Available` → `Reserved` y ninguna otra transición hacia `Reserved`, y que `SourceFlow` (T020) incluya `MARK_RESERVED`.
- [ ] T002 [P] Verificar `room_audit_log` y su puerto `RoomAuditLogPort` (plan base, T007 y T017), con el tipo de evento `RESERVATION_STATE_CONFLICT`.
- [ ] T003 [P] Crear `ReservationTrigger`, `MarkRoomReservedOutcome`, `MarkRoomReservedCommand`, `MarkRoomReservedResult` y el puerto `MarkRoomReservedUseCase` con el contrato de este plan.

---

## Phase 3: User Story 1 - Marcado reactivo de habitación como Reserved ante evento de Módulo 2 (Priority: P1)

**Goal**: Que la ingesta de reservas y el fin de limpieza aparten habitaciones `Available` para la reserva de hoy, con su periodo en el historial.

**Independent Test**: Con una transacción abierta desde una prueba:
- Invocar el puerto con `DAILY_LIST` sobre una habitación `Available` y verificar `Reserved`, `reserved_by_reservation_ref` y el periodo `Available` → `Reserved` con `MARK_RESERVED` y `actor_id` nulo.
- Repetir la transición encadenada: pasar la habitación de `InCleaning` a `Available` con `MarkRoomAvailableUseCase` e invocar este puerto con `CLEANING_END` en la misma transacción.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `MarkRoomReservedServiceTest`: `Available` → `Reserved` con `reserved_by_reservation_ref` = `reservationRef`, resultado `RESERVED` y periodo con estado previo `Available`, la `reservationRef`, `MARK_RESERVED` y hora de `Clock` (esc. 1; FR-004, FR-005, FR-011).
- [ ] T005 [P] [US1] Unit test: falta `roomId` o `reservationRef` y se lanza la excepción de validación sin cambios (FR-003; NFR-003).
- [ ] T006 [US1] Integration test con Testcontainers: transición encadenada; en una transacción la habitación pasa de `InCleaning` a `Available` (vía `MarkRoomAvailableUseCase` con `CONFIRM_CLEANING_END`) y a `Reserved` (vía este puerto con `CLEANING_END`), con dos periodos en `room_state_history` y el segundo con estado previo `Available` (esc. 3).
- [ ] T007 [US1] Integration test: una habitación `Reserved` es aceptada por *Registrar check-in* como origen válido hacia `Occupied` (esc. 2; FR-010).

### Implementation for User Story 1

- [ ] T008 [US1] Implementar `MarkRoomReservedService` con `@Transactional(propagation = Propagation.MANDATORY)` (FR-003 a FR-011):
  - Validar `roomId` y `reservationRef`.
  - Buscar la habitación; si no existe, registrar una advertencia en el log del sistema y devolver `ROOM_NOT_FOUND`.
  - Si está `Available`: invocar `TransitionRoomStateUseCase.transition(roomId, Reserved, null, MARK_RESERVED, reservationRef)`, asignar `reserved_by_reservation_ref` y devolver `RESERVED`.
  - Si está `Reserved` con la misma `reservationRef`: devolver `ALREADY_RESERVED` sin escribir nada.
  - En cualquier otro caso: registrar `RESERVATION_STATE_CONFLICT` en `room_audit_log` y devolver `CONFLICT` con el estado actual y la reserva vinculada.
  - No bloquea la fila: el invocador ya la bloqueó con `SELECT … FOR UPDATE` (regla 13 del plan base).

**Checkpoint**: Las habitaciones disponibles se apartan para las llegadas del día.

---

## Phase 4: User Story 2 - Procesamiento idempotente y gestión de conflictos de estado (Priority: P1)

**Goal**: Que los eventos duplicados no generen escrituras y que los conflictos queden registrados sin tocar el estado físico.

**Independent Test**: Invocar dos veces el puerto con la misma reserva y verificar un solo periodo. Invocarlo sobre habitaciones en cada uno de los otros 6 estados y sobre una `Reserved` para otra reserva; verificar que el estado no cambie y que cada caso deje su `RESERVATION_STATE_CONFLICT`.

### Tests for User Story 2

- [ ] T009 [P] [US2] Unit test: misma habitación y misma reserva dos veces; la segunda devuelve `ALREADY_RESERVED` sin periodo ni evento nuevos (esc. 4; FR-006; SC-002).
- [ ] T010 [P] [US2] Unit test: en `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` e `Inactive` devuelve `CONFLICT`, no cambia el estado y registra el conflicto con el estado actual y la `reservationRef` entrante (esc. 5 a 7; FR-007, FR-008; SC-003, SC-004).
- [ ] T011 [P] [US2] Unit test: `Reserved` para "RES-101" e invocación con "RES-202" devuelve `CONFLICT`, conserva "RES-101" y registra ambas referencias (esc. 9; FR-007, FR-008; SC-004).
- [ ] T012 [P] [US2] Unit test: `roomId` inexistente devuelve `ROOM_NOT_FOUND`, no altera ninguna entidad y deja la advertencia en el log (esc. 10).
- [ ] T013 [US2] Integration test con Testcontainers: dos invocaciones concurrentes para la misma habitación `Available` con reservas distintas, cada una con su bloqueo de fila; solo una la aparta y la otra espera y queda como conflicto (caso borde 3; regla 13 del plan base).
- [ ] T014 [US2] Integration test: un fallo de persistencia durante la transición revierte la transacción y la habitación sigue `Available` (caso borde "Interrupción de conectividad").

### Implementation for User Story 2

- [ ] T015 [US2] Verificar con el dueño de `plan-consultar-reservas.md` (T012) que `DailyReservationIngestionService` invoque este puerto en lugar de transicionar las habitaciones directamente (hoy T012 describe "transiciones automáticas" sin mencionar el puerto, aunque *Consultar reservas* FR-009 exige invocar este caso de uso), y que:
  - invoque el puerto con `DAILY_LIST` y `ADDED_UPDATE` solo para reservas con `startDate` = hoy;
  - en la lista de las 00:00 ejecute primero las liberaciones (`MarkRoomAvailableUseCase`) y después los apartados;
  - trate un `UPDATED` que cambia la habitación como liberación de la anterior y apartado de la nueva;
  - continúe con la siguiente habitación ante `CONFLICT` o `ROOM_NOT_FOUND`.
- [ ] T016 [US2] Verificar con el dueño de `plan-confirmar-fin-de-limpieza.md` (T015) que invoque el puerto con `CLEANING_END` solo si no se reportó daño y la copia local tiene una reserva de hoy para la habitación (FR-004).

**Checkpoint**: El caso de uso es idempotente y no sobrescribe estados físicos.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T017 Integration test: una llegada de hoy cuya habitación quedó en `DisabledForRepairs`, `TechnicalBlock` o `Inactive` aparece en `GET /api/reception/arrivals` con ese estado, que es la alerta a Recepción; y no se publica ningún mensaje hacia Módulo 2 (esc. 7 y 8; FR-012; SC-006).
- [ ] T018 Revisar el código: ningún controlador, ni *Registrar habitación*, *Editar habitación* o la reactivación de *Marcar habitación como disponible*, permite asignar `Reserved`; solo la ingesta y el fin de limpieza dependen del puerto (FR-001, FR-009; SC-005).
- [ ] T019 Revisar que ningún trabajo programado consulte a Módulo 2 para apartar habitaciones (FR-002).
- [ ] T020 Medir en la prueba de integración T006 que cada invocación tome menos de 200 ms (NFR-001; SC-001).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` (regla 8, T010 y T017), `SourceFlow` con `MARK_RESERVED` (T020) y `ClockConfig`. Detalla el puerto `MarkRoomReservedUseCase` de T019.
- **Planes de los que depende**:
  - `plan-marcar-habitacion-como-disponible.md`: `MarkRoomAvailableUseCase` para el primer paso de la transición encadenada.
- **Planes que invocan el puerto**:
  - `plan-consultar-reservas.md`: `DailyReservationIngestionService` con `DAILY_LIST` y `ADDED_UPDATE`.
  - `plan-confirmar-fin-de-limpieza.md`: `CompleteCleaningService` con `CLEANING_END`.
- **Planes relacionados**:
  - `plan-consultar-panel-recepcion.md`: muestra la alerta operativa (regla 11 del plan base).
  - `plan-registrar-check-in.md`: acepta `Reserved` como origen (FR-010). Como `reserved_by_reservation_ref` solo tiene sentido en `Reserved`, el check-in debería limpiarlo al pasar a `Occupied`; hoy ese plan valida que coincida con la reserva, pero no dice que lo limpie.
- **Orden**: Phase 2 → Phase 3 → Phase 4 → Phase 5. El puerto (T003, T008) debe estar listo antes de que los planes invocadores implementen sus servicios.
