# Implementation Plan: Registrar Check-Out

**Date**: 2026-10-09  
**Spec**: [spec-registrar-check-out.md](../SPEC/spec-registrar-check-out.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso misional **Registrar Check-Out** para el actor Recepcionista en Módulo 1, mediante un flujo guiado de 5 pasos en la interfaz de usuario:

1. **Consultar reserva**: Búsqueda 100% local sobre el repositorio de Módulo 1 (`Stay` + `Room` + `RoomGuest`), identificando al huésped titular (`Stay.titularFirstName`, `Stay.titularLastName`) y el canal de origen (`Stay.source`). Valida obligatoriamente que la habitación se encuentre en estado `Occupied`, sin invocar a Módulo 2 ni desplegar estados externos.
2. **Liquidación**: El frontend la obtiene con `GET /api/stays/{stayId}/settlement` (`plan-consultar-liquidacion.md`, Endpoint 2), que es quien consulta a Módulo 3; el backend de este plan no llama a Módulo 3. La liquidación es siempre informativa (`settlementType = INFORMATIVE`, `invoice = null`), porque Módulo 3 genera la final y la factura al procesar el evento de check-out (Contrato 2), que se publica después; como Módulo 3 calcula siempre con lo cotizado, coincide con la final y se muestra como valor exacto. Se despliega la fuente local (`Stay.source`), comisión OTA ("No aplica" en reserva directa) e ingreso neto (`netIncome`).
3. **Pago**: Visualización del resumen financiero de la liquidación informativa para revisión con el huésped: hospedaje (`lodgingAmount` devuelto directamente por Módulo 3, sin recálculo local en Módulo 1), noches y fechas reales (`checkInDate` y `checkOutDate` sin horas), con la nota "La factura se emite al confirmar la salida". Cero transacciones monetarias o cobros de consumos locales en Módulo 1.
4. **Confirmación**: Exigencia obligatoria de marcar la casilla de confirmación ("Confirmo la salida del huésped y la información mostrada es correcta") para habilitar el botón de salida. Presenta la nota informativa única de contingencia ante fallas de sincronización (sin jerga técnica ni mención de colas o módulos).
5. **Liberar habitación**: Transición atómica y síncrona (`@Transactional`) de `Room.status` de `Occupied` a `PendingCleaning` (pasando de inmediato a la bandeja de trabajo del Personal de limpieza), cierre de la entidad `Stay` con `checkOutDate`, registro de dos mensajes en `outbox_notification` dentro de la misma transacción, y su emisión asíncrona a RabbitMQ: a Módulo 2 (`m2.habitacion.checkout.queue` / routing key `habitacion.checkout`) el mensaje plano unificado con todos los huéspedes en `guests[]` (reutilizando `originPlace`/`destinationPlace` registrados en `RoomGuest` durante el Check-In), y a Módulo 3 (exchange `hospitua.events`, routing key `facturacion.checkout`) el evento de check-out para facturación (Contrato 2). Despliegue de pantalla de éxito con badges "Habitación: Pendiente de limpieza", estado de notificación enviada y botón "Volver al inicio".

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 4.1+, Spring Web MVC, Spring Data JPA, Hibernate Validator, Lombok, Spring AMQP
- **Storage**: PostgreSQL 16+ (Tablas: `room`, `stay`, `room_guest`, `outbox_notification`, `room_state_history`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL, RabbitMQ)
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / TypeScript, TSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Transición atómica de `Room.status` a `PendingCleaning` y cierre de `Stay`: < 50 ms.
  - Tiempo total de operación en mostrador: < 1 min desde la recepción de la liquidación de Módulo 3.
- **Constraints**:
  - No exponer horas en los campos de fecha en pantalla ni en contratos de negocio (`LocalDate` / YYYY-MM-DD).
  - Cero transacciones monetarias, pasarelas de pago o cobros de consumos de minibar/restaurante en Módulo 1 (responsabilidad exclusiva de Módulo 3).
  - Bloqueo estricto del botón de confirmación en el Paso 4 si la casilla obligatoria no está marcada.
  - Disponibilidad inmediata de la habitación liberada en la bandeja del Personal de limpieza.
  - Mensaje unificado plano hacia `m2.habitacion.checkout.queue` con todos los ocupantes en `guests[]`; sin colas separadas de extranjeros.
  - Segundo mensaje, en la misma transacción (outbox), hacia Módulo 3 con routing key `facturacion.checkout` (`EventEnvelope` sin datos de huéspedes ni importes).

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
    ├── plan-marcar-pendiente-a-limpieza.md
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
│   │   │   ├── RoomGuest.java
│   │   │   ├── CheckOutEligibility.java   # Reglas del Paso 1 (estancia activa, habitación Occupied); sin consultas
│   │   │   └── CheckOutDetails.java   # Sin uso asignado
│   │   └── exception/
│   │       ├── ActiveStayNotFoundException.java
│   │       └── MandatoryConfirmationMissingException.java
│   ├── application/
│   │   ├── ports/
│   │   │   ├── in/
│   │   │   │   ├── RegisterCheckOutUseCase.java
│   │   │   │   ├── GetActiveStayUseCase.java
│   │   │   │   └── MarkPendingCleaningUseCase.java   # Definido en plan-marcar-pendiente-a-limpieza.md; aquí se invoca
│   │   │   └── out/
│   │   │       ├── RoomRepositoryPort.java
│   │   │       ├── StayRepositoryPort.java
│   │   │       ├── RoomGuestRepositoryPort.java
│   │   │       ├── RoomAuditLogRepositoryPort.java   # Definido en el plan base (T016)
│   │   │       └── OutboxEventPublisherPort.java
│   │   ├── service/
│   │   │   ├── CheckOutExecutionService.java
│   │   │   └── ActiveStayQueryService.java
│   │   └── dto/
│   │       ├── ActiveStayResponseDto.java
│   │       ├── RegisterCheckOutCommand.java
│   │       ├── CheckOutResultDto.java
│   │       ├── CheckOutMessagePayload.java   # Mensaje plano de check-out; reutiliza GuestMessageDto de plan-registrar-check-in.md
│   │       └── CheckOutBillingEventPayload.java   # payload del evento de facturación para Módulo 3 (Contrato 2)
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   └── web/
│       │   │       ├── CheckOutController.java
│       │   │       └── CurrentUserResolver.java   # Definido en el plan base (T020); recepcionista del check-out
│       │   └── out/
│       │       ├── persistence/
│       │       │   └── adapter/
│       │       │       ├── RoomRepositoryAdapter.java
│       │       │       ├── StayRepositoryAdapter.java
│       │       │       ├── RoomGuestRepositoryAdapter.java
│       │       │       └── OutboxNotificationRepositoryAdapter.java
│       │       └── messaging/
│       │           └── RabbitMqOutboxPublisherAdapter.java
frontend/src/
├── pages/checkout/
│   ├── CheckOutWizard.tsx
│   ├── Step1ActiveStay.tsx
│   ├── Step2SettlementSummary.tsx
│   ├── Step3PaymentReview.tsx
│   ├── Step4Confirmation.tsx
│   └── Step5SuccessCleaning.tsx
└── api/
    ├── checkOutService.ts
    └── settlementService.ts
```

**Structure Decision**: El servicio `CheckOutExecutionService` maneja de forma local y autónoma la transición de estado y el cierre de la estancia física, sin llamar a Módulo 3: el frontend obtiene la liquidación con `GET /api/stays/{stayId}/settlement` (`plan-consultar-liquidacion.md`) y `POST /api/check-out` no recibe datos de la liquidación. Emite dos mensajes a RabbitMQ desacoplados por el Outbox Pattern: la notificación plana a Módulo 2 (Contrato 1) y el evento de facturación a Módulo 3 (Contrato 2).

---

## Contratos de integración

### Contrato 1 — Notificación de Check-Out (Módulo 1 → Módulo 2)

**Cola y routing key:**

```text
Cola: m2.habitacion.checkout.queue
Routing key: habitacion.checkout
```

**Campos del mensaje:**

| Nombre | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `messageId` | UUID | Sí | Identificador único del mensaje; se conserva idéntico en reintentos para la idempotencia del receptor |
| `sequenceNumber` | entero | Sí | Número de secuencia creciente por cola |
| `reservationRef` | string | Sí | Referencia de la reserva (ej. `RSV-8D02E5A4`) |
| `roomId` | UUID | Sí | Identificador de la habitación |
| `movementType` | string | Sí | Tipo de movimiento; para el Check-Out es siempre `DEPARTURE` |
| `movementDate` | fecha (YYYY-MM-DD) | Sí | Fecha de salida; igual a `checkOutDate` |
| `guests[]` | array | Sí | Totalidad de los huéspedes que ocuparon la habitación (nacionales y extranjeros) |

**Campos de cada elemento de `guests[]`:** misma estructura que la notificación de Check-In; `originPlace` y `destinationPlace` se reutilizan de los valores registrados en `RoomGuest` durante el Check-In.

| Nombre | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `firstName` | string | Sí | Nombres del huésped |
| `lastName` | string | Sí | Apellidos del huésped |
| `documentType` | string | Sí | Tipo de documento: `RC`, `TI`, `CC`, `CE`, `PAS` o `NIT` |
| `documentNumber` | string | Sí | Número de documento de identidad |
| `birthDate` | fecha (YYYY-MM-DD) | Sí | Fecha de nacimiento |
| `nationality` | string | Sí | Nacionalidad del huésped |
| `originPlace` | string | Solo si `nationality ≠ Colombia` | Lugar de procedencia reutilizado del Check-In, formato "Ciudad, País" |
| `destinationPlace` | string | Solo si `nationality ≠ Colombia` | Lugar de destino reutilizado del Check-In, formato "Ciudad, País" |

**Ejemplo de mensaje (misma estructura que Check-In, con `DEPARTURE`):**

```json
{
  "messageId": "UUIDv4",
  "sequenceNumber": 1,
  "reservationRef": "RSV-8D02E5A4",
  "roomId": "uuid",
  "movementType": "DEPARTURE",
  "movementDate": "2026-10-13",
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

### Contrato 2 — Evento de check-out para facturación (Módulo 1 → Módulo 3)

Se publica en la misma transacción que el Contrato 1, con tipo de outbox `event_type = CHECK_OUT_BILLING`. Con él Módulo 3 genera la liquidación final y la factura (`spec-registrar-check-out.md`, FR-016).

**Exchange y routing key:**

```text
Exchange: hospitua.events
Routing key: facturacion.checkout
Cola (de Módulo 3): modulo3.checkout
```

**Sobre (`EventEnvelope`):**

| Nombre | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `eventId` | UUID | Sí | `message_id` de la fila del outbox; se conserva idéntico en reintentos |
| `eventType` | string | Sí | Siempre `CHECK_OUT` |
| `occurredAt` | fecha y hora ISO 8601 | Sí | `created_at` de la fila del outbox, con offset `-05:00` |
| `sourceModule` | string | Sí | Siempre `MODULE_1` |
| `payload` | objeto | Sí | Datos del check-out (tabla siguiente) |

**Campos de `payload`:**

| Nombre | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `stayId` | UUID | Sí | `Stay.id`; clave de idempotencia en Módulo 3 |
| `reservationRef` | string | Sí | `Stay.reservationRef` |
| `roomId` | UUID | Sí | `Room.id` |
| `categoryRoom` | string | Sí | `Room.categoryRoom` |
| `checkInDate` | fecha (YYYY-MM-DD) | Sí | `Stay.checkInDate` |
| `checkOutDate` | fecha (YYYY-MM-DD) | Sí | Fecha del check-out (regla 6 del plan base) |
| `billingCustomer` | objeto | Sí | Siempre el titular: `name` = `titularFirstName` + " " + `titularLastName`, `taxId` = `titularDocumentNumber`. La factura va a su nombre y no se corrige en Módulo 3 |

Sin canal, importes, datos de la OTA ni datos migratorios o de huéspedes.

**Ejemplo de mensaje:**

```json
{
  "eventId": "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b",
  "eventType": "CHECK_OUT",
  "occurredAt": "2026-10-11T11:20:40-05:00",
  "sourceModule": "MODULE_1",
  "payload": {
    "stayId": "7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f",
    "reservationRef": "RSV-8D02E5A4",
    "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "categoryRoom": "DOBLE",
    "checkInDate": "2026-10-09",
    "checkOutDate": "2026-10-11",
    "billingCustomer": {
      "name": "Ana Pérez",
      "taxId": "123"
    }
  }
}
```

---

## Endpoints REST internos (Frontend → Módulo 1)

Los usa el Recepcionista (FR-001); el recepcionista responsable es el usuario actual, que se toma del encabezado `X-User` (regla 14 del plan base); solo lo usa el Endpoint 2. Los errores usan `ApiError` del plan base. La liquidación de los pasos 2 y 3 se obtiene con `GET /api/stays/{stayId}/settlement` (`plan-consultar-liquidacion.md`, Endpoint 2).

### Endpoint 1 — Estancia activa de la habitación (Paso 1)

```http
GET /api/check-out/active-stay?reservationRef=RSV-8D02E5A4&roomId=1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d
```

| Parámetro | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `reservationRef` | string | Sí | Referencia que entrega el botón "Check-out" del panel de recepción (FR-003); máximo 100 caracteres, sin espacios al inicio ni al final |
| `roomId` | UUID | Sí | Habitación seleccionada; con la referencia identifica la estancia, porque una reserva puede tener varias habitaciones |

Lee solo `Stay`, `Room` y `RoomGuest`, sin llamar a Módulo 2 (FR-003). Verifica, en orden, que el par tenga una estancia activa (404), que la habitación exista (404 `ROOM_NOT_FOUND`) y que esté `Occupied` (409). `guests[]` lleva solo lo que muestra el Paso 1 (sin `birthDate` ni lugares), con el titular primero si se aloja y los demás por apellido y nombre. Respuesta `200 OK` (`ActiveStayResponseDto`):

```json
{
  "stayId": "7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f",
  "reservationRef": "RSV-8D02E5A4",
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "roomNumber": "204",
  "categoryRoom": "DOBLE",
  "roomStatus": "Occupied",
  "titularFirstName": "Ana",
  "titularLastName": "Pérez",
  "checkInDate": "2026-10-09",
  "expectedCheckoutDate": "2026-10-11",
  "source": "BOOKING",
  "guests": [
    { "firstName": "Ana", "lastName": "Pérez", "documentType": "CC", "documentNumber": "123", "nationality": "Colombia" },
    { "firstName": "John", "lastName": "Smith", "documentType": "PAS", "documentNumber": "X99", "nationality": "Estados Unidos" }
  ]
}
```

### Endpoint 2 — Registrar el Check-Out (confirmación del Paso 4)

```http
POST /api/check-out
X-User: aruiz
Content-Type: application/json
```

```json
{
  "stayId": "7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f",
  "confirmed": true
}
```

| Campo | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `stayId` | UUID | Sí | Estancia del Paso 1 |
| `confirmed` | boolean | Sí | Casilla obligatoria del Paso 4; debe ser `true` (FR-008) |

Los huéspedes de la notificación no se envían: se reutilizan de `RoomGuest` (Contrato 1). Respuesta `200 OK` (`CheckOutResultDto`):

```json
{
  "stayId": "7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f",
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "roomNumber": "204",
  "roomStatus": "PendingCleaning",
  "checkOutDate": "2026-10-11"
}
```

La respuesta no depende de Módulo 2 ni de Módulo 3: los dos mensajes quedan en el outbox (FR-014).

### Errores de los dos endpoints

| Status Code | errorCode | Excepción | Cuándo ocurre | Endpoint |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base); la interfaz muestra "Tu sesión terminó. Inicia sesión de nuevo." | 2 |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Falta un parámetro o campo, o tiene formato inválido | 1 y 2 |
| 400 | `CONFIRMATION_REQUIRED` | `MandatoryConfirmationMissingException` | `confirmed` no es `true` (FR-008) | 2 |
| 404 | `ACTIVE_STAY_NOT_FOUND` | `ActiveStayNotFoundException` | No hay una estancia activa para ese par o ese `stayId` | 1 y 2 |
| 409 | `ROOM_INVALID_STATE` | `InvalidRoomTransitionException` | La habitación no está en `Occupied`; `details.currentStatus` lleva el estado actual (FR-004) | 1 y 2 |
| 409 | `CONCURRENT_UPDATE` | `ObjectOptimisticLockingFailureException` | Otra operación cambió la habitación al mismo tiempo (control optimista, regla 13 del plan base, último párrafo); se puede reintentar | 2 |

```json
{
  "errorCode": "ACTIVE_STAY_NOT_FOUND",
  "message": "No hay una estancia activa de la reserva RSV-8D02E5A4 en esta habitación.",
  "timestamp": "2026-10-11T11:20:40-05:00",
  "path": "/api/check-out/active-stay",
  "details": { "reservationRef": "RSV-8D02E5A4", "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d" }
}
```

---

## Phase 1: Setup (Shared Infrastructure)

- [x] T001 Verificar los componentes transversales de persistencia y colas en [PLAN/base/plan.md](base/plan.md).
  - Hecho (cierre de R7): persistencia (`Stay`, `RoomGuest`, `outbox_notification`) y colas del plan base, ejercitadas por `CheckOutRegistrationIntegrationTest`.
- [x] T002 Verificar la cola `m2.habitacion.checkout.queue` y su binding con routing key `habitacion.checkout`, y la publicación con la routing key `facturacion.checkout` para Módulo 3 con `mandatory = true` (plan base, T011 y T012).
  - Hecho (cierre de R7): `RabbitTopologyIntegrationTest` (la cola `m2.habitacion.checkout.queue` recibe `habitacion.checkout`; `facturacion.checkout` sin cola se devuelve con `mandatory = true`).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [x] T003 Implementar las excepciones de dominio (`ActiveStayNotFoundException`, `MandatoryConfirmationMissingException`) en `GlobalExceptionHandler`, con los códigos de la tabla "Errores de los dos endpoints". `InvalidRoomTransitionException`, `ObjectOptimisticLockingFailureException` y los códigos comunes vienen del catálogo de errores del plan base.
  - Hecho: `ActiveStayNotFoundException` (404 `ACTIVE_STAY_NOT_FOUND`), creada con su primer consumidor, la consulta de la liquidación (L14), con el constructor del par en L15; `MandatoryConfirmationMissingException` (400 `CONFIRMATION_REQUIRED`, L16). Sin `@ExceptionHandler` nuevos.

---

## Phase 3: User Story 1 - Formalización de Check-Out y liberación de habitación (Priority: P1)

**Goal**: Permitir al Recepcionista completar los 5 pasos de Check-Out a partir de los datos locales de la estancia, consultar la liquidación a Módulo 3, confirmar la salida con la casilla obligatoria, transicionar la habitación a `PendingCleaning`, emitir la notificación plana a Módulo 2 con todos los huéspedes y el evento de facturación a Módulo 3.

**Independent Test**: Iniciar con una habitación en estado `Occupied` y estancia activa, recuperar la liquidación simulada de Módulo 3, marcar la casilla en el paso 4, confirmar y verificar que la habitación pasa a `PendingCleaning`, la estancia registra `checkOutDate` de hoy, se inserta el mensaje en `outbox_notification` para RabbitMQ (`m2.habitacion.checkout.queue`) conteniendo `movementType = DEPARTURE`, `movementDate = checkOutDate` y el array `guests[]` completo, se inserta en la misma transacción el evento del Contrato 2 (`facturacion.checkout`), y se despliega la pantalla de éxito con badges y botón "Volver al inicio".

### Tests for User Story 1

- [x] T004 [P] [US1] Unit test para `ActiveStayQueryService` verificando recuperación 100% local desde `Stay`, `Room` y `RoomGuest` sin llamadas externas a Módulo 2.
  - Hecho (L15): `ActiveStayQueryServiceTest`; el servicio solo depende de los puertos de `Stay`, `Room` y `RoomGuest`, así que no puede llamar a Módulo 2.
- [x] T005 [P] [US1] Unit test para `CheckOutExecutionService` verificando la transición atómica a `PendingCleaning`, el cierre de `Stay` y la serialización del payload plano unificado en `outbox_notification`.
  - Hecho (L16): `CheckOutExecutionServiceTest` (bloqueo, estancia cerrada con su `version`, `PendingCleaning`, auditoría y los dos mensajes, en ese orden).
- [x] T006 [P] [US1] Integration test con Testcontainers para `POST /api/check-out` verificando respuesta HTTP 200 OK y entrega en cola `m2.habitacion.checkout.queue` con formato `CheckOutMessagePayload` (`movementType` = `MovementType.DEPARTURE`), y la fila del Contrato 2 (`event_type = CHECK_OUT_BILLING`, routing key `facturacion.checkout`) con `eventId` = `message_id`, `occurredAt` con offset `-05:00` y `billingCustomer` del titular.
  - Hecho (L16): `CheckOutRegistrationIntegrationTest` revisa las dos filas de `outbox_notification` (el worker está apagado en el perfil de pruebas). El sobre del Contrato 2 (`eventId` = `message_id`, `occurredAt` con `-05:00`) lo prueba `OutboxMessageSerializerTest`.
- [ ] T007 [P] [US1] Component test frontend para `CheckOutWizard.tsx` validando que el botón de confirmación permanece deshabilitado hasta marcar el checkbox del paso 4.
- [x] T008 [P] [US1] Integration test verificando que tras el check-out la habitación queda en `PendingCleaning` (su aparición en el panel de limpieza la prueba `plan-consultar-panel-limpieza.md`, T012).
  - Hecho (L16): `CheckOutRegistrationIntegrationTest`, a partir de un Check-In real: habitación `PendingCleaning` con el periodo `CHECK_OUT` del recepcionista en el historial.

### Implementation for User Story 1

- [x] T009 [P] [US1] Implementar en `ActiveStayQueryService` la búsqueda de la estancia activa por el par `reservationRef` y `roomId` (una reserva puede tener varias habitaciones, cada una con su `Stay`), obteniendo titular (`Stay.titularFirstName`, `Stay.titularLastName`), huéspedes vinculados y canal `Stay.source`.
  - Hecho (L15): `ActiveStayQueryService` (solo lectura) con las reglas de `CheckOutEligibility` (dominio) y `StayRepositoryPort.findByReservationRefAndRoomId`; pruebas en `CheckOutEligibilityTest` y `CheckOutActiveStayIntegrationTest`.
- [x] T010 [P] [US1] Implementar en `CheckOutExecutionService` el método `@Transactional registerCheckOut(RegisterCheckOutCommand command)`:
  - Hecho (L16): `confirmed` primero; estancia activa por `stayId`; `lockById` de la habitación y `CheckOutEligibility.verify` (L15) con la fila bloqueada; `Stay.checkOut(hoy)` conserva la `version` (control optimista); `MarkPendingCleaningUseCase` (`CHECK_OUT`); auditoría con `stayId`, `reservationRef` y `checkOutDate` (el `stayId` es también la referencia de la liquidación); los dos mensajes en el Outbox. `GuestMessageDto` quedó en su propio archivo y sale del `RoomGuest`, igual para el Check-In y el Check-Out. `MovementType.EXIT` (L12) pasó a `DEPARTURE`, el valor del Contrato 1 y del plan base; no tenía usos.
  - Validar que `command.isConfirmed()` sea `true` (arrojar `MandatoryConfirmationMissingException` si es false).
  - Verificar que la habitación esté en estado `Occupied`.
  - Transicionar `Room.status` a `RoomStatus.PendingCleaning` invocando `MarkPendingCleaningUseCase` con `sourceFlow = CHECK_OUT` y el recepcionista (usuario actual) como `actorId`.
  - Actualizar `Stay` asignando `checkOutDate = LocalDate.now(clock)` (regla 6 del plan base).
  - Construir el payload plano para outbox:

    ```json
    {
      "messageId": "UUIDv4",
      "sequenceNumber": 1,
      "reservationRef": "RES-000123",
      "roomId": "room-uuid",
      "movementType": "DEPARTURE",
      "movementDate": "YYYY-MM-DD",
      "guests": [
        {
          "firstName": "string",
          "lastName": "string",
          "documentType": "string",
          "documentNumber": "string",
          "nationality": "string",
          "birthDate": "YYYY-MM-DD",
          "originPlace": "Ciudad, País",
          "destinationPlace": "Ciudad, País"
        }
      ]
    }
    ```

    `birthDate` se incluye para todos los huéspedes; `originPlace` y `destinationPlace` únicamente si `nationality != "Colombia"`. Todos se reutilizan de los registros de Check-In (`RoomGuest`).
  - Registrar en `room_audit_log` el evento `CHECK_OUT` con `stayId`, `reservationRef`, recepcionista, `checkOutDate` y la referencia de la liquidación (el `stayId`), mediante `RoomAuditLogRepositoryPort` (FR-015).
  - Encolarlo con `OutboxEventPublisherPort.enqueue("habitacion.checkout", "CHECK_OUT", payload)` dentro de la misma transacción; `messageId` y `sequenceNumber` los asigna el outbox (plan base, T012).
  - Encolar también el Contrato 2 con `OutboxEventPublisherPort.enqueue("facturacion.checkout", "CHECK_OUT_BILLING", payload)` en la misma transacción, con `stayId`, `reservationRef`, `roomId`, `categoryRoom`, `checkInDate`, `checkOutDate` y `billingCustomer` del titular (FR-016); `eventId` es el `message_id` de la fila y `occurredAt` su `created_at` con offset `-05:00`.
- [x] T011 [US1] Implementar endpoints REST en `CheckOutController.java`:
  - Hecho: `GET /api/check-out/active-stay` (L15) y `POST /api/check-out` (L16), que responde 200 y toma el recepcionista de `X-User`; `RegisterCheckOutRequest` exige `stayId` y `confirmed` (si falta, `400 VALIDATION_ERROR`).
  - `GET /api/check-out/active-stay?reservationRef={ref}&roomId={roomId}`: Datos locales de la estancia de esa habitación para el Paso 1 (los mismos parámetros que envía el botón "Check-out" del panel); responde 404 si no hay una estancia activa para ese par (Endpoint 1).
  - `POST /api/check-out`: Confirmación y formalización del Check-Out (Endpoint 2).
- [ ] T012 [US1] Construir los componentes frontend en React:
  - `Step1ActiveStay.tsx` (vista `vistas/recepcionista/06-checkout-1-reserva.html`): Renderiza código, titular, fechas reales, canal `source` y estado "Ocupada". Sin barra de búsqueda propia ni estado de M2.
  - `Step2SettlementSummary.tsx` (vista `vistas/recepcionista/07-checkout-2-liquidacion.html`): Muestra la liquidación informativa como valor exacto: canal local `Stay.source`, comisión OTA ("No aplica" en reserva directa) e ingreso neto.
  - `Step3PaymentReview.tsx` (vista `vistas/recepcionista/07b-checkout-2b-pago.html`): Hospedaje de M3 (sin recálculo local), noches y fechas, con la nota "La factura se emite al confirmar la salida".
  - `Step4Confirmation.tsx` (vista `vistas/recepcionista/08-checkout-3-confirmacion.html`): Checkbox obligatorio de confirmación y nota informativa de contingencia sin jerga técnica.
  - `Step5SuccessCleaning.tsx` (vista `vistas/recepcionista/09-checkout-4-exito.html`): Badges "Habitación: Pendiente de limpieza", estado de notificación enviada y botón "Volver al inicio".

---

## Phase 4: User Story 2 - Bloqueo de salidas inconsistentes y resiliencia de integración (Priority: P2)

**Goal**: Garantizar que el sistema rechace salidas sobre habitaciones no ocupadas o sin estancia activa, y provea contingencia operativa ante indisponibilidad de Módulo 3.

**Independent Test**: Intentar iniciar Check-Out en una habitación en estado `Available` o `InCleaning`, simulando rechazo con error 400/409; simular timeout o caída de Módulo 3 y verificar que el sistema permita la liberación física excepcional informando el estado pendiente de regularización financiera.

### Tests for User Story 2

- [x] T013 [P] [US2] Unit test: Rechazar Check-Out si `Room.status != Occupied` con `InvalidRoomTransitionException`.
  - Hecho (L16): `CheckOutEligibilityTest`, `CheckOutExecutionServiceTest` y la integración (409 con `details.currentStatus`).
- [x] T014 [P] [US2] Unit test: Rechazar Check-Out si no existe estancia activa vinculada arrojando `ActiveStayNotFoundException`.
  - Hecho (L16): `CheckOutExecutionServiceTest` y la integración (404, también en un segundo Check-Out de la misma estancia).
- [x] T015 [P] [US2] Unit test: Rechazar Check-Out si `isConfirmed == false` arrojando `MandatoryConfirmationMissingException`.
  - Hecho (L16): `CheckOutExecutionServiceTest` y la integración (400 `CONFIRMATION_REQUIRED` sin leer ni escribir nada).
- [x] T016 [P] [US2] Integration test: con la liquidación en `UNAVAILABLE` (`plan-consultar-liquidacion.md`), `POST /api/check-out` registra la salida y libera la habitación a `PendingCleaning`.
  - Hecho (L16): la integración corre sin Módulo 3; `POST /api/check-out` no lo consulta, así que una liquidación `UNAVAILABLE` no lo bloquea.

### Implementation for User Story 2

- [x] T017 [P] [US2] Incorporar validaciones preventivas de estado y existencia en `CheckOutValidationService.java`.
  - Hecho (L15/L16): las validaciones de existencia y estado viven en `CheckOutEligibility.verify` (dominio, sin consultas); el Paso 1 las aplica con lo que leyó y el registro las repite con la habitación bloqueada. No hizo falta `CheckOutValidationService`.
- [ ] T018 [US2] Implementar en la UI el banner de contingencia ante caída de Módulo 3, permitiendo al Recepcionista autorizar la liberación física a limpieza sin bloquear al huésped.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T019 Verificar que en las pantallas y respuestas no se expongan horas en ningún campo de fecha (`checkInDate`, `checkOutDate`).
  - Hecho (L16): `checkInDate`, `checkOutDate` y `expectedCheckoutDate` son `LocalDate` en las respuestas y en los mensajes (`YYYY-MM-DD`).
- [x] T020 Verificar que ante reenvíos de notificación de Check-Out por la cola RabbitMQ, se mantenga el mismo `messageId` (Contrato 1) y el mismo `eventId` (Contrato 2) para garantizar procesamiento idempotente en Módulo 2 y en Módulo 3.
  - Hecho: `OutboxTransactionalIntegrationTest` ("se reintenta, conserva messageId") cubre a todos los tipos del Outbox; el `eventId` del Contrato 2 es ese mismo `message_id`.
- [ ] T021 Pruebas E2E de concurrencia: dos recepcionistas intentando el Check-Out sobre la misma habitación simultáneamente (la segunda debe fallar con colisión controlada).
  - Avance (L16): el bloqueo de la fila serializa dos Check-Out de la misma habitación; el segundo ve la habitación ya en `PendingCleaning` (409) o la estancia cerrada (404). Falta la prueba E2E con dos peticiones simultáneas (L38).

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Room`, `Stay`, `RoomGuest` y el esquema Outbox de [PLAN/base/plan.md](base/plan.md).
- **Sub-planes Requeridos**:
  - `plan-consultar-panel-recepcion.md`: Suministra la estancia seleccionada desde el buscador de Salidas.
  - `plan-consultar-liquidacion.md`: Provee el contrato y servicio reactivo hacia Módulo 3 (`GET /api/settlements`) y el Endpoint 2 que consume el frontend.
  - `plan-marcar-pendiente-a-limpieza.md`: `MarkPendingCleaningUseCase` con `sourceFlow = CHECK_OUT` y el recepcionista como `actorId`; verificar que el historial registra ese actor.
- **Flujo de Ejecución**: Paso 1 (Consultar reserva local) → Paso 2 (Liquidación M3) → Paso 3 (Pago informativo) → Paso 4 (Confirmación con checkbox) → Paso 5 (Liberar habitación a `PendingCleaning`).
