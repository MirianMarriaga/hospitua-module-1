# Implementation Plan: Consultar Reservas

**Date**: 2026-10-09  
**Spec**: [spec-consultar-reservas.md](../SPEC/spec-consultar-reservas.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso técnico **Consultar Reservas**, que resuelve dos requerimientos operativos claramente diferenciados conforme a la arquitectura de integración de HOSPITUA:

1. **Modo Local (Recepción, Panel de Llegadas y Check-In)**:
   - **Ingesta Asíncrona por Cola**: Consumir de la cola `m1.reservas.diarias.queue` la lista diaria de llegadas a las 00:00 (routing key `reserva.lista-del-dia`, `sequenceNumber = 1`) y las actualizaciones continuas durante el día (routing key `reserva.lista-del-dia.actualizacion`, acciones `ADDED`, `UPDATED`, `REMOVED`).
   - **Almacenamiento Local**: Mantener la copia local en las tablas `daily_reservation`, `daily_reservation_room` y `daily_reservation_message_log` con control estricto de idempotencia por `messageId` y orden por `sequenceNumber` y `updatedAt`.
   - **Efecto en Inventario Físico (Transiciones Automáticas)**: Al persistir la lista diaria o una acción `ADDED`, el sistema transiciona automáticamente cada habitación a `Reserved` asignando `Room.reserved_by_reservation_ref`. Al recibir `REMOVED` o quitar una habitación en `UPDATED`, transiciona de `Reserved` a `Available`. A las 00:00, las liberaciones se ejecutan antes de apartar las nuevas habitaciones. A las 23:59, las habitaciones en `Reserved` sin Check-In se liberan a `Available`.
   - **Búsqueda 100% Autónoma y Local**: La Recepcionista consulta el listado de llegadas y busca por código (`reservationRef`), número de documento o nombre del titular directamente sobre la copia local (invocado por `plan-consultar-panel-recepcion.md` y por el Paso 1 de `plan-registrar-check-in.md`), sin realizar ninguna petición HTTP/REST a Módulo 2.
   - **Sin campo `nights`**: Se elimina `nights` de todos los modelos y DTOs de persistencia/mensajería; el cálculo de noches se realiza dinámicamente en tiempo de ejecución (`endDate - startDate`).

2. **Modo REST (Mantenimiento y Administración - Bloqueo Técnico y Baja de Habitación)**:
   - **Consulta Directa REST a Módulo 2**: El Personal de mantenimiento y el Administrador consultan al Módulo 2 si un rango de fechas se solapa con reservas futuras de una habitación específica antes de intervenirla físicamente o retirarla de inventario.
   - **Parámetros Requeridos**: La petición envía únicamente el header HTTP y 3 parámetros: `roomId`, `startDate` y `endDate`.
   - **Restricción**: Se consulta exactamente **una sola habitación por petición** (no por categoría ni por listas).
   - **Regla para Bloqueo Técnico**: `startDate` = fecha de inicio del mantenimiento, `endDate` = fecha estimada de finalización.
   - **Regla para Dar de Baja Habitación**: `startDate` = fecha actual (`LocalDate.now()`), `endDate` = fecha límite dentro de 30 días (`LocalDate.now().plusDays(30)`). El horizonte máximo permitido hacia el futuro es de **30 días**.
   - **Puerto REST Desacoplado**: La ruta exacta del endpoint y el formato del payload de respuesta están pendientes de confirmación por Módulo 2. Módulo 1 implementa el puerto desacoplado `Module2ReservationConflictClientPort` con los tres parámetros (`roomId`, `startDate`, `endDate`).
   - **Resiliencia y Manejo de Errores**: Timeouts estrictos (conexión 1 s, lectura 2 s), traduciendo caídas, latencias o errores 503 en excepciones controladas (`ExternalModule2UnavailableException`) para informar al usuario sin generar fallos 500 no capturados.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, Spring Data JPA, Spring AMQP (RabbitMQ), Lombok, Resilience4j, RestClient
- **Storage**: PostgreSQL 16+ (tablas `daily_reservation`, `daily_reservation_room`, `daily_reservation_message_log`, `room`)
- **Messaging**: RabbitMQ 3.13+ (Exchange topic `hospitua.events`, cola durable `m1.reservas.diarias.queue`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL, RabbitMQ), MockRestServiceServer / WireMock
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / JSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Búsqueda local de llegadas en mostrador < 50 ms p95 (100% sobre base de datos local).
  - Consulta REST a Módulo 2 para mantenimiento/baja < 1.5 s p95 con timeout estricto a los 2 s.
- **Constraints**:
  - Mostrador / Recepción / Check-In: **Cero llamadas HTTP/REST a Módulo 2**.
  - Mantenimiento / Administración: Consulta REST restringida a exactamente 1 habitación y 3 parámetros (`roomId`, `startDate`, `endDate`); consulta pendiente de confirmar con Módulo 2.
  - Límite de Dar de Baja: `endDate` no puede exceder 30 días a partir de hoy.
  - Fechas sin horas en contratos de negocio (`LocalDate` / YYYY-MM-DD). Cero campo `nights` persistido.

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-consultar-reservas.md
│   ├── spec-consultar-panel-recepcion.md
│   └── spec-registrar-check-in.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-consultar-reservas.md
    ├── plan-consultar-panel-recepcion.md
    └── plan-registrar-check-in.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── DailyReservation.java
│   │   │   ├── DailyReservationRoom.java
│   │   │   ├── DailyReservationMessageLog.java
│   │   │   ├── ReservationConflictQuery.java         # Value Object (roomId, startDate, endDate)
│   │   │   ├── ReservationConflictResult.java        # Resultado de cruce (hasConflict, conflicts)
│   │   │   └── EnrichedReservationSummary.java
│   │   ├── exception/
│   │   │   ├── ReservationNotFoundException.java
│   │   │   ├── ExternalModule2UnavailableException.java
│   │   │   ├── InvalidReservationQueryException.java
│   │   │   └── DecommissionHorizonExceededException.java
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── IngestDailyReservationsUseCase.java
│   │       │   ├── SearchDailyReservationsUseCase.java
│   │       │   └── CheckRoomReservationConflictsUseCase.java
│   │       └── out/
│   │           ├── DailyReservationPersistencePort.java
│   │           ├── RoomPersistencePort.java
│   │           └── Module2ReservationConflictClientPort.java # Puerto desacoplado (pendiente de confirmar con Módulo 2)
│   ├── application/
│   │   ├── service/
│   │   │   ├── DailyReservationIngestionService.java
│   │   │   ├── DailyReservationSearchService.java
│   │   │   └── RoomReservationConflictService.java
│   │   └── dto/
│   │       ├── DailyReservationListMessageDto.java
│   │       ├── DailyReservationUpdateMessageDto.java
│   │       ├── DailyReservationResponseDto.java
│   │       └── ReservationConflictResponseDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   ├── amqp/
│       │   │   │   └── DailyReservationRabbitListener.java # Listener m1.reservas.diarias.queue
│       │   │   └── web/
│       │   │       ├── DailyReservationLookupController.java # Endpoints locales para Recepción (/api/reservations/daily/...)
│       │   │       └── RoomMaintenanceReservationController.java # Endpoints Mantenimiento/Admin (/api/rooms/{id}/reservation-conflicts)
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── DailyReservationJpaEntity.java
│       │       │   ├── DailyReservationRoomJpaEntity.java
│       │       │   ├── DailyReservationMessageLogJpaEntity.java
│       │       │   └── DailyReservationPersistenceAdapter.java
│       │       └── rest/
│       │           ├── Module2ReservationConflictRestAdapter.java # Cliente REST (ruta y payload pendientes de confirmar con Módulo 2)
│       │           └── Module2Properties.java
frontend/src/
├── components/reservation/
│   ├── DailyReservationLookup.jsx
│   └── MultipleReservationsModal.jsx
├── components/maintenance/
│   ├── RoomDecommissionConflictChecker.jsx
│   └── MaintenanceConflictChecker.jsx
└── services/
    ├── dailyReservationService.js
    └── roomConflictService.js
```

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Configurar propiedades de integración de Módulo 2 en `application.yml`:
  ```yaml
  m2:
    reservations:
      base-url: "http://localhost:8082"
      connect-timeout-ms: 1000
      read-timeout-ms: 2000
  ```
- [ ] T002 Configurar beans de RabbitMQ en `RabbitConfig.java`: Queue durable `m1.reservas.diarias.queue`, Topic Exchange `hospitua.events`, y bindings con routing keys `reserva.lista-del-dia` y `reserva.lista-del-dia.actualizacion`.
- [ ] T003 Configurar `RestClient` con timeouts estrictos (1 s conexión, 2 s lectura) y mapeo de errores HTTP 4xx/5xx en `Module2ReservationConflictRestAdapter.java` (ruta y payload pendientes de confirmar con Módulo 2).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T004 Crear migración Flyway `V2__create_daily_reservation_tables.sql` con las tablas:
  - `daily_reservation`: `reservation_ref` (PK VARCHAR(50)), `guest_first_name`, `guest_last_name`, `guest_document_type` (pendiente de confirmar con Módulo 2), `guest_document_number`, `guest_nationality`, `source`, `start_date`, `end_date`, `guest_count`, `updated_at`. (Sin campo `nights`).
  - `daily_reservation_room`: `reservation_ref` (FK), `room_id` (UUID), `room_number`, `category_room`, `guest_count` (pendiente de confirmar con Módulo 2) (PK compuesta `reservation_ref, room_id`).
  - `daily_reservation_message_log`: `message_id` (PK VARCHAR(100)), `sequence_number` BIGINT, `message_type` VARCHAR(50), `received_at` TIMESTAMP.
- [ ] T005 Implementar entidades JPA `DailyReservationJpaEntity`, `DailyReservationRoomJpaEntity` y `DailyReservationMessageLogJpaEntity`.
- [ ] T006 Implementar modelos de dominio `DailyReservation`, `DailyReservationRoom`, `ReservationConflictQuery` y `ReservationConflictResult`.
- [ ] T007 Implementar interfaces de puertos: `IngestDailyReservationsUseCase`, `SearchDailyReservationsUseCase`, `CheckRoomReservationConflictsUseCase`, `DailyReservationPersistencePort`, `Module2ReservationConflictClientPort` (puerto desacoplado; ruta y payload pendientes de confirmar con Módulo 2).
- [ ] T008 Registrar excepciones RFC 7807 (`ApiError`) en `GlobalExceptionHandler`: `ReservationNotFoundException`, `ExternalModule2UnavailableException`, `DecommissionHorizonExceededException`.

---

## Phase 3: User Story 1 - Ingesta asíncrona de lista diaria y actualizaciones por cola (Priority: P1)

**Goal**: Mantener la copia local de llegadas del día sincronizada a través de `m1.reservas.diarias.queue` a las 00:00 y con actualizaciones continuas, ejecutando transiciones atómicas automáticas en el inventario de habitaciones.

**Independent Test**: Publicar un mensaje `DailyReservationList` simulado a las 00:00 por `m1.reservas.diarias.queue` (`reserva.lista-del-dia`), verificar la purga de registros anteriores, persistencia en `daily_reservation` y `daily_reservation_room`, descarte de duplicados por `messageId`, transiciones automáticas `Available → Reserved` (con `reserved_by_reservation_ref`) y liberaciones a `Available` en `REMOVED`.

### Tests for User Story 1

- [ ] T009 [P] [US1] Unit test en `DailyReservationIngestionServiceTest`:
  - Descarte inmediato si `messageId` ya existe en `daily_reservation_message_log`.
  - Purgar copia local al recibir lista de las 00:00 (`sequenceNumber = 1`). Liberar habitaciones huérfanas antes de apartar las nuevas.
  - Transición automática a `Reserved` para habitaciones incluidas en la lista o en `ADDED`.
  - Procesamiento de actualización `UPDATED`: aplica cambios solo si `message.updatedAt > local.updatedAt`; descarta en caso contrario. Si cambia la habitación asignada, libera la anterior y aparta la nueva.
  - Procesamiento de actualización `REMOVED`: elimina la reserva y habitaciones de la copia local, y transiciona cada habitación de `Reserved` a `Available`.
- [ ] T010 [P] [US1] Integration test con Testcontainers RabbitMQ + PostgreSQL: Ingesta de lista diaria y mensaje duplicado, verificando idempotencia, transiciones en `Room` y logs.

### Implementation for User Story 1

- [ ] T011 [P] [US1] Implementar `DailyReservationPersistenceAdapter` para operaciones CRUD y purga atómica de la copia local.
- [ ] T012 [P] [US1] Implementar `DailyReservationIngestionService` con la lógica de negocio de ingesta, deduplicación, control de `sequenceNumber` y transiciones automáticas de habitaciones (`Available ↔ Reserved`), ejecutando primero las liberaciones a las 00:00.
- [ ] T013 [P] [US1] Implementar job programado para el corte diario de las 23:59: transicionar a `Available` toda habitación en `Reserved` sin Check-In registrado.
- [ ] T014 [P] [US1] Implementar `DailyReservationRabbitListener` consumiendo de `m1.reservas.diarias.queue` con las routing keys `reserva.lista-del-dia` y `reserva.lista-del-dia.actualizacion`.

---

## Phase 4: User Story 2 - Consulta y búsqueda en copia local por la Recepcionista (Priority: P1)

**Goal**: Proveer a la Recepcionista consultas y búsquedas de llegadas en mostrador (por código, documento o nombre del titular) **100% locales** sin peticiones HTTP externas.

**Independent Test**: Invocar la búsqueda por código o documento en la API local `GET /api/reservations/daily?query={term}` y verificar que retorna datos consolidados con el estado físico de la habitación (`Room.status = Reserved`) en < 50 ms sin invocar llamadas REST a Módulo 2.

### Tests for User Story 2

- [ ] T015 [P] [US2] Unit test en `DailyReservationSearchServiceTest`:
  - Búsqueda por `reservationRef` exacto.
  - Búsqueda por documento del titular.
  - Búsqueda por coincidencia de nombre/apellido (case-insensitive, trim).
  - Enriquecimiento con estado físico de `Room` local (`Room.status`).
  - Cálculo dinámico de noches (`endDate - startDate`) sin campo persistido `nights`.
  - Retorno de múltiples coincidencias cuando aplique.
- [ ] T016 [P] [US2] Component test frontend en `DailyReservationLookup.jsx` y `MultipleReservationsModal.jsx`: bloqueo de flujo hasta selección explícita si hay más de 1 coincidencia; visualización de "No hay reservas que mostrar." ante búsqueda vacía.

### Implementation for User Story 2

- [ ] T017 [P] [US2] Implementar consultas de búsqueda optimizadas en `DailyReservationRepository` (búsqueda por referencia, documento y nombre).
- [ ] T018 [P] [US2] Implementar `DailyReservationSearchService` enriqueciendo cada habitación de la reserva con `RoomPersistencePort` (estado `Reserved`).
- [ ] T019 [P] [US2] Exponer endpoints locales en `DailyReservationLookupController.java`:
  - `GET /api/reservations/daily`: Listado completo de llegadas de hoy.
  - `GET /api/reservations/daily/search?q={query}`: Búsqueda reactiva por código, documento o nombre.
- [ ] T020 [P] [US2] Implementar componentes de UI `DailyReservationLookup.jsx` y `MultipleReservationsModal.jsx` consumiendo `dailyReservationService.js`.

---

## Phase 5: User Story 3 - Consulta REST a Módulo 2 para Mantenimiento y Dar de Baja Habitación (Priority: P2)

**Goal**: Permitir al Personal de mantenimiento y al Administrador verificar síncronamente con Módulo 2 si un rango de fechas se solapa con reservas de una habitación física específica antes de programar un bloqueo técnico o darla de baja (consulta pendiente de confirmar con Módulo 2).

**Especificaciones Técnicas**:
1. **Parámetros de Entrada**:
   - `roomId` (UUID de la habitación en M1)
   - `startDate` (formato ISO `YYYY-MM-DD`, sin horas)
   - `endDate` (formato ISO `YYYY-MM-DD`, sin horas)
2. **Restricción de Cardinalidad**:
   - Exactamente **una sola habitación por petición**. Si se requieren verificar múltiples habitaciones, el llamador invoca la consulta de forma individual por cada una.
3. **Reglas de Negocio Específicas**:
   - **Bloqueo Técnico (Mantenimiento)**:
     - `startDate` = fecha de inicio de las obras/mantenimiento.
     - `endDate` = fecha estimada de finalización del mantenimiento.
   - **Dar de Baja Habitación (Administrador)**:
     - `startDate` = fecha actual (`LocalDate.now()`).
     - `endDate` = fecha límite dentro de 30 días (`LocalDate.now().plusDays(30)`).
     - Validación estricta: Si `endDate > LocalDate.now().plusDays(30)`, el sistema rechaza la consulta localmente con `DecommissionHorizonExceededException` (HTTP 422 Unprocessable Entity) sin emitir la llamada de red.
4. **Puerto y Adaptador REST (pendiente de confirmar con Módulo 2)**:
   - Módulo 1 define el puerto desacoplado `Module2ReservationConflictClientPort` con los tres parámetros (`roomId`, `startDate`, `endDate`). La ruta final y la estructura del payload devuelto por Módulo 2 se confirmarán formalmente.
5. **Manejo de Resiliencia y Fallos**:
   - Connect timeout: 1000 ms. Read timeout: 2000 ms.
   - Si Módulo 2 responde con error 5xx, timeout o caída de conexión, se captura la excepción y se lanza `ExternalModule2UnavailableException` (mapeada a HTTP 503 con código `MODULE_2_UNAVAILABLE`), informando al usuario la imposibilidad de verificar reservas en ese momento sin generar un 500 no controlado.

### Tests for User Story 3

- [ ] T021 [P] [US3] Unit test en `RoomReservationConflictServiceTest`:
  - Validación de horizonte de 30 días para "Dar de baja" (`startDate = hoy`, `endDate <= hoy + 30 días`); rechazo si `endDate > hoy + 30 días`.
  - Validación de consulta para Bloqueo Técnico con `startDate` y `endDate` válidos (`startDate <= endDate`).
  - Mapeo de respuesta de conflictos.
  - Lanzamiento de `ExternalModule2UnavailableException` ante fallo o timeout del cliente REST.
- [ ] T022 [P] [US3] Integration test con `MockRestServiceServer` para `Module2ReservationConflictRestAdapter` validando propagación de excepciones.
- [ ] T023 [P] [US3] Component test frontend para `RoomDecommissionConflictChecker.jsx` y `MaintenanceConflictChecker.jsx`.

### Implementation for User Story 3

- [ ] T024 [P] [US3] Implementar puerto de salida `Module2ReservationConflictClientPort` con el método:
  `ReservationConflictResult checkConflicts(UUID roomId, LocalDate startDate, LocalDate endDate)` (ruta y payload pendientes de confirmar con Módulo 2).
- [ ] T025 [P] [US3] Implementar `Module2ReservationConflictRestAdapter` consumiendo el endpoint configurado mediante `RestClient` con headers y timeouts estrictos.
- [ ] T026 [P] [US3] Implementar puerto de entrada `CheckRoomReservationConflictsUseCase` y el servicio `RoomReservationConflictService`:
  - Método `checkMaintenanceConflict(UUID roomId, LocalDate startDate, LocalDate endDate)`
  - Método `checkDecommissionConflict(UUID roomId)` (calcula automáticamente `startDate = LocalDate.now()` y `endDate = LocalDate.now().plusDays(30)`).
- [ ] T027 [P] [US3] Implementar controlador REST interno en Módulo 1 `RoomMaintenanceReservationController.java`:
  - `GET /api/rooms/{roomId}/reservation-conflicts?startDate={start}&endDate={end}` (para mantenimiento)
  - `GET /api/rooms/{roomId}/decommission-conflicts` (para baja de habitación, validando horizonte de 30 días)
- [ ] T028 [P] [US3] Implementar componentes en frontend `roomConflictService.js`, `RoomDecommissionConflictChecker.jsx` y `MaintenanceConflictChecker.jsx`.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T029 Sanitización de entradas de texto en búsquedas locales: trim de espacios accidentales e insensibilidad a mayúsculas/minúsculas.
- [ ] T030 Validar que no se muestren banners residuales tipo "Reserva encontrada" en la UI.
- [ ] T031 Auditoría y observabilidad: Métricas de latencia en la llamada REST a Módulo 2 y log transaccional de ingesta de cola en `daily_reservation_message_log`.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Room` y `RoomPersistencePort` de [PLAN/base/plan.md](base/plan.md), más la creación de las tablas de `daily_reservation`.
- **Consumidores**:
  - `plan-consultar-panel-recepcion.md`: Invoca la búsqueda local de llegadas (`SearchDailyReservationsUseCase`).
  - `plan-registrar-check-in.md`: Invoca la validación local de reserva en el Paso 1.
  - Flujos de Mantenimiento y Baja de Habitación: Invocan `CheckRoomReservationConflictsUseCase` para validar solapamientos antes de transicionar estados físicos.
