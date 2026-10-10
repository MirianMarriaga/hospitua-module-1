# Implementation Plan: Panel de Recepción — Vista de Inicio de Jornada

**Date**: 2026-10-09  
**Spec**: [spec-consultar-panel-recepcion.md](../SPEC/spec-consultar-panel-recepcion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar la pantalla central de inicio de turno **Panel de Recepción** para el actor Recepcionista en Módulo 1 (FR-001 a FR-010). Esta vista provee la visión global operativa del día y actúa como despachador directo hacia los flujos transaccionales de Check-In (`plan-registrar-check-in.md`) y Check-Out (`plan-registrar-check-out.md`), operando con **100% de autonomía local sin llamadas REST a Módulo 2**.

Componentes y lógica operativa:
1. **Indicadores Operativos (4 KPIs locales, FR-002)**:
   - *Llegadas de hoy*: Conteo de habitaciones pendientes de Check-In de la **copia local** (`daily_reservation_room` sin `Stay` asociado), ingestadas a las 00:00 y actualizadas en tiempo real por la cola `m1.reservas.diarias.queue`.
   - *Salidas de hoy*: Conteo de estancias físicas activas locales (`Stay`) con `expectedCheckoutTime` igual a hoy.
   - *Salidas vencidas*: Conteo de estancias físicas activas locales (`Stay`) con `expectedCheckoutTime` anterior a hoy (destacado visualmente con color de alerta).
   - *Habitaciones ocupadas*: Total de habitaciones con `Room.status = Occupied` en el inventario local de Módulo 1.
2. **Pestaña Llegadas (FR-003, FR-005, FR-005a, FR-007, FR-010)**: Listado obtenido **100% de la copia local** (`daily_reservation` y `daily_reservation_room`), organizado a razón de **una fila por cada habitación**, con el estado físico de la habitación:
   - Alerta visual en la fila cuando la habitación no se pudo apartar por estar en mantenimiento o fuera de servicio (`DisabledForRepairs`, `TechnicalBlock` o `Inactive`), con el texto "No disponible: [Estado]".
   - Indicador informativo cuando la habitación está ocupada o en limpieza (`Occupied`, `PendingCleaning` o `InCleaning`), indicando "[Estado]: se apartará al terminar la limpieza".
   - Buscador en tiempo real por nombre, documento o código de reserva contra la copia local (sin REST a Módulo 2).
   - Botón individual "Check-in" que transfiere `reservationRef` y `roomId` directamente al Paso 1 del flujo de admisión.
3. **Pestaña Salidas (FR-004, FR-006, FR-008)**: Listado 100% local sobre la persistencia de Módulo 1 (`Stay` + `Room` + `RoomGuest`) de estancias activas con salida hoy o vencida (etiqueta "Vencida" destacada). Incluye buscador local (nombre, documento, código o número de habitación) y botón "Check-out" que transfiere la estancia directamente al Paso 1 del flujo de salida.
4. **Resiliencia Total (FR-010)**: Cero llamadas REST a Módulo 2. Si la lista de las 00:00 no ha llegado, Llegadas muestra el aviso "Lista de llegadas no disponible. Esperando sincronización de Módulo 2." mientras Salidas y los KPIs de estancias siguen operando.
5. **Pantalla de inicio de jornada (FR-001)**: encabezado "Hotel Hospitua · Recepción" con el chip de sesión, saludo con el nombre del Recepcionista, subtítulo "Estancias · llegadas y salidas del día", imagen institucional, las cuatro tarjetas y las pestañas.
6. **Actualización (FR-005a)**: las llegadas y los KPIs se vuelven a consultar cada 60 segundos y al volver de un Check-In o Check-Out.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, Spring Data JPA, Lombok
- **Storage**: PostgreSQL 16+ (Lectura optimizada sobre `room`, `stay`, `room_guest`, `daily_reservation`, `daily_reservation_room`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / JSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Carga inicial del panel < 500 ms (KPIs y listados servidos desde almacenamiento local), dentro del máximo de 2 segundos de SC-001.
  - Filtrado del buscador de Llegadas (local `daily_reservation`) < 50 ms (SC-002).
  - Filtrado del buscador de Salidas (local `stay`) < 50 ms (SC-003).
- **Constraints**:
  - Sin operaciones de escritura ni transiciones de estado de habitaciones en esta pantalla (solo lectura).
  - Fechas expuestas sin horas (`LocalDate` / YYYY-MM-DD). Cero campo `nights` persistido (cálculo dinámico).
  - Mensaje unificado ante búsqueda sin resultados: "No hay reservas que mostrar.".

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `RECEPTIONIST`.
- **Fechas**: de calendario (`YYYY-MM-DD`), sin horas; la interfaz las muestra como DD-MM-YYYY.
- **Errores**: esquema `ApiError` del plan base.
- **Estados y tipos en la interfaz**: nombre en español del estado y etiqueta del tipo (regla 12 del plan base).
- **Actualización (FR-005a)**: el frontend vuelve a consultar `arrivals` y `panel/kpis` cada 60 segundos y al volver de un Check-In o Check-Out. El backend ya refleja los `ADDED`, `UPDATED` y `REMOVED` porque el consumidor de la cola actualiza la copia local (`plan-consultar-reservas.md`); no hay notificaciones en tiempo real.
- **Errores comunes**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | El usuario no tiene el rol `RECEPTIONIST` | "No tienes permiso para realizar esta acción." |

---

## GET /api/reception/arrivals

**Descripción:** Pestaña Llegadas: habitaciones de las reservas del día pendientes de Check-In, desde la copia local, agrupadas por reserva, con el estado físico de cada habitación (FR-003, FR-005, FR-005a, FR-007, FR-009, FR-010).
**Rol autorizado:** `RECEPTIONIST`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `query` | query | string | No | Nombre o apellido del titular, número de documento o `reservationRef` (sin distinguir mayúsculas ni espacios sobrantes) (FR-003) | `González` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `listAvailable` | boolean | `false` si hoy no se ha recibido la lista de las 00:00 (no hay mensaje `reserva.lista-del-dia` con el `operationalDate` de hoy en `daily_reservation_message_log`); el frontend muestra "Lista de llegadas no disponible. Esperando sincronización de Módulo 2." y `groups` llega vacío (FR-010) | `true` |
| `groups` | array | Una entrada por reserva con habitaciones pendientes de Check-In | |
| `groups[].reservationRef` | string | Referencia de la reserva | `"RES-10482"` |
| `groups[].pendingRooms` | entero | Habitaciones de la reserva sin `Stay` | `1` |
| `groups[].totalRooms` | entero | Habitaciones de la reserva (el frontend muestra "RES-10482 · 1 de 2 pendientes", FR-005) | `2` |
| `groups[].rooms` | array | Una fila por habitación pendiente (FR-005) | |
| `groups[].rooms[].roomId` | UUID | Habitación asignada | `"3f1c…"` |
| `groups[].rooms[].roomNumber` | string | Número de habitación | `"304"` |
| `groups[].rooms[].categoryRoom` | string | Código del tipo: `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE` | `"DOBLE"` |
| `groups[].rooms[].titularFirstName` | string | Nombres del titular | `"María"` |
| `groups[].rooms[].titularLastName` | string | Apellidos del titular | `"González"` |
| `groups[].rooms[].titularDocumentNumber` | string | Documento del titular | `"1032456789"` |
| `groups[].rooms[].startDate` | string | Llegada | `"2026-10-10"` |
| `groups[].rooms[].endDate` | string | Salida | `"2026-10-13"` |
| `groups[].rooms[].nights` | entero | `endDate` − `startDate`, calculado al responder | `3` |
| `groups[].rooms[].guestCount` | entero | Personas de la habitación | `2` |
| `groups[].rooms[].source` | string | `DIRECTA` o el nombre de la OTA, tal como llega | `"BOOKING"` |
| `groups[].rooms[].roomStatus` | string | Estado actual de la habitación | `"Reserved"` |
| `groups[].rooms[].notice` | object \| null | Aviso de la fila; nulo si la habitación está en `Reserved` | `null` |
| `groups[].rooms[].notice.type` | string | `UNAVAILABLE` (alerta: `DisabledForRepairs`, `TechnicalBlock` o `Inactive`) o `PENDING_ASSIGNMENT` (informativo: `Occupied`, `PendingCleaning` o `InCleaning`) | `"UNAVAILABLE"` |
| `groups[].rooms[].notice.text` | string | "No disponible: [Estado]" o "[Estado]: se apartará al terminar la limpieza", con el estado en español | `"No disponible: Bloqueo técnico"` |

**Body (JSON):**

```json
{
  "listAvailable": true,
  "groups": [
    {
      "reservationRef": "RES-10482",
      "pendingRooms": 2,
      "totalRooms": 2,
      "rooms": [
        {
          "roomId": "3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60",
          "roomNumber": "304",
          "categoryRoom": "DOBLE",
          "titularFirstName": "María",
          "titularLastName": "González",
          "titularDocumentNumber": "1032456789",
          "startDate": "2026-10-10",
          "endDate": "2026-10-13",
          "nights": 3,
          "guestCount": 2,
          "source": "BOOKING",
          "roomStatus": "Reserved",
          "notice": null
        },
        {
          "roomId": "8a7b6c5d-4e3f-4a1b-8c9d-0e1f2a3b4c5d",
          "roomNumber": "305",
          "categoryRoom": "SENCILLA",
          "titularFirstName": "María",
          "titularLastName": "González",
          "titularDocumentNumber": "1032456789",
          "startDate": "2026-10-10",
          "endDate": "2026-10-13",
          "nights": 3,
          "guestCount": 1,
          "source": "BOOKING",
          "roomStatus": "InCleaning",
          "notice": { "type": "PENDING_ASSIGNMENT", "text": "En limpieza: se apartará al terminar la limpieza" }
        }
      ]
    }
  ]
}
```

Sin reservas pendientes o sin coincidencias, la respuesta es `200` con `groups` vacío y el frontend muestra "No hay reservas que mostrar." (FR-009). "Check-in" navega a `/check-in?reservationRef={ref}&roomId={roomId}` (FR-007).

### Respuestas de error

Solo los errores comunes (401, 403).

---

## GET /api/reception/departures

**Descripción:** Pestaña Salidas: estancias activas locales con salida hoy o vencida (FR-004, FR-006, FR-008, FR-009).
**Rol autorizado:** `RECEPTIONIST`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `query` | query | string | No | Nombre o apellido del titular, número de documento, `reservationRef` o número de habitación (FR-004) | `304` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `stays` | array | Estancias activas con `expectedCheckoutTime` igual o anterior a hoy, primero las vencidas | |
| `stays[].stayId` | UUID | Estancia | `"c1d2…"` |
| `stays[].reservationRef` | string | Referencia de la reserva | `"RES-10411"` |
| `stays[].roomId` | UUID | Habitación | `"9b8a…"` |
| `stays[].roomNumber` | string | Número de habitación | `"210"` |
| `stays[].categoryRoom` | string | Código del tipo | `"SUITE"` |
| `stays[].titularFirstName` | string | Nombres del titular (copia en `Stay`) | `"Carlos"` |
| `stays[].titularLastName` | string | Apellidos del titular | `"Pérez"` |
| `stays[].titularDocumentNumber` | string | Documento del titular | `"79123456"` |
| `stays[].checkInDate` | string | Entrada real | `"2026-10-07"` |
| `stays[].expectedCheckoutTime` | string | Salida esperada (fecha) | `"2026-10-09"` |
| `stays[].overdue` | boolean | `expectedCheckoutTime` anterior a hoy (etiqueta "Vencida", FR-006) | `true` |
| `stays[].guestCount` | entero | Registros `RoomGuest` de la estancia | `2` |
| `stays[].source` | string | `source` de `Stay` | `"DIRECTA"` |

**Body (JSON):**

```json
{
  "stays": [
    {
      "stayId": "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f",
      "reservationRef": "RES-10411",
      "roomId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
      "roomNumber": "210",
      "categoryRoom": "SUITE",
      "titularFirstName": "Carlos",
      "titularLastName": "Pérez",
      "titularDocumentNumber": "79123456",
      "checkInDate": "2026-10-07",
      "expectedCheckoutTime": "2026-10-09",
      "overdue": true,
      "guestCount": 2,
      "source": "DIRECTA"
    }
  ]
}
```

Sin estancias o sin coincidencias, `stays` llega vacío y el frontend muestra "No hay reservas que mostrar." (FR-009). "Check-out" navega a `/check-out?reservationRef={ref}&roomId={roomId}` (FR-008).

### Respuestas de error

Solo los errores comunes (401, 403).

---

## GET /api/reception/panel/kpis

**Descripción:** Los cuatro indicadores del turno, calculados con datos locales (FR-002, FR-010).
**Rol autorizado:** `RECEPTIONIST`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:** No aplica.

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `arrivalsToday` | entero \| null | Habitaciones de la copia local sin `Stay`; nulo si la lista de las 00:00 no ha llegado (el frontend muestra "—", FR-010) | `12` |
| `departuresToday` | entero | Estancias activas con `expectedCheckoutTime` = hoy | `8` |
| `overdueDepartures` | entero | Estancias activas con `expectedCheckoutTime` anterior a hoy (tarjeta de alerta) | `1` |
| `occupiedRooms` | entero | Habitaciones en `Occupied` | `34` |

**Body (JSON):**

```json
{
  "arrivalsToday": 12,
  "departuresToday": 8,
  "overdueDepartures": 1,
  "occupiedRooms": 34
}
```

### Respuestas de error

Solo los errores comunes (401, 403).

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

- [ ] T004 Implementar los modelos de dominio y DTOs para el resumen del panel: `ReceptionDashboardKpis`, `ArrivalItemSummary` (campos de `groups[].rooms[]` en `GET /api/reception/arrivals`, con `notice` de tipo `UNAVAILABLE` o `PENDING_ASSIGNMENT`), `DepartureSummary` (campos de `stays[]`).
- [ ] T005 Implementar en `RoomPersistencePort` la consulta de agregación `countByStatus(RoomStatus.Occupied)`.
- [ ] T006 Implementar en `StayPersistencePort` las consultas de búsqueda de salidas activas:
  - `findActiveStaysWithExpectedCheckout(LocalDate date)` (Salidas de hoy).
  - `findActiveStaysWithOverdueCheckout(LocalDate date)` (Salidas vencidas).
  - `searchActiveDepartures(String query)` (búsqueda por nombre, documento, código o habitación).
- [ ] T007 Implementar en `DailyReservationPersistencePort` las consultas locales:
  - Conteo de habitaciones pendientes de llegada: `countPendingArrivalRoomsToday()`.
  - Búsqueda de habitaciones pendientes agrupadas por reserva: `searchPendingArrivalRooms(String query)`, expuesta por `SearchDailyReservationsUseCase` (`plan-consultar-reservas.md`, T019).
  - Lista del día recibida: `isTodayListReceived()` sobre `daily_reservation_message_log` (`reserva.lista-del-dia` con el `operationalDate` de hoy) (FR-010).

---

## Phase 3: User Story 1 - Consulta del listado de llegadas del día y búsqueda para Check-In (Priority: P1)

**Goal**: Permitir al Recepcionista consultar la pestaña Llegadas con reservas del día directamente de la copia local a razón de **una fila por habitación**, con badges de grupo y alertas visuales si la habitación no está apartada.

**Independent Test**: Poblar la copia local con reservas y habitaciones de prueba, consultar el endpoint `GET /api/reception/arrivals` **sin ninguna llamada REST a Módulo 2**, comprobando que devuelve una fila por habitación, calcula noches en runtime, devuelve el aviso `UNAVAILABLE` o `PENDING_ASSIGNMENT` si la habitación no está en `Reserved`, y redirige al flujo de Check-In con `reservationRef` y `roomId`.

### Tests for User Story 1

- [ ] T008 [P] [US1] Unit test para `ReceptionPanelQueryService` verificando que las llegadas se obtienen exclusivamente de la copia local, se dividen en una fila por habitación, se enriquecen con el estado físico de `Room` y se calculan las noches en tiempo de ejecución (FR-003, FR-005).
- [ ] T009 [P] [US1] Integration test para `GET /api/reception/arrivals` verificando que devuelve `ArrivalItemSummaryDto` (sin llamadas REST a M2) y presenta alerta técnica si la habitación está en `DisabledForRepairs` y el indicador informativo si está en `Occupied`, `PendingCleaning` o `InCleaning` (FR-005).
- [ ] T010 [P] [US1] Component test frontend para `ArrivalsTab.jsx` validando renderizado de agrupador `RES-xxx · X de Y pendientes`, alertas visuales y pase de parámetros al pulsar "Check-in" (FR-005, FR-007; SC-004).

### Implementation for User Story 1

- [ ] T011 [P] [US1] Implementar en `ReceptionPanelQueryService` el método que lista y filtra llegadas del día a nivel de habitación, cruzando con `Stay` existente para filtrar solo las pendientes de check-in y enriqueciendo con `Room.status` para calcular `notice` (FR-003, FR-005).
- [ ] T012 [US1] Implementar el endpoint `GET /api/reception/arrivals?query={q}` en `ReceptionPanelController.java` con `@PreAuthorize("hasRole('RECEPTIONIST')")` (FR-003, FR-010).
- [ ] T013 [US1] Construir los componentes frontend `ArrivalsTab.jsx` y `ReceptionSearchBar.jsx` con agrupamiento visual por reserva, alertas técnicas ("No disponible: [Estado]" o "[Estado]: se apartará al terminar la limpieza") y botón "Check-in" que navega a `/check-in?reservationRef={ref}&roomId={roomId}` (FR-003, FR-005, FR-007).

---

## Phase 4: User Story 2 - Consulta del listado de salidas y búsqueda para Check-Out (Priority: P1)

**Goal**: Permitir al Recepcionista consultar y filtrar salidas activas y vencidas operando de forma 100% local, y transferir la estancia seleccionada al Check-Out.

**Independent Test**: Registrar estancias activas con salida hoy y vencida, consultar la pestaña Salidas sin conexión a Módulo 2, comprobar la presencia de la etiqueta "Vencida", filtrar por habitación y verificar navegación al paso 1 de Check-Out.

### Tests for User Story 2

- [ ] T014 [P] [US2] Unit test para `DepartureSearchService` verificando filtrado local por nombre del titular, documento, código y número de habitación (FR-004).
- [ ] T015 [P] [US2] Integration test para `GET /api/reception/departures` verificando que devuelve `DepartureSummaryDto` con estancias activas locales, primero las vencidas (FR-004, FR-006).
- [ ] T016 [P] [US2] Component test frontend para `DeparturesTab.jsx` validando el resaltado visual de "Vencida" y paso de parámetros (FR-006, FR-008; SC-005).

### Implementation for User Story 2

- [ ] T017 [P] [US2] Implementar en `DepartureSearchService` la lógica de filtrado local insensible a mayúsculas y sin espacios (FR-004).
- [ ] T018 [US2] Implementar el endpoint `GET /api/reception/departures?query={q}` en `ReceptionPanelController.java` (FR-004).
- [ ] T019 [US2] Construir el componente frontend `DeparturesTab.jsx` mostrando columnas completas, badge "Vencida" y botón "Check-out" hacia `/check-out?reservationRef={ref}&roomId={roomId}` (FR-006, FR-008).

---

## Phase 5: User Story 3 - Indicadores operativos del turno (KPIs) (Priority: P2)

**Goal**: Mostrar los 4 KPIs operativos del turno calculados 100% de forma local al cargar la cabecera del panel.

**Independent Test**: Invocar `GET /api/reception/panel/kpis` y verificar que los 4 conteos concuerden con los datos locales (llegadas pendientes en copia local, salidas hoy, salidas vencidas y ocupadas).

### Tests for User Story 3

- [ ] T020 [P] [US3] Unit test calculando los 4 KPIs con datos de la copia local y entidades `Stay`/`Room`; `arrivalsToday` es nulo si la lista del día no llegó (FR-002, FR-010).
- [ ] T021 [P] [US3] Component test frontend para `KpiCards.jsx` verificando tarjeta de alerta destacada para "Salidas vencidas" y "—" cuando `arrivalsToday` es nulo (FR-002, FR-010).

### Implementation for User Story 3

- [ ] T022 [P] [US3] Implementar en `ReceptionPanelQueryService` el cálculo consolidado de los 4 KPIs locales (FR-002).
- [ ] T023 [US3] Implementar el endpoint `GET /api/reception/panel/kpis` en `ReceptionPanelController.java` (FR-002).
- [ ] T024 [US3] Construir el componente `KpiCards.jsx` en frontend con las 4 tarjetas de resumen (FR-002).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T025 Verificar que si la búsqueda no arroja coincidencias en ninguna pestaña se despliegue el mensaje unificado "No hay reservas que mostrar." (FR-009).
- [ ] T026 Garantizar que **ningún endpoint del panel realice llamadas REST a Módulo 2**; validar con pruebas de integración desacopladas de red, midiendo que el panel completo carga en menos de 2 segundos y cada búsqueda responde en menos de 0,5 segundos (SC-001 a SC-003).
- [ ] T027 Asegurar que ninguna fecha exponga horas y que los textos institucionales concuerden con los wireframes.
- [ ] T028 Integration test: sin mensaje `reserva.lista-del-dia` de hoy, `GET /api/reception/arrivals` responde `listAvailable = false` y `groups` vacío, mientras `departures` y los KPIs de estancias responden normalmente; el frontend muestra "Lista de llegadas no disponible. Esperando sincronización de Módulo 2." (FR-010; SC-006).
- [ ] T029 Integration test: tras procesar un `ADDED`, un `UPDATED` y un `REMOVED`, la siguiente consulta de `arrivals` y `panel/kpis` refleja los cambios; component test de `ReceptionPanel.jsx` que vuelve a consultar cada 60 segundos y al volver de un Check-In o Check-Out (FR-005a).
- [ ] T030 Construir `ReceptionPanel.jsx` según `vistas/recepcionista/01-inicio.html`: encabezado "Hotel Hospitua · Recepción" con chip de sesión (nombre y rol), saludo con el nombre del Recepcionista tomado del token, subtítulo "Estancias · llegadas y salidas del día", imagen institucional, `KpiCards.jsx` y las pestañas Llegadas y Salidas (FR-001).

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Room`, `Stay` y `RoomGuest` de [PLAN/base/plan.md](base/plan.md) y el rol `RECEPTIONIST` (T015).
- **Sub-planes Integrados**:
  - `plan-consultar-reservas.md`: Suministra la copia local `daily_reservation` y `daily_reservation_room`.
  - `plan-registrar-check-in.md`: Destino de navegación al pulsar "Check-in".
  - `plan-registrar-check-out.md`: Destino de navegación al pulsar "Check-out".
