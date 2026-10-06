# Implementation Plan: Panel de Recepción — Vista de Inicio de Jornada

**Date**: 2026-10-06  
**Spec**: [spec-consultar-panel-recepcion.md](../SPEC/spec-consultar-panel-recepcion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar la pantalla central de inicio de turno **Panel de Recepción** para el actor Recepcionista en Módulo 1. Esta vista provee la visión global operativa del día y actúa como despachador directo hacia los flujos transaccionales de Check-In (`plan-registrar-check-in.md`) y Check-Out (`plan-registrar-check-out.md`).

Componentes y lógica operativa:
1. **Indicadores Operativos (4 KPIs)**:
   - *Llegadas de hoy*: Conteo de reservas `ACTIVE` con `startDate = hoy` de la **copia local** (`daily_reservation`), ingestada a las 00:00 por cola.
   - *Salidas de hoy*: Conteo de estancias físicas activas locales (`Stay`) con `expectedCheckoutTime` igual a hoy.
   - *Salidas vencidas*: Conteo de estancias físicas activas locales (`Stay`) con `expectedCheckoutTime` anterior a hoy (destacado visualmente con color de alerta).
   - *Habitaciones ocupadas*: Total de habitaciones con `Room.status = Occupied` en el inventario local de Módulo 1.
2. **Pestaña Llegadas**: Listado por defecto de reservas con llegada hoy tomadas **100% de la copia local** (`daily_reservation`), enriquecidas con el estado físico de la habitación en Módulo 1 (`Reserved`). Incluye buscador en tiempo real (nombre, documento o código de reserva) **contra la copia local** sin llamadas a Módulo 2 y botón de acción "Check-in" que navega directamente al paso 1 del flujo de admisión.
3. **Pestaña Salidas**: Listado 100% local sobre la persistencia de Módulo 1 (`Stay` + `Room` + `RoomGuest`) de estancias activas con salida hoy o vencida (etiqueta "Vencida"). Incluye buscador local (nombre, documento, código o número de habitación) sin llamadas a Módulo 2 y botón "Check-out" que transfiere la estancia directamente al paso 1 del flujo de salida.
4. **Resiliencia**: Toda la pantalla opera con total autonomía sin depender del estado de red ni de la disponibilidad de Módulo 2, dado que tanto Llegadas como Salidas se sirven desde el almacenamiento local.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, Spring Data JPA, Lombok, Resilience4j / RestClient
- **Storage**: PostgreSQL 16+ (Lectura optimizada sobre `room`, `stay`, `room_guest`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers, MockRestServiceServer
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / JSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Carga inicial del panel < 500 ms (KPIs y listados servidos desde copia local).
  - Filtrado del buscador de Llegadas (local `daily_reservation`) < 50 ms.
  - Filtrado del buscador de Salidas (local `stay`) < 50 ms.
- **Constraints**:
  - Sin operaciones de escritura ni transiciones de estado de habitaciones en esta pantalla.
  - Fechas expuestas sin horas (`LocalDate` / YYYY-MM-DD).
  - Si no existen resultados, mostrar el mensaje unificado: "No hay reservas que mostrar.".

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
│   │   │   ├── ArrivalSummary.java
│   │   │   └── DepartureSummary.java
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── GetReceptionPanelUseCase.java
│   │       │   ├── SearchArrivalsUseCase.java
│   │       │   └── SearchDeparturesUseCase.java
│   │       └── out/
│   │           ├── RoomPersistencePort.java
│   │           ├── StayPersistencePort.java
│   │           └── DailyReservationRepositoryPort.java  # Lectura de copia local
│   ├── application/
│   │   ├── service/
│   │   │   ├── ReceptionPanelQueryService.java    # Consolida KPIs y llegadas desde copia local
│   │   │   └── DepartureSearchService.java        # Búsqueda local de estancias
│   │   └── dto/
│   │       ├── ReceptionPanelResponseDto.java
│   │       ├── ArrivalSummaryDto.java
│   │       └── DepartureSummaryDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── ReceptionPanelController.java
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── RoomRepositoryAdapter.java
│       │       │   ├── StayRepositoryAdapter.java
│       │       │   └── JpaDailyReservationAdapter.java  # Lee la copia local para Llegadas
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

**Structure Decision**: El servicio `ReceptionPanelQueryService` consolida los datos agregados: consulta la copia local `daily_reservation` para KPIs de llegadas y la pestaña Llegadas (sin REST), y consulta `Stay` + `Room` localmente para KPIs y pestaña Salidas. **Ninguna ruta del panel hace llamadas REST a Módulo 2.**

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar la configuración base de repositorios en [PLAN/base/plan.md](base/plan.md).
- [ ] T002 Verificar que el esquema de `daily_reservation` esté disponible (generado por `plan-consultar-reservas.md`).
- [ ] T003 Configurar el mapeo de excepciones en `GlobalExceptionHandler`.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T004 Implementar los modelos de dominio y DTOs para el resumen del panel: `ReceptionDashboardKpis`, `ArrivalSummaryDto`, `DepartureSummaryDto`.
- [ ] T005 Implementar en `RoomPersistencePort` la consulta de agregación `countByStatus(RoomStatus.Occupied)`.
- [ ] T006 Implementar en `StayPersistencePort` las consultas de búsqueda de salidas activas:
  - `findActiveStaysWithExpectedCheckout(LocalDate date)` (Salidas de hoy).
  - `findActiveStaysWithOverdueCheckout(LocalDate date)` (Salidas vencidas).
  - `searchActiveDepartures(String query)` (búsqueda por nombre, documento, código o habitación).
- [ ] T007 Implementar en `DailyReservationRepositoryPort` las consultas `countArrivalsToday()` y `searchArrivals(String query)` sobre la copia local.

---

## Phase 3: User Story 1 - Consulta del listado de llegadas del día y búsqueda para Check-In (Priority: P1)

**Goal**: Permitir al Recepcionista consultar la pestaña Llegadas con reservas del día directamente de la copia local, filtrar por texto y acceder al Check-In con la reserva preseleccionada.

**Independent Test**: Poblar la copia local con reservas de prueba y consultar el endpoint de llegadas **sin que se genere ninguna llamada REST a Módulo 2**, verificando las columnas requeridas y la redirección al flujo de Check-In.

### Tests for User Story 1

- [ ] T007 [P] [US1] Unit test para `ReceptionPanelQueryService` verificando que las llegadas se obtienen de `DailyReservationRepositoryPort` (copia local) y se enriquecen con el estado de habitación local, sin llamadas REST.
- [ ] T008 [P] [US1] Integration test para `GET /api/reception/arrivals` verificando que devuelve llegadas de la copia local (sin llamadas REST a M2).
- [ ] T009 [P] [US1] Component test frontend para `ArrivalsTab.jsx` validando renderizado de tabla y paso de `reservationRef` al pulsar "Check-in".

### Implementation for User Story 1

- [ ] T010 [P] [US1] Implementar en `ReceptionPanelQueryService` el método que lista y filtra llegadas del día llamando a `DailyReservationRepositoryPort.searchArrivals(query)` (copia local).
- [ ] T011 [US1] Implementar el endpoint `GET /api/reception/arrivals?query={q}` en `ReceptionPanelController.java`.
- [ ] T012 [US1] Construir los componentes frontend `ArrivalsTab.jsx` y `ReceptionSearchBar.jsx` con debounce para filtrado local en tiempo real.
- [ ] T013 [US1] Conectar el botón "Check-in" para redirigir a `/check-in?reservationRef={ref}` cargando directamente el paso 1 del wizard.

---

## Phase 4: User Story 2 - Consulta del listado de salidas y búsqueda para Check-Out (Priority: P1)

**Goal**: Permitir al Recepcionista consultar y filtrar salidas activas y vencidas operando de forma 100% local, y transferir la estancia seleccionada al Check-Out.

**Independent Test**: Registrar estancias activas con salida hoy y vencida, consultar la pestaña Salidas sin conexión a Módulo 2, comprobar la presencia de la etiqueta "Vencida", filtrar por habitación y verificar navegación al paso 1 de Check-Out.

### Tests for User Story 2

- [ ] T014 [P] [US2] Unit test para `DepartureSearchService` verificando filtrado local por nombre del titular, documento, código y número de habitación.
- [ ] T015 [P] [US2] Integration test para `GET /api/reception/departures` verificando que devuelve `DepartureSummaryDto` con estancias activas locales.
- [ ] T016 [P] [US2] Component test frontend para `DeparturesTab.jsx` validando el resaltado visual de "Vencida" y paso de `stayId` o `reservationRef`.

### Implementation for User Story 2

- [ ] T017 [P] [US2] Implementar en `DepartureSearchService` la lógica de filtrado insensible a mayúsculas y sin espacios.
- [ ] T018 [US2] Implementar el endpoint `GET /api/reception/departures?query={q}` en `ReceptionPanelController.java`.
- [ ] T019 [US2] Construir el componente frontend `DeparturesTab.jsx` mostrando columnas completas, badge "Vencida" y botón "Check-out".
- [ ] T020 [US2] Conectar el botón "Check-out" para redirigir a `/check-out?reservationRef={ref}` cargando el paso 1 del wizard.

---

## Phase 5: User Story 3 - Indicadores operativos del turno (KPIs) (Priority: P2)

**Goal**: Mostrar los 4 KPIs operativos del turno al cargar la cabecera del panel.

**Independent Test**: Invocar `GET /api/reception/panel/kpis` y verificar que los 4 conteos concuerden con los datos de Módulo 2 y la base local.

### Tests for User Story 3

- [ ] T021 [P] [US3] Unit test calculando los 4 KPIs con datos locales y externos.
- [ ] T022 [P] [US3] Component test frontend para `KpiCards.jsx` verificando tarjeta roja/alerta para "Salidas vencidas".

### Implementation for User Story 3

- [ ] T023 [P] [US3] Implementar en `ReceptionPanelQueryService` el cálculo consolidado de los 4 KPIs.
- [ ] T024 [US3] Implementar el endpoint `GET /api/reception/panel/kpis` en `ReceptionPanelController.java`.
- [ ] T025 [US3] Construir el componente `KpiCards.jsx` en frontend con las 4 tarjetas de resumen.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T026 Verificar que si la búsqueda no arroja coincidencias en ninguna pestaña se despliegue "No hay reservas que mostrar.".
- [ ] T027 Asegurar que **ningún endpoint del panel realice llamadas REST a Módulo 2**; validar con pruebas de integración sin red externa.
- [ ] T028 Asegurar que ninguna fecha exponga horas y que los textos institucionales y saludo concuerden con la especificación.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Room`, `Stay` y `RoomGuest` de [PLAN/base/plan.md](base/plan.md).
- **Sub-planes Integrados**:
  - `plan-consultar-reservas.md`: Suministra la copia local `daily_reservation` para la pestaña Llegadas y el KPI de llegadas.
  - `plan-registrar-check-in.md`: Destino de navegación al pulsar "Check-in".
  - `plan-registrar-check-out.md`: Destino de navegación al pulsar "Check-out".
