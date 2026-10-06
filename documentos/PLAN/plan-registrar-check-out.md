# Implementation Plan: Registrar Check-Out

**Date**: 2026-10-06  
**Spec**: [spec-registrar-check-out.md](../SPEC/spec-registrar-check-out.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso misional **Registrar Check-Out** para el actor Recepcionista en Módulo 1, mediante un flujo guiado de 5 pasos en la interfaz de usuario:
1. **Consultar reserva**: Búsqueda 100% local sobre el repositorio de Módulo 1 (`Stay` + `Room` + `RoomGuest`), identificando al huésped titular (`isReservationGuest = true`) y el canal de origen (`source`). Valida obligatoriamente que la habitación se encuentre en estado `Occupied`, sin invocar a Módulo 2 ni desplegar el estado externo.
2. **Liquidación**: Invocación reactiva mediante REST GET a Módulo 3 (`plan-consultar-liquidacion.md`), desplegando en la cabecera superior la factura definitiva oficial (`invoiceNumber`, ej. `FAC-40001`) y el desglose de la reserva (canal `source`, comisión OTA e ingreso neto).
3. **Pago**: Visualización del resumen financiero para revisión con el huésped (factura definitiva arriba, hospedaje consolidado, IVA y total a pagar), junto con las noches reales y fechas reales de la estadía (`checkInDate` y `checkOutDate` sin horas). Cero transacciones monetarias o cobros de consumos locales en Módulo 1.
4. **Confirmación**: Exigencia obligatoria de marcar la casilla de confirmación ("Confirmo la salida del huésped y la información mostrada es correcta") para habilitar el botón de salida. Presenta la nota informativa única de contingencia ante fallas de sincronización.
5. **Liberar habitación**: Transición atómica y síncrona (`@Transactional`) de `Room.status` de `Occupied` a `PendingCleaning` (pasando de inmediato a la bandeja de trabajo del Personal de limpieza), cierre de la entidad `Stay` con `checkOutDate` y `receptionistIdCheckOut`, registro del evento en `outbox_notification`, y emisión asíncrona a RabbitMQ (`m2.habitacion.checkout.queue` / routing key `habitacion.checkout`) para actualizar la reserva a `COMPLETED`. Despliegue de pantalla de éxito con badges "Habitación: Pendiente de limpieza", "Notificación a Módulo 2: Sincronizada" y botón "Volver al inicio".

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, Spring Data JPA, Hibernate Validator, Lombok, Spring AMQP
- **Storage**: PostgreSQL 16+ (Tablas: `room`, `stay`, `room_guest`, `outbox_notification`, `room_state_audit`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL, RabbitMQ), MockRestServiceServer
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / JSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Transición atómica de `Room.status` a `PendingCleaning` y cierre de `Stay`: < 50 ms.
  - Tiempo total de operación en mostrador: < 1 min desde la recepción de la liquidación de Módulo 3.
  - Consulta reactiva REST GET a Módulo 3: timeout de 1s con degradación airosa.
- **Constraints**:
  - No exponer horas en los campos de fecha en pantalla ni en contratos de negocio (`LocalDate` / YYYY-MM-DD).
  - Cero transacciones monetarias, pasarelas de pago o cobros de consumos de minibar/restaurante en Módulo 1 (responsabilidad exclusiva de Módulo 3).
  - Bloqueo estricto del botón de confirmación en el Paso 4 si la casilla obligatoria no está marcada.
  - Disponibilidad inmediata de la habitación liberada en la bandeja del Personal de limpieza.

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-registrar-check-out.md
│   ├── spec-consultar-liquidacion.md
│   ├── spec-marcar-pendiente-a-limpieza.md
│   └── spec-consultar-panel-recepcion.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-registrar-check-out.md
    ├── plan-consultar-liquidacion.md
    └── plan-consultar-panel-recepcion.md
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
│   │   │   ├── SettlementSummary.java
│   │   │   └── CheckOutDetails.java
│   │   ├── exception/
│   │   │   ├── InvalidRoomStateException.java
│   │   │   ├── ActiveStayNotFoundException.java
│   │   │   ├── SettlementUnavailableException.java
│   │   │   └── MandatoryConfirmationMissingException.java
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── RegisterCheckOutUseCase.java
│   │       │   └── GetActiveStayUseCase.java
│   │       └── out/
│   │           ├── RoomPersistencePort.java
│   │           ├── StayPersistencePort.java
│   │           ├── SettlementRestQueryPort.java
│   │           ├── OutboxEventPublisherPort.java
│   │           └── RoomAuditLogPort.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── CheckOutExecutionService.java
│   │   │   └── ActiveStayQueryService.java
│   │   └── dto/
│   │       ├── ActiveStayResponseDto.java
│   │       ├── RegisterCheckOutCommand.java
│   │       └── CheckOutResultDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── CheckOutController.java
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── RoomRepositoryAdapter.java
│       │       │   ├── StayRepositoryAdapter.java
│       │       │   └── OutboxNotificationRepositoryAdapter.java
│       │       ├── rest/
│       │       │   └── SettlementRestAdapter.java
│       │       └── messaging/
│       │           └── RabbitMqOutboxPublisherAdapter.java
frontend/src/
├── components/checkout/
│   ├── CheckOutWizard.jsx
│   ├── Step1ActiveStay.jsx
│   ├── Step2SettlementSummary.jsx
│   ├── Step3PaymentReview.jsx
│   ├── Step4Confirmation.jsx
│   └── Step5SuccessCleaning.jsx
└── services/
    ├── checkOutService.js
    └── settlementService.js
```

**Structure Decision**: El servicio `CheckOutExecutionService` maneja de forma local y autónoma la transición de estado y el cierre de la estancia física, consumiendo la liquidación mediante `SettlementRestQueryPort` y emitiendo el evento de cierre a RabbitMQ desacoplado por el Outbox Pattern.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar los componentes transversales de persistencia y colas en [PLAN/base/plan.md](base/plan.md).
- [ ] T002 Configurar el bean `SettlementRestClient` con timeout síncrono de 1000 ms hacia Módulo 3.
- [ ] T003 Configurar la cola `m2.habitacion.checkout.queue` y binding topic con routing key `habitacion.checkout` en RabbitMQ.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T004 Implementar el modelo de dominio `SettlementSummary` con campos: `invoiceNumber`, `accommodationTotalAmount`, `source`, `otaCommissionPercentage`, `otaCommissionAmount`, `taxAmount`, `netIncomeAmount`.
- [ ] T005 Implementar el puerto de salida `SettlementRestQueryPort` y su adaptador REST `SettlementRestAdapter`.
- [ ] T006 Implementar las excepciones de dominio (`InvalidRoomStateException`, `ActiveStayNotFoundException`, `MandatoryConfirmationMissingException`) en `GlobalExceptionHandler`.

---

## Phase 3: User Story 1 - Formalización de Check-Out y liberación de habitación (Priority: P1)

**Goal**: Permitir al Recepcionista completar los 5 pasos de Check-Out a partir de los datos locales de la estancia, consultar la liquidación a Módulo 3, confirmar la salida con la casilla obligatoria, transicionar la habitación a `PendingCleaning` y emitir la notificación a Módulo 2.

**Independent Test**: Iniciar con una habitación en estado `Occupied` y estancia activa, recuperar la liquidación simulada de Módulo 3, marcar la casilla en el paso 4, confirmar y verificar que la habitación pasa a `PendingCleaning`, la estancia registra `checkOutDate` de hoy, se inserta el mensaje en `outbox_notification` para RabbitMQ y se despliega la pantalla de éxito con badges y botón "Volver al inicio".

### Tests for User Story 1

- [ ] T007 [P] [US1] Unit test para `ActiveStayQueryService` verificando recuperación 100% local desde `Stay` y `RoomGuest` sin llamadas externas a Módulo 2.
- [ ] T008 [P] [US1] Unit test para `CheckOutExecutionService` verificando la transición atómica a `PendingCleaning`, el cierre de `Stay` y el guardado en `outbox_notification`.
- [ ] T009 [P] [US1] Integration test con Testcontainers para `POST /api/check-out` verificando respuesta HTTP 200 OK y entrega en cola `m2.habitacion.checkout.queue`.
- [ ] T010 [P] [US1] Component test frontend para `CheckOutWizard.jsx` validando que el botón de confirmación permanece deshabilitado hasta marcar el checkbox.
- [ ] T011 [P] [US1] Unit test verificando que la habitación liberada queda visible de inmediato en las consultas de inventario para el Personal de limpieza.

### Implementation for User Story 1

- [ ] T012 [P] [US1] Implementar en `ActiveStayQueryService` la búsqueda de la estancia activa por `reservationRef` o `roomId`, obteniendo titular y canal `source`.
- [ ] T013 [P] [US1] Implementar en `CheckOutExecutionService` el método `@Transactional registerCheckOut(RegisterCheckOutCommand command)`:
  - Validar que `command.isConfirmed()` sea `true` (arrojar `MandatoryConfirmationMissingException` si es false).
  - Verificar que la habitación esté en estado `Occupied`.
  - Transicionar `Room.status` a `RoomStatus.PendingCleaning` (invocando caso interno de máquina de estados).
  - Actualizar `Stay` asignando `checkOutDate = LocalDate.now()` y `receptionistIdCheckOut`.
  - Registrar evento de auditoría en `room_state_audit`.
  - Guardar mensaje outbox en `outbox_notification` (tipo `habitacion.checkout`).
- [ ] T014 [US1] Implementar endpoints REST en `CheckOutController.java`:
  - `GET /api/check-out/active-stay/{reservationRef}`: Datos locales de la estancia para el Paso 1.
  - `POST /api/check-out`: Confirmación y formalización del Check-Out.
- [ ] T015 [US1] Construir los componentes frontend en React:
  - `Step1ActiveStay.jsx`: Renderiza código, titular, fechas reales, canal `source` y estado "Ocupada".
  - `Step2SettlementSummary.jsx`: Factura definitiva arriba, desglose del canal, comisión OTA e ingreso neto.
  - `Step3PaymentReview.jsx`: Factura definitiva arriba, hospedaje, IVA, total a pagar, noches reales y fechas.
  - `Step4Confirmation.jsx`: Checkbox obligatorio de confirmación y nota informativa de contingencia.
  - `Step5SuccessCleaning.jsx`: Badges "Habitación: Pendiente de limpieza", "Notificación a Módulo 2: Sincronizada" y botón "Volver al inicio".

---

## Phase 4: User Story 2 - Bloqueo de salidas inconsistentes y resiliencia de integración (Priority: P2)

**Goal**: Garantizar que el sistema rechace salidas sobre habitaciones no ocupadas o sin estancia activa, y provea contingencia operativa ante indisponibilidad de Módulo 3.

**Independent Test**: Intentar iniciar Check-Out en una habitación en estado `Available` o `InCleaning`, simulando rechazo con error 400/409; simular timeout o caída 503 de Módulo 3 y verificar que el sistema permita la liberación física excepcional informando el estado pendiente de regularización financiera.

### Tests for User Story 2

- [ ] T016 [P] [US2] Unit test: Rechazar Check-Out si `Room.status != Occupied` con `InvalidRoomStateException`.
- [ ] T017 [P] [US2] Unit test: Rechazar Check-Out si no existe estancia activa vinculada arrojando `ActiveStayNotFoundException`.
- [ ] T018 [P] [US2] Unit test: Rechazar Check-Out si `isConfirmed == false` arrojando `MandatoryConfirmationMissingException`.
- [ ] T019 [P] [US2] Integration test: Manejo de fallback cuando Módulo 3 no responde (timeout 1000 ms).

### Implementation for User Story 2

- [ ] T020 [P] [US2] Incorporar validaciones preventivas de estado y existencia en `CheckOutValidationService.java`.
- [ ] T021 [US2] Implementar en `SettlementRestAdapter` la captura de excepciones REST (`ResourceAccessException`, `HttpServerErrorException`) mapeándolas a contingencia controlada con flag `settlementPending = true`.
- [ ] T022 [US2] Implementar en la UI el banner de contingencia ante caída de Módulo 3, permitiendo al Recepcionista autorizar la liberación física a limpieza.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T023 Verificar que en las pantallas y respuestas no se expongan horas en ningún campo de fecha (`checkInDate`, `checkOutDate`).
- [ ] T024 Verificar que ante reenvíos de notificación de Check-Out por la cola RabbitMQ, Módulo 2 procese de forma idempotente.
- [ ] T025 Pruebas E2E de concurrencia: dos recepcionistas intentando el Check-Out sobre la misma habitación simultáneamente (la segunda debe fallar con colisión controlada).

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Room`, `Stay` y el esquema Outbox de [PLAN/base/plan.md](base/plan.md).
- **Sub-planes Requeridos**:
  - `plan-consultar-panel-recepcion.md`: Suministra la estancia seleccionada desde el buscador de Salidas.
  - `plan-consultar-liquidacion.md`: Provee el contrato y servicio reactivo hacia Módulo 3.
- **Flujo de Ejecución**: Paso 1 (Consultar reserva local) → Paso 2 (Liquidación M3) → Paso 3 (Pago informativo) → Paso 4 (Confirmación con checkbox) → Paso 5 (Liberar habitación a `PendingCleaning`).
