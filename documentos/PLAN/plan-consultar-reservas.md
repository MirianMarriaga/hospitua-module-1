# Implementation Plan: Consultar Reservas

**Date**: 2026-10-09  
**Spec**: [spec-consultar-reservas.md](../SPEC/spec-consultar-reservas.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso técnico **Consultar Reservas**, que resuelve dos requerimientos operativos claramente diferenciados conforme a la arquitectura de integración de HOSPITUA:

1. **Modo Local (Recepción, Panel de Llegadas y Check-In)**:
   - **Ingesta Asíncrona por Cola**: Consumir de la cola `m1.reservas.diarias.queue` la lista diaria de llegadas a las 00:00 (routing key `reserva.lista-del-dia`, `sequenceNumber = 1`) y las actualizaciones continuas durante el día (routing key `reserva.lista-del-dia.actualizacion`, acciones `ADDED`, `UPDATED`, `REMOVED`).
   - **Almacenamiento Local**: Mantener la copia local en las tablas `daily_reservation`, `daily_reservation_room` y `daily_reservation_message_log` con control estricto de idempotencia por `messageId` y orden por `sequenceNumber`: ante varios cambios de la misma reserva se conserva el de mayor secuencia.
   - **Efecto en Inventario Físico (FR-009)**: La ingesta no transiciona las habitaciones por su cuenta. Al persistir la lista diaria o una acción `ADDED` con `startDate` = hoy, aparta cada habitación con `MarkRoomReservedUseCase` (`ReservationTrigger` `DAILY_LIST` o `ADDED_UPDATE`, `plan-marcar-habitacion-reservada.md`), que asigna `Room.reserved_by_reservation_ref` y registra los conflictos. Al recibir `REMOVED` o quitar una habitación en `UPDATED`, la libera con `MarkRoomAvailableUseCase` (`plan-marcar-habitacion-como-disponible.md`) solo si está `Reserved` por esa reserva; si no, solo actualiza la copia local. A las 00:00, las liberaciones (incluidas las de reservas que ya no figuran en la nueva lista) se ejecutan antes de apartar las nuevas habitaciones. No hay corte local de las 23:59: el no-show lo decide Módulo 2 y llega como `REMOVED`.
   - **Búsqueda 100% Autónoma y Local**: La Recepcionista consulta el listado de llegadas y busca por código (`reservationRef`), número de documento o nombre del titular directamente sobre la copia local (invocado por `plan-consultar-panel-recepcion.md` y por el Paso 1 de `plan-registrar-check-in.md`), sin realizar ninguna petición HTTP/REST a Módulo 2.
   - **Sin campo `nights`**: Se elimina `nights` de todos los modelos y DTOs de persistencia/mensajería; el cálculo de noches se realiza dinámicamente en tiempo de ejecución (`endDate - startDate`).

2. **Modo REST (Mantenimiento y Administración - Bloqueo Técnico y Baja de Habitación)**:
   - **Consulta Directa REST a Módulo 2**: El Personal de mantenimiento y el Administrador consultan al Módulo 2 si un rango de fechas se solapa con reservas futuras de una habitación específica antes de intervenirla físicamente o retirarla de inventario.
   - **Parámetros Requeridos**: La petición envía 3 parámetros: `dateFrom`, `dateTo` y `roomId`.
   - **Restricción**: Se consulta exactamente **una sola habitación por petición** (no por categoría ni por listas).
   - **Regla para Bloqueo Técnico**: `startDate` = fecha de inicio del mantenimiento, `endDate` = fecha estimada de finalización.
   - **Regla para Dar de Baja Habitación**: `startDate` = fecha actual (`LocalDate.now(clock)`, regla 6 del plan base), `endDate` = fecha actual + 365 días (constante `DECOMMISSION_HORIZON_DAYS = 365`): 366 días contando hoy, el rango máximo que acepta Módulo 2 en su consulta C5 (FR-023); un rango mayor responde `400 DATE_RANGE_TOO_LARGE`.
   - **Ruta REST (Módulo 2, *Consultar reservas* FR-023)**: `GET /api/reservations?dateFrom={startDate}&dateTo={endDate}&roomId={roomId}`. Módulo 1 implementa el puerto `Module2ReservationConflictClientPort` con `roomId`, `startDate` y `endDate`, que el adaptador envía como `roomId`, `dateFrom` y `dateTo`.
   - **Resiliencia y Manejo de Errores**: Timeouts estrictos (conexión 1 s, lectura 2 s), traduciendo caídas, latencias o errores 4xx/5xx de Módulo 2 en excepciones controladas (`ExternalModule2UnavailableException`) para informar al usuario sin generar fallos 500 no capturados.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 4.1+, Spring Web MVC, Spring Data JPA, Spring AMQP (RabbitMQ), Lombok, Resilience4j, RestClient
- **Storage**: PostgreSQL 16+ (tablas `daily_reservation`, `daily_reservation_room`, `daily_reservation_message_log`, `room`)
- **Messaging**: RabbitMQ 3.13+ (Exchange topic `hospitua.events`, cola durable `m1.reservas.diarias.queue`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL, RabbitMQ), MockRestServiceServer / WireMock
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / TypeScript, TSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Búsqueda local de llegadas en mostrador < 50 ms p95 (100% sobre base de datos local).
  - Consulta REST a Módulo 2 para mantenimiento/baja < 1.5 s p95 con timeout estricto a los 2 s.
- **Constraints**:
  - Mostrador / Recepción / Check-In: **Cero llamadas HTTP/REST a Módulo 2**.
  - Mantenimiento / Administración: Consulta REST restringida a exactamente 1 habitación y 3 parámetros (`roomId`, `dateFrom`, `dateTo`).
  - Dar de Baja: consulta desde hoy hasta hoy + 365 días (máximo de 366 días de Módulo 2).
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
│   │   │   ├── ArrivalGroup.java                     # Reserva del día con pendingRooms, totalRooms y rooms (ArrivalItemSummary) (T020)
│   │   │   └── EnrichedReservationSummary.java   # Sin uso asignado
│   │   └── exception/
│   │       ├── ReservationNotFoundException.java
│   │       ├── ReservationConflictException.java   # 409 RESERVATION_CONFLICT (T029)
│   │       └── ExternalModule2UnavailableException.java   # También para un 400 de Módulo 2; un rango invertido es error de programación
│   ├── application/
│   │   ├── ports/
│   │   │   ├── in/
│   │   │   │   ├── IngestDailyReservationsUseCase.java
│   │   │   │   ├── SearchDailyReservationsUseCase.java
│   │   │   │   └── CheckRoomReservationConflictsUseCase.java
│   │   │   └── out/
│   │   │       ├── DailyReservationRepositoryPort.java
│   │   │       ├── RoomRepositoryPort.java
│   │   │       └── Module2ReservationConflictClientPort.java # Puerto hacia GET /api/reservations de Módulo 2
│   │   ├── service/
│   │   │   ├── DailyReservationIngestionService.java
│   │   │   ├── DailyReservationSearchService.java
│   │   │   └── RoomReservationConflictService.java
│   │   └── dto/
│   │       ├── DailyReservationListMessageDto.java
│   │       ├── DailyReservationUpdateMessageDto.java
│   │       ├── DailyReservationResponseDto.java   # Sin uso asignado
│   │       └── ReservationConflictResponseDto.java   # Sin uso asignado
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   └── amqp/
│       │   │       └── DailyReservationRabbitListener.java # Listener m1.reservas.diarias.queue
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── entity/
│       │       │   │   ├── DailyReservationJpaEntity.java
│       │       │   │   ├── DailyReservationRoomJpaEntity.java
│       │       │   │   └── DailyReservationMessageLogJpaEntity.java
│       │       │   ├── repository/
│       │       │   │   └── DailyReservationRepository.java # Búsquedas por referencia, documento y nombre (T018)
│       │       │   └── adapter/
│       │       │       └── DailyReservationRepositoryAdapter.java
│       │       └── rest/
│       │           ├── Module2ReservationConflictRestAdapter.java # Cliente REST de GET /api/reservations
│       │           └── Module2Properties.java
└── main/resources/db/migration/
    └── (sin migración propia: las tablas de la copia local se crean en `V1__init_schema.sql` del plan base)
frontend/src/
└── (sin componentes propios: la búsqueda de llegadas es del panel de recepción y la verificación de reservas ocurre dentro de la baja y del bloqueo técnico)
```

---

## Contratos de mensajería recibidos de Módulo 2

Estructuras confirmadas por Módulo 2 para la cola `m1.reservas.diarias.queue` (exchange `hospitua.events`). Se documentan los campos recibidos; Módulo 1 no persiste `status`, `notes` ni `guestRef` en la copia local (el orden se controla por `sequenceNumber`: ante varios cambios de la misma reserva se conserva el de mayor secuencia, como garantiza Módulo 1 al consumir).

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
| `updatedAt` | fecha-hora | Marca de la última actualización en Módulo 2; se guarda como dato informativo y no decide el orden (lo decide `sequenceNumber`) |
| `rooms[]` | array | Habitaciones asignadas (de 1 a 10) |
| `guest` | objeto | Titular de la reserva |

**Campos de cada elemento de `rooms[]`:** `roomId` (UUID), `roomNumber` (string), `categoryRoom` (string) y `guestCount` (entero, cantidad de personas de esa habitación).

**Campos del objeto `guest` (titular):** `guestRef` (UUID, no se persiste), `firstName`, `lastName`, `documentType`, `documentNumber` y `nationality`.

**Ejemplo:**

```json
{
  "messageId": "UUIDv4",
  "sequenceNumber": 1,
  "operationalDate": "2026-10-09",
  "generatedAt": "2026-10-09T00:00:02-05:00",
  "totalReservations": 1,
  "totalRooms": 2,
  "totalGuests": 3,
  "reservations": [
    {
      "reservationRef": "RSV-3F9A1C7B",
      "status": "ACTIVE",
      "source": "BOOKING",
      "startDate": "2026-10-09",
      "endDate": "2026-10-12",
      "guestCount": 3,
      "notes": "Llegada tarde",
      "updatedAt": "2026-10-08T15:42:10.123456-05:00",
      "rooms": [
        { "roomId": "uuid-1", "roomNumber": "201", "categoryRoom": "DOBLE", "guestCount": 2 },
        { "roomId": "uuid-2", "roomNumber": "202", "categoryRoom": "SENCILLA", "guestCount": 1 }
      ],
      "guest": {
        "guestRef": "uuid",
        "firstName": "Ana",
        "lastName": "Pérez",
        "documentType": "CC",
        "documentNumber": "123456",
        "nationality": "Colombia"
      }
    }
  ]
}
```

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

**Ejemplo:**

```json
{
  "messageId": "UUIDv4",
  "sequenceNumber": 2,
  "operationalDate": "2026-10-09",
  "updateType": "UPDATED",
  "occurredAt": "2026-10-09T09:15:00-05:00",
  "reservationRef": "RSV-3F9A1C7B",
  "reservation": {
    "reservationRef": "RSV-3F9A1C7B",
    "status": "ACTIVE",
    "source": "BOOKING",
    "startDate": "2026-10-09",
    "endDate": "2026-10-13",
    "guestCount": 3,
    "notes": "Llegada tarde",
    "updatedAt": "2026-10-09T09:15:00.000000-05:00",
    "rooms": [
      { "roomId": "uuid-1", "roomNumber": "201", "categoryRoom": "DOBLE", "guestCount": 2 },
      { "roomId": "uuid-2", "roomNumber": "202", "categoryRoom": "SENCILLA", "guestCount": 1 }
    ],
    "guest": {
      "guestRef": "uuid",
      "firstName": "Ana",
      "lastName": "Pérez",
      "documentType": "CC",
      "documentNumber": "123456",
      "nationality": "Colombia"
    }
  }
}
```

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

**Ejemplo:**

```json
{
  "messageId": "UUIDv4",
  "sequenceNumber": 3,
  "operationalDate": "2026-10-09",
  "updateType": "REMOVED",
  "occurredAt": "2026-10-09T23:59:00-05:00",
  "reservationRef": "RSV-8D02E5A4",
  "removalReason": "NO_SHOW"
}
```

---

## Phase 1: Setup (Shared Infrastructure)

- [x] T001 Verificar las propiedades de integración de Módulo 2 en `application.yml` (`Module2Properties`, plan base T013):

  ```yaml
  hospitua:
    module2:
      base-url: ${MODULE2_BASE_URL:http://localhost:8082}
      connect-timeout: 1s
      read-timeout: 2s
  ```

- [x] T002 Verificar en `RabbitConfig.java` (plan base, T011) la cola durable `m1.reservas.diarias.queue`, el exchange topic `hospitua.events`, los bindings `reserva.lista-del-dia` y `reserva.lista-del-dia.actualizacion` y la cola de mensajes fallidos `m1.reservas.diarias.dlq`.
  - Hecho: lo cubre `RabbitTopologyIntegrationTest` (L07, R3).
- [x] T003 Crear el `RestClient` hacia Módulo 2 con los timeouts de `RestClientConfig` (plan base, T013: 1 s conexión, 2 s lectura) y el mapeo de errores HTTP 4xx/5xx que usa `Module2ReservationConflictRestAdapter.java` (T026).
  - Hecho: `Module2ReservationConflictRestAdapter` usa el bean `module2RestClient` de `RestClientConfig`; cualquier 4xx, 5xx, timeout, caída o respuesta ilegible termina en `ExternalModule2UnavailableException` (L24).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [x] T004 Verificar que la migración `V1__init_schema.sql` del plan base (T007) cree las tablas de la copia local con estas columnas (este plan no tiene migración propia):
  - `daily_reservation`: `reservation_ref` (PK VARCHAR(50)), `guest_first_name`, `guest_last_name`, `guest_document_type`, `guest_document_number`, `guest_nationality`, `source`, `start_date`, `end_date`, `guest_count`, `updated_at`. (Sin campo `nights`).
  - `daily_reservation_room`: `reservation_ref` (FK a `daily_reservation`), `room_id` (UUID, sin FK a `room`: una habitación desconocida se salta con `ROOM_NOT_FOUND` sin revertir la ingesta), `room_number`, `category_room`, `guest_count` (PK compuesta `reservation_ref, room_id`).
  - `daily_reservation_message_log`: `message_id` (PK VARCHAR(100)), `sequence_number` BIGINT, `operational_date` DATE NOT NULL, `message_type` VARCHAR(50), `reservation_ref` VARCHAR(50) nullable (la reserva de cada actualización), `received_at` TIMESTAMP, con índice `(operational_date, reservation_ref, sequence_number)` para la última secuencia aplicada de cada reserva y con índice `(operational_date, message_type)`.
- [x] T005 Implementar entidades JPA `DailyReservationJpaEntity`, `DailyReservationRoomJpaEntity` y `DailyReservationMessageLogJpaEntity`.
- [x] T006 Implementar modelos de dominio `DailyReservation`, `DailyReservationRoom`, `ReservationConflictQuery`, `ReservationConflictResult` y `ArrivalGroup` (sus `rooms` son `ArrivalItemSummary`, `plan-consultar-panel-recepcion.md`).
  - Avance: hechos `DailyReservation` y `DailyReservationRoom` (L05). Hechos `ReservationConflictQuery` y `ReservationConflictResult` (L24). Hecho `ArrivalGroup` (L08).
- [x] T007 Implementar interfaces de puertos: `IngestDailyReservationsUseCase`, `SearchDailyReservationsUseCase`, `CheckRoomReservationConflictsUseCase`, `DailyReservationRepositoryPort`, `Module2ReservationConflictClientPort`. `DailyReservationRepositoryPort` incluye `Optional<String> findPendingArrivalReservationRef(UUID roomId, LocalDate operationalDate)`: la reserva del día (`start_date = operationalDate`) asignada a la habitación que todavía no tiene una `Stay` para esa habitación (el mismo criterio de "pendiente" que `pendingRooms`, T020); la usa `plan-confirmar-fin-de-limpieza.md`.
  - Avance: hechos `DailyReservationRepositoryPort` (L05) e `IngestDailyReservationsUseCase` (L06). Hechos `CheckRoomReservationConflictsUseCase` y `Module2ReservationConflictClientPort` (L24). Hecho `SearchDailyReservationsUseCase` (L08).
- [x] T008 Registrar excepciones con formato `ApiError` en `GlobalExceptionHandler`: `ReservationNotFoundException`, `ExternalModule2UnavailableException`.
  - Avance: hecha `ExternalModule2UnavailableException` (424 `MODULE_2_UNAVAILABLE`, categoría `DEPENDENCY_FAILED` de `DomainError`; L24). Hecha `ReservationNotFoundException` (404 `RESERVATION_NOT_FOUND`) con su primer consumidor, el Paso 1 del Check-In (L11).

---

## Phase 3: User Story 1 - Ingesta asíncrona de lista diaria y actualizaciones por cola (Priority: P1)

**Goal**: Mantener la copia local de llegadas del día sincronizada a través de `m1.reservas.diarias.queue` a las 00:00 y con actualizaciones continuas, apartando y liberando las habitaciones con los puertos `MarkRoomReservedUseCase` y `MarkRoomAvailableUseCase` en la misma transacción.

**Independent Test**: Publicar un mensaje `DailyReservationList` simulado a las 00:00 por `m1.reservas.diarias.queue` (`reserva.lista-del-dia`), verificar la purga de registros anteriores, persistencia en `daily_reservation` y `daily_reservation_room`, descarte de duplicados por `messageId`, apartados `Available → Reserved` por `MarkRoomReservedUseCase` (con `reserved_by_reservation_ref`) y liberaciones a `Available` por `MarkRoomAvailableUseCase` en `REMOVED` solo de habitaciones `Reserved` por esa reserva.

### Tests for User Story 1

- [x] T009 [P] [US1] Unit test en `DailyReservationIngestionServiceTest`:
  - Descarte inmediato si `messageId` ya existe en `daily_reservation_message_log`.
  - Purgar copia local al recibir lista de las 00:00 (`sequenceNumber = 1`). Liberar habitaciones huérfanas con `MarkRoomAvailableUseCase` antes de apartar las nuevas.
  - Invocación de `MarkRoomReservedUseCase` (`DAILY_LIST` o `ADDED_UPDATE`) por cada habitación de la lista o de un `ADDED` con `startDate` = hoy; ante `CONFLICT` o `ROOM_NOT_FOUND` continúa con la siguiente.
  - Orden por reserva: un `ADDED`, `UPDATED` o `REMOVED` cuya secuencia sea menor o igual que la última aplicada para esa `reservationRef` en el mismo `operationalDate` se descarta (registra su `messageId` y no cambia la copia local); `updatedAt` no interviene. Incluye dos `UPDATED` de la misma reserva que llegan en desorden: queda el de mayor secuencia.
  - Procesamiento de actualización `UPDATED`: actualiza la reserva y sus habitaciones. Si cambia la habitación asignada, libera la anterior con `MarkRoomAvailableUseCase` solo si está `Reserved` por esa reserva, y aparta la nueva con `MarkRoomReservedUseCase`.
  - Procesamiento de actualización `REMOVED`: elimina la reserva y habitaciones de la copia local, y libera cada habitación con `MarkRoomAvailableUseCase` solo si está `Reserved` por esa reserva.
  - Habitación en `PendingCleaning`/`InCleaning`, en otro estado o `Reserved` por otra reserva: en `REMOVED`, `UPDATED` o la lista de las 00:00 no invoca `MarkRoomAvailableUseCase`; solo actualiza la copia local.
  - La ingesta no invoca `TransitionRoomStateUseCase` directamente.
- [x] T010 [P] [US1] Integration test con Testcontainers RabbitMQ + PostgreSQL: Ingesta de lista diaria y mensaje duplicado, verificando idempotencia, transiciones en `Room` y logs. Verificar también que `DailyReservationIngestionService` aparta las habitaciones solo a través de `MarkRoomReservedUseCase` (FR-009) y que (prueba trasladada desde `plan-marcar-habitacion-reservada.md`):
  - invoca el puerto con `DAILY_LIST` y `ADDED_UPDATE` solo para reservas con `startDate` = hoy;
  - en la lista de las 00:00 ejecuta primero las liberaciones (`MarkRoomAvailableUseCase`) y después los apartados;
  - trata un `UPDATED` que cambia la habitación como liberación de la anterior y apartado de la nueva;
  - continúa con la siguiente habitación ante `CONFLICT` o `ROOM_NOT_FOUND`;
  - un `REMOVED` de una reserva cuya habitación está en limpieza se aplica sin revertir la ingesta y sin ir a la DLQ;
  - publica `DailyReservationListIngestedEvent` dentro de la transacción de la ingesta de las 00:00: un listener `@TransactionalEventListener(AFTER_COMMIT)` de prueba lo recibe después del commit.

### Implementation for User Story 1

- [x] T011 [P] [US1] Implementar `DailyReservationRepositoryAdapter` para operaciones CRUD y purga atómica de la copia local, incluida `findPendingArrivalReservationRef` (`daily_reservation_room` de una `daily_reservation` con `start_date = operationalDate` sin una fila de `stay` con la misma `reservation_ref` y `room_id`).
- [x] T012 [P] [US1] Implementar `DailyReservationIngestionService` con la lógica de negocio de ingesta, deduplicación y control de `sequenceNumber`. Aparta las habitaciones con `MarkRoomReservedUseCase` y las libera con `MarkRoomAvailableUseCase` (puertos de T018 del plan base), ejecutando primero las liberaciones a las 00:00; no transiciona las habitaciones por su cuenta (FR-009). Las liberaciones pasan por una guarda compartida `releaseIfReservedBy(room, reservationRef)`, usada por `REMOVED`, `UPDATED` y la lista de las 00:00: invoca `MarkRoomAvailableUseCase` solo si la habitación está `Reserved` y su `reserved_by_reservation_ref` coincide; si no, solo actualiza la copia local. Por cada habitación, bloquea su fila de `room` con `RoomRepositoryPort.lockById` (plan base T008, regla 13) antes de invocar el puerto; el conflicto lo registra *Marcar habitación como reservada*. Guarda el `operationalDate` de cada mensaje en `daily_reservation_message_log.operational_date`; el día de la copia local es el `operationalDate` de la última lista `reserva.lista-del-dia` aplicada. Descarta, registrando su `messageId`, los mensajes cuyo `operationalDate` sea anterior al día de la copia local, y controla el orden de `sequenceNumber` solo dentro del mismo `operationalDate` (FR-003). Si llega un `sequenceNumber` mayor que el siguiente esperado, aplica el mensaje y registra `SEQUENCE_GAP` en el log con el número esperado y el recibido, sin detener la ingesta (FR-003). Guarda la `reservationRef` de cada actualización en `daily_reservation_message_log.reservation_ref` y descarta un `ADDED`, `UPDATED` o `REMOVED` cuya secuencia sea menor o igual que la última aplicada para esa reserva en el día (se conserva aunque un `REMOVED` haya borrado la reserva de la copia local); `updatedAt` no decide el orden (FR-003). Al terminar la ingesta de las 00:00 (liberaciones y apartados), publica `DailyReservationListIngestedEvent(operationalDate)` dentro de la transacción de la ingesta; lo consume mantenimiento después del commit (regla 10 del plan base).
- [x] T013 [P] [US1] Implementar en la ingesta de las 00:00 la liberación a `Available` (mediante `MarkRoomAvailableUseCase`) de cada habitación en `Reserved` cuya reserva no figura en la nueva lista, con la guarda `releaseIfReservedBy` de T012. No se implementa un corte local de las 23:59: el no-show lo decide Módulo 2 y llega como `REMOVED`.
- [x] T014 [P] [US1] Implementar `DailyReservationRabbitListener` consumiendo de `m1.reservas.diarias.queue` con las routing keys `reserva.lista-del-dia` y `reserva.lista-del-dia.actualizacion`. Los mensajes ilegibles, sin `messageId`, `sequenceNumber`, `operationalDate` o `reservationRef`, con una routing key desconocida o con una reserva incompleta (en la lista de las 00:00, toda la lista) van, sin aplicarse ni reintentarse, a la cola de mensajes fallidos `m1.reservas.diarias.dlq` (Dead-Letter Exchange del plan base, T011). Un fallo temporal se reintenta 3 veces con espera creciente (`DailyReservationRetryProperties`, `hospitua.daily-reservations.retry.*`) y luego va a la misma cola (FR-003). El listener decide por la routing key si es la lista del día o una actualización, y la columna `message_type` (la routing key, que consulta el panel de recepción) la escribe el mapper de persistencia: el dominio no conoce las routing keys.
- [x] T015 [P] [US4] Unit test en `DailyReservationIngestionServiceTest` e integration test de `DailyReservationRabbitListener` con Testcontainers RabbitMQ: un mensaje con `operationalDate` anterior al día de la copia local se descarta registrando su `messageId` sin cambiar la copia local ni las habitaciones; un `sequenceNumber` mayor que el siguiente esperado se aplica y registra `SEQUENCE_GAP` con el número esperado y el recibido; un mensaje ilegible o sin `messageId`, `sequenceNumber`, `operationalDate` o `reservationRef` llega a `m1.reservas.diarias.dlq` sin aplicarse (FR-003).

---

## Phase 4: User Story 2 - Consulta y búsqueda en copia local por la Recepcionista (Priority: P1)

**Goal**: Proveer a la Recepcionista consultas y búsquedas de llegadas en mostrador (por código, documento o nombre del titular) **100% locales** sin peticiones HTTP externas.

**Independent Test**: Invocar la búsqueda por código o documento mediante `SearchDailyReservationsUseCase` (expuesta a la Recepcionista por `GET /api/reception/arrivals?query={term}` de `plan-consultar-panel-recepcion.md`) y verificar que retorna datos consolidados con el estado físico de la habitación (`Room.status = Reserved`) en < 50 ms sin invocar llamadas REST a Módulo 2.

### Tests for User Story 2

- [x] T016 [P] [US2] Unit test en `DailyReservationSearchServiceTest`:
  - Búsqueda por `reservationRef` exacto.
  - Búsqueda por documento del titular.
  - Búsqueda por coincidencia de nombre/apellido (case-insensitive, trim).
  - Enriquecimiento con estado físico de `Room` local (`Room.status`).
  - Cálculo dinámico de noches (`endDate - startDate`) sin campo persistido `nights`.
  - Retorno de múltiples coincidencias cuando aplique.
- [x] T017 [P] [US2] Integration test de `SearchDailyReservationsUseCase`: con varias coincidencias devuelve todas (la Recepcionista elige una en la tabla de Llegadas del panel) y sin coincidencias devuelve una lista vacía (HU-2, escenarios 6 y 7).

### Implementation for User Story 2

- [x] T018 [P] [US2] Implementar consultas de búsqueda optimizadas en `DailyReservationRepository` (búsqueda por referencia, documento y nombre).
- [x] T019 [P] [US2] Implementar `DailyReservationSearchService` enriqueciendo cada habitación de la reserva con `RoomRepositoryPort` (estado `Reserved`).
- [x] T020 [P] [US2] Exponer `SearchDailyReservationsUseCase` con `PendingArrivalsDto searchPendingArrivals(String query)` (`listAvailable` y la lista de `ArrivalGroup` en `groups`): cada `ArrivalGroup` es una reserva con `pendingRooms`, `totalRooms` y sus habitaciones sin `Stay` (los campos de `groups[]` de `GET /api/reception/arrivals`); `query` vacío devuelve todas las llegadas pendientes del día (`start_date = operationalDate`, mismo filtro que `findPendingArrivalReservationRef`). Diferencia documentada: un `ADDED` de hoy recibido sin lista del día sí aparta la habitación, mientras Recepción muestra "lista no disponible" (`plan-consultar-panel-recepcion.md`). Es el puerto de entrada para `ReceptionPanelController` (`GET /api/reception/arrivals?query={q}`, `plan-consultar-panel-recepcion.md`). Este plan no expone endpoints propios para la búsqueda, para que exista una sola ruta de consulta de llegadas.
  - Hecho (L08): `searchPendingArrivals(String query)` devuelve `PendingArrivalsDto` (`listAvailable` y `groups`), no solo la lista, para que el controlador no consulte aparte si llegó la lista del día (FR-011). Una habitación que no está en el inventario se omite, como en la ingesta.
- [ ] T021 [P] [US2] Sin componentes propios: la interfaz de búsqueda y listado de llegadas es la pestaña Llegadas del panel (`ArrivalsTab.tsx` y `ReceptionSearchBar.tsx`, `plan-consultar-panel-recepcion.md` T013).

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
     - `startDate` = fecha actual (`LocalDate.now(clock)`).
     - `endDate` = hoy + 365 días (`DECOMMISSION_HORIZON_DAYS`): 366 días contando hoy, el máximo que acepta Módulo 2.
4. **Puerto y Adaptador REST** (*Consultar reservas* FR-023 de Módulo 2). El puerto `Module2ReservationConflictClientPort` usa `startDate`/`endDate` y el adaptador los envía como `dateFrom`/`dateTo`, ambos inclusivos. Cada reserva devuelta es un conflicto; una lista vacía significa que la operación es segura. El servicio arma `ReservationConflictResult` con `hasConflict = items no vacío` y las reservas recibidas, sin reevaluar estados ni fechas.

   **Contrato de la llamada (Módulo 1 → Módulo 2)**

   ```http
   GET /api/reservations?dateFrom=2026-10-20&dateTo=2026-10-22&roomId=1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d
   Accept: application/json
   X-Client: MODULE1
   ```

   La cabecera `X-Client: MODULE1` es obligatoria: Módulo 2 identifica con ella a Módulo 1 y, sin ella o con otro valor, responde `400 INVALID_CLIENT`. Es la única ruta de Módulo 2 que usa Módulo 1.

   | Parámetro | Tipo | Obligatorio | Descripción |
   | --- | --- | --- | --- |
   | `dateFrom` | fecha (`AAAA-MM-DD`) | Sí | Inicio del rango (inclusivo) |
   | `dateTo` | fecha (`AAAA-MM-DD`) | Sí | Fin del rango (inclusivo); hoy + 365 días para la baja. Módulo 2 rechaza rangos de más de 366 días |
   | `roomId` | UUID | Sí para Módulo 1 (opcional en Módulo 2) | Habitación consultada; siempre una por petición |

   Respuesta `200 OK`. Los campos son los de FR-023 de Módulo 2; la envoltura `items`, sin paginar, está confirmada por Módulo 2. Con `roomId`, cada reserva trae todas sus habitaciones, no solo la filtrada:

   | Campo | Tipo | Descripción |
   | --- | --- | --- |
   | `items[]` | array | Reservas vigentes cuya estadía se cruza con el rango (`startDate` ≤ `dateTo` y `endDate` > `dateFrom`); vacía si no hay |
   | `items[].reservationRef` | string | Referencia de la reserva |
   | `items[].status` | string | `PENDING`, `ACTIVE` o `IN_PROGRESS` |
   | `items[].startDate` | fecha | Llegada |
   | `items[].endDate` | fecha | Salida (no se considera ocupada) |
   | `items[].rooms[]` | array | Habitaciones de la reserva: `roomId`, `roomNumber`, `categoryRoom` |

   ```json
   {
     "items": [
       {
         "reservationRef": "RSV-3F9A1C7B",
         "status": "ACTIVE",
         "startDate": "2026-10-21",
         "endDate": "2026-10-24",
         "rooms": [
           { "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d", "roomNumber": "106", "categoryRoom": "DOBLE" }
         ]
       }
     ]
   }
   ```

   | Respuesta de Módulo 2 | Excepción en Módulo 1 | Qué responde Módulo 1 |
   | --- | --- | --- |
   | `200` con `items` vacío | — | Sin conflicto: la operación continúa |
   | `200` con reservas | `ReservationConflictException` | `409 RESERVATION_CONFLICT` con `details.reservations` (`reservationRef`, `startDate`, `endDate`, `status` de cada una) |
   | `200` sin `items`, ilegible o con una reserva sin `reservationRef`, `status`, `startDate` o `endDate` | `ExternalModule2UnavailableException` | `424 MODULE_2_UNAVAILABLE`: no se informa un conflicto a medias; la operación se cancela sin cambiar la habitación |
   | `400` (cuerpo `{ errorCode, message, timestamp, path }` con `INCOMPLETE_DATE_RANGE`, `INVALID_DATE`, `INVALID_DATE_RANGE`, `DATE_RANGE_TOO_LARGE`, `INVALID_ROOM_ID`, `UNSUPPORTED_PARAMETER` o `INVALID_CLIENT`), `5xx`, timeout o caída | `ExternalModule2UnavailableException` | `424 MODULE_2_UNAVAILABLE`; la operación se cancela sin cambiar la habitación |

   Los ejemplos de esos errores están en los endpoints que los devuelven: `POST /api/rooms/{roomId}/technical-blocks` (`plan-programar-bloqueo-tecnico-para-habitacion.md`) y `POST /api/rooms/{roomId}/decommission` (`plan-dar-de-baja-habitacion.md`).
5. **Manejo de Resiliencia y Fallos**:
   - Connect timeout: 1000 ms. Read timeout: 2000 ms.
   - Si Módulo 2 responde con un error (400 o 5xx), timeout o caída de conexión, se captura la excepción y se lanza `ExternalModule2UnavailableException` (mapeada a HTTP 424 con código `MODULE_2_UNAVAILABLE`), informando al usuario la imposibilidad de verificar reservas en ese momento sin generar un 500 no controlado. Ante un `400`, además registra en el log de errores una alerta de configuración con el `errorCode` de Módulo 2, con el mismo patrón que la alerta `OUTBOX_PUBLISH_FAILED` del plan base, para distinguir un error de configuración (por ejemplo, `INVALID_CLIENT`) de una caída.

### Tests for User Story 3

- [x] T022 [P] [US3] Unit test en `RoomReservationConflictServiceTest`:
  - Consulta de "Dar de baja" con `startDate = hoy` y `endDate = hoy + 365 días`.
  - Validación de consulta para Bloqueo Técnico con `startDate` y `endDate` válidos (`startDate <= endDate`).
  - Mapeo de `items` vacío (`hasConflict = false`) y de `items` con reservas (`hasConflict = true`, con cada reserva devuelta).
  - Lanzamiento de `ExternalModule2UnavailableException` ante fallo o timeout del cliente REST.
- [x] T023 [P] [US3] Integration test con WireMock (en lugar de `MockRestServiceServer`, para probar el timeout y la conexión rechazada con HTTP real) para `Module2ReservationConflictRestAdapter`: llamada a `GET /api/reservations?dateFrom={startDate}&dateTo={endDate}&roomId={roomId}` con la cabecera `X-Client: MODULE1`, respuesta 200 OK con reservas y propagación de `ExternalModule2UnavailableException` ante timeout, `5xx` o cualquiera de los `400` de Módulo 2 (incluido `INVALID_CLIENT`).
- [x] T024 [P] [US3] Contract test de `Module2ReservationConflictRestAdapter` con el ejemplo del punto 4: parámetros `dateFrom`/`dateTo`/`roomId` y lectura de `items[]` con sus habitaciones. Las habitaciones de cada reserva se aceptan pero no se leen: ningún flujo las usa (`details.reservations` solo lleva `reservationRef`, `startDate`, `endDate` y `status`).

### Implementation for User Story 3

- [x] T025 [P] [US3] Implementar puerto de salida `Module2ReservationConflictClientPort` con el método:
  `ReservationConflictResult checkConflicts(ReservationConflictQuery query)`; la consulta (`roomId`, `startDate`, `endDate`) valida que el fin no sea anterior al inicio.
- [x] T026 [P] [US3] Implementar `Module2ReservationConflictRestAdapter` consumiendo `GET /api/reservations?dateFrom={startDate}&dateTo={endDate}&roomId={roomId}` mediante `RestClient` con la cabecera `X-Client: MODULE1` en cada petición y timeouts estrictos. Ante un `400`, registra el `errorCode` de Módulo 2 como alerta de configuración en el log de errores (patrón de `OUTBOX_PUBLISH_FAILED` del plan base) y lanza `ExternalModule2UnavailableException`; la respuesta al usuario sigue siendo `424 MODULE_2_UNAVAILABLE`. Una respuesta `200` sin `items`, ilegible o con una reserva sin `reservationRef`, `status`, `startDate` o `endDate` también lanza `ExternalModule2UnavailableException`.
- [x] T027 [P] [US3] Implementar puerto de entrada `CheckRoomReservationConflictsUseCase` y el servicio `RoomReservationConflictService`:
  - Método `checkMaintenanceConflict(UUID roomId, LocalDate startDate, LocalDate endDate)`
  - Método `checkDecommissionConflict(UUID roomId)` (calcula automáticamente `startDate = LocalDate.now(clock)` y `endDate = startDate.plusDays(365)`).
- [x] T028 [P] [US3] Exponer `checkMaintenanceConflict` y `checkDecommissionConflict` solo como puerto interno, sin endpoints REST propios: los usan `POST /api/rooms/{roomId}/technical-blocks` (`plan-programar-bloqueo-tecnico-para-habitacion.md`) y `POST /api/rooms/{roomId}/decommission` (`plan-dar-de-baja-habitacion.md`).
- [x] T029 [P] [US3] Implementar y registrar en `GlobalExceptionHandler` `ReservationConflictException` (409 `RESERVATION_CONFLICT`; este plan es su único dueño, catálogo del plan base) compartida por la baja y el bloqueo, con `details.reservations` (`reservationRef`, `startDate`, `endDate`, `status` de cada reserva devuelta).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T030 Sanitización de entradas de texto en búsquedas locales: trim de espacios accidentales e insensibilidad a mayúsculas/minúsculas.
  - Hecho (L08): `DailyReservationSearchService.normalize` quita los espacios sobrantes y pasa a minúsculas; la referencia y el documento se comparan exactos, y el nombre, el apellido o el nombre completo por coincidencia parcial, con los comodines de `LIKE` escapados.
- [ ] T031 Validar que no se muestren banners residuales tipo "Reserva encontrada" en la UI.
- [ ] T032 Auditoría y observabilidad: Métricas de latencia en la llamada REST a Módulo 2 y log transaccional de ingesta de cola en `daily_reservation_message_log`.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Room` y `RoomRepositoryPort` de [PLAN/base/plan.md](base/plan.md), más la creación de las tablas de `daily_reservation`.
- **Puertos de transición**: la ingesta usa `MarkRoomReservedUseCase` (`plan-marcar-habitacion-reservada.md`) y `MarkRoomAvailableUseCase` (`plan-marcar-habitacion-como-disponible.md`), ambos de T018 del plan base; esos planes se implementan antes que la Phase 3 de este.
- **Consumidores**:
  - `plan-consultar-panel-recepcion.md`: Invoca la búsqueda local de llegadas (`SearchDailyReservationsUseCase`).
  - `plan-registrar-check-in.md`: Invoca la validación local de reserva en el Paso 1.
  - Flujos de Mantenimiento y Baja de Habitación: Invocan `CheckRoomReservationConflictsUseCase` para validar solapamientos antes de transicionar estados físicos.
