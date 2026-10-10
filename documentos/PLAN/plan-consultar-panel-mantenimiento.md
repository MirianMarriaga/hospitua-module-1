# Implementation Plan: Consultar Panel de Mantenimiento

**Date**: 2026-10-10  
**Spec**: [spec-consultar-panel-mantenimiento.md](../SPEC/spec-consultar-panel-mantenimiento.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Consultar Panel de Mantenimiento** para el Personal de mantenimiento: el punto de entrada de su trabajo (FR-001 a FR-011).

- **Listado** (FR-002 a FR-006, FR-009): todas las habitaciones excepto las `Inactive`, primero las que requieren intervención (`DisabledForRepairs` y `TechnicalBlock`, con los bloqueos vencidos primero). Cada una muestra número, tipo, estado en español ("Bloqueo técnico (vencido)" cuando aplica), mantenimiento programado (el `Scheduled` más próximo), la etiqueta "En reparación" si tiene una tarea abierta, y sus acciones. Encima van los conteos de inhabilitadas, en bloqueo técnico (y vencidas) y con mantenimiento programado.
- **Búsqueda y mensajes** (FR-007, FR-008): por número exacto, con los mensajes de búsqueda sin resultados y de panel vacío.
- **Redirección** (FR-001): un miembro con una tarea abierta va a la vista de tarea activa de `plan-confirmar-reparacion-finalizada.md`.
- **Actualización** (FR-010): el frontend vuelve a consultar el panel cada 60 segundos y al volver de una acción; no hay notificaciones en tiempo real.

Las acciones no se implementan aquí:

- **Iniciar reparaciones**: `POST /api/maintenance/tasks` (`plan-confirmar-reparacion-finalizada.md`).
- **Programar mantenimiento** y **Ver informe** del bloqueo: `plan-programar-bloqueo-tecnico-para-habitacion.md`.
- **Reportar daño** y **Ver informe** del daño: `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`.

---

## Technical Context

- **Storage**: PostgreSQL 16+. Solo lectura de `room`, `reparation_task` y `technical_block_report` del plan base (T007 y T018).
- **Performance Goals**: el panel responde en menos de 2 segundos (SC-001); una habitación que pasa a `DisabledForRepairs` aparece en menos de 60 segundos (SC-004).
- **Constraints**:
  - Solo lectura (FR-011).
  - "Vencido" usa `TechnicalBlockReport.isOverdue(today)` (`plan-programar-bloqueo-tecnico-para-habitacion.md`, T003) con la fecha de `Clock`.
  - Los informes `Expired` no se muestran; la habitación queda "Sin programar" (FR-004).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `MAINTENANCE_STAFF`. El miembro se toma del token.
- **Fechas**: las fechas de mantenimiento son de calendario (`YYYY-MM-DD`) y la interfaz las muestra como DD-MM-YYYY.
- **Errores**: esquema `ApiError` del plan base.
- **Estados y tipos en la interfaz**: nombre en español del estado y etiqueta del tipo (regla 12 del plan base).
- **Errores comunes**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | El usuario no tiene el rol `MAINTENANCE_STAFF` | "No tienes permiso para realizar esta acción." |

---

## GET /api/maintenance/panel

**Descripción:** Devuelve el panel de mantenimiento del miembro autenticado con sus conteos, o su tarea activa para redirigirlo (FR-001 a FR-011).
**Rol autorizado:** `MAINTENANCE_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomNumber` | query | string | No | Filtra por número de habitación con coincidencia exacta (FR-007) | `105` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `activeTaskId` | UUID \| null | Tarea abierta del miembro; si no es nula, el frontend redirige a la vista de tarea activa y `rooms` llega vacío (FR-001) | `null` |
| `summary` | object | Conteos del panel sin aplicar la búsqueda (FR-009) | |
| `summary.disabledForRepairs` | entero | Habitaciones en `DisabledForRepairs` | `2` |
| `summary.technicalBlock` | entero | Habitaciones en `TechnicalBlock` | `1` |
| `summary.technicalBlockOverdue` | entero | De ellas, las que tienen el bloqueo vencido | `1` |
| `summary.scheduled` | entero | Habitaciones con al menos un `TechnicalBlockReport` en `Scheduled` | `3` |
| `rooms` | array | Todas las habitaciones excepto las `Inactive`: primero `DisabledForRepairs` y `TechnicalBlock` (vencidos antes), luego el resto; cada grupo por número ascendente (FR-002, FR-006) | |
| `rooms[].roomId` | UUID | Identificador de la habitación | `"9b8a…"` |
| `rooms[].roomNumber` | string | Número de habitación | `"105"` |
| `rooms[].categoryRoom` | string | Código del tipo: `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE` (la interfaz muestra Sencilla, Doble, Suite o Boutique) | `"SUITE"` |
| `rooms[].status` | string | Estado actual de la habitación (cualquiera salvo `Inactive`) | `"DisabledForRepairs"` |
| `rooms[].overdue` | boolean | `true` si la habitación está en `TechnicalBlock` y su informe `Applied` tiene la fecha estimada de fin anterior a hoy ("Bloqueo técnico (vencido)", FR-003) | `false` |
| `rooms[].hasOpenTask` | boolean | La habitación ya tiene una `ReparationTask` abierta: el frontend muestra "En reparación" y oculta "Iniciar reparaciones" (FR-005) | `false` |
| `rooms[].scheduledMaintenance` | object \| null | Rango del `TechnicalBlockReport` en `Scheduled` de inicio más temprano; nulo si no hay ("Sin programar"), incluso si tuvo uno que pasó a `Expired` (FR-004) | `null` |
| `rooms[].scheduledMaintenance.startDate` | string | Inicio programado | `"2026-10-20"` |
| `rooms[].scheduledMaintenance.estimatedEndDate` | string | Fin estimado | `"2026-10-22"` |

**Body (JSON):**

```json
{
  "activeTaskId": null,
  "summary": {
    "disabledForRepairs": 1,
    "technicalBlock": 0,
    "technicalBlockOverdue": 0,
    "scheduled": 1
  },
  "rooms": [
    {
      "roomId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
      "roomNumber": "105",
      "categoryRoom": "SUITE",
      "status": "DisabledForRepairs",
      "overdue": false,
      "hasOpenTask": false,
      "scheduledMaintenance": null
    },
    {
      "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "roomNumber": "106",
      "categoryRoom": "DOBLE",
      "status": "Available",
      "overdue": false,
      "hasOpenTask": false,
      "scheduledMaintenance": { "startDate": "2026-10-20", "estimatedEndDate": "2026-10-22" }
    }
  ]
}
```

Una búsqueda sin coincidencias (incluida una habitación `Inactive`) o un panel sin habitaciones responde `200` con `rooms` vacío; el frontend muestra "No se encontró la habitación X" o "No hay habitaciones para listar" (FR-008).

### Respuestas de error

Solo los errores comunes (401, 403).

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   └── spec-consultar-panel-mantenimiento.md
├── PLAN/
│   ├── base/
│   │   └── plan.md
│   └── plan-consultar-panel-mantenimiento.md
└── vistas/mantenimiento/
    └── 01-panel.html                              # Maqueta del panel
```

### Source Code

```text
backend/src/
└── main/java/com/hospitua/habitaciones/
    ├── domain/ports/in/
    │   └── GetMaintenancePanelUseCase.java
    ├── application/
    │   ├── service/
    │   │   └── MaintenancePanelQueryService.java
    │   └── dto/
    │       └── MaintenancePanelDto.java           # activeTaskId, summary, rooms
    └── infrastructure/adapters/in/web/
        └── MaintenanceController.java             # Definido en plan-confirmar-reparacion-finalizada.md; aquí se agrega GET /api/maintenance/panel
frontend/src/
├── pages/mantenimiento/
│   ├── MaintenancePanelPage.jsx
│   ├── MaintenanceSummary.jsx
│   └── MaintenanceRoomTable.jsx
└── api/
    └── maintenanceService.js                      # Compartido con los planes de mantenimiento
```

**Structure Decision**: El panel solo lee. Reutiliza `ReparationTaskRepositoryPort` (`plan-confirmar-reparacion-finalizada.md`, T002), `TechnicalBlockReportRepositoryPort` e `isOverdue(today)` (`plan-programar-bloqueo-tecnico-para-habitacion.md`, T003). Los botones de acción abren los diálogos y llaman a los endpoints de sus propios planes.

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base (rol `MAINTENANCE_STAFF`, layout de mantenimiento de T016, `ClockConfig`).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que existan `ReparationTaskRepositoryPort` (`plan-confirmar-reparacion-finalizada.md`, T002), `TechnicalBlockReportRepositoryPort` con `isOverdue(today)` (`plan-programar-bloqueo-tecnico-para-habitacion.md`, T003) y la consulta de habitaciones distintas de `Inactive` en `RoomPersistencePort`.
- [ ] T002 [P] Implementar `MaintenancePanelDto` (con `summary`) y el puerto `GetMaintenancePanelUseCase`.

---

## Phase 3: User Story 1 - Consulta del panel de mantenimiento (Priority: P1)

**Goal**: Que el miembro vea primero las habitaciones que requieren intervención, con su mantenimiento programado y sus acciones, busque por número y vuelva al panel actualizado después de cada acción.

**Independent Test**: Con habitaciones en `DisabledForRepairs` (una con tarea abierta), `TechnicalBlock` (una vencida), `Occupied` con un `Scheduled`, `Available` e `Inactive`, consultar el panel con un miembro sin tarea y verificar el listado, el orden, los conteos, `overdue`, `hasOpenTask` y `scheduledMaintenance`; repetir con un miembro con tarea abierta y verificar `activeTaskId`.

### Tests for User Story 1

- [ ] T003 [P] [US1] Unit test en `MaintenancePanelQueryServiceTest`: excluye `Inactive`; ordena `DisabledForRepairs` y `TechnicalBlock` primero (vencidos antes) y luego el resto por número; `summary` cuenta sin aplicar la búsqueda (HU-1 esc. 1 y 4; FR-002, FR-006, FR-009).
- [ ] T004 [P] [US1] Unit test con `Clock` fijo: `overdue` según `isOverdue(today)`; `scheduledMaintenance` toma el `Scheduled` de inicio más temprano e ignora los `Expired`; `hasOpenTask` con una tarea abierta de otro miembro (HU-1 esc. 3, 4 y 5; FR-003, FR-004, FR-005).
- [ ] T005 [P] [US1] Unit test: con tarea abierta devuelve `activeTaskId` y `rooms` vacío; búsqueda exacta, sin coincidencias y de una habitación `Inactive` (HU-1 esc. 6 y 7; FR-001, FR-007).
- [ ] T006 [US1] Integration test: un usuario sin token recibe `401` y uno sin rol `MAINTENANCE_STAFF` recibe `403`; la consulta no modifica `room`, `reparation_task` ni `technical_block_report` (FR-001, FR-011).
- [ ] T007 [P] [US1] Component test en `MaintenancePanelPage`: conteos; botones por estado (todas: Programar mantenimiento; `AVB`: además Reportar daño; `DFR`/`TB`: además Ver informe e Iniciar reparaciones solo si `hasOpenTask` es `false`; demás estados: Ver informe si hay programación); etiquetas "En reparación" y "Bloqueo técnico (vencido)"; "Sin programar"; mensajes de FR-008; redirección con `activeTaskId`; nueva consulta cada 60 segundos y al volver de una acción (HU-1 esc. 2 a 5; FR-003 a FR-005, FR-008, FR-010).

### Implementation for User Story 1

- [ ] T008 [US1] Implementar `MaintenancePanelQueryService` (`@Transactional(readOnly = true)`): detección de tarea activa, todas las habitaciones salvo `Inactive`, orden, búsqueda exacta, conteos, `overdue`, `hasOpenTask` y el `Scheduled` de inicio más temprano (FR-001 a FR-007, FR-009, FR-011).
- [ ] T009 [US1] Agregar a `MaintenanceController` el endpoint `GET /api/maintenance/panel` con `@PreAuthorize("hasRole('MAINTENANCE_STAFF')")` (FR-001).
- [ ] T010 [US1] Construir `MaintenancePanelPage.jsx`, `MaintenanceSummary.jsx` y `MaintenanceRoomTable.jsx` según `vistas/mantenimiento/01-panel.html`: conteos, columnas número, tipo, estado (nombre en español o "Bloqueo técnico (vencido)"), mantenimiento programado (DD-MM-YYYY a DD-MM-YYYY o "Sin programar"), etiqueta "En reparación" y botones por estado; búsqueda exacta; mensajes de FR-008; redirección si `activeTaskId` no es nulo (FR-001, FR-003 a FR-005, FR-007 a FR-009).
- [ ] T011 [US1] Conectar las acciones y la actualización (FR-005, FR-010):
  - "Iniciar reparaciones" llama a `POST /api/maintenance/tasks` y redirige a la vista de tarea activa (`plan-confirmar-reparacion-finalizada.md`).
  - "Programar mantenimiento" y "Ver informe" del bloqueo (`TB`, o cualquier habitación con programación salvo `DFR`) abren `ScheduleTechnicalBlockDialog.jsx` y `TechnicalBlockViewDialog.jsx` (`plan-programar-bloqueo-tecnico-para-habitacion.md`).
  - "Reportar daño" y "Ver informe" de `DFR` abren `DamageReportDialog.jsx` y `DamageReportViewDialog.jsx` (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`).
  - El panel se vuelve a consultar cada 60 segundos, al volver de una acción y cuando una acción falla por cambio de estado o por tarea abierta.

