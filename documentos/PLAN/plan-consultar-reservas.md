# Implementation Plan: Consultar Reservas

**Date**: 2026-10-09  
**Spec**: [spec-consultar-reservas.md](../SPEC/spec-consultar-reservas.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso técnico **Consultar Reservas**, que resuelve dos requerimientos operativos claramente diferenciados conforme a la arquitectura de integración de HOSPITUA:

1. **Modo Local (Recepción, Panel de Llegadas y Check-In)**:
   - **Ingesta Asíncrona por Cola**: Consumir de la cola `m1.reservas.diarias.queue` la lista diaria de llegadas a las 00:00 (routing key `reserva.lista-del-dia`, `sequenceNumber = 1`) y las actualizaciones continuas durante el día (routing key `reserva.lista-del-dia.actualizacion`, acciones `ADDED`, `UPDATED`, `REMOVED`).
   - **Almacenamiento Local**: Mantener la copia local en las tablas `daily_reservation`, `daily_reservation_room` y `daily_reservation_message_log` con control estricto de idempotencia por `messageId` y orden por `sequenceNumber` y `updatedAt`.
   - **Efecto en Inventario Físico (Transiciones Automáticas)**: Al persistir la lista diaria o una acción `ADDED`, el sistema transiciona automáticamente cada habitación a `Reserved` asignando `Room.reserved_by_reservation_ref`. Al recibir `REMOVED` o quitar una habitación en `UPDATED`, transiciona de `Reserved` a `Available`. A las 00:00, las liberaciones (incluidas las de reservas que ya no figuran en la nueva lista) se ejecutan antes de apartar las nuevas habitaciones. No hay corte local de las 23:59: el no-show lo decide Módulo 2 y llega como `REMOVED`.
   - **Búsqueda 100% Autónoma y Local**: La Recepcionista consulta el listado de llegadas y busca por código (`reservationRef`), número de documento o nombre del titular directamente sobre la copia local (invocado por `plan-consultar-panel-recepcion.md` y por el Paso 1 de `plan-registrar-check-in.md`), sin realizar ninguna petición HTTP/REST a Módulo 2.
   - **Sin campo `nights`**: Se elimina `nights` de todos los modelos y DTOs de persistencia/mensajería; el cálculo de noches se realiza dinámicamente en tiempo de ejecución (`endDate - startDate`).

2. **Modo REST (Mantenimiento y Administración - Bloqueo Técnico y Baja de Habitación)**:
   - **Consulta Directa REST a Módulo 2**: El Personal de mantenimiento y el Administrador consultan al Módulo 2 si un rango de fechas se solapa con reservas futuras de una habitación específica antes de intervenirla físicamente o retirarla de inventario.
   - **Parámetros Requeridos**: La petición envía la credencial de servicio de Módulo 1 y 3 parámetros: `dateFrom`, `dateTo` y `roomId`.
   - **Restricción**: Se consulta exactamente **una sola habitación por petición** (no por categoría ni por listas).
   - **Regla para Bloqueo Técnico**: `startDate` = fecha de inicio del mantenimiento, `endDate` = fecha estimada de finalización.
   - **Regla para Dar de Baja Habitación**: `startDate` = fecha actual (`LocalDate.now()`), `endDate` = `9999-12-31` (constante `NO_UPPER_LIMIT`: sin límite superior), para que cualquier reserva vigente desde hoy impida la baja. No afecta el rendimiento: Módulo 2 filtra por `roomId` (índice `room_id` y restricción anti-solape por habitación), así que el costo depende de las reservas vigentes de esa habitación y no del ancho del rango.
   - **Ruta REST (Módulo 2, *Consultar reservas* FR-023)**: `GET /api/reservations?dateFrom={startDate}&dateTo={endDate}&roomId={roomId}`. Módulo 1 implementa el puerto `Module2ReservationConflictClientPort` con `roomId`, `startDate` y `endDate`, que el adaptador envía como `roomId`, `dateFrom` y `dateTo`.
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
  - Mantenimiento / Administración: Consulta REST restringida a exactamente 1 habitación y 3 parámetros (`roomId`, `dateFrom`, `dateTo`).
  - Dar de Baja: consulta desde hoy y sin límite superior (`endDate` = `9999-12-31`).
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
│   │   │   └── InvalidReservationQueryException.java
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── IngestDailyReservationsUseCase.java
│   │       │   ├── SearchDailyReservationsUseCase.java
│   │       │   └── CheckRoomReservationConflictsUseCase.java
│   │       └── out/
│   │           ├── DailyReservationPersistencePort.java
│   │           ├── RoomPersistencePort.java
│   │           └── Module2ReservationConflictClientPort.java # Puerto hacia GET /api/reservations de Módulo 2
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
│       │   │       └── RoomMaintenanceReservationController.java # Endpoint de Administración (/api/rooms/{id}/decommission-conflicts)
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── DailyReservationJpaEntity.java
│       │       │   ├── DailyReservationRoomJpaEntity.java
│       │       │   ├── DailyReservationMessageLogJpaEntity.java
│       │       │   └── DailyReservationPersistenceAdapter.java
│       │       └── rest/
│       │           ├── Module2ReservationConflictRestAdapter.java # Cliente REST de GET /api/reservations
│       │           └── Module2Properties.java
frontend/src/
├── components/maintenance/
│   └── RoomDecommissionConflictChecker.jsx
└── services/
    └── roomConflictService.js
```

---

## Contratos de mensajería recibidos de Módulo 2

Estructuras confirmadas por Módulo 2 para la cola `m1.reservas.diarias.queue` (exchange `hospitua.events`). Se documentan los campos recibidos; Módulo 1 no persiste `status`, `notes` ni `guestRef` en la copia local (el orden se controla por `sequenceNumber` y `updatedAt`).

### Lista del día — `reserva.lista-del-dia` (00:00)

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `messageId` | UUID | Identificador único del mensaje; base del control de idempotencia |
| `sequenceNumber` | entero | Número de secuencia; `1` para la lista diaria inicial |
| `operationalDate` | fecha (YYYY-MM-DD) | Día operativo al que corresponde la lista |
| `generatedAt` | fecha-hora | Marca de generación del mensaje |
| `totalReservations` | entero | Total de reservas incluidas |
| `totalRooms` | entero | Total de habitaciones incluidas |
| `totalGuests` | entero | Total de huéspedes del día |
| `reservations` | array | Reservas incluidas (estado `ACTIVE` con `startDate = hoy`) |

**Campos de cada elemento de `reservations`:**

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `reservationRef` | string | Referencia de la reserva (clave de la copia local) |
| `status` | string | Estado de la reserva (`ACTIVE`) |
| `source` | string | Canal de origen (`DIRECTA` o nombre de la OTA) |
| `startDate` | fecha (YYYY-MM-DD) | Fecha de llegada |
| `endDate` | fecha (YYYY-MM-DD) | Fecha de salida |
| `guestCount` | entero | Cantidad de huéspedes de la reserva |
| `notes` | string | Observaciones operativas (no se persisten) |
| `updatedAt` | fecha-hora | Marca de la última actualización; gobierna el orden de aplicación |
| `rooms[]` | array | Habitaciones asignadas (de 1 a 10) |
| `guest` | objeto | Titular de la reserva |

**Campos de cada elemento de `rooms[]`:** `roomId` (UUID), `roomNumber` (string), `categoryRoom` (string) y `guestCount` (entero, cantidad de personas de esa habitación).

**Campos del objeto `guest` (titular):** `guestRef` (UUID, no se persiste), `firstName`, `lastName`, `documentType`, `documentNumber` y `nationality`.

### Actualización `ADDED` / `UPDATED` — `reserva.lista-del-dia.actualizacion`

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `messageId` | UUID | Identificador único del mensaje |
| `sequenceNumber` | entero | Número de secuencia estrictamente incremental |
| `operationalDate` | fecha (YYYY-MM-DD) | Día operativo |
| `updateType` | string | `ADDED` o `UPDATED` |
| `occurredAt` | fecha-hora | Momento en que ocurrió el cambio |
| `reservationRef` | string | Referencia de la reserva afectada |
| `reservation` | objeto | Objeto completo de la reserva con la misma estructura de la lista del día (`reservationRef`, `status`, `source`, `startDate`, `endDate`, `guestCount`, `notes`, `updatedAt`, `rooms[]` y `guest`) |

### Actualización `REMOVED` — `reserva.lista-del-dia.actualizacion`

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `messageId` | UUID | Identificador único del mensaje |
| `sequenceNumber` | entero | Número de secuencia estrictamente incremental |
| `operationalDate` | fecha (YYYY-MM-DD) | Día operativo |
| `updateType` | string | `REMOVED` |
| `occurredAt` | fecha-hora | Momento en que ocurrió la baja |
| `reservationRef` | string | Referencia de la reserva retirada de la copia local |
| `removalReason` | string | Motivo de la baja: `CANCELLED`, `DATE_CHANGED` o `NO_SHOW` |

El mensaje `REMOVED` no incluye el objeto `reservation`.

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
- [ ] T003 Configurar `RestClient` con timeouts estrictos (1 s conexión, 2 s lectura) y mapeo de errores HTTP 4xx/5xx en `Module2ReservationConflictRestAdapter.java`.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T004 Crear migración Flyway `V2__create_daily_reservation_tables.sql` con las tablas:
  - `daily_reservation`: `reservation_ref` (PK VARCHAR(50)), `guest_first_name`, `guest_last_name`, `guest_document_type`, `guest_document_number`, `guest_nationality`, `source`, `start_date`, `end_date`, `guest_count`, `updated_at`. (Sin campo `nights`).
  - `daily_reservation_room`: `reservation_ref` (FK), `room_id` (UUID), `room_number`, `category_room`, `guest_count` (PK compuesta `reservation_ref, room_id`).
  - `daily_reservation_message_log`: `message_id` (PK VARCHAR(100)), `sequence_number` BIGINT, `message_type` VARCHAR(50), `received_at` TIMESTAMP.
- [ ] T005 Implementar entidades JPA `DailyReservationJpaEntity`, `DailyReservationRoomJpaEntity` y `DailyReservationMessageLogJpaEntity`.
- [ ] T006 Implementar modelos de dominio `DailyReservation`, `DailyReservationRoom`, `ReservationConflictQuery` y `ReservationConflictResult`.
- [ ] T007 Implementar interfaces de puertos: `IngestDailyReservationsUseCase`, `SearchDailyReservationsUseCase`, `CheckRoomReservationConflictsUseCase`, `DailyReservationPersistencePort`, `Module2ReservationConflictClientPort`.
- [ ] T008 Registrar excepciones con formato `ApiError` en `GlobalExceptionHandler`: `ReservationNotFoundException`, `ExternalModule2UnavailableException`.

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
- [ ] T012 [P] [US1] Implementar `DailyReservationIngestionService` con la lógica de negocio de ingesta, deduplicación, control de `sequenceNumber` y transiciones automáticas de habitaciones (`Available ↔ Reserved`), ejecutando primero las liberaciones a las 00:00. Por cada habitación, bloquea su fila de `room` (`SELECT … FOR UPDATE`, regla 13 del plan base) antes de apartarla, liberarla o registrar el conflicto. Descarta, registrando su `messageId`, los mensajes cuyo `operationalDate` sea anterior al día de la copia local, y controla el orden de `sequenceNumber` solo dentro del mismo `operationalDate` (FR-003).
- [ ] T013 [P] [US1] Implementar en la ingesta de las 00:00 la liberación a `Available` (mediante `MarkRoomAvailableUseCase`) de cada habitación en `Reserved` cuya reserva no figura en la nueva lista, con la salvaguarda de `reservedByReservationRef`. No se implementa un corte local de las 23:59: el no-show lo decide Módulo 2 y llega como `REMOVED`.
- [ ] T014 [P] [US1] Implementar `DailyReservationRabbitListener` consumiendo de `m1.reservas.diarias.queue` con las routing keys `reserva.lista-del-dia` y `reserva.lista-del-dia.actualizacion`.

---

## Phase 4: User Story 2 - Consulta y búsqueda en copia local por la Recepcionista (Priority: P1)

**Goal**: Proveer a la Recepcionista consultas y búsquedas de llegadas en mostrador (por código, documento o nombre del titular) **100% locales** sin peticiones HTTP externas.

**Independent Test**: Invocar la búsqueda por código o documento mediante `SearchDailyReservationsUseCase` (expuesta a la Recepcionista por `GET /api/reception/arrivals?query={term}` de `plan-consultar-panel-recepcion.md`) y verificar que retorna datos consolidados con el estado físico de la habitación (`Room.status = Reserved`) en < 50 ms sin invocar llamadas REST a Módulo 2.

### Tests for User Story 2

- [ ] T015 [P] [US2] Unit test en `DailyReservationSearchServiceTest`:
  - Búsqueda por `reservationRef` exacto.
  - Búsqueda por documento del titular.
  - Búsqueda por coincidencia de nombre/apellido (case-insensitive, trim).
  - Enriquecimiento con estado físico de `Room` local (`Room.status`).
  - Cálculo dinámico de noches (`endDate - startDate`) sin campo persistido `nights`.
  - Retorno de múltiples coincidencias cuando aplique.
- [ ] T016 [P] [US2] Integration test de `SearchDailyReservationsUseCase`: con varias coincidencias devuelve todas (la Recepcionista elige una en la tabla de Llegadas del panel) y sin coincidencias devuelve una lista vacía (HU-2, escenarios 3 y 4).

### Implementation for User Story 2

- [ ] T017 [P] [US2] Implementar consultas de búsqueda optimizadas en `DailyReservationRepository` (búsqueda por referencia, documento y nombre).
- [ ] T018 [P] [US2] Implementar `DailyReservationSearchService` enriqueciendo cada habitación de la reserva con `RoomPersistencePort` (estado `Reserved`).
- [ ] T019 [P] [US2] Exponer `SearchDailyReservationsUseCase` como puerto de entrada para `ReceptionPanelController` (`GET /api/reception/arrivals?query={q}`, `plan-consultar-panel-recepcion.md`). Este plan no expone endpoints propios para la búsqueda, para que exista una sola ruta de consulta de llegadas.
- [ ] T020 [P] [US2] Sin componentes propios: la interfaz de búsqueda y listado de llegadas es la pestaña Llegadas del panel (`ArrivalsTab.jsx` y `ReceptionSearchBar.jsx`, `plan-consultar-panel-recepcion.md` T013).

---

## Phase 5: User Story 3 - Consulta REST a Módulo 2 para Mantenimiento y Dar de Baja Habitación (Priority: P2)

**Goal**: Permitir al Personal de mantenimiento y al Administrador verificar síncronamente con Módulo 2 si un rango de fechas se solapa con reservas de una habitación física específica antes de programar un bloqueo técnico o darla de baja.

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
     - `endDate` = `9999-12-31` (`NO_UPPER_LIMIT`): sin límite superior, cualquier reserva vigente desde hoy cuenta.
4. **Puerto y Adaptador REST** (*Consultar reservas* FR-023 de Módulo 2, con la credencial de servicio de Módulo 1):
   - Ruta: `GET /api/reservations?dateFrom={startDate}&dateTo={endDate}&roomId={roomId}`. El puerto `Module2ReservationConflictClientPort` usa `startDate`/`endDate`; el adaptador los envía como `dateFrom`/`dateTo`, ambos inclusivos.
   - Respuesta 200 OK: `items[]` con las reservas vigentes (`PENDING`, `ACTIVE` o `IN_PROGRESS`) de la habitación cuya estadía se cruza con el rango (Módulo 2 aplica `startDate` ≤ `dateTo` y `endDate` > `dateFrom`). Cada reserva devuelta es un conflicto; una lista vacía significa que la operación es segura. El servicio arma `ReservationConflictResult` con `hasConflict = items no vacío` y las reservas recibidas, sin reevaluar estados ni fechas.
5. **Manejo de Resiliencia y Fallos**:
   - Connect timeout: 1000 ms. Read timeout: 2000 ms.
   - Si Módulo 2 responde con un error (400 o 5xx), timeout o caída de conexión, se captura la excepción y se lanza `ExternalModule2UnavailableException` (mapeada a HTTP 503 con código `MODULE_2_UNAVAILABLE`), informando al usuario la imposibilidad de verificar reservas en ese momento sin generar un 500 no controlado.

### Tests for User Story 3

- [ ] T021 [P] [US3] Unit test en `RoomReservationConflictServiceTest`:
  - Consulta de "Dar de baja" con `startDate = hoy` y `endDate = 9999-12-31`.
  - Validación de consulta para Bloqueo Técnico con `startDate` y `endDate` válidos (`startDate <= endDate`).
  - Mapeo de `items` vacío (`hasConflict = false`) y de `items` con reservas (`hasConflict = true`, con cada reserva devuelta).
  - Lanzamiento de `ExternalModule2UnavailableException` ante fallo o timeout del cliente REST.
- [ ] T022 [P] [US3] Integration test con `MockRestServiceServer` para `Module2ReservationConflictRestAdapter`: llamada a `GET /api/reservations?dateFrom={startDate}&dateTo={endDate}&roomId={roomId}` con respuesta 200 OK con reservas y propagación de `ExternalModule2UnavailableException` ante timeout o error.
- [ ] T023 [P] [US3] Component test frontend para `RoomDecommissionConflictChecker.jsx`.

### Implementation for User Story 3

- [ ] T024 [P] [US3] Implementar puerto de salida `Module2ReservationConflictClientPort` con el método:
  `ReservationConflictResult checkConflicts(UUID roomId, LocalDate startDate, LocalDate endDate)`.
- [ ] T025 [P] [US3] Implementar `Module2ReservationConflictRestAdapter` consumiendo `GET /api/reservations?dateFrom={startDate}&dateTo={endDate}&roomId={roomId}` mediante `RestClient` con headers y timeouts estrictos.
- [ ] T026 [P] [US3] Implementar puerto de entrada `CheckRoomReservationConflictsUseCase` y el servicio `RoomReservationConflictService`:
  - Método `checkMaintenanceConflict(UUID roomId, LocalDate startDate, LocalDate endDate)`
  - Método `checkDecommissionConflict(UUID roomId)` (calcula automáticamente `startDate = LocalDate.now()` y `endDate = 9999-12-31`).
- [ ] T027 [P] [US3] Implementar controlador REST interno en Módulo 1 `RoomMaintenanceReservationController.java`:
  - `GET /api/rooms/{roomId}/decommission-conflicts` (para baja de habitación, desde hoy y sin límite superior)
- [ ] T028 [P] [US3] Implementar componentes en frontend `roomConflictService.js` y `RoomDecommissionConflictChecker.jsx`. *Programar bloqueo técnico* no tiene verificación previa propia: `POST /api/rooms/{roomId}/technical-blocks` consulta las reservas con `checkMaintenanceConflict` y responde `RESERVATION_CONFLICT`.

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
