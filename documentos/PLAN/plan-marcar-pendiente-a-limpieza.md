# Implementation Plan: Marcar Pendiente a Limpieza

**Date**: 2026-10-08  
**Spec**: [spec-marcar-pendiente-a-limpieza.md](../SPEC/spec-marcar-pendiente-a-limpieza.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso interno **Marcar Pendiente a Limpieza**, que centraliza toda transición de una habitación hacia `PendingCleaning` (FR-005). No tiene interfaz ni endpoint propios: es un puerto de entrada (`MarkPendingCleaningUseCase`) que invocan, dentro de su propia transacción, tres flujos:

1. **Registrar check-out**: `Occupied` → `PendingCleaning`.
2. **Confirmar Fin de Reparación de Habitación**: `DisabledForRepairs` / `TechnicalBlock` → `PendingCleaning`.
3. **Liberar tarea de limpieza** (definida en *Confirmar Fin de Limpieza de Habitación*): `InCleaning` → `PendingCleaning`.

El servicio valida el estado de origen según el flujo invocador (FR-001), aplica la transición con `TransitionRoomStateUseCase.transition(...)` (regla 8 del plan base), que valida la matriz con `Room.transitionTo()` y registra el periodo en `room_state_history` con fecha y hora del servidor (FR-004, FR-006) y, si rechaza la invocación, devuelve el error al flujo invocador, que es quien lo muestra al usuario (FR-007).

---

## Technical Context

- **Storage**: PostgreSQL 16+ (tablas `room` y `room_state_history` del plan base; este caso de uso no crea tablas).
- **Performance Goals**: transición, actualización de estado y registro en el historial en menos de 300 ms (SC-002).
- **Constraints**:
  - Se ejecuta solo dentro de la transacción del flujo invocador; si falla, el invocador hace rollback completo (casos límite 2 y 4).
  - `TransitionDateTime` lo genera exclusivamente el servidor mediante `Clock` y no se acepta del cliente ni del invocador (FR-006).
  - `InCleaning` solo es un origen válido cuando el invocador es la liberación de una tarea de limpieza (FR-001).
  - No hay caché de estados: el panel de limpieza y las consultas de disponibilidad leen el estado persistido, así que el cambio queda visible apenas se confirma la transacción (FR-003, caso límite 3, SC-004).

---

## Contratos REST

No aplica: es un caso de uso interno sin endpoint. Su contrato es el puerto de entrada:

```java
public interface MarkPendingCleaningUseCase {
    // Debe ejecutarse dentro de una transacción ya abierta por el flujo invocador.
    void markPendingCleaning(MarkPendingCleaningCommand command);
}

// sourceFlow (enum SourceFlow del plan base): CHECK_OUT | CONFIRM_REPAIR_END | RELEASE_CLEANING_TASK
public record MarkPendingCleaningCommand(UUID roomId, SourceFlow sourceFlow, String actorId) {}
```

Ante un rechazo lanza `InvalidRoomTransitionException` (plan base, traducida a HTTP 409 por `GlobalExceptionHandler` en el endpoint del invocador) con el estado actual de la habitación.

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-marcar-pendiente-a-limpieza.md
│   ├── spec-registrar-check-out.md
│   ├── spec-confirmar-reparacion-finalizada.md
│   └── spec-confirmar-fin-de-limpieza.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-marcar-pendiente-a-limpieza.md
    ├── plan-registrar-check-out.md
    ├── plan-confirmar-reparacion-finalizada.md
    └── plan-confirmar-fin-de-limpieza.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   └── ports/
│   │       └── in/
│   │           └── MarkPendingCleaningUseCase.java
│   └── application/
│       ├── service/
│       │   └── MarkPendingCleaningService.java
│       └── dto/
│           └── MarkPendingCleaningCommand.java
└── test/java/com/hospitua/habitaciones/
    ├── application/
    │   └── MarkPendingCleaningServiceTest.java
    └── infrastructure/persistence/
        └── MarkPendingCleaningIntegrationTest.java
```

**Structure Decision**: Al no tener interfaz, el caso de uso vive solo en las capas de dominio y aplicación. Reutiliza del plan base `TransitionRoomStateUseCase` (transición y registro en el historial, regla 8) y el enum `SourceFlow`. Los flujos invocadores lo reciben por inyección del puerto `MarkPendingCleaningUseCase`.

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que la matriz de transiciones de `RoomStateTransitionService` (plan base, T010) incluya `Occupied`, `DisabledForRepairs`, `TechnicalBlock` e `InCleaning` → `PendingCleaning`, según `maquina-estados-habitacion.md`.
- [ ] T002 Verificar que `TransitionRoomStateUseCase` registre el periodo en `room_state_history` (plan base, regla 8 y T017) y que el enum `SourceFlow` (plan base, T020) incluya `CHECK_OUT`, `CONFIRM_REPAIR_END` y `RELEASE_CLEANING_TASK`.
- [ ] T003 [P] Crear el comando `MarkPendingCleaningCommand` y el puerto `MarkPendingCleaningUseCase`.

---

## Phase 3: User Story 1 - Transición automática a PendingCleaning tras check-out o fin de reparación (Priority: P1)

**Goal**: Que los tres flujos invocadores muevan la habitación a `PendingCleaning` con una única implementación, registrando el periodo en el historial y devolviendo el error al invocador cuando el estado no es válido.

**Independent Test**: Con una transacción abierta desde una prueba, invocar el servicio sobre habitaciones en `Occupied`, `DisabledForRepairs`, `TechnicalBlock` e `InCleaning` (esta última con `sourceFlow = RELEASE_CLEANING_TASK`) y verificar el estado final y el registro en `room_state_history`. Invocarlo sobre `Available` y verificar el rechazo sin cambios.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `MarkPendingCleaningServiceTest`: transición `Occupied` → `PendingCleaning` con `sourceFlow = CHECK_OUT` (HU-1 esc. 1; SC-001).
- [ ] T005 [P] [US1] Unit test: transición desde `DisabledForRepairs` y desde `TechnicalBlock` con `sourceFlow = CONFIRM_REPAIR_END` (HU-1 esc. 2; SC-001).
- [ ] T006 [P] [US1] Unit test: transición `InCleaning` → `PendingCleaning` solo con `sourceFlow = RELEASE_CLEANING_TASK`; con otro `sourceFlow`, rechazo (HU-1 esc. 4, FR-001; SC-001).
- [ ] T007 [P] [US1] Unit test: rechazo desde `Available` y desde cualquier otro estado no permitido, sin modificar la habitación y con el estado actual en la excepción (HU-1 esc. 3, FR-007; SC-003).
- [ ] T008 [P] [US1] Unit test con `Clock` fijo: `TransitionDateTime` es la hora del servidor y el comando no admite fecha (FR-006).
- [ ] T009 [P] [US1] Unit test: el registro en el historial cierra el periodo abierto y abre `PendingCleaning` con `previous_status`, `actor_id` y `source_flow` del comando (FR-004; SC-005).
- [ ] T010 [US1] Integration test con Testcontainers: si la transacción del invocador falla después de invocar el servicio, el rollback deja la habitación en su estado de origen y sin registro nuevo en `room_state_history` (casos límite 2 y 4).
- [ ] T011 [US1] Integration test: dos invocaciones concurrentes sobre la misma habitación; la segunda falla por bloqueo optimista (`room.version`) y el error llega al invocador (caso límite 1).

### Implementation for User Story 1

- [ ] T012 [US1] Implementar `MarkPendingCleaningService` con `@Transactional(propagation = Propagation.MANDATORY)` (FR-002, casos límite 2 y 4):
  - Validar el estado de origen según `sourceFlow`: `CHECK_OUT` → `Occupied`; `CONFIRM_REPAIR_END` → `DisabledForRepairs` o `TechnicalBlock`; `RELEASE_CLEANING_TASK` → `InCleaning` (FR-001).
  - Obtener la hora con `Clock` (FR-006).
  - Invocar `TransitionRoomStateUseCase.transition(roomId, PendingCleaning, actorId, sourceFlow, null)`, que aplica la transición y registra el periodo en `room_state_history` (FR-002, FR-004).
  - Ante un rechazo, lanzar `InvalidRoomTransitionException` con el estado actual (FR-007).
- [ ] T013 [US1] Verificar que `room_state_history` no tenga operaciones de modificación ni de eliminación, salvo el cierre del periodo que hace la siguiente transición (FR-008).

**Checkpoint**: El servicio funciona de forma aislada y queda listo para que lo invoquen check-out, confirmar reparación y liberar tarea de limpieza.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T014 Integration test: una habitación que pasa a `PendingCleaning` deja de aparecer en `GET /api/rooms?status=Available` y aparece en `GET /api/rooms?status=PendingCleaning` (FR-003, SC-004).
- [ ] T015 Medir en la prueba de integración T010 que la transición con su registro en el historial tome menos de 300 ms (SC-002).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` con su registro en el historial (regla 8, T010 y T017), el enum `SourceFlow` (T020) y `ClockConfig`.
- **Planes que lo invocan** (FR-005; ninguno debe reimplementar la transición):
  - `plan-registrar-check-out.md`: `CheckOutExecutionService` invoca `MarkPendingCleaningUseCase` con `sourceFlow = CHECK_OUT`.
  - `plan-confirmar-reparacion-finalizada.md`: invoca con `sourceFlow = CONFIRM_REPAIR_END`.
  - `plan-confirmar-fin-de-limpieza.md`: invoca con `sourceFlow = RELEASE_CLEANING_TASK` desde la liberación de la tarea.
- **Orden**: Este plan es el primero de limpieza y mantenimiento; los invocadores lo usan una vez completada la Phase 3.
