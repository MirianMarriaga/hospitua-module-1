# Implementation Plan: Registrar Check-In

**Date**: 2026-10-09  
**Spec**: [spec-registrar-check-in.md](../SPEC/spec-registrar-check-in.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso central **Registrar Check-In** para el actor Recepcionista en Módulo 1, mediante un flujo guiado de 4 pasos en la interfaz de usuario:

1. **Validar reserva**: Selección de la habitación desde el Panel de Recepción (`reservationRef`, `roomId`). Consulta exclusiva **en la copia local** (`daily_reservation`, `daily_reservation_room`) mantenida por `plan-consultar-reservas.md`. Verificación de que la habitación asignada se encuentre en estado `Reserved` con `reservedByReservationRef` igual a la reserva, con `startDate = hoy` y sin `Stay` previo para ese par `(reservationRef, roomId)`. **No se realiza ninguna llamada REST a Módulo 2** en este paso.
2. **Datos de huéspedes**: Captura directa y unificada de los ocupantes de esa habitación específica (integrando la lógica de captura y validaciones anteriormente delegada a `procesar-datos-huespedes`):
   - Titular precargado desde la copia local (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`), en solo lectura y con `isReservationGuest = true`. El titular llega siempre con nombres y apellidos separados.
   - Captura de acompañantes (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`). `documentType` es uno de `RC`, `TI`, `CC`, `CE`, `PAS` o `NIT`. `birthDate` es obligatoria para todos los huéspedes, incluido el titular si se aloja (la lista del día no la trae).
   - Validación de aforo contra `guest_count` de la habitación en `daily_reservation_room` (respaldo: `room.maxCapacity`).
   - Para huéspedes extranjeros (`nationality ≠ Colombia`), despliegue obligatorio del panel de control migratorio con: `originPlace` y `destinationPlace`. `movementType` ("ENTRY") y `movementDate` (fecha de hoy) son asignados automáticamente por el sistema.
3. **Confirmación**: Resumen visual de la habitación asignada, titular, ocupantes, estadía (noches calculadas y fechas) y texto informativo de que la habitación pasará a `Occupied` y la reserva a **En curso** (sin menciones técnicas a módulos externos).
4. **Check-In**: Ejecución atómica y transaccional local (`@Transactional`):
   - Creación de la entidad `Stay` por habitación con: `source` (`DIRECTA` o nombre de la OTA), `titularFirstName`, `titularLastName`, `titularDocumentNumber`, `checkInDate`, `expectedCheckinTime`, `expectedCheckoutTime`.
   - Persistencia inmutable de los ocupantes en `RoomGuest` (con campos migratorios para extranjeros).
   - Transición física de `Room.status` de `Reserved` a `Occupied` mediante `TransitionRoomStateUseCase`, que registra el periodo en `room_state_history` (`SourceFlow` = `CHECK_IN`).
   - Inserción en `outbox_notification` del mensaje plano hacia `m2.habitacion.checkin.queue` con el arreglo unificado `guests[]` (nacionales y extranjeros).
   - Despliegue de pantalla de éxito con badges "Habitación: Ocupada", "Reserva: En curso" y botón "Volver al inicio".

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, Spring Data JPA, Hibernate Validator, Lombok, Spring AMQP
- **Storage**: PostgreSQL 16+ (Tablas: `room`, `stay`, `room_guest`, `daily_reservation`, `daily_reservation_room`, `outbox_notification`, `room_state_history`)
- **Storage**: PostgreSQL 16+ (Tablas: `room`, `stay`, `room_guest`, `daily_reservation`, `daily_reservation_room`, `outbox_notification`, `room_state_history`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL, RabbitMQ)
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador
- **Performance Goals**:
  - Transición atómica de `Room.status` y creación de `Stay`: < 50 ms.
  - Tiempo total de operación en mostrador: < 2 min.
  - Lectura en copia local: < 10 ms (sin llamadas de red).
- **Constraints**:
  - Un `Stay` por cada par `(reservationRef, roomId)` (check-in individual por habitación).
  - No exponer horas en fechas de negocio (`LocalDate` / YYYY-MM-DD).
  - Resiliencia: La caída o lentitud de la cola nunca bloquea la entrega de la habitación ni revierte la transacción física local (patrón Outbox).
  - Integridad de la máquina de estados: Check-In procede solo si la habitación está en `Reserved` apartada para esa reserva (sin `Stay` activo para ese par `reservationRef/roomId`); `Available → Occupied` no existe (`maquina-estados-habitacion.md`).
  - Formato de mensaje plano acordado con M2 (sin `EventEnvelope`, sin `foreignGuestCount`, sin cola separada de extranjeros).

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-registrar-check-in.md
│   └── spec-consultar-reservas.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-registrar-check-in.md
    └── plan-consultar-reservas.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Room.java
│   │   │   ├── RoomStatus.java
│   │   │   ├── Stay.java
│   │   │   ├── RoomGuest.java
│   │   │   ├── DailyReservation.java
│   │   │   └── DailyReservationRoom.java
│   │   ├── exception/
│   │   │   ├── GuestCountMismatchException.java
│   │   │   ├── RoomCapacityExceededException.java
│   │   │   ├── DuplicateGuestDocumentException.java
│   │   │   ├── InvalidCheckInDateException.java
│   │   │   └── StayAlreadyExistsException.java
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── RegisterCheckInUseCase.java
│   │       │   └── ValidateCheckInReservationUseCase.java
│   │       └── out/
│   │           ├── RoomPersistencePort.java
│   │           ├── StayPersistencePort.java
│   │           ├── RoomGuestPersistencePort.java
│   │           ├── DailyReservationQueryPort.java
│   │           └── OutboxEventPublisherPort.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── CheckInExecutionService.java
│   │   │   └── CheckInValidationService.java
│   │   └── dto/
│   │       ├── CheckInValidationResponse.java
│   │       ├── RegisterCheckInCommand.java
│   │       ├── GuestInputDto.java
│   │       ├── CheckInMessagePayload.java
│   │       └── CheckInResultDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── CheckInController.java
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── RoomRepositoryAdapter.java
│       │       │   ├── StayRepositoryAdapter.java
│       │       │   ├── RoomGuestRepositoryAdapter.java
│       │       │   ├── DailyReservationAdapter.java
│       │       │   └── OutboxNotificationRepositoryAdapter.java
│       │       └── messaging/
│       │           └── RabbitMqOutboxPublisherAdapter.java
```

---

## Contratos de integración

### Contrato 1 — Notificación de Check-In (Módulo 1 → Módulo 2)

**Cola y routing key:**

```text
Cola: m2.habitacion.checkin.queue
Routing key: habitacion.checkin
```

**Campos del mensaje:**

| Nombre | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `messageId` | UUID | Sí | Identificador único del mensaje; se conserva idéntico en reintentos para la idempotencia del receptor |
| `sequenceNumber` | entero | Sí | Número de secuencia creciente por cola |
| `reservationRef` | string | Sí | Referencia de la reserva (ej. `RSV-8D02E5A4`) |
| `roomId` | UUID | Sí | Identificador de la habitación |
| `movementType` | string | Sí | Tipo de movimiento; para el Check-In es siempre `ENTRY` |
| `movementDate` | fecha (YYYY-MM-DD) | Sí | Fecha de ingreso; igual a `checkInDate` |
| `guests[]` | array | Sí | Totalidad de los huéspedes admitidos en la habitación (nacionales y extranjeros) |

**Campos de cada elemento de `guests[]`:**

| Nombre | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `firstName` | string | Sí | Nombres del huésped |
| `lastName` | string | Sí | Apellidos del huésped |
| `documentType` | string | Sí | Tipo de documento: `RC`, `TI`, `CC`, `CE`, `PAS` o `NIT` |
| `documentNumber` | string | Sí | Número de documento de identidad |
| `birthDate` | fecha (YYYY-MM-DD) | Sí | Fecha de nacimiento |
| `nationality` | string | Sí | Nacionalidad del huésped |
| `originPlace` | string | Solo si `nationality ≠ Colombia` | Lugar de procedencia, formato "Ciudad, País" |
| `destinationPlace` | string | Solo si `nationality ≠ Colombia` | Lugar de destino, formato "Ciudad, País" |

**Ejemplo de mensaje (ilustra huésped nacional y extranjero):**

```json
{
  "messageId": "UUIDv4",
  "sequenceNumber": 1,
  "reservationRef": "RSV-8D02E5A4",
  "roomId": "uuid",
  "movementType": "ENTRY",
  "movementDate": "2026-10-09",
  "guests": [
    {
      "firstName": "Ana",
      "lastName": "Pérez",
      "documentType": "CC",
      "documentNumber": "123",
      "birthDate": "1995-03-20",
      "nationality": "Colombia"
    },
    {
      "firstName": "John",
      "lastName": "Smith",
      "documentType": "PAS",
      "documentNumber": "X99",
      "birthDate": "1990-05-12",
      "nationality": "Estados Unidos",
      "originPlace": "Miami, Estados Unidos",
      "destinationPlace": "Cartagena, Colombia"
    }
  ]
}
```

---

## Endpoints REST internos (Frontend → Módulo 1)

Ambos exigen `Authorization: Bearer <JWT>` con rol Recepcionista (FR-001); el recepcionista responsable se toma del token. Los errores usan `ApiError` del plan base; los `401` y `403` son los generales.

### Endpoint 1 — Validar la reserva y la habitación (Paso 1)

```http
GET /api/check-in/validate?reservationRef=RSV-8D02E5A4&roomId=1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d
Authorization: Bearer <JWT>
```

| Parámetro | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `reservationRef` | string | Sí | Referencia que entrega el botón "Check-in" del panel de recepción (FR-003) |
| `roomId` | UUID | Sí | Habitación seleccionada en el panel |

Valida solo contra la copia local (`DailyReservationQueryPort`) y el inventario, sin llamar a Módulo 2 (FR-003). Respuesta `200 OK` (`CheckInValidationResponse`):

```json
{
  "reservationRef": "RSV-8D02E5A4",
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "roomNumber": "204",
  "categoryRoom": "DOBLE",
  "guestCount": 2,
  "maxCapacity": 3,
  "startDate": "2026-10-09",
  "endDate": "2026-10-11",
  "nights": 2,
  "source": "BOOKING",
  "holder": {
    "firstName": "Ana",
    "lastName": "Pérez",
    "documentType": "CC",
    "documentNumber": "123",
    "nationality": "Colombia"
  }
}
```

`holder` son los datos del titular en solo lectura (FR-007); `nights` se calcula como `endDate − startDate`.

### Endpoint 2 — Registrar el Check-In (confirmación del Paso 3)

```http
POST /api/check-in
Authorization: Bearer <JWT>
Content-Type: application/json
```

```json
{
  "reservationRef": "RSV-8D02E5A4",
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "guests": [
    {
      "firstName": "Ana",
      "lastName": "Pérez",
      "documentType": "CC",
      "documentNumber": "123",
      "birthDate": "1995-03-20",
      "nationality": "Colombia",
      "isReservationGuest": true
    },
    {
      "firstName": "John",
      "lastName": "Smith",
      "documentType": "PAS",
      "documentNumber": "X99",
      "birthDate": "1990-05-12",
      "nationality": "Estados Unidos",
      "originPlace": "Miami, Estados Unidos",
      "destinationPlace": "Cartagena, Colombia",
      "isReservationGuest": false
    }
  ]
}
```

Cada elemento de `guests[]` (`GuestInputDto`) tiene los campos del Contrato 1 más `isReservationGuest` (FR-007): `birthDate` obligatoria para todos y anterior a hoy, `documentType` uno de `RC`, `TI`, `CC`, `CE`, `PAS` o `NIT`, y `originPlace`/`destinationPlace` obligatorios solo si `nationality ≠ Colombia` (FR-006, FR-009). El servidor repite las validaciones del Paso 1.

Respuesta `201 Created` (`CheckInResultDto`):

```json
{
  "stayId": "7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f",
  "reservationRef": "RSV-8D02E5A4",
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "roomNumber": "204",
  "roomStatus": "Occupied",
  "checkInDate": "2026-10-09"
}
```

La respuesta no depende de Módulo 2: la notificación queda en el outbox (FR-014).

### Errores de los dos endpoints

| Status Code | errorCode | Excepción | Cuándo ocurre | Endpoint |
| --- | --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Falta un parámetro o campo, `documentType` no admitido, `birthDate` no anterior a hoy, o extranjero sin `originPlace`/`destinationPlace`; `details.field` y `details.guestIndex` señalan el campo (FR-006, FR-009) | 1 y 2 |
| 400 | `GUEST_COUNT_MISMATCH` | `GuestCountMismatchException` | La cantidad de huéspedes difiere de `guestCount` de la habitación (FR-008) | 2 |
| 400 | `ROOM_CAPACITY_EXCEEDED` | `RoomCapacityExceededException` | La cantidad de huéspedes supera `maxCapacity` (FR-008) | 2 |
| 400 | `DUPLICATE_GUEST_DOCUMENT` | `DuplicateGuestDocumentException` | Dos huéspedes de la habitación tienen el mismo tipo y número de documento | 2 |
| 404 | `RESERVATION_NOT_FOUND` | `ReservationNotFoundException` (`plan-consultar-reservas.md`) | El par `reservationRef`, `roomId` no está en la copia local del día | 1 y 2 |
| 409 | `INVALID_CHECK_IN_DATE` | `InvalidCheckInDateException` | La reserva no inicia hoy (`startDate ≠ fechaActual`, FR-005) | 1 y 2 |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación no está en `Reserved` o está apartada para otra reserva; `details.currentStatus` lleva el estado actual (FR-004) | 1 y 2 |
| 409 | `STAY_ALREADY_EXISTS` | `StayAlreadyExistsException` | Ya existe una estancia para ese par (FR-003) | 1 y 2 |
| 409 | `CONCURRENT_UPDATE` | `ObjectOptimisticLockingFailureException` | Otra operación cambió la habitación al mismo tiempo (regla 13 del plan base); se puede reintentar | 2 |

```json
{
  "errorCode": "ROOM_INVALID_STATE",
  "message": "La habitación 204 está Disponible; solo se puede hacer el Check-In de una habitación Reservada para esta reserva.",
  "timestamp": "2026-10-09T14:05:12-05:00",
  "path": "/api/check-in/validate",
  "details": { "currentStatus": "Available" }
}
```

---

## Phase 1: Setup & Database Migrations

- [ ] T001 Migración Flyway: Actualizar tabla `stay` agregando columnas `titular_first_name`, `titular_last_name`, `titular_document_number` y asegurando que `source` almacene `DIRECTA` o el nombre de la OTA.
- [ ] T002 Migración Flyway: Actualizar tabla `room_guest` reemplazando `full_name` por `first_name` y `last_name`, y agregando columnas `birth_date` (DATE NOT NULL), `origin_place` (VARCHAR nullable), `destination_place` (VARCHAR nullable).
- [ ] T003 Configurar exchange y cola RabbitMQ `m2.habitacion.checkin.queue` (enrutamiento para check-in con mensaje plano).

---

## Phase 2: Foundational Domain & Entities

- [ ] T004 Actualizar entidad de dominio JPA `Stay`:
  - `id` (UUID)
  - `reservationRef` (String)
  - `roomId` (UUID)
  - `source` (String: `DIRECTA` o nombre OTA)
  - `titularFirstName`, `titularLastName`, `titularDocumentNumber`
  - `checkInDate` (LocalDate), `checkOutDate` (LocalDate nullable)
  - `expectedCheckinTime` (LocalDate), `expectedCheckoutTime` (LocalDate)
  - `receptionistIdCheckIn` (String), `receptionistIdCheckOut` (String nullable)
- [ ] T005 Actualizar entidad inmutable `RoomGuest`:
  - `id` (UUID), `stayId` (UUID)
  - `firstName` (String), `lastName` (String)
  - `documentType` (String), `documentNumber` (String)
  - `nationality` (String), `isReservationGuest` (boolean)
  - `birthDate` (LocalDate, obligatorio para todos los huéspedes)
  - `originPlace` (String nullable, obligatorio si `nationality ≠ Colombia`)
  - `destinationPlace` (String nullable, obligatorio si `nationality ≠ Colombia`)
- [ ] T006 Implementar DTOs para el contrato plano hacia `m2.habitacion.checkin.queue`:
  - `CheckInMessagePayload`: `messageId` (UUID), `sequenceNumber` (int), `reservationRef` (String), `roomId` (UUID), `movementType` ("ENTRY"), `movementDate` (String: YYYY-MM-DD), `guests` (List<GuestMessageDto>).
  - `GuestMessageDto`: `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`, `originPlace`, `destinationPlace`.
- [ ] T007 Implementar excepciones de dominio en `GlobalExceptionHandler`: `GuestCountMismatchException`, `RoomCapacityExceededException`, `DuplicateGuestDocumentException`, `InvalidCheckInDateException`, `StayAlreadyExistsException`, con los códigos de la tabla "Errores de los dos endpoints"; `ReservationNotFoundException` es de `plan-consultar-reservas.md`. `InvalidRoomTransitionException`, `ObjectOptimisticLockingFailureException` y los códigos comunes vienen del catálogo de errores del plan base.

---

## Phase 3: User Story 1 - Admisión física, validación de ocupantes y outbox unificado (Priority: P1)

**Goal**: Permitir al Recepcionista registrar el Check-In por habitación validando contra copia local, registrando la estancia física con ocupantes (nacionales y extranjeros), actualizando el estado a `Occupied` y emitiendo el mensaje plano unificado a `m2.habitacion.checkin.queue`.

**Independent Test**: Invocar el Check-In para una habitación en estado `Reserved` de una reserva en `daily_reservation`. Verificar que se crea `Stay` con la copia del titular, se transiciona la habitación a `Occupied`, se persisten los huéspedes y se guarda en `outbox_notification` el mensaje plano con `guests[]` completo sin colas satélite de extranjeros.

### Tests for User Story 1

- [ ] T008 [P] [US1] Unit test: Verificar validación del Paso 1 contra la copia local `DailyReservationQueryPort` y validación de precondición `Room.status == Reserved` y ausencia de `Stay` para `(reservationRef, roomId)`.
- [ ] T009 [P] [US1] Unit test: Verificar que el titular queda precargado con `isReservationGuest = true`, selector de país bloqueado y campos de solo lectura.
- [ ] T010 [P] [US1] Unit test: Validar rechazo si cualquier huésped no incluye `birthDate` o si es igual o posterior a hoy, si un extranjero no incluye `originPlace` o `destinationPlace`, o si `documentType` no es uno de los códigos admitidos.
- [ ] T011 [P] [US1] Unit test: Verificar que para huéspedes colombianos `originPlace` y `destinationPlace` se persisten como null y no se exigen, y que `birthDate` sí se exige.
- [ ] T012 [P] [US1] Unit test: Verificar que `CheckInExecutionService` transiciona atómicamente `Room.status` a `Occupied` vía `TransitionRoomStateUseCase` con `SourceFlow` = `CHECK_IN`.
- [ ] T013 [P] [US1] Integration test: Verificar payload insertado en `outbox_notification`: formato plano (sin EventEnvelope), `movementType = ENTRY`, `movementDate = checkInDate`, `guests[]` con todos los ocupantes y `messageId` inmutable en reintentos.
- [ ] T014 [P] [US1] Integration test con Testcontainers para `POST /api/check-in` retornando HTTP 201 Created y `CheckInResultDto`.

### Implementation for User Story 1

- [ ] T015 [P] [US1] Implementar en `CheckInValidationService`:
  - Búsqueda en copia local `daily_reservation` y `daily_reservation_room` mediante `DailyReservationQueryPort`: puerto de solo lectura que implementa el mismo adaptador de la copia local de `plan-consultar-reservas.md`, sin un segundo origen de datos.
  - Validación de existencia y no duplicidad de `Stay` previo para ese `(reservationRef, roomId)`.
  - Validación de habitación física en estado `Reserved` con `reservedByReservationRef == reservationRef`.
- [ ] T016 [P] [US1] Implementar en `CheckInExecutionService` el método `@Transactional registerCheckIn(RegisterCheckInCommand command)`:
  - Validar lista de huéspedes: presencia obligatoria de nombres, apellidos y documentos.
  - Validar `birthDate < LocalDate.now()` y `documentType` admitido para todos los huéspedes, y `originPlace` y `destinationPlace` no vacíos para extranjeros.
  - Validar aforo contra `guest_count` de la habitación en `daily_reservation_room` (respaldo: `room.maxCapacity`).
  - Validar unicidad de documento dentro de la habitación (`DuplicateGuestDocumentException`).
  - Crear y guardar entidad `Stay` con `source`, `titularFirstName`, `titularLastName`, `titularDocumentNumber`.
  - Persistir ocupantes en `RoomGuest`.
  - Registrar en `room_audit_log` el evento `CHECK_IN` con `stayId`, `reservationRef`, recepcionista y marca de tiempo, mediante `RoomAuditLogPort` (FR-015).
  - Ejecutar transición `Reserved → Occupied` con `TransitionRoomStateUseCase.transition(roomId, Occupied, recepcionista, CHECK_IN, reservationRef)`, que registra el periodo en `room_state_history` (regla 8 del plan base).
  - Construir el mensaje plano `CheckInMessagePayload`; `messageId` y `sequenceNumber` (creciente por cola, nunca reiniciado) los asigna `OutboxEventPublisherPort.enqueue(...)` (plan base, T012).
  - Encolarlo con `OutboxEventPublisherPort.enqueue("habitacion.checkin", "CHECK_IN", payload)` dentro de la misma transacción, para su publicación asíncrona hacia `m2.habitacion.checkin.queue`.
- [ ] T017 [US1] Implementar controlador REST `CheckInController`:
  - `GET /api/check-in/validate?reservationRef={ref}&roomId={roomId}` (Paso 1; Endpoint 1).
  - `POST /api/check-in` (confirmación del Paso 3; Endpoint 2).
- [ ] T018 [US1] Adaptar componentes de UI en frontend:
  - Formulario con nombres y apellidos separados.
  - Panel migratorio condicional visible exclusivamente para extranjeros con validación de fecha de nacimiento en el pasado.
  - Despliegue de la habitación específica (número de habitación, categoría y aforo).
  - Pantalla de éxito con badges `Habitación: Ocupada` y `Reserva: En curso`.

---

## Phase 4: User Story 2 - Validaciones preventivas y resiliencia de colas (Priority: P2)

**Goal**: Garantizar la integridad del sistema ante datos inválidos, aforos sobrepasados o indisponibilidad temporal del broker de mensajería.

### Tests for User Story 2

- [ ] T019 [P] [US2] Unit test: Rechazar Check-In si la habitación no está en `Reserved`, incluido `Available`, o si está apartada para otra reserva (`InvalidRoomTransitionException`).
- [ ] T020 [P] [US2] Unit test: Rechazar Check-In si ya existe un `Stay` activo para esa habitación (`StayAlreadyExistsException`).
- [ ] T021 [P] [US2] Unit test: Rechazar Check-In si el número de ocupantes supera el `guest_count` de la habitación (`GuestCountMismatchException`) o la capacidad física (`RoomCapacityExceededException`).
- [ ] T022 [P] [US2] Unit test: Rechazar Check-In si dos ocupantes de la habitación comparten el mismo documento (`DuplicateGuestDocumentException`).
- [ ] T023 [P] [US2] Integration test: Simular fallo de RabbitMQ durante la publicación; comprobar que la transacción local de `Stay` y `Room.status = Occupied` queda en firme y el outbox reintenta con el mismo `messageId`.

### Implementation for User Story 2

- [ ] T024 [P] [US2] Verificar con el worker del outbox del plan base (T012) que un Check-In no publicado se reintenta con el mismo `messageId` y `sequenceNumber`, sin frenar otros mensajes, y que agotados los reintentos queda en `FAILED` con alerta.
- [ ] T025 [US2] Implementar manejo de contingencia en frontend: mensajes controlados sin jerga técnica cuando no se pueda registrar el Check-In.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T026 Verificar que ninguna traza de log ni endpoint exponga información migratoria innecesaria ni credenciales.
- [ ] T027 Validar que no quede ninguna llamada REST dirigida a Módulo 2 para obtener reservas en el flujo de Check-In.
- [ ] T028 Validar que no exista referencia a `foreignGuestCount`, `m2.huespedes.extranjeros.queue` ni `SendForeignGuestsUseCase`.
- [ ] T029 Pruebas E2E completas del wizard de Check-In con huéspedes nacionales y extranjeros.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `daily_reservation` y `daily_reservation_room` pobladas desde `plan-consultar-reservas.md`.
- **Integración con Módulo 2**: Solo asíncrona mediante publicación outbox a `m2.habitacion.checkin.queue`.
- **Cierre**: La lógica de `procesar-datos-huespedes` y la captura migratoria quedan absorbidas en su totalidad en este plan.
