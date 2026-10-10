# Implementation Plan: Consultar Panel de Limpieza

**Date**: 2026-10-10  
**Spec**: [spec-consultar-panel-limpieza.md](../SPEC/spec-consultar-panel-limpieza.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Consultar Panel de Limpieza** para el Personal de limpieza: el punto de entrada de su trabajo (FR-001 a FR-011).

- **Listado** (FR-002 a FR-006, FR-010): habitaciones en `PendingCleaning` y `Available`, primero las pendientes, con número, tipo, estado en español, última limpieza y acciones (`PC`: Iniciar limpieza; `AVB`: Reportar daño, Iniciar limpieza), más el conteo de pendientes y disponibles.
- **Búsqueda y mensajes** (FR-007, FR-008): por número exacto, con los mensajes de búsqueda sin resultados y de panel vacío.
- **Redirección** (FR-001): un miembro con una tarea abierta va a la vista de tarea activa de `plan-confirmar-fin-de-limpieza.md`.
- **Actualización** (FR-009): el frontend vuelve a consultar el panel cada 60 segundos y al volver de una acción; no hay notificaciones en tiempo real.

Las acciones no se implementan aquí: **Iniciar limpieza** usa `POST /api/cleaning/tasks` (`plan-marcar-habitacion-en-limpieza.md`) y **Reportar daño** abre el diálogo de `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`.

---

## Technical Context

- **Storage**: PostgreSQL 16+. Solo lectura de `room` y `cleaning_task` del plan base (T007 y T018).
- **Performance Goals**: el panel responde en menos de 2 segundos (SC-001); una habitación nueva en `PendingCleaning` aparece en menos de 60 segundos (SC-004).
- **Constraints**:
  - Solo lectura (FR-011): no transiciona habitaciones ni crea o cierra tareas.
  - La última limpieza ignora las tareas con `outcome = Released` (FR-004).
  - Casos límite sin implementación propia: 3 (las habitaciones en otros estados simplemente no se consultan).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `CLEANING_STAFF`. El miembro se toma del token.
- **Fechas**: ISO 8601 con zona de Colombia; la interfaz las muestra como DD-MM-YYYY HH:MM.
- **Errores**: esquema `ApiError` del plan base.
- **Estados y tipos en la interfaz**: nombre en español del estado y etiqueta del tipo (regla 12 del plan base).
- **Errores comunes**:

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | `AuthenticationException` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." (caso límite 2) |
| 403 | `FORBIDDEN` | `AccessDeniedException` | El usuario no tiene el rol `CLEANING_STAFF` | "No tienes permiso para realizar esta acción." (caso límite 2) |

---

## GET /api/cleaning/panel

**Descripción:** Devuelve el panel de limpieza del miembro autenticado: las habitaciones en `PendingCleaning` y `Available` con su última limpieza y el conteo por estado, o la tarea activa del miembro para redirigirlo a su vista (FR-001 a FR-011).
**Rol autorizado:** `CLEANING_STAFF`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomNumber` | query | string | No | Filtra por número de habitación con coincidencia exacta (FR-007) | `101` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `activeTaskId` | UUID \| null | Tarea abierta del miembro; si no es nula, el frontend redirige a la vista de tarea activa y `rooms` llega vacío (FR-001) | `null` |
| `summary` | object | Conteo del panel sin aplicar la búsqueda (FR-010) | |
| `summary.pendingCleaning` | entero | Habitaciones en `PendingCleaning` | `3` |
| `summary.available` | entero | Habitaciones en `Available` | `12` |
| `rooms` | array | Habitaciones en `PendingCleaning` o `Available`: primero las `PendingCleaning`, cada grupo por número ascendente (FR-002, FR-006) | |
| `rooms[].roomId` | UUID | Identificador de la habitación | `"3f1c…"` |
| `rooms[].roomNumber` | string | Número de habitación | `"101"` |
| `rooms[].categoryRoom` | string | Código del tipo: `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE` (la interfaz muestra Sencilla, Doble, Suite o Boutique) | `"DOBLE"` |
| `rooms[].status` | string | `PendingCleaning` o `Available` | `"PendingCleaning"` |
| `rooms[].lastCleaningDateTime` | string \| null | `EndDateTime` de la última `CleaningTask` con `outcome` `Completed` o `DamageReported`; nulo si no existe (el frontend muestra "Sin registro", FR-004) | `"2026-10-07T16:40:00-05:00"` |

**Body (JSON):**

```json
{
  "activeTaskId": null,
  "summary": {
    "pendingCleaning": 1,
    "available": 1
  },
  "rooms": [
    {
      "roomId": "3f1c2a9e-5b7d-4c1e-9a0f-1b2c3d4e5f60",
      "roomNumber": "101",
      "categoryRoom": "DOBLE",
      "status": "PendingCleaning",
      "lastCleaningDateTime": "2026-10-07T16:40:00-05:00"
    },
    {
      "roomId": "8a7b6c5d-4e3f-4a1b-8c9d-0e1f2a3b4c5d",
      "roomNumber": "102",
      "categoryRoom": "SENCILLA",
      "status": "Available",
      "lastCleaningDateTime": null
    }
  ]
}
```

Una búsqueda sin coincidencias o un panel sin habitaciones responde `200` con `rooms` vacío; el frontend muestra "No se encontró la habitación X" o "No hay habitaciones pendientes de limpieza ni disponibles" (FR-008).

### Respuestas de error

Solo los errores comunes de la sección "Convenciones de los endpoints":

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | `AuthenticationException` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." (caso límite 2) |
| 403 | `FORBIDDEN` | `AccessDeniedException` | El usuario no tiene el rol `CLEANING_STAFF` | "No tienes permiso para realizar esta acción." (caso límite 2) |

```json
{
  "errorCode": "FORBIDDEN",
  "message": "No tienes permiso para realizar esta acción.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/cleaning/panel"
}
```

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   └── spec-consultar-panel-limpieza.md
├── PLAN/
│   ├── base/
│   │   └── plan.md
│   └── plan-consultar-panel-limpieza.md
└── vistas/limpieza/
    └── 01-panel.html                              # Maqueta del panel
```

### Source Code

```text
backend/src/
└── main/java/com/hospitua/habitaciones/
    ├── domain/ports/in/
    │   └── GetCleaningPanelUseCase.java
    ├── application/
    │   ├── service/
    │   │   └── CleaningPanelQueryService.java
    │   └── dto/
    │       └── CleaningPanelDto.java              # activeTaskId, summary, rooms
    └── infrastructure/adapters/in/web/
        └── CleaningController.java                # Definido en plan-marcar-habitacion-en-limpieza.md; aquí se agrega GET /api/cleaning/panel
frontend/src/
├── pages/limpieza/
│   ├── CleaningPanelPage.jsx
│   ├── CleaningSummary.jsx
│   ├── CleaningRoomTable.jsx
│   └── RoomNumberSearch.jsx
└── api/
    └── cleaningService.js                         # Compartido con los planes de limpieza
```

**Structure Decision**: El panel solo lee. Reutiliza `CleaningTaskRepositoryPort` (consultas "tarea abierta por miembro" y "última tarea cerrada con `outcome` `Completed` o `DamageReported` por habitación", `plan-marcar-habitacion-en-limpieza.md` T002) y la consulta de habitaciones por estado del plan base. Los botones de acción delegan en los diálogos y endpoints de sus propios planes.

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base (rol `CLEANING_STAFF`, layout de limpieza de T016, `ClockConfig`).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que existan `CleaningTaskRepositoryPort` con sus consultas de tarea abierta y última limpieza (`plan-marcar-habitacion-en-limpieza.md`, T002) y la consulta de habitaciones por estado en `RoomPersistencePort` (plan base).
- [ ] T002 [P] Implementar `CleaningPanelDto` (con `summary`) y el puerto `GetCleaningPanelUseCase`.

---

## Phase 3: User Story 1 - Consulta del panel de limpieza (Priority: P1)

**Goal**: Que el miembro vea las habitaciones que puede atender, con su última limpieza y sus acciones, busque por número y vuelva al panel actualizado después de cada acción.

**Independent Test**: Con habitaciones en `PendingCleaning`, `Available`, `InCleaning` y `Occupied`, consultar el panel con un miembro sin tarea y verificar el listado, el orden, el conteo y la última limpieza; repetir con un miembro con tarea abierta y verificar `activeTaskId`.

### Tests for User Story 1

- [ ] T003 [P] [US1] Unit test en `CleaningPanelQueryServiceTest`: lista solo `PendingCleaning` y `Available`, primero las pendientes y por número; `summary` cuenta ambos estados sin aplicar la búsqueda (HU-1 esc. 1; FR-002, FR-006, FR-010).
- [ ] T004 [P] [US1] Unit test: `lastCleaningDateTime` toma la última tarea `Completed` o `DamageReported`, ignora las `Released` y es nulo sin registro (HU-1 esc. 3; FR-004).
- [ ] T005 [P] [US1] Unit test: con tarea abierta devuelve `activeTaskId` y `rooms` vacío (HU-1 esc. 6; FR-001; SC-003).
- [ ] T006 [P] [US1] Unit test: búsqueda por número exacto y búsqueda sin coincidencias (HU-1 esc. 4 y 5; FR-007).
- [ ] T007 [US1] Integration test: un usuario sin token recibe `401` y uno sin rol `CLEANING_STAFF` recibe `403`; la consulta no modifica `room` ni `cleaning_task` (FR-001, FR-011; caso límite 2).
- [ ] T008 [P] [US1] Component test en `CleaningPanelPage`: botones por estado, "Sin registro", conteo, mensajes de búsqueda vacía y panel vacío, redirección cuando hay `activeTaskId` y nueva consulta cada 60 segundos y al volver de una acción (HU-1 esc. 2, 5 y 7; FR-003, FR-005, FR-008, FR-009).

### Implementation for User Story 1

- [ ] T009 [US1] Implementar `CleaningPanelQueryService` (`@Transactional(readOnly = true)`): detección de tarea activa, filtro de estados, orden, búsqueda exacta, conteo y última limpieza (FR-001 a FR-007, FR-010, FR-011).
- [ ] T010 [US1] Agregar a `CleaningController` el endpoint `GET /api/cleaning/panel` con `@PreAuthorize("hasRole('CLEANING_STAFF')")`, tomando el miembro del token (FR-001).
- [ ] T011 [US1] Construir `CleaningPanelPage.jsx`, `CleaningSummary.jsx`, `CleaningRoomTable.jsx` y `RoomNumberSearch.jsx` según `vistas/limpieza/01-panel.html`: conteo, tabla con número, tipo, estado ("Pendiente de limpieza" / "Disponible"), última limpieza (DD-MM-YYYY HH:MM o "Sin registro") y botones por estado; búsqueda exacta; mensajes de FR-008; redirección si `activeTaskId` no es nulo (FR-001, FR-003, FR-005, FR-007, FR-008, FR-010).
- [ ] T012 [US1] Conectar las acciones y la actualización: "Iniciar limpieza" llama a `POST /api/cleaning/tasks` y redirige a la vista de tarea activa (`plan-marcar-habitacion-en-limpieza.md`); "Reportar daño" abre `DamageReportDialog.jsx` (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`); el panel se vuelve a consultar cada 60 segundos, al volver de una acción y cuando una acción falla por cambio de estado (FR-005, FR-009).

**Checkpoint**: El Personal de limpieza ve su panel, busca habitaciones y entra a sus acciones; el panel se mantiene actualizado sin recargar la página.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T013 Integration test: con 500 habitaciones, `GET /api/cleaning/panel` responde en menos de 2 segundos (SC-001).
- [ ] T014 Integration test: una habitación que pasa a `PendingCleaning` por un check-out aparece en la siguiente consulta del panel (SC-002, SC-004).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base el rol `CLEANING_STAFF` (T015), el layout de limpieza (T016), la tabla `cleaning_task` (T018) y la regla 12.
- **Planes relacionados**:
  - `plan-marcar-habitacion-en-limpieza.md`: `CleaningController`, `CleaningTaskRepositoryPort` y la acción "Iniciar limpieza".
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: el diálogo "Reportar daño".
  - `plan-confirmar-fin-de-limpieza.md`: la vista de tarea activa a la que se redirige.
- **Orden**: Phase 2 de `plan-marcar-habitacion-en-limpieza.md` → Phase 2 → Phase 3 → Phase 4.
