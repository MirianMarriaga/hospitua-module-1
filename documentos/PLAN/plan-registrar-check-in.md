# Implementation Plan: Registrar Check-In

**Date**: 2026-10-06  
**Spec**: [spec-registrar-check-in.md](../SPEC/spec-registrar-check-in.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso central **Registrar Check-In** para el actor Recepcionista en Módulo 1, mediante un flujo guiado de 4 pasos en la interfaz de usuario:
1. **Validar reserva**: Recepción de la reserva precargada desde el Panel de Recepción (`reservationRef`), consulta **en la copia local** de reservas del día (`daily_reservation`) mantenida por `plan-consultar-reservas.md` (ingestada a las 00:00 vía `m1.reservas.diarias.queue`), y verificación física obligatoria de que la habitación asignada se encuentre en estado `Reserved` en Módulo 1 con `startDate = fechaActual`. **No se realiza ninguna llamada REST a Módulo 2** en este paso.
2. **Datos de huéspedes**: Invocación al caso de uso interno `plan-procesar-datos-huespedes.md` (`<<includes>>`), presentando al Huésped Titular precargado y de solo lectura (`isReservationGuest = true`), capturando acompañantes, validando coincidencia exacta con `guestCount` y tope de `maxCapacity`, y alertando reactivamente ante huéspedes extranjeros para activar `plan-enviar-datos-huespedes-extranjeros.md` (`<<extend>>`).
3. **Confirmación**: Resumen visual con habitación ("Hab. 304" y tipo en gris), cantidad de huéspedes, estadía (noches y fechas en gris) y advertencia informativa del cambio de estado inminente a `Occupied` y sincronización con Módulo 2 (sin mostrar contador de extranjeros).
4. **Check-In**: Ejecución atómica y transaccional local (`@Transactional`): creación de la entidad `Stay` (con canal `source`, fechas sin hora `checkInDate`, `expectedCheckinTime`, `expectedCheckoutTime`), persistencia inmutable de `RoomGuest`, transición física de `Room.status` de `Reserved` a `Occupied`, inserción del evento en la tabla `outbox_notification`, y despacho asíncrono proactivo a RabbitMQ (`m2.habitacion.checkin.queue` / routing key `habitacion.checkin`) con los campos `eventId`, `reservationRef`, `roomId`, `checkInDate` y `foreignGuestCount` (cantidad de extranjeros registrados; los datos migratorios completos viajan de forma independiente por `m2.huespedes.extranjeros.queue`). Despliegue de pantalla de éxito con badges "Habitación: Ocupada", "Notificación a Módulo 2: Sincronizada" y botón único "Volver al inicio".

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, Spring Data JPA, Hibernate Validator, Lombok, Spring AMQP
- **Storage**: PostgreSQL 16+ (Tablas: `room`, `stay`, `room_guest`, `outbox_notification`, `room_state_audit`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL, RabbitMQ), MockRestServiceServer
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / JSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Transición atómica de `Room.status` y creación de `Stay`: < 50 ms.
  - Tiempo total de operación en mostrador: < 2 min (nacionales), < 3 min (extranjeros).
  - Lectura en copia local `daily_reservation`: < 50 ms (sin llamadas de red).
- **Constraints**:
  - No exponer horas en los campos de fecha en pantalla ni en contratos de negocio (`LocalDate` / YYYY-MM-DD).
  - Resiliencia "HTTP a Cola": la caída de red o lentitud de Módulo 2 nunca bloquea la entrega física de la llave ni la transición local a `Occupied` (garantizado por el patrón Outbox).
  - Integridad de la máquina de estados: Check-In solo procede si la habitación está en `Reserved`.
  - Inmutabilidad estricta de `RoomGuest` tras completarse la confirmación.

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-registrar-check-in.md
│   ├── spec-consultar-reservas.md
│   ├── spec-procesar-datos-huespedes.md
│   └── spec-enviar-datos-huespedes-extranjeros.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-registrar-check-in.md
    ├── plan-consultar-reservas.md
    ├── plan-procesar-datos-huespedes.md
    └── plan-enviar-datos-huespedes-extranjeros.md
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
│   │   │   └── DailyReservation.java          # Copia local de reserva del día
│   │   ├── exception/
│   │   │   ├── InvalidRoomStateException.java
│   │   │   ├── ReservationNotActiveException.java
│   │   │   ├── GuestCountMismatchException.java
│   │   │   ├── RoomCapacityExceededException.java
│   │   │   └── InvalidCheckInDateException.java
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── RegisterCheckInUseCase.java
│   │       │   └── ValidateCheckInReservationUseCase.java
│   │       └── out/
│   │           ├── RoomPersistencePort.java
│   │           ├── StayPersistencePort.java
│   │           ├── ReservationRestQueryPort.java
│   │           ├── OutboxEventPublisherPort.java
│   │           └── RoomAuditLogPort.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── CheckInExecutionService.java
│   │   │   └── CheckInValidationService.java
│   │   └── dto/
│   │       ├── CheckInValidationRequest.java
│   │       ├── CheckInValidationResponse.java
│   │       ├── RegisterCheckInCommand.java
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
│       │       │   └── OutboxNotificationRepositoryAdapter.java
│       │       ├── persistence/
│       │       │   └── JpaDailyReservationAdapter.java  # Lee de daily_reservation
│       │       └── messaging/
│       │           └── RabbitMqOutboxPublisherAdapter.java
frontend/src/
├── components/checkin/
│   ├── CheckInWizard.jsx
│   ├── Step1ValidateReservation.jsx
│   ├── Step2GuestData.jsx
│   ├── Step3Confirmation.jsx
│   └── Step4Success.jsx
└── services/
    ├── checkInService.js
    └── reservationService.js
```

**Structure Decision**: Arquitectura hexagonal limpia donde `CheckInExecutionService` orquesta la transacción atómica local, consulta Módulo 2 mediante `ReservationRestQueryPort`, delega la captura y validación de ocupantes a los casos de uso de huéspedes y emite el evento asíncrono a través de `OutboxEventPublisherPort`.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar la configuración compartida de persistencia, colas y esquema relacional en [PLAN/base/plan.md](base/plan.md).
- [ ] T002 Verificar que el esquema de `daily_reservation` y `daily_reservation_room` esté disponible (generado por `plan-consultar-reservas.md`).
- [ ] T003 Configurar el exchange `hospitua.events` y la cola de salida `m2.habitacion.checkin.queue` en RabbitMQ.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T004 Implementar la entidad de dominio `Stay` con campos: `id` (UUID), `reservationRef` (String), `roomId` (UUID), `source` (String: Directo/OTA), `checkInDate` (LocalDate), `checkOutDate` (LocalDate nullable), `expectedCheckinTime` (LocalDate), `expectedCheckoutTime` (LocalDate), `receptionistIdCheckIn` (String), `receptionistIdCheckOut` (String nullable).
- [ ] T005 Implementar la entidad de dominio inmutable `RoomGuest` con campos: `id`, `stayId`, `fullName`, `documentType`, `documentNumber`, `nationality`, `isReservationGuest`.
- [ ] T006 Implementar los puertos de salida `StayPersistencePort`, `RoomPersistencePort`, `ReservationRestQueryPort` y `OutboxEventPublisherPort`.
- [ ] T007 Implementar las excepciones de dominio para rechazos de Check-In (`InvalidRoomStateException`, `ReservationNotActiveException`, `GuestCountMismatchException`, `RoomCapacityExceededException`, `InvalidCheckInDateException`) mapeadas en `GlobalExceptionHandler`.

---

## Phase 3: User Story 1 - Admisión física de huéspedes y ocupación en inventario (Priority: P1)

**Goal**: Permitir al Recepcionista completar el flujo de 4 pasos de admisión, persistiendo la estancia física, transicionando la habitación a `Occupied`, emitiendo la notificación a Módulo 2 y mostrando la pantalla de éxito.

**Independent Test**: Simular una reserva activa obtenida desde el Panel de Recepción, completar el formulario con titular y acompañantes, confirmar y verificar que `Room.status` pasa a `Occupied`, se persiste `Stay` con `source`, se registra el evento en `outbox_notification` para RabbitMQ y se despliega la pantalla de éxito con badges "Habitación: Ocupada" y "Notificación a Módulo 2: Sincronizada".

### Tests for User Story 1

- [ ] T008 [P] [US1] Unit test para `CheckInExecutionService` verificando la transacción atómica: persistencia de `Stay`, actualización de `Room.status = Occupied`, inserción de `RoomGuest` y registro en `outbox_notification`.
- [ ] T009 [P] [US1] Integration test con Testcontainers para `POST /api/check-in` validando respuesta HTTP 201 Created con `CheckInResultDto`.
- [ ] T010 [P] [US1] Contract test para la llamada REST GET a Módulo 2 (`/api/reservations/{reservationRef}`) con `MockRestServiceServer`.
- [ ] T011 [P] [US1] Integration test del publicador Outbox verificando que el mensaje se entrega a la cola `m2.habitacion.checkin.queue` en RabbitMQ.
- [ ] T012 [P] [US1] Component test en frontend para el wizard de 4 pasos (`CheckInWizard.jsx`), validando transiciones de paso y pantalla de confirmación sin contador de extranjeros.

### Implementation for User Story 1

- [ ] T013 [P] [US1] Implementar en `CheckInValidationService` la consulta a la copia local `DailyReservationRepositoryPort.findByRef(reservationRef)` y verificación del estado físico de la habitación asignada (sin REST a M2).
- [ ] T014 [P] [US1] Implementar en `CheckInExecutionService` el método `@Transactional registerCheckIn(RegisterCheckInCommand command)`:
  - Crear y persistir la entidad `Stay`.
  - Crear y persistir la lista de `RoomGuest` asignando `isReservationGuest = true` al titular.
  - Actualizar `Room.status` de `Reserved` a `Occupied` mediante `RoomPersistencePort`.
  - Registrar entrada en `room_state_audit`.
  - Contar huéspedes extranjeros (`foreignGuestCount = guests.stream().filter(g -> !"Colombia".equals(g.nationality())).count()`).
  - Ensamblar payload JSON con `eventId`, `reservationRef`, `roomId`, `checkInDate` y `foreignGuestCount`, y guardar registro en `outbox_notification` (tipo `habitacion.checkin`).
- [ ] T015 [US1] Implementar el endpoint REST `POST /api/check-in` en `CheckInController.java`.
- [ ] T016 [US1] Implementar el endpoint REST `GET /api/check-in/validate/{reservationRef}` en `CheckInController.java` para el Paso 1.
- [ ] T017 [US1] Construir los componentes frontend en React:
  - `Step1ValidateReservation.jsx`: Renderiza código, titular, fechas (sin hora), `guestCount`, canal y estado — datos provenientes de la copia local sin llamadas REST.
  - `Step2GuestData.jsx`: Formulario con titular de solo lectura y botón "+ Agregar huésped".
  - `Step3Confirmation.jsx`: Resumen con Habitación (número y tipo en gris), Huéspedes, Estadía (noches y fechas en gris) y cuadro de advertencia informativa.
  - `Step4Success.jsx`: Pantalla de éxito con badges de validación y botón único "Volver al inicio".

---

## Phase 4: User Story 2 - Bloqueo de admisiones inválidas e integridad de estados (Priority: P2)

**Goal**: Bloquear en mostrador cualquier intento de Check-In que no cumpla las precondiciones físicas, temporales o contractuales, entregando retroalimentación informativa específica al Recepcionista.

**Independent Test**: Ejecutar pruebas unitarias y de integración suministrando reservas no activas, habitaciones en estado `Available` u ocupadas, fechas futuras/pasadas o cantidades de huéspedes que no coincidan con `guestCount`, comprobando que ninguna transacción altera la base de datos y se responde con código HTTP 400/409/422 controlado.

### Tests for User Story 2

- [ ] T018 [P] [US2] Unit test: Rechazar Check-In si `Room.status != Reserved` arrojando `InvalidRoomStateException`.
- [ ] T019 [P] [US2] Unit test: Rechazar Check-In si `registeredGuests.size() != reservation.guestCount` arrojando `GuestCountMismatchException`.
- [ ] T020 [P] [US2] Unit test: Rechazar Check-In si `registeredGuests.size() > room.maxCapacity` arrojando `RoomCapacityExceededException`.
- [ ] T021 [P] [US2] Unit test: Rechazar Check-In si `reservation.startDate != today` arrojando `InvalidCheckInDateException`.
- [ ] T022 [P] [US2] Unit test: Rechazar Check-In si `reservation.status != ACTIVE` arrojando `ReservationNotActiveException`.

### Implementation for User Story 2

- [ ] T023 [P] [US2] Añadir las validaciones preventivas en `CheckInValidationService.java`:
  - Validar estado contractual `status == ACTIVE`.
  - Validar fecha contractual `startDate.isEqual(LocalDate.now())`.
  - Validar estado físico de la habitación `room.getStatus() == RoomStatus.Reserved`.
- [ ] T024 [US2] Añadir validación estricta de concurrencia y aforo en `CheckInExecutionService.java`:
  - Validar `command.getGuests().size() == reservation.getGuestCount()`.
  - Validar `command.getGuests().size() <= room.getMaxCapacity()`.
- [ ] T025 [US2] Implementar en `Step2GuestData.jsx` la deshabilitación automática del botón "+ Agregar huésped" al alcanzar `guestCount` o `maxCapacity` y advertencias visuales en rojo ante discrepancias.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T026 Verificar que la emisión de eventos Outbox nunca filtre contraseñas ni PII sensible innecesaria en los logs de producción.
- [ ] T027 Validar el flujo de contingencia: si RabbitMQ no está disponible momentáneamente, el registro Outbox queda en estado `PENDING` y el Check-In físico del huésped no se cancela ni se revierte.
- [ ] T028 Realizar pruebas E2E del flujo completo desde el Panel de Recepción hasta la pantalla de éxito con regreso al inicio.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Room`, `Stay` y el esquema Outbox de [PLAN/base/plan.md](base/plan.md).
- **Sub-planes Requeridos**:
  - `plan-consultar-reservas.md`: Provee la copia local `daily_reservation` que se consulta en el Paso 1 (sin REST).
  - `plan-procesar-datos-huespedes.md`: Provee la lógica de captura y validación en el Paso 2.
  - `plan-enviar-datos-huespedes-extranjeros.md`: Genera registros outbox independientes por extranjero en `m2.huespedes.extranjeros.queue`.
- **Flujo de Ejecución**: Paso 1 (Validar reserva) → Paso 2 (Datos de huéspedes) → Paso 3 (Confirmación) → Paso 4 (Check-In atómico).
