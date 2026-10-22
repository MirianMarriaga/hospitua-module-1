# Implementation Plan: Marcar Pendiente a Limpieza

**Date**: 2026-10-08  
**Spec**: [spec-marcar-pendiente-a-limpieza.md](../SPEC/spec-marcar-pendiente-a-limpieza.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso interno **Marcar Pendiente a Limpieza**, que centraliza toda transición de una habitación hacia `PendingCleaning` (FR-005). No tiene interfaz ni endpoint propios: es un puerto de entrada (`MarkPendingCleaningUseCase`) que invocan, dentro de su propia transacción, tres flujos:

1. **Registrar check-out**: `Occupied` → `PendingCleaning`.
2. **Confirmar Fin de Reparación de Habitación**: `DisabledForRepairs` / `TechnicalBlock` → `PendingCleaning`.
3. **Confirmar fin de limpieza (liberar tarea)**, la liberación de una tarea de limpieza: `InCleaning` → `PendingCleaning`.

El servicio valida su restricción propia, `InCleaning` solo con `RELEASE_CLEANING_TASK` (FR-001), aplica la transición con `TransitionRoomStateUseCase.transition(...)` (regla 8 del plan base), que valida la matriz con `Room.transitionTo()` y registra el periodo en `room_state_history` con fecha y hora del servidor (FR-004, FR-006) y, si rechaza la invocación, devuelve el error al flujo invocador, que es quien lo muestra al usuario (FR-007).

---

## Technical Context

- **Storage**: PostgreSQL 16+ (tablas `room` y `room_state_history` del plan base; este caso de uso no crea tablas).
- **Performance Goals**: no define metas propias; su tiempo está dentro del de su flujo invocador (SC-002 eliminado).
- **Constraints**:
  - Se ejecuta solo dentro de la transacción del flujo invocador; si falla, el invocador hace rollback completo (caso límite 2).
  - La marca de la transición la genera `TransitionRoomStateUseCase` con `Clock` (regla 8 del plan base); no se acepta del cliente ni del invocador y el puerto la devuelve en su resultado (FR-006).
  - `InCleaning` solo es un origen válido cuando el invocador es la liberación de una tarea de limpieza (FR-001); los demás orígenes los valida la matriz de la máquina de estados (`Room.transitionTo()`).
  - Ante invocaciones concurrentes rige el control optimista de `room.version` (regla 13 del plan base, caso límite 1); con `RELEASE_CLEANING_TASK` el invocador ya bloqueó la fila.
  - No hay caché de estados: el panel de limpieza y las consultas de disponibilidad leen el estado persistido, así que el cambio aparece en la siguiente actualización del panel (FR-003).

---

## Contratos REST

No aplica: es un caso de uso interno sin endpoint. Su contrato es el puerto de entrada:

```java
public interface MarkPendingCleaningUseCase {
    // Debe ejecutarse dentro de una transacción ya abierta por el flujo invocador.
    MarkPendingCleaningResult markPendingCleaning(MarkPendingCleaningCommand command);
}

// sourceFlow (enum SourceFlow del plan base): CHECK_OUT | CONFIRM_REPAIR_END | RELEASE_CLEANING_TASK
public record MarkPendingCleaningCommand(UUID roomId, SourceFlow sourceFlow, String actorId) {}

// transitionDateTime: marca del periodo PendingCleaning abierto en room_state_history; los invocadores
// que cierran una tarea en el mismo flujo la usan como EndDateTime
public record MarkPendingCleaningResult(UUID roomId, RoomStatus previousStatus, OffsetDateTime transitionDateTime) {}
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
│   └── application/
│       ├── ports/
│       │   └── in/
│       │       └── MarkPendingCleaningUseCase.java
│       ├── service/
│       │   └── MarkPendingCleaningService.java
│       └── dto/
│           ├── MarkPendingCleaningCommand.java
│           └── MarkPendingCleaningResult.java
└── test/java/com/hospitua/habitaciones/
    ├── application/
    │   └── MarkPendingCleaningServiceTest.java        # Prueba parametrizada origen × sourceFlow (T004)
    └── infrastructure/persistence/
        └── MarkPendingCleaningIntegrationTest.java    # Rollback de la transacción del invocador (T010)
```

**Structure Decision**: Al no tener interfaz, el caso de uso vive solo en las capas de dominio y aplicación. Reutiliza del plan base `TransitionRoomStateUseCase` (transición y registro en el historial, regla 8) y el enum `SourceFlow`. Los flujos invocadores lo reciben por inyección del puerto `MarkPendingCleaningUseCase`.

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que la matriz de transiciones de `RoomStateTransitionService` (plan base, T010) incluya `Occupied`, `DisabledForRepairs`, `TechnicalBlock` e `InCleaning` → `PendingCleaning`, según `maquina-estados-habitacion.md`.
- [ ] T002 Verificar que `TransitionRoomStateUseCase` registre el periodo en `room_state_history` (plan base, regla 8 y T017) y que el enum `SourceFlow` (plan base, T020) incluya `CHECK_OUT`, `CONFIRM_REPAIR_END` y `RELEASE_CLEANING_TASK`.
- [ ] T003 [P] Crear el comando `MarkPendingCleaningCommand`, el resultado `MarkPendingCleaningResult` y el puerto `MarkPendingCleaningUseCase`.

---

## Phase 3: User Story 1 - Transición automática a PendingCleaning tras check-out, fin de reparación o liberación de una tarea de limpieza (Priority: P1)

**Goal**: Que los tres flujos invocadores muevan la habitación a `PendingCleaning` con una única implementación, registrando el periodo en el historial y devolviendo el error al invocador cuando el estado no es válido.

**Independent Test**: Con la prueba parametrizada, invocar el servicio con cada combinación de estado de origen y `sourceFlow` y verificar la transición a `PendingCleaning` o el rechazo sin cambios según la matriz de la máquina de estados y FR-001. Con una transacción abierta desde una prueba de integración, forzar un fallo después de invocarlo y verificar el rollback.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test parametrizado en `MarkPendingCleaningServiceTest` (estado de origen × `sourceFlow`): `Occupied`, `DisabledForRepairs` y `TechnicalBlock` pasan a `PendingCleaning`; `InCleaning` pasa solo con `RELEASE_CLEANING_TASK` y con otro `sourceFlow` se rechaza; `Available` y los demás estados no permitidos se rechazan sin modificar la habitación y con el estado actual en la excepción; en las transiciones válidas, el resultado devuelve la marca que devuelve `TransitionRoomStateUseCase` (HU-1 esc. 1 a 4; FR-001, FR-006, FR-007; SC-001).
- [ ] T005 [fusionada en T004]
- [ ] T006 [fusionada en T004]
- [ ] T007 [fusionada en T004]
- [ ] T008 [eliminada: la marca de la transición la genera y la prueba `TransitionRoomStateUseCase` (plan base, regla 8); el resultado lo cubre T004]
- [ ] T009 [eliminada: el registro en el historial lo prueba el plan base (regla 8, T017)]
- [ ] T010 [US1] Integration test con Testcontainers: si la transacción del invocador falla después de invocar el servicio, el rollback deja la habitación en su estado de origen y sin registro nuevo en `room_state_history` (caso límite 2).
- [ ] T011 [US1] Integration test: dos invocaciones concurrentes sobre la misma habitación desde el check-out (`CHECK_OUT`); la segunda falla por bloqueo optimista (`room.version`) y el error llega al invocador (caso límite 1). Con `RELEASE_CLEANING_TASK` no aplica: el invocador ya bloqueó la fila (regla 13 del plan base).

### Implementation for User Story 1

- [ ] T012 [US1] Implementar `MarkPendingCleaningService` con `@Transactional(propagation = Propagation.MANDATORY)` (FR-002, caso límite 2):
  - Si la habitación está en `InCleaning` y `sourceFlow` no es `RELEASE_CLEANING_TASK`, rechazar con `InvalidRoomTransitionException` (FR-001); los demás orígenes los valida la matriz de la máquina de estados en la transición.
  - Invocar `TransitionRoomStateUseCase.transition(roomId, PendingCleaning, actorId, sourceFlow, null)`, que aplica la transición y registra el periodo en `room_state_history` (FR-002, FR-004), y devolver `MarkPendingCleaningResult` con la marca de tiempo que devuelve la transición (FR-006).
  - Ante un rechazo, lanzar `InvalidRoomTransitionException` con el estado actual (FR-007).
- [ ] T013 [eliminada: la inmutabilidad de `room_state_history` la declara la regla 8 del plan base (FR-008)]

**Checkpoint**: El servicio funciona de forma aislada y queda listo para que lo invoquen check-out, confirmar reparación y liberar tarea de limpieza.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T014 [eliminada: la lectura del estado persistido la prueba plan-consultar-panel-limpieza.md (T014); SC-004 eliminado]
- [ ] T015 [eliminada: SC-002 eliminado]

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `TransitionRoomStateUseCase` con su registro en el historial (regla 8, T010 y T017), el enum `SourceFlow` (T020) y `ClockConfig`.
- **Planes que lo invocan** (FR-005; ninguno debe reimplementar la transición):
  - `plan-registrar-check-out.md`: `CheckOutExecutionService` invoca `MarkPendingCleaningUseCase` con `sourceFlow = CHECK_OUT`.
  - `plan-confirmar-reparacion-finalizada.md`: invoca con `sourceFlow = CONFIRM_REPAIR_END`.
  - `plan-confirmar-fin-de-limpieza.md`: invoca con `sourceFlow = RELEASE_CLEANING_TASK` desde la liberación de la tarea.
- **Orden**: Es uno de los puertos transversales de T019 del plan base y va antes de check-out y de los planes de limpieza y mantenimiento ("Orden de implementación de los planes" del plan base); los invocadores lo usan una vez completada la Phase 3.
