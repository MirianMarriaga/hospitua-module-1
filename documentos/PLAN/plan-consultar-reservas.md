# Implementation Plan: Consultar Reservas

**Date**: 2026-10-06  
**Spec**: [spec-consultar-reservas.md](../SPEC/spec-consultar-reservas.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso interno de consulta reactiva **Consultar Reservas**, invocado como inclusión (`<<includes>>`) por el **Panel de Recepción** (`plan-consultar-panel-recepcion.md`, buscador de Llegadas) y por el Paso 1 de **Registrar Check-In** (`plan-registrar-check-in.md`).

Responsabilidades:
1. **Consulta síncrona REST GET a Módulo 2**: Ejecutar peticiones reactivas a los endpoints de Módulo 2 (`/api/reservations?startDate={today}&status=ACTIVE`, `/api/reservations/{reservationRef}` o búsqueda por documento/nombre del titular).
2. **Recepción del contrato `ReservationSummary`**: Capturar código de reserva (`reservationRef`), datos del titular (`fullName`, `documentType`, `documentNumber`, `nationality`), cantidad de huéspedes (`guestCount`), habitación asignada (`roomId`), fechas sin hora (`startDate`, `endDate`), noches, canal de origen (`source`: Directo u OTA) y estado (`status`: ACTIVE, etc.).
3. **Enriquecimiento local con Módulo 1**: Consultar localmente en el inventario de habitaciones el estado físico actual de la habitación asignada (`Room.status = Reserved` en el flujo feliz) y entregar el objeto consolidado.
4. **Manejo de múltiples coincidencias**: Proveer soporte para búsquedas ambiguas (ej. múltiples reservas con el mismo documento o titular), retornando la lista para selección explícita y obligatoria antes de continuar.
5. **Resiliencia y timeouts**: Implementar timeout de conexión (1 s) y lectura (2 s) mediante `RestClient`, traduciendo fallas o indisponibilidad en excepciones de negocio controladas (`ExternalModule2UnavailableException`) sin propagar errores 500 no controlados.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, RestClient, Lombok, Resilience4j
- **Storage**: Consulta remota a Módulo 2 + lectura local en tabla `room` de PostgreSQL
- **Testing**: JUnit 5, Mockito, MockRestServiceServer, WireMock
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / JSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Consulta por código < 1.5 s p95 en condiciones de red normales.
  - Timeout estricto de 2 s ante demoras de Módulo 2.
- **Constraints**:
  - Operación 100% de solo lectura: cero mutaciones en base de datos local o remota.
  - No validar reglas de admisión de Check-In (responsabilidad exclusiva del consumidor `plan-registrar-check-in.md`).
  - No exponer marcas de tiempo con horas en contratos de negocio (`LocalDate` / YYYY-MM-DD).

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
│   │   │   ├── ReservationSummary.java
│   │   │   ├── ReservationQueryCriteria.java
│   │   │   └── EnrichedReservationSummary.java
│   │   ├── exception/
│   │   │   ├── ReservationNotFoundException.java
│   │   │   └── ExternalModule2UnavailableException.java
│   │   └── ports/
│   │       ├── in/
│   │       │   └── QueryReservationUseCase.java
│   │       └── out/
│   │           ├── ReservationRestQueryPort.java
│   │           └── RoomPersistencePort.java
│   ├── application/
│   │   ├── service/
│   │   │   └── ReservationQueryService.java
│   │   └── dto/
│   │       ├── ReservationSummaryDto.java
│   │       └── EnrichedReservationDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── ReservationProxyController.java
│       │   └── out/
│       │       └── rest/
│       │           ├── ReservationRestAdapter.java
│       │           └── Module2Properties.java
frontend/src/
├── components/reservation/
│   ├── ReservationLookup.jsx
│   └── MultipleReservationsModal.jsx
└── services/
    └── reservationService.js
```

**Structure Decision**: El puerto de salida `ReservationRestQueryPort` abstrae la comunicación HTTP con Módulo 2. `ReservationQueryService` coordina la llamada REST y enriquece la respuesta consultando el estado físico de la habitación local en `RoomPersistencePort`.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Configurar propiedades de integración de Módulo 2 en `application.yml` (`m2.reservations.base-url`, `m2.reservations.connect-timeout-ms=1000`, `m2.reservations.read-timeout-ms=2000`).
- [ ] T002 Crear el cliente HTTP `RestClient` con soporte para timeouts y mapeo de errores HTTP 4xx/5xx en `ReservationRestAdapter.java`.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T003 Implementar los modelos de dominio `ReservationSummary` y `EnrichedReservationSummary` con validaciones de campos requeridos.
- [ ] T004 Implementar la interfaz del puerto de entrada `QueryReservationUseCase` y el puerto de salida `ReservationRestQueryPort`.
- [ ] T005 Configurar el mapeo de excepciones `ReservationNotFoundException` y `ExternalModule2UnavailableException` a RFC 7807 (`ApiError`) en `GlobalExceptionHandler`.

---

## Phase 3: User Story 1 - Búsqueda de reservas y consulta de datos de estadía para Check-In (Priority: P1)

**Goal**: Permitir la consulta síncrona de una reserva por código o listado de llegadas del día, enriqueciendo los datos con el estado físico de la habitación en Módulo 1.

**Independent Test**: Invocar la búsqueda por código suministrando una respuesta simulada de Módulo 2 y verificar que retorna el titular, fechas, canal, `guestCount` y el estado físico de la habitación (`Reserved`).

### Tests for User Story 1

- [ ] T006 [P] [US1] Unit test para `ReservationQueryService` verificando enriquecimiento exitoso con `RoomPersistencePort`.
- [ ] T007 [P] [US1] Integration test con `MockRestServiceServer` simulando respuesta HTTP 200 de Módulo 2 y validando deserialización.
- [ ] T008 [P] [US1] Contract test para la llamada REST `GET /api/reservations/{ref}`.

### Implementation for User Story 1

- [ ] T009 [P] [US1] Implementar en `ReservationRestAdapter` el método `getReservationByRef(String ref)` consumiendo el endpoint remoto.
- [ ] T010 [P] [US1] Implementar en `ReservationRestAdapter` el método `getArrivalsByDate(LocalDate date)` para listar llegadas activas del día.
- [ ] T011 [US1] Implementar en `ReservationQueryService` la consolidación con la entidad `Room` de Módulo 1.
- [ ] T012 [US1] Exponer el endpoint `/api/reservations/lookup` en `ReservationProxyController.java` para uso de la UI.

---

## Phase 4: User Story 2 - Manejo de múltiples coincidencias y tolerancia a fallos externos (Priority: P2)

**Goal**: Gestionar resultados múltiples exigiendo selección explícita y controlar caídas o timeouts de Módulo 2 sin caídas no controladas.

**Independent Test**: Simular una búsqueda por número de documento que retorne dos reservas, verificar que la API devuelve ambas y que el componente modal frontend requiere selección; simular timeout de 2000 ms y verificar respuesta de indisponibilidad controlada 503/422.

### Tests for User Story 2

- [ ] T013 [P] [US2] Unit test: Simular múltiples reservas coincidentes y verificar entrega de lista ordenada.
- [ ] T014 [P] [US2] Integration test: Simular timeout de red y verificar que se lanza `ExternalModule2UnavailableException`.
- [ ] T015 [P] [US2] Component test frontend para `MultipleReservationsModal.jsx` validando selección obligatoria.

### Implementation for User Story 2

- [ ] T016 [P] [US2] Implementar en `ReservationRestAdapter` la búsqueda por documento y titular con manejo de múltiples resultados.
- [ ] T017 [US2] Implementar en `MultipleReservationsModal.jsx` la lista de tarjetas de reserva con código, fechas y estado, bloqueando avance hasta seleccionar una.
- [ ] T018 [US2] Implementar el interceptor de reintentos y timeouts en `ReservationRestAdapter`.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T019 Sanitización de entradas de texto: trim de espacios accidentales e insensibilidad a mayúsculas/minúsculas.
- [ ] T020 Validar que no se muestren banners residuales tipo "Reserva encontrada" en la UI.
- [ ] T021 Auditoría y observabilidad: métricas de latencia de la llamada REST a Módulo 2.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Room` y `RoomPersistencePort` de [PLAN/base/plan.md](base/plan.md).
- **Consumidores**:
  - `plan-consultar-panel-recepcion.md`: Invoca este caso de uso para listar y filtrar Llegadas.
  - `plan-registrar-check-in.md`: Invoca este caso de uso en el Paso 1 (Validar reserva).
