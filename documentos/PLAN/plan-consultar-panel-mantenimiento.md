# Implementation Plan: Consultar Panel de Mantenimiento

**Date**: 2026-10-10  
**Spec**: [spec-consultar-panel-mantenimiento.md](../SPEC/spec-consultar-panel-mantenimiento.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Consultar Panel de Mantenimiento** para el Personal de mantenimiento: el punto de entrada de su trabajo (FR-001 a FR-011).

- **Listado** (FR-002 a FR-006, FR-009): todas las habitaciones excepto las `Inactive`, en tres grupos: los `TechnicalBlock` vencidos, el resto de `DisabledForRepairs` y `TechnicalBlock`, y las demás. Cada una muestra número, tipo, estado en español ("Bloqueo técnico (vencido)" cuando aplica), mantenimiento (el `Applied` o, si no hay, el `Scheduled` más próximo), la etiqueta "En reparación" si tiene una tarea abierta, y sus acciones. Encima van los conteos de inhabilitadas, en bloqueo técnico (y vencidas) y con mantenimiento programado, que el frontend calcula sobre la lista.
- **Búsqueda y mensajes** (FR-007, FR-008): por número exacto, en el frontend sobre la lista, con los mensajes de búsqueda sin resultados y de panel vacío.
- **Redirección** (FR-001): un miembro con una tarea abierta va a la vista de tarea activa de `plan-confirmar-reparacion-finalizada.md`; el panel le entrega `activeTask` con la fila de su habitación.
- **Actualización** (FR-010): el frontend vuelve a consultar el panel cada 60 segundos y al volver de una acción; no hay notificaciones en tiempo real.

Las acciones no se implementan aquí:

- **Iniciar reparaciones**: el endpoint `POST /api/maintenance/tasks` y su tabla de errores (`plan-confirmar-reparacion-finalizada.md`); la interfaz de la acción (botón, llamada, retroalimentación, redirección y recarga) es de este plan.
- **Programar mantenimiento** y **Ver informe** del bloqueo: `plan-programar-bloqueo-tecnico-para-habitacion.md`.
- **Reportar daño** y **Ver informe** del daño: `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`.

---

## Technical Context

- **Storage**: PostgreSQL 16+. Solo lectura de `room`, `reparation_task` y `technical_block_report` del plan base (T007 y T017).
- **Performance Goals**: el panel responde en menos de 2 segundos (SC-001; prueba de rendimiento fuera de CI, T012); una habitación que pasa a `DisabledForRepairs` aparece en menos de 60 segundos (SC-002).
- **Constraints**:
  - Solo lectura (FR-011).
  - "Vencido" usa `TechnicalBlockReport.isOverdue(today)` (`plan-programar-bloqueo-tecnico-para-habitacion.md`, T003) con la fecha de `Clock`.
  - Los informes `Expired` no se muestran; la habitación queda "Sin programar" (FR-004).

---

## Convenciones de los endpoints

- **Fechas**: las fechas de mantenimiento son de calendario (`YYYY-MM-DD`) y la interfaz las muestra como DD-MM-YYYY.
- **Errores**: esquema `ApiError` del plan base.
- **Usuario actual**: el miembro del Personal de mantenimiento se toma del encabezado `X-User` (regla 14 del plan base).
- **Estados y tipos en la interfaz**: nombre en español del estado y etiqueta del tipo (regla 12 del plan base).

---

## GET /api/maintenance/panel

**Descripción:** Devuelve el panel de mantenimiento del miembro que lo consulta, o su tarea activa para redirigirlo (FR-001 a FR-011).

### Petición (Request)

**Headers:**

```http
X-User: mtorres
Accept: application/json
```

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `activeTask` | object \| null | Tarea activa del miembro; si no es nula, el frontend redirige a la vista de tarea activa, que toma sus datos de este campo, y `rooms` llega vacío (FR-001) | `null` |
| `activeTask.taskId` | UUID | Tarea abierta del miembro | `"7e6d5c4b-…"` |
| `activeTask.room` | object | La habitación de la tarea, con los mismos campos de un ítem de `rooms[]` | |
| `rooms` | array | Todas las habitaciones excepto las `Inactive`, en tres grupos: `TechnicalBlock` vencidos, el resto de `DisabledForRepairs` y `TechnicalBlock`, y las demás; partición estable sobre el orden natural por número (plan base, T008) (FR-002, FR-006). La búsqueda (FR-007) y los conteos (FR-009) los calcula el frontend sobre esta lista | |
| `rooms[].roomId` | UUID | Identificador de la habitación | `"9b8a…"` |
| `rooms[].roomNumber` | string | Número de habitación | `"105"` |
| `rooms[].categoryRoom` | string | Código del tipo: `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE` (la interfaz muestra Sencilla, Doble, Suite o Boutique) | `"SUITE"` |
| `rooms[].status` | string | Estado actual de la habitación (cualquiera salvo `Inactive`) | `"DisabledForRepairs"` |
| `rooms[].overdue` | boolean | `true` si la habitación está en `TechnicalBlock` y su informe `Applied` tiene la fecha estimada de fin anterior a hoy ("Bloqueo técnico (vencido)", FR-003) | `false` |
| `rooms[].hasOpenTask` | boolean | La habitación ya tiene una `ReparationTask` abierta: el frontend muestra "En reparación" y oculta "Iniciar reparaciones" (FR-005) | `false` |
| `rooms[].maintenance` | object \| null | Rango del `TechnicalBlockReport` en `Applied` o, si no hay, del `Scheduled` de inicio más temprano, igual que `GET /api/rooms/{roomId}/technical-blocks/current`; nulo si no hay ("Sin programar"), incluso si tuvo uno que pasó a `Expired` (FR-004). Es un `Scheduled` cuando la habitación no está en `TechnicalBlock`, y así lo cuenta el frontend como "con mantenimiento programado" (FR-009) | `null` |
| `rooms[].maintenance.startDate` | string | Inicio | `"2026-10-20"` |
| `rooms[].maintenance.estimatedEndDate` | string | Fin estimado | `"2026-10-22"` |

**Body (JSON):**

```json
{
  "activeTask": null,
  "rooms": [
    {
      "roomId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
      "roomNumber": "105",
      "categoryRoom": "SUITE",
      "status": "DisabledForRepairs",
      "overdue": false,
      "hasOpenTask": false,
      "maintenance": null
    },
    {
      "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "roomNumber": "106",
      "categoryRoom": "DOBLE",
      "status": "Available",
      "overdue": false,
      "hasOpenTask": false,
      "maintenance": { "startDate": "2026-10-20", "estimatedEndDate": "2026-10-22" }
    }
  ]
}
```

Un panel sin habitaciones responde `200` con `rooms` vacío y el frontend muestra "No hay habitaciones para listar"; una búsqueda sin coincidencias en `rooms` (incluida una habitación `Inactive`) muestra "No se encontró la habitación X" (FR-008).

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |

Un fallo inesperado se responde con el formato `ApiError` del plan base.

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
    ├── application/
    │   ├── ports/in/
    │   │   └── GetMaintenancePanelUseCase.java
    │   ├── service/
    │   │   └── MaintenancePanelQueryService.java
    │   └── dto/
    │       └── MaintenancePanelDto.java           # activeTask, rooms
    └── infrastructure/adapters/in/web/
        └── MaintenanceController.java             # Definido en plan-confirmar-reparacion-finalizada.md; aquí se agrega GET /api/maintenance/panel
frontend/src/
├── pages/mantenimiento/
│   ├── MaintenancePanelPage.tsx
│   ├── MaintenanceSummary.tsx
│   └── MaintenanceRoomTable.tsx
└── api/
    └── maintenanceService.ts                      # Compartido con los planes de mantenimiento
```

**Structure Decision**: El panel solo lee. Reutiliza `ReparationTaskRepositoryPort` (`plan-confirmar-reparacion-finalizada.md`, T002), `TechnicalBlockReportRepositoryPort` e `isOverdue(today)` (`plan-programar-bloqueo-tecnico-para-habitacion.md`, T003). Los botones de acción abren los diálogos y llaman a los endpoints de sus propios planes; la interfaz de "Iniciar reparaciones" es de este plan. La búsqueda y los conteos los calcula el frontend sobre `rooms`, y la vista de tarea activa toma sus datos de `activeTask`.

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base (layout de mantenimiento de T015, `ClockConfig`).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que existan:
  - `ReparationTaskRepositoryPort` con `findOpenByMember` y `findRoomIdsWithOpenTask`, y `MaintenanceController` (`plan-confirmar-reparacion-finalizada.md`, T002 y T004).
  - `TechnicalBlockReport.isOverdue(today)` y `TechnicalBlockReportRepositoryPort` con `findAppliedByRoomIds` y `findEarliestScheduledByRoomIds` (`plan-programar-bloqueo-tecnico-para-habitacion.md`, T003).
  - `RoomRepositoryPort.findByStatusNot(RoomStatus)` (plan base, T008).
- [ ] T002 [P] Implementar `MaintenancePanelDto` (con `activeTask`, que reutiliza el ítem de `rooms`) y el puerto `GetMaintenancePanelUseCase`.

---

## Phase 3: User Story 1 - Consulta del panel de mantenimiento (Priority: P1)

**Goal**: Que el miembro vea primero las habitaciones que requieren intervención, con su mantenimiento y sus acciones, busque por número y vuelva al panel actualizado después de cada acción.

**Independent Test**: Con habitaciones en `DisabledForRepairs` (una con tarea abierta), `TechnicalBlock` (una vencida), `Occupied` con un `Scheduled`, `Available` e `Inactive`, consultar el panel con un miembro sin tarea y verificar el listado, el orden, `overdue`, `hasOpenTask` y `maintenance`; repetir con un miembro con tarea abierta y verificar `activeTask`.

### Tests for User Story 1

- [ ] T003 [P] [US1] Unit test en `MaintenancePanelQueryServiceTest`: excluye `Inactive`; ordena en tres grupos (`TechnicalBlock` vencidos, resto de `DisabledForRepairs` y `TechnicalBlock`, demás), cada uno por número (HU-1 esc. 1 y 4; FR-002, FR-006).
- [ ] T004 [P] [US1] Unit test con `Clock` fijo: `overdue` según `isOverdue(today)`; `maintenance` toma el `Applied` o, si no hay, el `Scheduled` de inicio más temprano, e ignora los `Expired`; `hasOpenTask` con una tarea abierta de otro miembro (HU-1 esc. 3, 4 y 5; FR-003, FR-004, FR-005).
- [ ] T005 [P] [US1] Unit test: con tarea abierta devuelve `activeTask` (con `taskId` y la fila de su habitación) y `rooms` vacío (HU-1 esc. 7; FR-001).
- [ ] T006 [US1] Integration test: la consulta no modifica `room`, `reparation_task` ni `technical_block_report` (FR-011).
- [ ] T007 [P] [US1] Component test en `MaintenancePanelPage` con la API simulada: conteos calculados sobre la lista ("con mantenimiento programado" solo cuenta los `Scheduled`); botones por estado (todas: Programar mantenimiento; `AVB`: además Reportar daño; `DFR`/`TB`: además Iniciar reparaciones solo si `hasOpenTask` es `false`; Ver informe del daño en `DFR` y del bloqueo en cualquier otra con `maintenance`); etiquetas "En reparación" y "Bloqueo técnico (vencido)"; "Sin programar"; búsqueda por número exacto sobre la lista, que al vaciarse vuelve al listado completo; mensajes de FR-008; redirección cuando hay `activeTask`; nueva consulta cada 60 segundos y al volver de una acción. Acción "Iniciar reparaciones": redirige a la vista de tarea activa al tener éxito y, si se rechaza, muestra el texto de la tabla de errores de `plan-confirmar-reparacion-finalizada.md` según el `errorCode` y recarga el panel (HU-1 esc. 2 a 6; FR-003 a FR-005, FR-007 a FR-010; *Confirmar Fin de Reparación de Habitación* FR-011, FR-012).

### Implementation for User Story 1

- [ ] T008 [US1] Implementar `MaintenancePanelQueryService` (`@Transactional(readOnly = true)`): detección de tarea activa (`activeTask` con la fila de su habitación), todas las habitaciones salvo `Inactive` con una partición estable en tres grupos sobre el orden natural de `findByStatusNot`, `overdue`, `hasOpenTask` y `maintenance` (FR-001 a FR-006, FR-011).
- [ ] T009 [US1] Agregar a `MaintenanceController` el endpoint `GET /api/maintenance/panel`, tomando como miembro al usuario actual (FR-001).
- [ ] T010 [US1] Construir `MaintenancePanelPage.tsx`, `MaintenanceSummary.tsx` y `MaintenanceRoomTable.tsx` según `vistas/mantenimiento/01-panel.html`: conteos calculados sobre `rooms` (FR-009), columnas número, tipo, estado (nombre en español o "Bloqueo técnico (vencido)"), mantenimiento (DD-MM-YYYY a DD-MM-YYYY o "Sin programar"), etiqueta "En reparación" y botones por estado; búsqueda exacta sobre `rooms`; mensajes de FR-008; redirección si `activeTask` no es nulo (FR-001, FR-003 a FR-005, FR-007 a FR-009).
- [ ] T011 [US1] Conectar las acciones y la actualización (FR-005, FR-010):
  - Este plan es dueño del botón, la llamada y la navegación de cada acción; los mensajes de éxito y error los define el plan de cada caso de uso.
  - "Iniciar reparaciones" (interfaz de este plan): llama a `POST /api/maintenance/tasks`; al tener éxito redirige a la vista de tarea activa; si se rechaza, muestra el texto de la tabla de errores de `plan-confirmar-reparacion-finalizada.md` según el `errorCode` y recarga el panel; ante un fallo de red sugiere reintentar.
  - "Programar mantenimiento" y "Ver informe" del bloqueo (cualquier habitación con `maintenance` salvo `DFR`, incluida `TB`) abren `ScheduleTechnicalBlockDialog.tsx` y `TechnicalBlockViewDialog.tsx` (`plan-programar-bloqueo-tecnico-para-habitacion.md`).
  - "Reportar daño" y "Ver informe" de `DFR` abren `DamageReportDialog.tsx` y `DamageReportViewDialog.tsx` (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`).
  - El panel se vuelve a consultar cada 60 segundos, al volver de una acción y cuando una acción falla por cambio de estado o por tarea abierta.

