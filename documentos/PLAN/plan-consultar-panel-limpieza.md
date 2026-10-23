# Implementation Plan: Consultar Panel de Limpieza

**Date**: 2026-10-10  
**Spec**: [spec-consultar-panel-limpieza.md](../SPEC/spec-consultar-panel-limpieza.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Consultar Panel de Limpieza** para el Personal de limpieza: el punto de entrada de su trabajo (FR-001 a FR-011).

- **Listado** (FR-002 a FR-006, FR-010): habitaciones en `PendingCleaning` y `Available`, primero las pendientes, con número, tipo, estado en español, última limpieza y acciones (Pendiente de limpieza: Iniciar limpieza; Disponible: Reportar daño, Iniciar limpieza), más el conteo de pendientes y disponibles, que calcula el frontend sobre la lista.
- **Búsqueda y mensajes** (FR-007, FR-008): por número exacto, en el frontend sobre la lista completa, con los mensajes de búsqueda sin resultados y de panel vacío.
- **Redirección** (FR-001): un miembro con una tarea activa (su tarea abierta) va a la vista de tarea activa de `plan-confirmar-fin-de-limpieza.md`; el panel le entrega `activeTask` con la fila de su habitación.
- **Actualización** (FR-009): el frontend vuelve a consultar el panel cada 60 segundos y al volver de una acción; no hay notificaciones en tiempo real.

Las reglas de las acciones no se implementan aquí: **Iniciar limpieza** usa `POST /api/cleaning/tasks` (`plan-marcar-habitacion-en-limpieza.md`) y **Reportar daño** abre el diálogo de `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`. Este plan es dueño de toda la interfaz de **Iniciar limpieza** (botón, llamada, mensajes con los textos de la tabla de errores de `plan-marcar-habitacion-en-limpieza.md` y navegación).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Solo lectura de `room` y `cleaning_task` del plan base (T007 y T018).
- **Performance Goals**: el panel responde en menos de 2 segundos (SC-001), medido con historial realista de `cleaning_task` (T013).
- **Constraints**:
  - Solo lectura (nota de FR-011): no transiciona habitaciones ni crea o cierra tareas.
  - La última limpieza ignora las tareas con `outcome = Released` (FR-004) y se obtiene con una sola consulta por lotes para todas las habitaciones del panel.
  - Casos límite sin implementación propia: 2 (las habitaciones en otros estados simplemente no se consultan).

---

## Convenciones de los endpoints

- **Fechas**: ISO 8601 con zona de Colombia; la interfaz las muestra como DD-MM-YYYY HH:MM.
- **Errores**: esquema `ApiError` del plan base.
- **Usuario actual**: el miembro del Personal de limpieza se toma del encabezado `X-User` (regla 14 del plan base).
- **Estados y tipos en la interfaz**: nombre en español del estado y etiqueta del tipo (regla 12 del plan base).

---

## GET /api/cleaning/panel

**Descripción:** Devuelve el panel de limpieza del miembro que lo consulta: las habitaciones en `PendingCleaning` y `Available` con su última limpieza, o la tarea activa del miembro con la fila de su habitación para redirigirlo a su vista (FR-001 a FR-011).

### Petición (Request)

**Headers:**

```http
X-User: jramirez
Accept: application/json
```

**Parámetros:** No aplica. La búsqueda por número (FR-007) se hace en el frontend sobre `rooms`.

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `activeTask` | object \| null | Tarea activa del miembro; si no es nula, el frontend redirige a la vista de tarea activa, que toma sus datos de este campo, y `rooms` llega vacío (FR-001) | `null` |
| `activeTask.taskId` | UUID | Tarea abierta del miembro | `"c0ffee00-…"` |
| `activeTask.startDateTime` | string | Inicio de labores | `"2026-10-08T09:15:00-05:00"` |
| `activeTask.room` | object | La habitación de la tarea, con los mismos campos de un ítem de `rooms[]` | |
| `rooms` | array | Habitaciones en `PendingCleaning` o `Available`: primero las `PendingCleaning`, cada grupo por número ascendente en orden natural (plan base, T008) (FR-002, FR-006). El conteo por estado (FR-010) lo calcula el frontend sobre esta lista | |
| `rooms[].roomId` | UUID | Identificador de la habitación | `"3f1c…"` |
| `rooms[].roomNumber` | string | Número de habitación | `"101"` |
| `rooms[].categoryRoom` | string | Código del tipo: `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE` (la interfaz muestra Sencilla, Doble, Suite o Boutique) | `"DOBLE"` |
| `rooms[].status` | string | `PendingCleaning` o `Available` | `"PendingCleaning"` |
| `rooms[].lastCleaningDateTime` | string \| null | `EndDateTime` de la última `CleaningTask` con `outcome` `Completed` o `DamageReported`; nulo si no existe (el frontend muestra "Sin registro", FR-004) | `"2026-10-07T16:40:00-05:00"` |

**Body (JSON):**

```json
{
  "activeTask": null,
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

Un panel sin habitaciones responde `200` con `rooms` vacío y el frontend muestra "No hay habitaciones pendientes de limpieza ni disponibles"; una búsqueda sin coincidencias la detecta el frontend y muestra "No se encontró la habitación X" (FR-008).

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
    ├── application/
    │   ├── ports/in/
    │   │   └── GetCleaningPanelUseCase.java
    │   ├── service/
    │   │   └── CleaningPanelQueryService.java
    │   └── dto/
    │       └── CleaningPanelDto.java              # activeTask, rooms
    └── infrastructure/adapters/in/web/
        └── CleaningController.java                # Definido en plan-marcar-habitacion-en-limpieza.md; aquí se agrega GET /api/cleaning/panel
frontend/src/
├── pages/limpieza/
│   ├── CleaningPanelPage.tsx
│   ├── CleaningSummary.tsx
│   ├── CleaningRoomTable.tsx
│   └── RoomNumberSearch.tsx
└── api/
    └── cleaningService.ts                         # Compartido con los planes de limpieza
```

**Structure Decision**: El panel solo lee. Reutiliza `CleaningTaskRepositoryPort` (`findOpenByMember` y la consulta por lotes `findLastCleaningEndByRoomIds`, `plan-marcar-habitacion-en-limpieza.md` T002) y la consulta de habitaciones por estado del plan base, que ya las entrega en orden natural (T008). La búsqueda y los conteos se calculan en el frontend. Los botones de acción delegan en los endpoints y diálogos de sus propios planes; la interfaz completa de "Iniciar limpieza" es de este plan.

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base (layout de limpieza de T016, `ClockConfig`).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que existan `CleaningTaskRepositoryPort` con `findOpenByMember` y `findLastCleaningEndByRoomIds` (`plan-marcar-habitacion-en-limpieza.md`, T002), `CleaningController` (`plan-marcar-habitacion-en-limpieza.md`, T004) y `RoomRepositoryPort.findByStatusIn(Set<RoomStatus>)` con orden natural (plan base, T008).
- [ ] T002 [P] Implementar `CleaningPanelDto` (con `activeTask`, que reutiliza el ítem de `rooms`) y el puerto `GetCleaningPanelUseCase`.

---

## Phase 3: User Story 1 - Consulta del panel de limpieza (Priority: P1)

**Goal**: Que el miembro vea las habitaciones que puede atender, con su última limpieza y sus acciones, busque por número y vuelva al panel actualizado después de cada acción.

**Independent Test**: Con habitaciones en `PendingCleaning`, `Available`, `InCleaning` y `Occupied`, consultar el panel con un miembro sin tarea y verificar el listado, el orden y la última limpieza; repetir con un miembro con tarea abierta y verificar `activeTask`.

### Tests for User Story 1

- [ ] T003 [P] [US1] Unit test en `CleaningPanelQueryServiceTest`: lista solo `PendingCleaning` y `Available`, primero las pendientes y cada grupo en el orden del puerto (orden natural del plan base, T008) (HU-1 esc. 1; FR-002, FR-006).
- [ ] T004 [P] [US1] Unit test: `lastCleaningDateTime` toma la última tarea `Completed` o `DamageReported` de la consulta por lotes, ignora las `Released` y es nulo sin registro (HU-1 esc. 3; FR-004).
- [ ] T005 [P] [US1] Unit test: con tarea abierta devuelve `activeTask` (con `taskId`, `startDateTime` y la fila de su habitación) y `rooms` vacío (HU-1 esc. 6; FR-001; SC-003).
- [ ] T006 (eliminada: ver T008)
- [ ] T007 (eliminada: la garantiza `@Transactional(readOnly = true)`, nota de FR-011)
- [ ] T008 [P] [US1] Component test en `CleaningPanelPage` con la API simulada: botones por estado, "Sin registro", conteo por estado calculado sobre la lista, búsqueda por número exacto y al vaciarla vuelve el listado completo, mensajes de búsqueda vacía y panel vacío, redirección cuando hay `activeTask` y nueva consulta cada 60 segundos y al volver de una acción. Acción "Iniciar limpieza": muestra la retroalimentación de la acción, redirige a la vista de tarea activa al tener éxito, si se rechaza muestra el texto de la tabla de errores de `plan-marcar-habitacion-en-limpieza.md` según el `errorCode` y recarga el panel, y ante un fallo de red sugiere reintentar (HU-1 esc. 2, 4, 5 y 7; FR-003, FR-005, FR-007, FR-008, FR-009, FR-010; *Marcar Habitación en Limpieza* FR-001, FR-009, FR-016, FR-017).

### Implementation for User Story 1

- [ ] T009 [US1] Implementar `CleaningPanelQueryService` (`@Transactional(readOnly = true)`): detección de tarea activa con su fila, filtro de estados, agrupación (primero `PendingCleaning`) sobre el orden del puerto y última limpieza con una sola llamada a `findLastCleaningEndByRoomIds` para todas las habitaciones (FR-001 a FR-006, FR-011).
- [ ] T010 [US1] Agregar a `CleaningController` el endpoint `GET /api/cleaning/panel`, tomando como miembro al usuario actual (FR-001).
- [ ] T011 [US1] Construir `CleaningPanelPage.tsx`, `CleaningSummary.tsx`, `CleaningRoomTable.tsx` y `RoomNumberSearch.tsx` según `vistas/limpieza/01-panel.html`: conteo por estado calculado sobre `rooms`, tabla con número, tipo, estado ("Pendiente de limpieza" / "Disponible"), última limpieza (DD-MM-YYYY HH:MM o "Sin registro") y botones por estado; búsqueda exacta sobre `rooms`; mensajes de FR-008; redirección si `activeTask` no es nulo (FR-001, FR-003, FR-005, FR-007, FR-008, FR-010).
- [ ] T012 [US1] Conectar las acciones y la actualización:
  - "Iniciar limpieza": este plan es dueño de toda su interfaz. Llama a `POST /api/cleaning/tasks`, muestra la retroalimentación de la acción y, al tener éxito, redirige a la vista de tarea activa; si se rechaza, muestra el texto de la tabla de errores de `plan-marcar-habitacion-en-limpieza.md` según el `errorCode` y recarga el panel; ante fallos de red, sugiere reintentar (*Marcar Habitación en Limpieza* FR-001, FR-009, FR-016, FR-017).
  - "Reportar daño": abre `DamageReportDialog.tsx`, con sus mensajes (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`, T015).
  - El panel se vuelve a consultar cada 60 segundos, al volver de una acción y cuando una acción falla por cambio de estado (FR-005, FR-009).

**Checkpoint**: El Personal de limpieza ve su panel, busca habitaciones y entra a sus acciones; el panel se mantiene actualizado sin recargar la página.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T013 Prueba de rendimiento, etiquetada según la convención de pruebas de rendimiento de la estrategia de testing del plan base (fuera del CI por defecto): con 500 habitaciones y un historial realista de `cleaning_task` (por ejemplo, un año de tareas), `GET /api/cleaning/panel` responde en menos de 2 segundos (SC-001).
- [ ] T014 Integration test: una habitación que pasa a `PendingCleaning` por un check-out aparece en la siguiente consulta del panel (SC-002). Es la única prueba de "aparece en el panel o el inventario" de los planes de limpieza.

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base el layout de limpieza (T016), la tabla `cleaning_task` (T018) y la regla 12.
- **Planes relacionados**:
  - `plan-marcar-habitacion-en-limpieza.md`: `CleaningController`, `CleaningTaskRepositoryPort`, el endpoint `POST /api/cleaning/tasks` y la tabla de errores cuyos textos muestra la interfaz de "Iniciar limpieza".
  - `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`: el diálogo "Reportar daño".
  - `plan-confirmar-fin-de-limpieza.md`: la vista de tarea activa a la que se redirige, que toma sus datos de `activeTask`.
- **Orden**: Phase 2 de `plan-marcar-habitacion-en-limpieza.md` → Phase 2 → Phase 3 → Phase 4.
