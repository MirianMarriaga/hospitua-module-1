# Reporte de Pendientes y Decisiones Técnicas

## Lista de `TODO(pendiente)` en el código
- No se dejaron `TODO(pendiente)` activos en el código. El pendiente previo en `RoomStateTransitionService.java` sobre la excepción ante la falta de `reservationRef` al transicionar hacia `Reserved` fue cerrado utilizando `IllegalArgumentException` conforme a la especificación técnica y contrato de `plan-marcar-habitacion-reservada.md` (FR-003, NFR-003).

---

## Decisiones Técnicas Tomadas en el Lote de Puertos Transversales (T018)

1. **Pipeline de Integración Continua (Tarea 1 - T025)**:
   - Configurado `.github/workflows/backend-ci.yml` ejecutando sobre pull requests hacia `develop` y `main`.
   - Utiliza JDK 21 (Temurin), caché de Maven y ejecuta `./mvnw verify -DexcludedGroups=performance` desde el subdirectorio `backend/`.

2. **Puerto Transversal `MarkRoomAvailableUseCase` (Tarea 2 - `plan-marcar-habitacion-como-disponible.md`)**:
   - Implementado en `MarkRoomAvailableService` con `@Transactional(propagation = Propagation.MANDATORY)` asegurando que se invoque en el contexto transaccional del caso de uso padre (o con bloqueo de fila previo).
   - Valida estados de origen compatibles según la máquina de estados y las reglas del plan:
     - Desde `Inactive` requiere `actorId` obligatorio (Administrador).
     - Desde `Reserved` requiere que `reservationRef` coincida con la que apartó la habitación.
     - Desde `InCleaning` requiere `sourceFlow = CONFIRM_CLEANING_END`.
   - Delega la transición a `TransitionRoomStateUseCase`, que actualiza la entidad, limpia automáticamente `reservedByReservationRef` al salir de `Reserved` y registra el periodo en `room_state_history`.
   - Alcance acotado estrictamente a Phases 2 y 3 (puerto transversal y servicio); las Phases 4 y 5 (endpoints REST de reactivación, consulta de bajas y UI) quedan para el lote correspondiente tras la implementación de bajas.

3. **Puerto Transversal `MarkRoomReservedUseCase` (Tarea 3 - `plan-marcar-habitacion-reservada.md`)**:
   - DTOs y modelos creados: `ReservationTrigger` (`DAILY_LIST`, `ADDED_UPDATE`, `CLEANING_END`), `MarkRoomReservedOutcome` (`RESERVED`, `ALREADY_RESERVED`, `CONFLICT`, `ROOM_NOT_FOUND`), `MarkRoomReservedCommand`, `MarkRoomReservedResult`.
   - Implementado en `MarkRoomReservedService` con `@Transactional(propagation = Propagation.MANDATORY)`.
   - Comportamiento implementado:
     - Valida `roomId` y `reservationRef` (lanzando `IllegalArgumentException` si son nulos o blancos).
     - Si la habitación no existe, registra advertencia y retorna `ROOM_NOT_FOUND`.
     - Si la habitación está en `Available`, transiciona a `Reserved` vía `TransitionRoomStateUseCase` con `MARK_RESERVED` y `actorId = null` (autónomo), retornando `RESERVED`.
     - Si la habitación ya está en `Reserved` para la misma reserva, es idempotente y retorna `ALREADY_RESERVED` sin escrituras redundantes.
     - En cualquier otro caso (conflicto con otros estados operativos o con otra reserva previa), registra el conflicto en `room_audit_log` con `RESERVATION_STATE_CONFLICT` y detalle estructurado (`currentStatus`, `incomingReservationRef`, `linkedReservationRef`, `trigger`), retornando `CONFLICT`.

4. **Puerto Transversal `MarkPendingCleaningUseCase` (Tarea 4 - `plan-marcar-pendiente-a-limpieza.md`)**:
   - Modelado con `MarkPendingCleaningCommand` (`roomId`, `sourceFlow`, `actorId`) y `MarkPendingCleaningResult` (`roomId`, `previousStatus`, `transitionDateTime`).
   - Implementado en `MarkPendingCleaningService` con `@Transactional(propagation = Propagation.MANDATORY)`.
   - Valida la regla FR-001: el origen `InCleaning` solo se acepta si el flujo invocador es `RELEASE_CLEANING_TASK`; cualquier otro flujo con `InCleaning` es rechazado con `InvalidRoomTransitionException`.
   - Los demás estados de origen permitidos (`Occupied`, `DisabledForRepairs`, `TechnicalBlock`) son transicionados a `PendingCleaning` vía `TransitionRoomStateUseCase` registrando el periodo correspondiente en `room_state_history`. Orígenes inválidos son rechazados con `InvalidRoomTransitionException(currentStatus)`.

