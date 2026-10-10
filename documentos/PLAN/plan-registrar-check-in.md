# Implementation Plan: Registrar Check-In

**Date**: 2026-10-09  
**Spec**: [spec-registrar-check-in.md](../SPEC/spec-registrar-check-in.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso central **Registrar Check-In** para el actor Recepcionista en Módulo 1, mediante un flujo guiado de 4 pasos en la interfaz de usuario:

1. **Validar reserva**: Selección de la habitación desde el Panel de Recepción (`reservationRef`, `roomId`). Consulta exclusiva **en la copia local** (`daily_reservation`, `daily_reservation_room`) mantenida por `plan-consultar-reservas.md`. Verificación de que la habitación asignada se encuentre en estado `Reserved` en Módulo 1 con `startDate = hoy` y sin `Stay` previo para ese par `(reservationRef, roomId)`. **No se realiza ninguna llamada REST a Módulo 2** en este paso.
2. **Datos de huéspedes**: Captura directa y unificada de los ocupantes de esa habitación específica (integrando la lógica de captura y validaciones anteriormente delegada a `procesar-datos-huespedes`):
   - Titular precargado desde la copia local (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`), en solo lectura y con `isReservationGuest = true` [NEEDS_CONFIRMATION_MODULO_2 si llega `fullName`].
   - Captura de acompañantes (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`).
   - Validación de aforo contra `guest_count` de la habitación en `daily_reservation_room` (respaldo: `room.maxCapacity` [NEEDS_CONFIRMATION_MODULO_2]).
   - Para huéspedes extranjeros (`nationality ≠ Colombia`), despliegue obligatorio del panel de control migratorio con: `birthDate`, `originPlace`, `destinationPlace`. `movementType` ("ENTRY") y `movementDate` (fecha de hoy) son asignados automáticamente por el sistema.
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
  - Integridad de la máquina de estados: Check-In solo procede si la habitación se encuentra en estado `Reserved`.
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
│   │   │   ├── InvalidRoomStateException.java
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

## Phase 1: Setup & Database Migrations

- [ ] T001 Migración Flyway: Actualizar tabla `stay` agregando columnas `titular_first_name`, `titular_last_name`, `titular_document_number` y asegurando que `source` almacene `DIRECTA` o el nombre de la OTA.
- [ ] T002 Migración Flyway: Actualizar tabla `room_guest` reemplazando `full_name` por `first_name` y `last_name`, y agregando columnas `birth_date` (DATE nullable), `origin_place` (VARCHAR nullable), `destination_place` (VARCHAR nullable).
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
  - `birthDate` (LocalDate nullable, obligatorio si `nationality ≠ Colombia`)
  - `originPlace` (String nullable, obligatorio si `nationality ≠ Colombia`)
  - `destinationPlace` (String nullable, obligatorio si `nationality ≠ Colombia`)
- [ ] T006 Implementar DTOs para el contrato plano hacia `m2.habitacion.checkin.queue`:
  - `CheckInMessagePayload`: `messageId` (UUID), `sequenceNumber` (int), `reservationRef` (String), `roomId` (UUID), `movementType` ("ENTRY"), `movementDate` (String: YYYY-MM-DD), `guests` (List<GuestMessageDto>).
  - `GuestMessageDto`: `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`, `originPlace`, `destinationPlace`.
- [ ] T007 Implementar excepciones de dominio en `GlobalExceptionHandler`: `InvalidRoomStateException`, `GuestCountMismatchException`, `RoomCapacityExceededException`, `DuplicateGuestDocumentException`, `InvalidCheckInDateException`, `StayAlreadyExistsException`.

---

## Phase 3: User Story 1 - Admisión física, validación de ocupantes y outbox unificado (Priority: P1)

**Goal**: Permitir al Recepcionista registrar el Check-In por habitación validando contra copia local, registrando la estancia física con ocupantes (nacionales y extranjeros), actualizando el estado a `Occupied` y emitiendo el mensaje plano unificado a `m2.habitacion.checkin.queue`.

**Independent Test**: Invocar el Check-In para una habitación en estado `Reserved` de una reserva en `daily_reservation`. Verificar que se crea `Stay` con la copia del titular, se transiciona la habitación a `Occupied`, se persisten los huéspedes y se guarda en `outbox_notification` el mensaje plano con `guests[]` completo sin colas satélite de extranjeros.

### Tests for User Story 1

- [ ] T008 [P] [US1] Unit test: Verificar validación del Paso 1 contra la copia local `DailyReservationQueryPort` y validación de precondición `Room.status == Reserved` y ausencia de `Stay` para `(reservationRef, roomId)`.
- [ ] T009 [P] [US1] Unit test: Verificar que el titular queda precargado con `isReservationGuest = true`, selector de país bloqueado y campos de solo lectura.
- [ ] T010 [P] [US1] Unit test: Validar rechazo si un huésped extranjero no incluye `birthDate`, `originPlace` o `destinationPlace`, o si `birthDate` es igual o posterior a hoy.
- [ ] T011 [P] [US1] Unit test: Verificar que para huéspedes colombianos `birthDate`, `originPlace` y `destinationPlace` se persisten como null y no se exigen.
- [ ] T012 [P] [US1] Unit test: Verificar que `CheckInExecutionService` transiciona atómicamente `Room.status` a `Occupied` vía `TransitionRoomStateUseCase` con `SourceFlow` = `CHECK_IN`.
- [ ] T013 [P] [US1] Integration test: Verificar payload insertado en `outbox_notification`: formato plano (sin EventEnvelope), `movementType = ENTRY`, `movementDate = checkInDate`, `guests[]` con todos los ocupantes y `messageId` inmutable en reintentos.
- [ ] T014 [P] [US1] Integration test con Testcontainers para `POST /api/check-in` retornando HTTP 201 Created y `CheckInResultDto`.

### Implementation for User Story 1

- [ ] T015 [P] [US1] Implementar en `CheckInValidationService`:
  - Búsqueda en copia local `daily_reservation` y `daily_reservation_room`.
  - Validación de existencia y no duplicidad de `Stay` previo para ese `(reservationRef, roomId)`.
  - Validación de habitación física en estado `Reserved` con `reservedByReservationRef == reservationRef`.
- [ ] T016 [P] [US1] Implementar en `CheckInExecutionService` el método `@Transactional registerCheckIn(RegisterCheckInCommand command)`:
  - Validar lista de huéspedes: presencia obligatoria de nombres, apellidos y documentos.
  - Validar campos migratorios para extranjeros: `birthDate < LocalDate.now()`, `originPlace` y `destinationPlace` no vacíos.
  - Validar aforo contra `guest_count` de la habitación en `daily_reservation_room` (respaldo: `room.maxCapacity` [NEEDS_CONFIRMATION_MODULO_2]).
  - Validar unicidad de documento dentro de la habitación (`DuplicateGuestDocumentException`).
  - Crear y guardar entidad `Stay` con `source`, `titularFirstName`, `titularLastName`, `titularDocumentNumber`.
  - Persistir ocupantes en `RoomGuest`.
  - Ejecutar transición `Reserved → Occupied` con `TransitionRoomStateUseCase.transition(roomId, Occupied, recepcionista, CHECK_IN, reservationRef)`, que registra el periodo en `room_state_history` (regla 8 del plan base).
  - Construir mensaje plano `CheckInMessagePayload` con `messageId` (UUIDv4) y `sequenceNumber` creciente del día.
  - Insertar mensaje en `outbox_notification` para publicación asíncrona hacia `m2.habitacion.checkin.queue`.
- [ ] T017 [US1] Implementar controlador REST `CheckInController`:
  - `GET /api/check-in/validate?reservationRef={ref}&roomId={roomId}` (Paso 1).
  - `POST /api/check-in` (Confirmación final del Paso 4).
- [ ] T018 [US1] Adaptar componentes de UI en frontend:
  - Formulario con nombres y apellidos separados.
  - Panel migratorio condicional visible exclusivamente para extranjeros con validación de fecha de nacimiento en el pasado.
  - Despliegue de habitación específica y pendientes (`Habitación 304 — 2 de 2 pendientes`).
  - Pantalla de éxito con badges `Habitación: Ocupada` y `Reserva: En curso`.

---

## Phase 4: User Story 2 - Validaciones preventivas y resiliencia de colas (Priority: P2)

**Goal**: Garantizar la integridad del sistema ante datos inválidos, aforos sobrepasados o indisponibilidad temporal del broker de mensajería.

### Tests for User Story 2

- [ ] T019 [P] [US2] Unit test: Rechazar Check-In si la habitación se encuentra en estado distinto a `Reserved` (`InvalidRoomStateException`).
- [ ] T020 [P] [US2] Unit test: Rechazar Check-In si ya existe un `Stay` activo para esa habitación (`StayAlreadyExistsException`).
- [ ] T021 [P] [US2] Unit test: Rechazar Check-In si el número de ocupantes supera el `guest_count` de la habitación (`GuestCountMismatchException`) o la capacidad física (`RoomCapacityExceededException`).
- [ ] T022 [P] [US2] Unit test: Rechazar Check-In si dos ocupantes de la habitación comparten el mismo documento (`DuplicateGuestDocumentException`).
- [ ] T023 [P] [US2] Integration test: Simular fallo de RabbitMQ durante la publicación; comprobar que la transacción local de `Stay` y `Room.status = Occupied` queda en firme y el outbox reintenta con el mismo `messageId`.

### Implementation for User Story 2

- [ ] T024 [P] [US2] Implementar el scheduler/worker de despacho Outbox con reintentos exponenciales y conservación estricta de `messageId` original.
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
