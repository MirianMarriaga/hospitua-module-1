# Implementation Plan: Enviar Datos de Huéspedes Extranjeros

**Date**: 2026-10-06  
**Spec**: [spec-enviar-datos-huespedes-extranjeros.md](../SPEC/spec-enviar-datos-huespedes-extranjeros.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso extendido (`<<extend>>`) **Enviar Datos de Huéspedes Extranjeros**, activado condicionalmente desde `plan-procesar-datos-huespedes.md` cuando uno o más ocupantes registrados poseen una nacionalidad distinta de "Colombia".

Responsabilidades:
1. **Asignación automática y transparente**:
   - `movementType`: Fijado automáticamente como `ENTRY` en Check-In y `DEPARTURE` en Check-Out.
   - `movementDate`: Asignado como `checkInDate` (Check-In) o `checkOutDate` (Check-Out) de la Estancia (solo fecha, `LocalDate`, sin hora).
   - No solicitar estos campos técnicos migratorios en pantalla al Recepcionista.
2. **Empaquetado de los 10 campos SIRE (Acuerdo B15) por mensaje**:
   - Identidad: `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`.
   - Tránsito: Procedencia (`originPlace`) y destino (`destinationPlace`) aceptando texto libre en formato `"Ciudad, País"` (ej. `"Madrid, España"`), asistidos opcionalmente por datalist/catálogo sugerido.
   - Parámetros del movimiento: `movementType` (`ENTRY` o `DEPARTURE`) y `movementDate` (`checkInDate` o `checkOutDate`).
   - Metadatos de mensaje: `messageId`, `sequenceNumber`, `reservationRef`, `roomId`.
3. **Cola separada — 1 mensaje por extranjero**:
   - Despachar **un mensaje individual** por cada huésped extranjero a la cola `m2.huespedes.extranjeros.queue`.
   - La notificación de habitación viaja de forma **independiente y paralela** por `m2.habitacion.checkin.queue` o `m2.habitacion.checkout.queue`, llevando el campo `foreignGuestCount`.
   - Almacenar cada registro en `outbox_notification` de forma atómica dentro de la transacción del Check-In o Check-Out físico (`@Transactional`).
4. **Reutilización de datos en Check-Out**:
   - En la salida, `originPlace` y `destinationPlace` se toman de los datos capturados durante el Check-In y registrados en `RoomGuest`, asociando `movementType = DEPARTURE` y `movementDate = checkOutDate`.
5. **Resiliencia desacoplada**:
   - El envío es completamente asíncrono y no bloqueante.
   - Si Módulo 2 o RabbitMQ experimentan demoras o caídas, la entrega física de la llave y el cambio a `Occupied` (o `PendingCleaning`) se mantienen firmes; el despachador de Outbox reintenta con backoff exponencial.
   - En el resumen visual del paso 3 no se expone el conteo de extranjeros (para preservar la privacidad del huésped en mostrador).

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Data JPA, Spring AMQP, Jackson, Lombok
- **Storage**: PostgreSQL 16+ (Persistencia transaccional en tabla `outbox_notification`)
- **Messaging**: RabbitMQ (Exchange `hospitua.events`, colas durables, Publisher Confirms)
- **Testing**: JUnit 5, Mockito, Testcontainers (RabbitMQ, PostgreSQL)
- **Target Platform**: Servidor Linux/Windows
- **Project Type**: Web Application backend service
- **Performance Goals**:
  - Ensamblado del paquete migratorio < 5 ms.
  - Inserción en outbox atómica junto al Check-In < 15 ms.
- **Constraints**:
  - `originPlace` y `destinationPlace` deben admitir texto libre con coma (`"Ciudad, País"`, ej. `"Madrid, España"`).
  - No exponer ni contar a los extranjeros en el resumen visual del Paso 3.
  - El paquete migratorio requiere un `checkInDate` o `checkOutDate` válido; si no existe, no se despacha.
  - Cada extranjero origina **un mensaje independiente** en `m2.huespedes.extranjeros.queue` (no agregado).
  - Los campos de identidad usan `firstName` y `lastName` por separado (sin `fullName` en el contrato de cola).

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-enviar-datos-huespedes-extranjeros.md
│   ├── spec-procesar-datos-huespedes.md
│   └── spec-registrar-check-in.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-enviar-datos-huespedes-extranjeros.md
    ├── plan-procesar-datos-huespedes.md
    └── plan-registrar-check-in.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── ForeignGuestData.java            # VO migratorio: firstName, lastName, 10 campos SIRE
│   │   │   ├── MovementType.java                # Enum: ENTRY / DEPARTURE
│   │   │   └── ForeignMigratoryPackage.java     # Agregado de mensajes por extranjero
│   │   └── ports/
│   │       ├── in/
│   │       │   └── BuildForeignGuestDataUseCase.java
│   │       └── out/
│   │           └── OutboxEventPublisherPort.java
│   ├── application/
│   │   ├── service/
│   │   │   └── ForeignGuestDataBuilderService.java  # Filtra extranjeros, asigna ENTRY/DEPARTURE
│   │   └── dto/
│   │       ├── ForeignGuestDataDto.java              # 10 campos + messageId + sequenceNumber
│   │       └── CheckInMigratoryEventPayload.java
│   └── infrastructure/
│       └── adapters/
│           └── out/
│               └── messaging/
│                   └── RabbitMqOutboxPublisherAdapter.java
frontend/src/
└── components/guests/
    └── ForeignGuestLocationFields.jsx
```

**Structure Decision**: El modelo de datos migratorio se encapsula en el Value Object `ForeignGuestData` con `firstName` y `lastName` separados. `ForeignGuestDataBuilderService` extrae los huéspedes extranjeros, inyecta `movementType = ENTRY` (Check-In) o `DEPARTURE` (Check-Out) y `movementDate`, y genera **un registro outbox independiente por extranjero** en la cola `m2.huespedes.extranjeros.queue`.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar la configuración de RabbitMQ y el Exchange `hospitua.events` según [PLAN/base/plan.md](base/plan.md).
- [ ] T002 Configurar el binding y cola `m2.huespedes.extranjeros.queue` (durable) con dead-letter exchange `hospitua.dlx` en `RabbitConfig.java`.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T003 Implementar el Value Object `ForeignGuestData` con los 10 campos acordados: `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`, `originPlace`, `destinationPlace`, `movementType` (enum `MovementType`: `ENTRY` / `DEPARTURE`), `movementDate`.
- [ ] T004 Implementar validaciones de formato para `originPlace` y `destinationPlace` permitiendo texto libre `"Ciudad, País"`.
- [ ] T005 Configurar la serialización JSON de `ForeignGuestDataDto` en Jackson (incluyendo `messageId`, `sequenceNumber`, `reservationRef`, `roomId`).

---

## Phase 3: User Story 1 - Asignación automática de datos migratorios para extranjeros (Priority: P1)

**Goal**: Asignar automáticamente `movementType = ENTRY` (Check-In) o `DEPARTURE` (Check-Out) y `movementDate` a todo ocupante extranjero capturado, sin intervención manual del recepcionista.

**Independent Test**: Registrar una lista de ocupantes con un nacional y un extranjero, invocar `ForeignGuestDataBuilderService` en contexto Check-In y verificar que asigna `movementType = ENTRY` y `movementDate = checkInDate`; invocar en contexto Check-Out y verificar `movementType = DEPARTURE` y `movementDate = checkOutDate`.

### Tests for User Story 1

- [ ] T006 [P] [US1] Unit test: Verificar que `ForeignGuestDataBuilderService` filtra ocupantes con `nationality != 'Colombia'` y asigna `movementType = ENTRY` + `movementDate = checkInDate` (o `DEPARTURE` + `checkOutDate` en Check-Out).
- [ ] T007 [P] [US1] Unit test: Validar que `originPlace` y `destinationPlace` aceptan cadenas como `"Madrid, España"`, `"Miami, Estados Unidos"` o simplemente el país.
- [ ] T008 [P] [US1] Unit test: Rechazar el armado del paquete migratorio si `checkInDate` o `checkOutDate` son nulos.

### Implementation for User Story 1

- [ ] T009 [P] [US1] Implementar en `ForeignGuestDataBuilderService` el método `buildForeignGuestsPackage(Stay stay, List<RoomGuest> guests, MovementType movementType)`, generando un `ForeignGuestData` por extranjero con `firstName` y `lastName` separados.
- [ ] T010 [US1] Construir el componente frontend `ForeignGuestLocationFields.jsx` con inputs de procedencia y destino asistidos por datalist.
- [ ] T011 [US1] Verificar en la interfaz que el resumen del Paso 3 de Check-In no muestre el contador de extranjeros.

---

## Phase 4: User Story 2 - Consolidación en la notificación a Módulo 2 y resiliencia (Priority: P2)

**Goal**: Despachar **un mensaje independiente por extranjero** a `m2.huespedes.extranjeros.queue` (no en la notificación de habitación), manteniendo la resiliencia ante caídas de Módulo 2.

**Independent Test**: Ejecutar una prueba de integración con Testcontainers verificando que el Check-In se completa localmente a `Occupied`, el mensaje se deposita en `outbox_notification` con la sección de extranjeros poblada y el consumidor de RabbitMQ recibe el mensaje estructurado.

### Tests for User Story 2

- [ ] T012 [P] [US2] Integration test con Testcontainers verificando la serialización de `ForeignGuestDataDto` (con `firstName`, `lastName`, `movementType = ENTRY`, `messageId`, `sequenceNumber`) en `outbox_notification`.
- [ ] T013 [P] [US2] Integration test verificando la entrega **en `m2.huespedes.extranjeros.queue`** (1 mensaje por extranjero, no en la cola de habitación).
- [ ] T014 [P] [US2] Unit test de resiliencia: comprobar que la falla momentánea de RabbitMQ no cancela la transacción de la estancia en la base de datos.

### Implementation for User Story 2

- [ ] T015 [P] [US2] Integrar en `CheckInExecutionService` (y en `CheckOutExecutionService`) la llamada a `ForeignGuestDataBuilderService` dentro de la transacción atómica, generando un registro outbox independiente por extranjero en `m2.huespedes.extranjeros.queue`.
- [ ] T016 [US2] Serializar el evento `ForeignGuestDataDto` con `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y los 10 campos (`firstName`/`lastName` separados).
- [ ] T017 [US2] Incluir `foreignGuestCount` en el payload de la notificación de habitación (`m2.habitacion.checkin.queue` / `m2.habitacion.checkout.queue`), sin datos individuales de los extranjeros.
- [ ] T018 [US2] Configurar la política de reintentos del outbox (3 reintentos con backoff exponencial y posterior envío a DLQ en caso de fallo permanente).

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T019 Verificación de auditoría: asegurar que ningún dato sensible de identidad o migratorio se exponga en logs no autorizados.
- [ ] T020 Validar que en caso de reenvío de notificación, Módulo 2 pueda procesar el mensaje de forma idempotente sin duplicar registros.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Stay`, `RoomGuest` y `outbox_notification` de [PLAN/base/plan.md](base/plan.md).
- **Disparador**: Se activa como extensión de [PLAN/plan-procesar-datos-huespedes.md](plan-procesar-datos-huespedes.md) y se despacha en [PLAN/plan-registrar-check-in.md](plan-registrar-check-in.md).
