# Plan: Backend del Módulo 1 por lotes (un lote = un commit pequeño)

## Estado al 07-11-2026

| Rama | Lotes | Estado |
| --- | --- | --- |
| R0 `feature/backend` | L00 | Integrada (PR #4) |
| R1 `feature/m1-inventario` | L01–L03 | Integrada (PR #5) |
| R2 `feature/m1-historial` | L04 | Integrada (PR #6) |
| R3 `feature/m1-reservas-diarias` | L05–L07 | Integrada (PR #7) |
| R4 `feature/m1-conflictos-modulo2` | L24 | Integrada (PR #8) |
| R5 `feature/m1-recepcion` | L08–L10 | Integrada (PR #9) |
| R6 `feature/m1-check-in` | L11–L12 | Integrada (PR #10) |
| R7 `feature/m1-check-out` | L13–L16 | Integrada (PR #11) |
| R8 `feature/m1-limpieza` | L17–L21 | Integrada (PR #12) |
| R9 `feature/m1-admin-habitaciones` | L31–L34 | Integrada (PR #13) |
| R10 `feature/m1-bloqueos-tecnicos` | L22, L23, L25, L26 | Integrada (PR #15) |
| R11 `feature/m1-reparaciones` | L27–L30 | Integrada (PR #16) |
| R12 `feature/m1-personal` | L35–L37 | Integrada (PR #17) |
| R13 `feature/m1-cierre` | L38, L40 (L39 aplazado) | Integrada (PR #18, 07-11-2026) |

El frontend tiene su propio plan de fases (`FASES_IMPLEMENTACION_FRONTEND.md`); hoy está fuera del repositorio.

Las secciones que siguen conservan el texto con el que se planeó al empezar; el estado real es la tabla de arriba.

## Contexto al inicio (29-10-2026)

La rama `origin/feature/backend` (último commit `21be19c`) ya tiene la **plataforma base** del plan
`documentos/PLAN/base/plan.md`: esquema V1/V2, `Room` + máquina de estados (`RoomTransitions`), historial
(`TransitionRoomStateUseCase`, `RoomStateHistoryRecorder`), bitácora `room_audit_log`, Outbox + RabbitMQ
(serializadores `CHECK_IN`, `CHECK_OUT`, `CHECK_OUT_BILLING`, `MAINTENANCE_NOTICE` ya registrados), `RestClientConfig`
M2/M3, errores (`DomainError` + `GlobalExceptionHandler`), login + `CurrentUserResolver` (`X-User`), cuentas del
personal (núcleo), `MaintenanceNoticePublisher`, `Clock`, CI y `ArchitectureTest` (ArchUnit). También los tres
puertos transversales: `MarkRoomAvailableUseCase`, `MarkRoomReservedUseCase`, `MarkPendingCleaningUseCase`.

**Falta todo lo funcional**: ningún plan de feature de `documentos/PLAN/plan-*.md` tiene endpoints; el único
controlador es `AuthController`. Pendiente del plan base solo T017 (migración V3) en backend (T001/T003/T004/T015/T021
son frontend, fuera de alcance).

Objetivo: implementar el backend completo en lotes verticales pequeños, en el orden de dependencias de
"Orden de implementación de los planes" (plan base, línea 1135), con un prototipo funcional, limpio y sin
sobreingeniería.

## Estrategia de ramas (Gitflow)

Hoy `develop` (`6f24ee4`) **no tiene el backend**: `feature/backend` va 21 commits por delante. El CI
(`backend-ci.yml`) solo corre en PR/push a `develop` y `main`.

Flujo: `develop` → `feature/m1-<área>` → lotes (commits) → PR a `develop` con CI verde → merge → borrar la rama →
la siguiente rama sale del `develop` actualizado. Antes de abrir un PR, se hace merge de `develop` en la rama
(no rebase), por si otra rama paralela ya se integró.

### ¿Cada área es independiente? (validación del grafo de dependencias)

No todas. Lo que se comparte entre áreas es esto, revisado contra las secciones de dependencias de cada `plan-*.md`:

| Área | Necesita de otra área (código, no solo UI) | ¿Independiente? |
| --- | --- | --- |
| Inventario (registrar + consultar) | solo la base | Sí |
| Historial | solo la base (su prueba T015 "todos los flujos" necesita todo → va al cierre) | Sí |
| Reservas diarias (ingesta + cola) | solo la base y los puertos transversales | Sí |
| Conflictos con M2 (cliente REST, reservas Phase 5) | solo la base (`RestClientConfig`) | Sí |
| Recepción (panel) | Reservas (`DailyReservationRepositoryPort`, `ArrivalGroup`) | No → después de Reservas |
| Check-in | Reservas (validación contra la copia local) | No → después de Reservas (en paralelo con Recepción) |
| Liquidación + check-out | `Stay` y `Module3Properties` de la base | Sí en código (conviene después de check-in para probar el flujo real) |
| Limpieza | Reservas (`findPendingArrivalReservationRef`); **`applyForRoom` de Mantenimiento** | Parcial: se entrega sin `applyForRoom` y Mantenimiento lo conecta (ya está previsto así en `plan-confirmar-fin-de-limpieza.md`, "Orden") |
| Reportar daño | lo usan Limpieza y Mantenimiento | Va en la rama de Limpieza (primera en usarlo) |
| Bloqueos técnicos | Conflictos con M2, Limpieza (para conectar `applyForRoom`) | No |
| Reparaciones | Reportar daño, Bloqueos técnicos (cierra el informe) | No |
| Administración de habitaciones (baja, reactivar, editar, tarifa) | Inventario (`RoomController`, validaciones de `Room`), Conflictos con M2 | No (la tarifa base sí lo es) |
| Personal (registrar, consultar, editar) | solo la base | Sí |
| Personal (desactivar) | puertos de liberación de Limpieza y Reparaciones | No → al final |

Puntos de choque entre ramas paralelas y cómo se evitan:
- **Versiones de Flyway**: se reservan de antemano. V3 en la rama base, V4 en Inventario y V5 en Historial.
  Ninguna otra rama trae migraciones.
- **`DomainError`** (todas las ramas le agregan valores): un valor por línea, sin reordenar; el conflicto se resuelve
  trivialmente al hacer merge de `develop`.
- **`RoomController`**: lo crea Inventario. Daños, bloqueos y tarifa usan sus propios controladores (como dicen sus
  planes), así que solo Administración vuelve a tocarlo.

### Estrategias para definir el tamaño de cada rama

1. **Por área funcional** (la que elegiste): agrupa por dominio de negocio. Fácil de nombrar, pero sola no garantiza
   que la rama se pueda integrar, porque las áreas se cruzan (tabla anterior).
2. **Por corte cerrado de dependencias** (recomendada, combinada con la 1): una rama solo puede usar puertos que ya
   estén en `develop` o que ella misma cree. Si una pieza la usan dos áreas, va en la primera rama que la necesita
   (dueño único). Si una conexión necesita algo que llega después, se hace en la rama posterior (por ejemplo,
   `applyForRoom` → Limpieza). Así `develop` siempre compila y funciona.
3. **Por actor/rol** (Administrador, Gerente, Recepción, Limpieza, Mantenimiento, Módulos 2/3): cada rama se puede
   demostrar con una vista del prototipo, pero se cruza más (Limpieza y Mantenimiento comparten daño y bloqueos).
4. **Por contrato de integración**: aislar en ramas propias lo que habla con M2/M3 (cola diaria, cliente de
   conflictos, liquidación), así los riesgos externos y las pruebas de WireMock y Testcontainers quedan en PRs
   enfocados. Ya se aplica en "Conflictos con M2".
5. **Límite de tamaño** como regla de corte: como máximo unos 5 commits o lo que se revise en una sesión. Si la
   rama crece más, se divide por el punto natural del grafo (así se separaron Bloqueos y Reparaciones).

### Ramas resultantes (orden y paralelismo)

| # | Rama | Lotes | Depende de | Puede ir en paralelo con |
| --- | --- | --- | --- | --- |
| R0 | `feature/backend` (base actual) → PR a `develop` | + L00 (V3) | — | — |
| R1 | `feature/m1-inventario` | L01–L03 (V4) | R0 | R2, R3, R4, R6, R9 |
| R2 | `feature/m1-historial` | L04 (V5) | R0 | R1, R3, R4 |
| R3 | `feature/m1-reservas-diarias` | L05–L07 | R0 | R1, R2, R4 |
| R4 | `feature/m1-conflictos-modulo2` | L24 | R0 | R1–R3 |
| R5 | `feature/m1-recepcion` | L08–L10 | R3 | R6, R7 |
| R6 | `feature/m1-check-in` | L11–L12 | R3 | R5 |
| R7 | `feature/m1-check-out` | L13–L16 | R0 (mejor después de R6) | R5 |
| R8 | `feature/m1-limpieza` | L17–L21 | R3 | R5–R7 |
| R9 | `feature/m1-admin-habitaciones` | L31–L34 | R1, R4 | R5–R8 |
| R10 | `feature/m1-bloqueos-tecnicos` | L22, L23, L25, L26 | R4, R8 | R9 |
| R11 | `feature/m1-reparaciones` | L27–L30 | R8, R10 | R9 |
| R12 | `feature/m1-personal` | L35–L37 | R8, R11 (solo L37; L35–L36 solo necesitan R0) | — |
| R13 | `feature/m1-cierre` | L38, L40 (L39 aplazado) | todas | — |

Aunque se pueda trabajar en paralelo, la sugerencia es ir **de a una rama a la vez** en el orden de la tabla (R0 → R13)
mientras somos un solo desarrollador por sesión. El paralelismo queda disponible si el equipo se reparte ramas.

## Reglas que aplican a todos los lotes

**Flujo hexagonal por lote** (validar en cada uno, regla 1 del plan base y `ArchitectureTest`):
`domain/model` + `domain/exception` (puro, sin Spring/JPA/Jackson) → `application/ports/in` (`*UseCase`) y
`ports/out` (`*RepositoryPort` / nombre funcional) → `application/service` (solo `@Service`, `@Transactional`,
`ApplicationEventPublisher`; DTOs sin anotaciones) → `infrastructure/adapters/out` (JPA, REST, AMQP) →
`infrastructure/adapters/in/web` (request DTO con `@Valid` → comando; nunca entidades en la respuesta).

**Reutilizar siempre (no reimplementar):**
- Transiciones solo vía `TransitionRoomStateUseCase.transition(...)` / `registerInitialStatus(...)`
  (`application/service/RoomStateTransitionService.java`) o los puertos `MarkRoomAvailable/Reserved/PendingCleaning`.
- Bloqueo de fila con `RoomRepositoryPort.lockById` al inicio de toda operación de decisión (regla 13);
  los puertos transversales son `MANDATORY`, así que el invocador bloquea primero.
- Errores: nueva excepción = subclase de `DomainException` + valor en `DomainError` (el handler ya traduce por categoría;
  no se añaden `@ExceptionHandler` nuevos salvo traducción de `DataIntegrityViolationException` en el adaptador).
- Usuario actual: `CurrentUserResolver` (`infrastructure/adapters/in/web/CurrentUserResolver.java`).
- Avisos a M2: `MaintenanceNoticePublisher`; mensajes: `OutboxEventPublisherPort.enqueue(...)`.
- Auditoría: `RoomAuditLogRepositoryPort.record(RoomAuditEntry)`.
- Fechas/horas: `Clock` inyectado.

**KISS / YAGNI aplicados a los planes:**
- Las tareas "Verificar…" no generan commit propio: se comprueban dentro del lote que las necesita.
- No se crean las clases marcadas "Sin uso asignado" en el árbol del plan base (`ForeignGuestData`,
  `CheckOutDetails`, `ReservationSummary`, `EnrichedReservationSummary`).
- Pruebas `@Tag("performance")` y tareas de frontend/E2E: fuera de estos lotes (se listan al final como opcionales).
- Una abstracción solo con ≥2 consumidores. Única nueva prevista: `PageResult<T>` en `application/dto`
  (inventario, historial y personal la usan).
- `InvalidDateRangeException`: se crea en el primer consumidor (historial, solo la regla `END_BEFORE_START`) y el lote de
  mantenimientos agrega sus demás reglas (evita implementar reglas sin uso todavía).
- Lógica en el dominio con guard clauses; servicios cortos que orquestan; controladores solo traducen HTTP.

**Definición de terminado por lote:** `./mvnw verify` verde (Spotless + unitarias + integración Testcontainers +
ArchUnit), tareas marcadas `[x]` en su `plan-*.md`, commit convencional en español
(`feat(<área>): … (Txxx)`), push a la rama `feature/m1-<área>` correspondiente. Al cerrar la rama: PR a `develop`
(solo cuando lo pidas) con CI verde.

---

## Fase 0 — Cimiento pendiente

| Lote | Commit | Contenido | Plan / tareas |
| --- | --- | --- | --- |
| L00 | `feat(db): migración V3 de tareas de limpieza y mantenimiento` | `V3__cleaning_maintenance_schema.sql` (`cleaning_task`, `reparation_task`, `damage_report`, `technical_block_report`, FK, CHECK e índices parciales únicos); ampliar `FlywayMigrationAndSchemaTest`. Va **antes** de V4/V5 para no romper el orden de Flyway. Sin datos. | base T017 |

## Fase 1 — Inventario (Administrador, Gerente, Módulo 2)

| Lote | Commit | Contenido | Plan / tareas |
| --- | --- | --- | --- |
| L01 | `feat(habitaciones): registrar habitación` | `Room.register(...)` (fábrica con validación de piso/capacidad/tarifa > 0 en dominio), `RoomNumberAlreadyExistsException` (+`DomainError`), `RegisterRoomUseCase`/`Command`/`Result`, `RegisterRoomService` (`save` + `registerInitialStatus` con `REGISTER_ROOM`), traducción del índice único en `RoomRepositoryAdapter`, `RoomController` `POST /api/rooms` + `RegisterRoomRequest` | registrar-habitacion T001–T009, T011, T012, T016 |
| L02 | `feat(habitaciones): consultar inventario interno paginado` | `V4__room_inventory_indexes.sql`, `RoomInventoryFilter`, `PageResult<T>`, `RoomRepositoryPort.searchInventory`, `RoomInventoryQueryService` (readOnly), `GET /api/rooms/inventory` y `/inventory/{roomId}` (con `version`) | consultar-inventario T001, T002, T004–T008, T010, T015, T016; registrar-habitacion T015 |
| L03 | `feat(habitaciones): contrato de inventario para Módulo 2` | Consultas vendibles (sin `Inactive`) por categoría y por id; `GET /api/rooms?categoryRoom=` y `GET /api/rooms/{roomId}` (solo `id`, `roomNumber`, `categoryRoom`, `maxCapacity`) | consultar-inventario T009, T015–T016 (parte M2) |
| L04 | `feat(historial): consultar historial de estados` | `V5__room_state_history_indexes.sql`, `RoomStateHistoryFilter`, `search(...)` en puerto/adaptador (nombre del actor por join a `user_account`, sin N+1), `InvalidDateRangeException` (`END_BEFORE_START`), `RoomStateHistoryQueryService`, `GET /api/room-state-history` | consultar-historial T001–T008, T010, T011 |

## Fase 2 — Reservas del día, Recepción, Check-In / Check-Out

| Lote | Commit                                                       | Contenido                                                                                                                                                                                                                                                                                                                      | Plan / tareas                                                                         |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------- |
| L05  | `feat(reservas): persistencia de la copia local de reservas` | Dominio `DailyReservation`, `DailyReservationRoom`; entidades JPA (3 tablas de V1); `DailyReservationRepositoryPort` + adaptador (guardar, purga, log de mensajes, `findPendingArrivalReservationRef`)                                                                                                                         | consultar-reservas T004–T007 (parte), T011                                            |
| L06  | `feat(reservas): ingesta de lista diaria y actualizaciones`  | `IngestDailyReservationsUseCase`, `DailyReservationIngestionService`: idempotencia por `messageId`, `sequenceNumber`/`SEQUENCE_GAP`, descarte de `operationalDate` antiguo, purga, apartar con `MarkRoomReservedUseCase` y liberar con `MarkRoomAvailableUseCase` (con `lockById`), evento `DailyReservationListIngestedEvent` | consultar-reservas T009, T012, T013, T015 (servicio)                                  |
| L07  | `feat(reservas): listener RabbitMQ de la cola diaria`        | `DailyReservationRabbitListener` + DTO del mensaje; inválidos → DLQ sin reintento; errores temporales → 3 reintentos; prueba Testcontainers end-to-end                                                                                                                                                                         | consultar-reservas T010, T014, T015 (listener)                                        |
| L08  | `feat(recepcion): pestaña de llegadas`                       | `ArrivalGroup`, `ArrivalItemSummary`, `SearchDailyReservationsUseCase`, `DailyReservationSearchService` (búsqueda local trim/insensible a mayúsculas, estado de la habitación, `listAvailable`), `ReceptionPanelController` `GET /api/reception/arrivals`                                                                      | consultar-reservas T016–T020, T030; panel-recepcion T004, T007–T009, T011, T012, T028 |
| L09  | `feat(recepcion): pestaña de salidas`                        | Consulta de estancias activas en `StayRepositoryPort`, `SearchDeparturesUseCase`, `DepartureSearchService` (vencidas primero), `GET /api/reception/departures`                                                                                                                                                                 | panel-recepcion T006, T014, T015, T017, T018                                          |
| L10  | `feat(recepcion): indicadores del panel`                     | `ReceptionDashboardKpis`, `countByStatus`, `GetReceptionPanelUseCase`, `ReceptionPanelQueryService`, `GET /api/reception/panel/kpis`                                                                                                                                                                                           | panel-recepcion T005, T020, T022, T023, T029                                          |
| L11  | `feat(check-in): validar reserva (paso 1)`                   | Excepciones de check-in (+`DomainError`), `ValidateCheckInReservationUseCase`, `CheckInValidationService`, `CheckInController` `GET /api/check-in/validate`                                                                                                                                                                    | registrar-check-in T007, T008, T015, T017 (parte), T019, T020, T026                   |
| L12  | `feat(check-in): registrar check-in`                         | Ajustes de `Stay`/`RoomGuest` si faltan campos, `CheckInMessagePayload`/`MovementType`, `RegisterCheckInUseCase`, `CheckInExecutionService` (una transacción: `Stay`, `RoomGuest`, `CHECK_IN` vía transición, outbox `habitacion.checkin`, auditoría), `POST /api/check-in` (201)                                              | registrar-check-in T004–T006, T009–T014, T016, T017, T021–T024                        |
| L13  | `feat(liquidacion): adaptador REST de Módulo 3`              | `SettlementRequest`, `SettlementSummary`, `SettlementRestQueryPort`, `SettlementRestAdapter` (RestClient, traducción de errores/timeouts a `ExternalModule3UnavailableException`, `@CircuitBreaker` sin reintentos), pruebas WireMock                                                                                          | consultar-liquidacion T002–T005, T007, T010, T019, T020, T022, T023                   |
| L14  | `feat(liquidacion): consultar liquidación de la estancia`    | `QuerySettlementUseCase`, `SettlementQueryService` (`UNAVAILABLE` controlado, sin escrituras), `GET /api/stays/{stayId}/settlement` (200, `no-store`), logging sin montos                                                                                                                                                      | consultar-liquidacion T006, T008, T009, T014, T017, T018, T021, T024, T025            |
| L15  | `feat(check-out): estancia activa (paso 1)`                  | `GetActiveStayUseCase`, `ActiveStayQueryService`, `GET /api/check-out/active-stay`                                                                                                                                                                                                                                             | registrar-check-out T004, T009, T011 (parte)                                          |
| L16  | `feat(check-out): registrar check-out`                       | Excepciones, `RegisterCheckOutUseCase`, `CheckOutExecutionService` (cierre de `Stay`, `MarkPendingCleaningUseCase`, outbox `habitacion.checkout` + `facturacion.checkout`, auditoría, `version` optimista), `POST /api/check-out`                                                                                              | registrar-check-out T003, T005–T008, T010, T011, T013–T017, T020                      |

## Fase 3 — Limpieza

| Lote | Commit | Contenido | Plan / tareas |
| --- | --- | --- | --- |
| L17 | `feat(limpieza): tarea de limpieza y su persistencia` | `CleaningTask` (+`CleaningTaskOutcome.countsAsCleaning()`, `ensureClosableBy`, `close`), JPA, `CleaningTaskRepositoryPort` + adaptador (traduce índices únicos a `ACTIVE_TASK_EXISTS`/`ROOM_TASK_EXISTS`) | en-limpieza T001–T003 |
| L18 | `feat(limpieza): iniciar limpieza` | `StartCleaningUseCase`, `StartCleaningService` (lock, `START_CLEANING`), `CleaningController` `POST /api/cleaning/tasks` (201) | en-limpieza T004–T013 |
| L19 | `feat(limpieza): panel de limpieza` | `CleaningPanelDto`, `GetCleaningPanelUseCase`, `CleaningPanelQueryService` (última limpieza por lotes, tarea activa), `GET /api/cleaning/panel` | panel-limpieza T001–T008, T012 |
| L20 | `feat(mantenimiento): reportar daño` | `DamageReport` + persistencia, `DamageReportNotFoundException`, `ReportDamageUseCase`/`Service` (lock, `REPORT_DAMAGE`, aviso `SCHEDULED`), `DamageReportQueryService`, `POST /api/rooms/{id}/damage-reports` y `GET …/latest` | inhabilitada T001–T013, T015 |
| L21 | `feat(limpieza): confirmar fin y liberar tarea` | `CompleteCleaningService` (Available → Reserved si hay llegada / daño), `ReleaseCleaningTaskService` + `ReleaseCleaningTaskOnDeactivationUseCase`, endpoints `complete`/`release` (204). El camino `applyForRoom` se conecta en L25 | confirmar-fin-limpieza T001–T018 (sin la rama de `applyForRoom`); reservada T014 |

## Fase 4 — Mantenimiento

| Lote | Commit | Contenido | Plan / tareas |
| --- | --- | --- | --- |
| L22 | `feat(mantenimiento): informe de bloqueo técnico y su persistencia` | `TechnicalBlockReport` (estados, `covers`, `isOverdue`), `TechnicalBlockStatus`, JPA, `TechnicalBlockReportRepositoryPort` + adaptador, excepciones `MAINTENANCE_CONFLICT`/`TECHNICAL_BLOCK_NOT_FOUND` | bloqueo-tecnico T001–T004 |
| L23 | `feat(mantenimiento): consultar información de mantenimientos` | `MaintenanceDateRange` (`forAvailability`, `forScheduling`), `MaintenanceConflict`, reglas restantes de `InvalidDateRangeException`, `MaintenanceAvailabilityService`, `GET /api/rooms/{roomId}/maintenances` | info-mantenimientos T001–T012 |
| L24 | `feat(reservas): consulta de conflictos con Módulo 2` | `Module2ReservationConflictClientPort`, `Module2ReservationConflictRestAdapter` (`X-Client: MODULE1`), `ExternalModule2UnavailableException`, `ReservationConflictException`, `CheckRoomReservationConflictsUseCase` (`checkMaintenanceConflict`, `checkDecommissionConflict`), pruebas WireMock | consultar-reservas T003, T008, T022–T029 |
| L25 | `feat(mantenimiento): programar y aplicar bloqueo técnico` | `ScheduleTechnicalBlockService` (consulta M2 fuera de tx) + `TechnicalBlockRegistration` (tx con lock, aviso `SCHEDULED`), `ApplyScheduledTechnicalBlocksService` (`runDaily`, `applyForRoom`), conexión de `applyForRoom` en `CompleteCleaningService`, `POST /api/rooms/{id}/technical-blocks` | bloqueo-tecnico T005–T021 (núcleo); confirmar-fin-limpieza T006/T012 (rama `applyForRoom`) |
| L26 | `feat(mantenimiento): trabajos diarios y consulta del bloqueo` | `TechnicalBlockScheduler` (listener AFTER_COMMIT + cron 01:00), `DailyMaintenanceNoticeService` (00:00), `TechnicalBlockQueryService` + `GET …/technical-blocks/current` | bloqueo-tecnico T022–T025 |
| L27 | `feat(mantenimiento): tarea de reparación y su persistencia` | `ReparationTask` + JPA + `ReparationTaskRepositoryPort`, `MaintenanceController` | confirmar-reparacion T001–T004 |
| L28 | `feat(mantenimiento): panel de mantenimiento` | `MaintenancePanelDto`, `GetMaintenancePanelUseCase`, `MaintenancePanelQueryService`, `GET /api/maintenance/panel` | panel-mantenimiento T001–T009 |
| L29 | `feat(mantenimiento): iniciar reparaciones` | `StartRepairService` (`ROOM_TASK_EXISTS` con fila bloqueada), `POST /api/maintenance/tasks` | confirmar-reparacion T005–T011 |
| L30 | `feat(mantenimiento): confirmar fin y liberar reparación` | `CompleteRepairService` (`MarkPendingCleaningUseCase`, aviso `UPDATED`/`CANCELLED`, cierre de informe), `ReleaseRepairTaskService` + puerto de desactivación, endpoints `complete`/`release` | confirmar-reparacion T012–T025 |

## Fase 5 — Administración

| Lote | Commit | Contenido | Plan / tareas |
| --- | --- | --- | --- |
| L31 | `feat(habitaciones): dar de baja habitación` | `RoomDecommission`, `DecommissionReason`, persistencia, `DecommissionRoomService` (consulta M2 fuera de tx, revalida con lock, `DECOMMISSION_ROOM`), `POST /api/rooms/{id}/decommission` (200) | dar-de-baja T001–T016 |
| L32 | `feat(habitaciones): reactivar habitación` | `ReactivateRoomService` (idempotente por `decommissionId`), `GetRoomDecommissionUseCase`, `GET` y `POST …/reactivation` | como-disponible T010–T018, T020 |
| L33 | `feat(habitaciones): editar habitación` | `Room.applyChanges(...)` (+`RoomAttributeChange`, reutiliza validaciones de L01), `EditRoomService` (solo `Available`, `version`, auditoría `ROOM_EDITED`), `PATCH /api/rooms/{id}` (200) | editar-habitacion T001–T015 |
| L34 | `feat(habitaciones): tarifa base para Módulo 3` | `GetRoomBaseRateUseCase`, `RoomBaseRateQueryService`, `RoomBaseRateController` `GET /api/rooms/{id}/base-rate?date=` (`baseRate` texto con 2 decimales) | tarifa-base T001–T015 |
| L35 | `feat(personal): registrar y consultar cuentas` | Excepciones del plan, consultas paginadas en `StaffAccountRepositoryPort`, `StaffAccountQueryService`, `StaffController` `POST /api/staff`, `GET /api/staff`, `GET /api/staff/{username}` | gestionar-personal T001–T021 |
| L36 | `feat(personal): editar cuenta` | `EditStaffAccountService`, `PATCH /api/staff/{username}` (200, `version`) | gestionar-personal T022–T027 |
| L37 | `feat(personal): desactivar cuenta` | `DeactivateStaffAccountService` (bloqueo de admins activos, propia cuenta, último admin, libera tareas vía puertos de L21 y L30), `POST /api/staff/{username}/deactivation` (200) | gestionar-personal T028–T036 |

## Fase 6 — Cierre

| Lote | Commit | Contenido |
| --- | --- | --- |
| L38 | `test: verificaciones transversales` | Tareas de revisión de los planes: nadie asigna `Reserved` fuera de los puertos (reservada T015–T017), sin REST a M2 en check-in/panel, historial sin huecos entre todos los flujos (historial T015), sin horas en fechas expuestas; regla ArchUnit si alguna se puede automatizar |
| L39 | `chore(db): datos sintéticos de desarrollo` (opcional) | **Aplazado (06-11-2026); se puede hacer más adelante.** `db/dev-data` con habitaciones en `DisabledForRepairs`/`TechnicalBlock` y sus informes, respetando invariantes (base T017, solo perfil `dev`). Mientras tanto, esos estados se generan con los propios flujos (reportar daño, programar bloqueo). Cuando se haga: rama pequeña desde `develop` (o la que esté abierta), `classpath:db/dev-data` en Flyway solo en el perfil `dev` (nota de T006) y numeración aparte de las migraciones `V1`–`V5`. En el plan base, `db/dev-data/` queda marcada como opcional y pendiente (árbol, T006 y punto abierto 6); L40 lo anota. |
| L40 | `build(backend): contrato OpenAPI con springdoc` | Ver "Contrato OpenAPI" abajo. |

**Fuera de estos lotes (opcional, a pedido):** pruebas `@Tag("performance")` de cada plan y todo el frontend (el frontend
tiene su propio plan, `FASES_IMPLEMENTACION_FRONTEND.md`).

### Contrato OpenAPI (L40, agregado el 06-11-2026)

Se agrega por el principio de sincronización automática de contratos del frontend: el backend publica su contrato y el
frontend genera sus tipos desde él, para que un cambio en un DTO rompa el `typecheck` del frontend. No cambia ningún
endpoint, DTO ni regla de negocio.

**L40** — Dependencia `springdoc-openapi-starter-webmvc-api` 3.2.0 (compatible con Spring Boot 4.1; sin la interfaz Swagger UI) que publica `GET /v3/api-docs` con las rutas `/api/**` y queda apagada en `prod`. Prueba `OpenApiContractIntegrationTest` (sigue el nombre de las demás pruebas de integración): compara el contrato generado con el versionado en `documentos/contrato/openapi.json` y falla si difieren; con `-Dopenapi.update=true` lo reescribe. El plan base se actualiza: fila nueva en "Dependencias y herramientas transversales aprobadas", el árbol (`OpenApiContractIntegrationTest.java`, `documentos/contrato/openapi.json` y este archivo) y la tarea nueva T026. También anota en el plan base que `db/dev-data/` sigue pendiente por L39 aplazado (ver la fila de L39).

- Va en la rama de cierre (`feature/m1-cierre`, R13), decidido el 06-11-2026: es cierre del backend, R13 aún no tiene
  PR y así no hace falta otra rama. La parte del frontend (C02, tipos generados) va en la rama base del frontend
  (RF0), antes de F00, porque `feature/m1-cierre` es solo de backend.
- No interfiere con el Javadoc: `springdoc` lee las clases compiladas (rutas, DTOs y anotaciones de validación), no los
  comentarios; no se agregan `@Operation` ni `@Schema` a los controladores.
- Ajustes hechos para que el contrato coincida con el backend (detalle en la tarea T026 del plan base): `OpenApiConfig`
  (título, `ApiError` y `4XX`), filtros como parámetros sueltos y `@Schema(implementation = String.class)` en 6 métodos
  de validación `@AssertTrue`. Los `201` aparecen como `200` (limitación conocida, sin efecto en los tipos).
- Los planes de cada caso de uso no cambian: sus contratos (rutas, cuerpos y errores) siguen iguales.
- Las respuestas de error se documentan con el `ApiError` actual (`errorCode`, `message`, `details`), que agrega
  `OpenApiConfig`. No se migra a Problem Details (RFC 7807).
- Verificación: `./mvnw verify` verde y `GET /v3/api-docs` en perfil `dev` devuelve el mismo contrato versionado.

## Archivos críticos

- `backend/src/main/java/com/hospitua/habitaciones/domain/model/Room.java` (fábrica de registro, `applyChanges`)
- `.../application/ports/out/RoomRepositoryPort.java`, `StayRepositoryPort.java`, `StaffAccountRepositoryPort.java`
- `.../domain/exception/DomainError.java` (un valor por código nuevo)
- `.../infrastructure/adapters/in/web/` (nuevos `RoomController`, `ReceptionPanelController`, `CheckInController`,
  `CheckOutController`, `SettlementController`, `CleaningController`, `MaintenanceController`, `StaffController`, …)
- `backend/src/main/resources/db/migration/V3…`, `V4…`, `V5…`
- `documentos/PLAN/plan-*.md` (marcar `[x]` en cada lote)

## Verificación

- Por lote: `cd backend && ./mvnw verify` (requiere Docker para Testcontainers); `ArchitectureTest` debe seguir verde.
- Por fase, prueba manual: `docker compose up -d && ./mvnw spring-boot:run`, login `admin`/`admin123`, y `curl` con
  `X-User: admin` contra los endpoints del lote (p. ej. `POST /api/rooms` → `GET /api/rooms/inventory` →
  `GET /api/room-state-history`); para la cola diaria, publicar un mensaje en `m1.reservas.diarias.queue` desde la consola
  de RabbitMQ (`:15672`) y ver la llegada en `GET /api/reception/arrivals`.