**Checkpoint**: El Personal de mantenimiento ve su panel, prioriza las habitaciones que requieren intervención y entra a sus acciones; el panel se mantiene actualizado sin recargar la página.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T012 Integration test: con 500 habitaciones, `GET /api/maintenance/panel` responde en menos de 2 segundos (SC-001).
- [ ] T013 Integration test: una habitación que pasa a `DisabledForRepairs` aparece en la siguiente consulta del panel antes que las habitaciones sin intervención, y ninguna `Inactive` aparece (SC-002, SC-003, SC-004).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base el rol `MAINTENANCE_STAFF` (T015), el layout de mantenimiento (T016), las tablas `reparation_task` y `technical_block_report` (T018) y la regla 12.
- **Planes relacionados**:
  - `plan-confirmar-reparacion-finalizada.md`: `MaintenanceController`, `ReparationTaskRepositoryPort`, la acción "Iniciar reparaciones" y la vista de tarea activa a la que se redirige.
  - `plan-programar-bloqueo-tecnico-para-habitacion.md`: `isOverdue(today)` y los diálogos de programar y ver bloqueo.
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: los diálogos de reportar y ver daño.
- **Orden**: Phase 2 de `plan-confirmar-reparacion-finalizada.md` y de `plan-programar-bloqueo-tecnico-para-habitacion.md` → Phase 2 → Phase 3 → Phase 4.