**Checkpoint**: El Personal de mantenimiento ve su panel, prioriza las habitaciones que requieren intervención y entra a sus acciones; el panel se mantiene actualizado sin recargar la página.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T012 Prueba de rendimiento (etiquetada según la estrategia de testing del plan base): con 500 habitaciones, `GET /api/maintenance/panel` responde en menos de 2 segundos (SC-001).
- [ ] T013 Integration test sin tiempos: una habitación que pasa a `DisabledForRepairs` aparece en la siguiente consulta del panel antes que las habitaciones sin intervención, en el orden natural real de SQL, y ninguna `Inactive` aparece; el número de consultas no crece con el número de habitaciones (FR-002, FR-006; SC-002).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base el layout de mantenimiento (T015), las tablas `reparation_task` y `technical_block_report` (T017) y la regla 12.
- **Planes relacionados**:
  - `plan-confirmar-reparacion-finalizada.md`: `MaintenanceController`, `ReparationTaskRepositoryPort`, el endpoint de "Iniciar reparaciones" con su tabla de errores y la vista de tarea activa a la que se redirige, que toma sus datos de `activeTask`.
  - `plan-programar-bloqueo-tecnico-para-habitacion.md`: `isOverdue(today)` y los diálogos de programar y ver bloqueo.
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: los diálogos de reportar y ver daño.
- **Orden**: Phase 2 de `plan-confirmar-reparacion-finalizada.md` y de `plan-programar-bloqueo-tecnico-para-habitacion.md` → Phase 2 → Phase 3 → Phase 4.
