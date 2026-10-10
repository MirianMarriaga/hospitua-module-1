# Implementation Plan: Panel de Recepción — Vista de Inicio de Jornada

**Date**: 2026-10-09  
**Spec**: [spec-consultar-panel-recepcion.md](../SPEC/spec-consultar-panel-recepcion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar la pantalla central de inicio de turno **Panel de Recepción** para el actor Recepcionista en Módulo 1. Esta vista provee la visión global operativa del día y actúa como despachador directo hacia los flujos transaccionales de Check-In (`plan-registrar-check-in.md`) y Check-Out (`plan-registrar-check-out.md`), operando con **100% de autonomía local sin llamadas REST a Módulo 2**.

Componentes y lógica operativa:
1. **Indicadores Operativos (4 KPIs locales)**:
   - *Llegadas de hoy*: Conteo de habitaciones pendientes de Check-In de la **copia local** (`daily_reservation_room` sin `Stay` asociado), ingestadas a las 00:00 y actualizadas en tiempo real por la cola `m1.reservas.diarias.queue`.
   - *Salidas de hoy*: Conteo de estancias físicas activas locales (`Stay`) con `expectedCheckoutTime` igual a hoy.
   - *Salidas vencidas*: Conteo de estancias físicas activas locales (`Stay`) con `expectedCheckoutTime` anterior a hoy (destacado visualmente con color de alerta).
   - *Habitaciones ocupadas*: Total de habitaciones con `Room.status = Occupied` en el inventario local de Módulo 1.
2. **Pestaña Llegadas**: Listado obtenido **100% de la copia local** (`daily_reservation` y `daily_reservation_room`), organizado a razón de **una fila por cada habitación**, agrupadas por reserva con etiqueta descriptiva (ej. `RES-10482 · 1 de 2 pendientes`). Enriquecido con el estado físico de la habitación:
   - Alerta visual en la fila cuando la habitación no se pudo apartar por estar en mantenimiento o fuera de servicio (`DisabledForRepairs`, `TechnicalBlock` o `Inactive`), con el texto "No disponible: [Estado]".
   - Indicador informativo cuando la habitación está en limpieza (`PendingCleaning` o `InCleaning`), indicando "En limpieza: se apartará al terminar".
   - Buscador en tiempo real por nombre, documento o código de reserva contra la copia local (sin REST a Módulo 2).
   - Botón individual "Check-in" que transfiere `reservationRef` y `roomId` directamente al Paso 1 del flujo de admisión.
3. **Pestaña Salidas**: Listado 100% local sobre la persistencia de Módulo 1 (`Stay` + `Room` + `RoomGuest`) de estancias activas con salida hoy o vencida (etiqueta "Vencida" destacada). Incluye buscador local (nombre, documento, código o número de habitación) y botón "Check-out" que transfiere la estancia directamente al Paso 1 del flujo de salida.
4. **Resiliencia Total**: Cero llamadas REST a Módulo 2. Si la sincronización asíncrona no estuviera disponible, se muestra aviso informativo controlado en Llegadas mientras Salidas y KPIs de estancias continúan operando normalmente.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, Spring Data JPA, Lombok
- **Storage**: PostgreSQL 16+ (Lectura optimizada sobre `room`, `stay`, `room_guest`, `daily_reservation`, `daily_reservation_room`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / JSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Carga inicial del panel < 500 ms (KPIs y listados servidos desde almacenamiento local).
  - Filtrado del buscador de Llegadas (local `daily_reservation`) < 50 ms.
  - Filtrado del buscador de Salidas (local `stay`) < 50 ms.
- **Constraints**:
  - Sin operaciones de escritura ni transiciones de estado de habitaciones en esta pantalla (solo lectura).
  - Fechas expuestas sin horas (`LocalDate` / YYYY-MM-DD). Cero campo `nights` persistido (cálculo dinámico).
  - Mensaje unificado ante búsqueda sin resultados: "No hay reservas que mostrar.".

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-consultar-panel-recepcion.md
│   ├── spec-consultar-reservas.md
│   ├── spec-registrar-check-in.md
│   └── spec-registrar-check-out.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-consultar-panel-recepcion.md
    ├── plan-consultar-reservas.md
    ├── plan-registrar-check-in.md
    └── plan-registrar-check-out.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── ReceptionDashboardKpis.java
│   │   │   ├── ArrivalItemSummary.java
│   │   │   └── DepartureSummary.java
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── GetReceptionPanelUseCase.java
│   │       │   ├── SearchArrivalsUseCase.java
│   │       │   └── SearchDeparturesUseCase.java
│   │       └── out/
│   │           ├── RoomPersistencePort.java
│   │           ├── StayPersistencePort.java
│   │           └── DailyReservationPersistencePort.java # Lectura 100% copia local
│   ├── application/
│   │   ├── service/
│   │   │   ├── ReceptionPanelQueryService.java       # Consolida 4 KPIs y llegadas (1 fila por habitación)
│   │   │   └── DepartureSearchService.java           # Búsqueda local de salidas
│   │   └── dto/
│   │       ├── ReceptionDashboardKpisDto.java
│   │       ├── ArrivalItemSummaryDto.java
│   │       └── DepartureSummaryDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── ReceptionPanelController.java
│       │   └── out/
│       │       └── persistence/
│       │           ├── RoomRepositoryAdapter.java
│       │           ├── StayRepositoryAdapter.java
│       │           └── DailyReservationPersistenceAdapter.java
frontend/src/
├── components/panel/
│   ├── ReceptionPanel.jsx
│   ├── KpiCards.jsx
│   ├── ArrivalsTab.jsx
│   ├── DeparturesTab.jsx
│   └── ReceptionSearchBar.jsx
└── services/
    ├── receptionPanelService.js
    └── departureService.js
```

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar la configuración base de repositorios en [PLAN/base/plan.md](base/plan.md).
- [ ] T002 Verificar que las tablas `daily_reservation` y `daily_reservation_room` estén disponibles (generadas por `plan-consultar-reservas.md`).
- [ ] T003 Configurar el mapeo de excepciones en `GlobalExceptionHandler`.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T004 Implementar los modelos de dominio y DTOs para el resumen del panel: `ReceptionDashboardKpis`, `ArrivalItemSummary` (con `reservationRef`, `roomId`, `roomNumber`, `categoryRoom`, `titularName`, `titularDoc`, `nights`, `guestCount`, `source`, `roomStatus`, `hasConflictAlert`, `conflictMessage`), `DepartureSummary`.
- [ ] T005 Implementar en `RoomPersistencePort` la consulta de agregación `countByStatus(RoomStatus.Occupied)`.
- [ ] T006 Implementar en `StayPersistencePort` las consultas de búsqueda de salidas activas:
  - `findActiveStaysWithExpectedCheckout(LocalDate date)` (Salidas de hoy).
  - `findActiveStaysWithOverdueCheckout(LocalDate date)` (Salidas vencidas).
  - `searchActiveDepartures(String query)` (búsqueda por nombre, documento, código o habitación).
- [ ] T007 Implementar en `DailyReservationPersistencePort` las consultas locales:
  - Conteo de habitaciones pendientes de llegada: `countPendingArrivalRoomsToday()`.
  - Búsqueda de habitaciones pendientes agrupadas por reserva: `searchPendingArrivalRooms(String query)`.

---

## Phase 3: User Story 1 - Consulta del listado de llegadas del día y búsqueda para Check-In (Priority: P1)

**Goal**: Permitir al Recepcionista consultar la pestaña Llegadas con reservas del día directamente de la copia local a razón de **una fila por habitación**, con badges de grupo y alertas visuales si la habitación no está apartada.

**Independent Test**: Poblar la copia local con reservas y habitaciones de prueba, consultar el endpoint `GET /api/reception/arrivals` **sin ninguna llamada REST a Módulo 2**, comprobando que devuelve una fila por habitación, calcula noches en runtime, muestra badge de grupo (`RES-xxx · X de Y pendientes`), alerta si la habitación no está en `Reserved`, y redirige al flujo de Check-In con `reservationRef` y `roomId`.

### Tests for User Story 1

- [ ] T008 [P] [US1] Unit test para `ReceptionPanelQueryService` verificando que las llegadas se obtienen exclusivamente de la copia local, se dividen en una fila por habitación, se enriquecen con el estado físico de `Room` y se calculan las noches en tiempo de ejecución.
- [ ] T009 [P] [US1] Integration test para `GET /api/reception/arrivals` verificando que devuelve `ArrivalItemSummaryDto` (sin llamadas REST a M2) y presenta alerta técnica si la habitación está en `DisabledForRepairs` o en limpieza.
- [ ] T010 [P] [US1] Component test frontend para `ArrivalsTab.jsx` validando renderizado de agrupador `RES-xxx · X de Y pendientes`, alertas visuales y pase de parámetros al pulsar "Check-in".

### Implementation for User Story 1

- [ ] T011 [P] [US1] Implementar en `ReceptionPanelQueryService` el método que lista y filtra llegadas del día a nivel de habitación, cruzando con `Stay` existente para filtrar solo las pendientes de check-in y enriqueciendo con `Room.status` para detectar alertas de indisponibilidad.
- [ ] T012 [US1] Implementar el endpoint `GET /api/reception/arrivals?query={q}` en `ReceptionPanelController.java`.
- [ ] T013 [US1] Construir los componentes frontend `ArrivalsTab.jsx` y `ReceptionSearchBar.jsx` con agrupamiento visual por reserva, alertas técnicas ("No disponible: [Estado]" o "En limpieza: se apartará al terminar") y botón "Check-in" que navega a `/check-in?reservationRef={ref}&roomId={roomId}`.

---

## Phase 4: User Story 2 - Consulta del listado de salidas y búsqueda para Check-Out (Priority: P1)

**Goal**: Permitir al Recepcionista consultar y filtrar salidas activas y vencidas operando de forma 100% local, y transferir la estancia seleccionada al Check-Out.

**Independent Test**: Registrar estancias activas con salida hoy y vencida, consultar la pestaña Salidas sin conexión a Módulo 2, comprobar la presencia de la etiqueta "Vencida", filtrar por habitación y verificar navegación al paso 1 de Check-Out.

### Tests for User Story 2

- [ ] T014 [P] [US2] Unit test para `DepartureSearchService` verificando filtrado local por nombre del titular, documento, código y número de habitación.
- [ ] T015 [P] [US2] Integration test para `GET /api/reception/departures` verificando que devuelve `DepartureSummaryDto` con estancias activas locales.
- [ ] T016 [P] [US2] Component test frontend para `DeparturesTab.jsx` validando el resaltado visual de "Vencida" y paso de parámetros.

### Implementation for User Story 2

- [ ] T017 [P] [US2] Implementar en `DepartureSearchService` la lógica de filtrado local insensible a mayúsculas y sin espacios.
- [ ] T018 [US2] Implementar el endpoint `GET /api/reception/departures?query={q}` en `ReceptionPanelController.java`.
- [ ] T019 [US2] Construir el componente frontend `DeparturesTab.jsx` mostrando columnas completas, badge "Vencida" y botón "Check-out" hacia `/check-out?reservationRef={ref}&roomId={roomId}`.

---

## Phase 5: User Story 3 - Indicadores operativos del turno (KPIs) (Priority: P2)

**Goal**: Mostrar los 4 KPIs operativos del turno calculados 100% de forma local al cargar la cabecera del panel.

**Independent Test**: Invocar `GET /api/reception/panel/kpis` y verificar que los 4 conteos concuerden con los datos locales (llegadas pendientes en copia local, salidas hoy, salidas vencidas y ocupadas).

### Tests for User Story 3

- [ ] T020 [P] [US3] Unit test calculando los 4 KPIs con datos de la copia local y entidades `Stay`/`Room`.
- [ ] T021 [P] [US3] Component test frontend para `KpiCards.jsx` verificando tarjeta de alerta destacada para "Salidas vencidas".

### Implementation for User Story 3

- [ ] T022 [P] [US3] Implementar en `ReceptionPanelQueryService` el cálculo consolidado de los 4 KPIs locales.
- [ ] T023 [US3] Implementar el endpoint `GET /api/reception/panel/kpis` en `ReceptionPanelController.java`.
- [ ] T024 [US3] Construir el componente `KpiCards.jsx` en frontend con las 4 tarjetas de resumen.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T025 Verificar que si la búsqueda no arroja coincidencias en ninguna pestaña se despliegue el mensaje unificado "No hay reservas que mostrar.".
- [ ] T026 Garantizar que **ningún endpoint del panel realice llamadas REST a Módulo 2**; validar con pruebas de integración desacopladas de red.
- [ ] T027 Asegurar que ninguna fecha exponga horas y que los textos institucionales concuerden con los wireframes.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Room`, `Stay` y `RoomGuest` de [PLAN/base/plan.md](base/plan.md).
- **Sub-planes Integrados**:
  - `plan-consultar-reservas.md`: Suministra la copia local `daily_reservation` y `daily_reservation_room`.
  - `plan-registrar-check-in.md`: Destino de navegación al pulsar "Check-in".
  - `plan-registrar-check-out.md`: Destino de navegación al pulsar "Check-out".