5. **Aislamiento de Entornos de Pruebas de Integración Concurrente**:
   - En las pruebas de integración con Testcontainers, las entidades `Room` utilizan números de habitación con prefijo (`AVA-*`, `RES-*`, `CLN-*`) para garantizar que la restricción de unicidad `uk_room_room_number` no interfiera con otras clases de prueba de la suite como `RoomRepositoryAdapterTest`.

---

## Archivos Creados y Modificados en este Lote

### Tarea 1: Pipeline CI (T025)
- `.github/workflows/backend-ci.yml`: Workflow para validación continua en PRs hacia `develop` y `main`.

### Tarea 2: Puerto Marcar Disponible
- `backend/src/main/java/com/hospitua/habitaciones/application/dto/MarkRoomAvailableCommand.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/dto/MarkRoomAvailableResult.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/in/MarkRoomAvailableUseCase.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/service/MarkRoomAvailableService.java`
- `backend/src/test/java/com/hospitua/habitaciones/application/service/MarkRoomAvailableServiceTest.java`
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/MarkRoomAvailableIntegrationTest.java`

### Tarea 3: Puerto Marcar Reservada
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/ReservationTrigger.java`
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/MarkRoomReservedOutcome.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/dto/MarkRoomReservedCommand.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/dto/MarkRoomReservedResult.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/in/MarkRoomReservedUseCase.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/service/MarkRoomReservedService.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/service/RoomStateTransitionService.java` (resolución de comentario TODO confirmando `IllegalArgumentException`).
- `backend/src/test/java/com/hospitua/habitaciones/application/service/MarkRoomReservedServiceTest.java`
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/MarkRoomReservedIntegrationTest.java`

### Tarea 4: Puerto Marcar Pendiente a Limpieza
- `backend/src/main/java/com/hospitua/habitaciones/application/dto/MarkPendingCleaningCommand.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/dto/MarkPendingCleaningResult.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/in/MarkPendingCleaningUseCase.java`
- `backend/src/main/java/com/hospitua/habitaciones/application/service/MarkPendingCleaningService.java`
- `backend/src/test/java/com/hospitua/habitaciones/application/service/MarkPendingCleaningServiceTest.java`
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/MarkPendingCleaningIntegrationTest.java`

### Planes Actualizados
- `documentos/PLAN/base/plan.md`: Marcadas `[x]` las tareas T018 y T025.
- `documentos/PLAN/plan-marcar-habitacion-como-disponible.md`: Marcadas `[x]` las tareas T001 a T009 (Phases 2 y 3).
- `documentos/PLAN/plan-marcar-habitacion-reservada.md`: Marcadas `[x]` las tareas T001 a T013.
- `documentos/PLAN/plan-marcar-pendiente-a-limpieza.md`: Marcadas `[x]` las tareas T001 a T007.

---

## Tareas dejadas sin hacer según el alcance definido (KISS / YAGNI)
1. `plan-marcar-habitacion-como-disponible.md`:
   - Phases 4 y 5 (T010 a T025): Endpoints de reactivación (`POST /api/rooms/{roomId}/reactivation`), consulta de bajas (`GET /api/rooms/{roomId}/decommission`), integración de `RoomDecommission` y componentes de frontend. Se implementan en el lote posterior tras `plan-dar-de-baja-habitacion.md`.
2. `plan-marcar-habitacion-reservada.md`:
   - T014 a T018: Verificación e integración con los invocadores (`DailyReservationIngestionService`, `CompleteCleaningService`, vistas de recepción) y pruebas E2E de latencia, que corresponden a los lotes de cada caso de uso invocador.
3. `plan-marcar-pendiente-a-limpieza.md`:
   - T008 en adelante: Flujos invocadores (check-out, reparación y liberación de tareas en recepción y limpieza), que se probarán de extremo a extremo al construir esos casos de uso.

---

## Resultado de `mvnw.cmd verify`
- **Compilación**: 110 archivos de código fuente compilados con Java 21 sin advertencias.
- **Formateo Spotless**: 147 archivos verificados limpios.
- **Pruebas Automatizadas**: **172 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidos**.
  - Pruebas unitarias de servicios de transición y casos de uso con Mockito.
  - Pruebas de integración con Testcontainers (PostgreSQL 16 y RabbitMQ 3.13) cubriendo concurrencia, bloqueo pesimista y optimista, rollbacks atómicos, persistencia de historial y bitácora de auditoría.
- **Empaquetado**: JAR ejecutable `habitaciones-0.0.1-SNAPSHOT.jar` generado exitosamente.
- **Estado General**: `BUILD SUCCESS`.
