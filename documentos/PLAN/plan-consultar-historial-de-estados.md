# Implementation Plan: Consultar Historial de Estados de una Habitación

**Date**: 2026-10-09  
**Spec**: [spec-consultar-historial-de-estados.md](../SPEC/spec-consultar-historial-de-estados.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Consultar Historial de Estados**, de solo lectura (FR-005), para el Gerente (FR-001). La vista muestra los periodos de `room_state_history`, uno por fila, con habitación, tipo, estado, inicio, fin (o "En curso" si el periodo sigue abierto), responsable (o "Sistema" si la transición fue autónoma) y flujo de origen (FR-003, FR-004). Así el Gerente puede auditar quién limpió o reparó una habitación y cuánto tardó. Se puede:

- filtrar por habitación (número o identificador), tipo, estado y rango de fechas (FR-002), validando que "Desde" no sea posterior a "Hasta" (FR-006);
- abrir desde "Ver historial" del inventario con la habitación ya filtrada (FR-007);
- paginar de 15 en 15 (FR-008).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Lee `room_state_history` y `room` del plan base; no crea tablas. Agrega índices de consulta en la migración `V5__room_state_history_indexes.sql`.
- **Performance Goals**: la vista carga en menos de 10 segundos con cualquier combinación de filtros, incluso con miles de periodos (SC-001, caso borde "Gran rango de fechas").
- **Constraints**:
  - Solo lectura: no modifica datos ni ejecuta transiciones (FR-005).
  - Snapshot consistente sin bloquear las transiciones de otras habitaciones: transacción `readOnly` con aislamiento `REPEATABLE_READ`, que en PostgreSQL lee una instantánea sin bloquear escrituras (caso borde "Concurrente"; SC-004).
  - Rango de fechas: incluye los periodos que se cruzan con el rango, es decir, que empiezan antes del fin de "Hasta" y terminan después del inicio de "Desde" o siguen abiertos.
  - Orden cronológico por inicio del periodo; a igual inicio, por número de habitación (SC-002).
  - El tipo que se muestra y se filtra es el tipo actual de la habitación (`room.room_category`); el historial no guarda el tipo de cada periodo.
  - Toda habitación tiene al menos un periodo, el abierto por *Registrar habitación* (FR-013 de ese spec); una habitación sin cambios muestra una fila "En curso" (caso borde "Historial vacío").

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `MANAGER` (FR-001).
- **Fechas**: los filtros son fechas de calendario (`YYYY-MM-DD`); la interfaz las pide y muestra como DD-MM-YYYY. Los periodos son ISO 8601 con zona de Colombia; la interfaz los muestra como DD-MM-YYYY HH:MM.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Estados**: la API usa el nombre en inglés; la interfaz muestra el nombre en español (regla 12 del plan base).
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | `AuthenticationException` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | `AccessDeniedException` | El usuario no tiene el rol `MANAGER` (FR-001) | "No tienes permiso para realizar esta acción." |

---

## GET /api/room-state-history

**Descripción:** Devuelve los periodos del historial de estados con filtros y paginación (FR-002 a FR-006, FR-008).
**Rol autorizado:** `MANAGER`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:** todos opcionales y combinables (esc. 2).

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | query | UUID | No | Habitación por identificador; lo usa "Ver historial" (FR-002, FR-007) | `9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182` |
| `roomNumber` | query | string | No | Habitación por número, coincidencia exacta (FR-002) | `203` |
| `categoryRoom` | query | string | No | `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE` | `DOBLE` |
| `status` | query | string | No | Uno de los 8 estados | `InCleaning` |
| `from` | query | date | No | "Desde" | `2026-01-01` |
| `to` | query | date | No | "Hasta" | `2026-01-31` |
| `page` | query | integer | No | Página, desde 1 (por defecto 1); siempre de 15 registros (FR-008) | `1` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `items` | array | Periodos de la página, en orden cronológico | |
| `items[].roomId` | UUID | Habitación; no se muestra | `"9b2e…"` |
| `items[].roomNumber` | string | Número | `"203"` |
| `items[].categoryRoom` | string | Categoría (tipo) actual de la habitación | `"DOBLE"` |
| `items[].status` | string | Estado del periodo | `"InCleaning"` |
| `items[].startDateTime` | string | Inicio del periodo | `"2026-01-14T09:15:00-05:00"` |
| `items[].endDateTime` | string \| null | Fin del periodo; nulo si sigue abierto (el frontend muestra "En curso", FR-004) | `"2026-01-14T10:05:00-05:00"` |
| `items[].actorName` | string \| null | Nombre del usuario que provocó la transición (`user_account.full_name` de `actor_id`); nulo si fue autónoma (el frontend muestra "Sistema", FR-003) | `"Ana Gómez"` |
| `items[].sourceFlow` | string | Flujo de origen (valor de `SourceFlow`, plan base T020) | `"START_CLEANING"` |
| `items[].sourceFlowName` | string | Nombre legible del caso de uso que provocó la transición (FR-003) | `"Marcar habitación en limpieza"` |
| `page` | integer | Página actual | `1` |
| `size` | integer | Siempre 15 | `15` |
| `totalElements` | integer | Total de periodos que cumplen los filtros | `42` |
| `totalPages` | integer | Total de páginas | `3` |

**Body (JSON):**

```json
{
  "items": [
    {
      "roomId": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182",
      "roomNumber": "203",
      "categoryRoom": "DOBLE",
      "status": "InCleaning",
      "startDateTime": "2026-01-14T09:15:00-05:00",
      "endDateTime": "2026-01-14T10:05:00-05:00",
      "actorName": "Ana Gómez",
      "sourceFlow": "START_CLEANING",
      "sourceFlowName": "Marcar habitación en limpieza"
    }
  ],
  "page": 1,
  "size": 15,
  "totalElements": 42,
  "totalPages": 3
}
```

Una consulta sin coincidencias responde `200` con `items` vacío y el frontend muestra "No se encontraron resultados" (caso borde "Filtros sin resultados"). Con `page`, `size` y `totalElements` el frontend arma "Mostrando X–Y de Z" (FR-008).

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `INVALID_DATE_RANGE` | `InvalidDateRangeException` | `from` es posterior a `to` (FR-006, esc. 4); `details.rule` = `FROM_AFTER_TO` | "La fecha \"Desde\" no puede ser posterior a \"Hasta\"." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | `categoryRoom` o `status` fuera de sus valores, fecha con formato inválido o `page` no es un entero positivo | "Revisa los filtros aplicados." |

```json
{
  "errorCode": "INVALID_DATE_RANGE",
  "message": "La fecha \"Desde\" no puede ser posterior a \"Hasta\".",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/room-state-history",
  "details": { "rule": "FROM_AFTER_TO" }
}
```

El frontend también valida el rango antes de consultar, y no muestra resultados mientras el rango sea inválido (FR-006).

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-consultar-historial-de-estados.md
│   ├── spec-consultar-inventario-habitaciones.md
│   └── referencias/maquina-estados-habitacion.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-consultar-historial-de-estados.md
    └── plan-consultar-inventario-habitaciones.md
```

### Source Code

```text
backend/src/main/
├── java/com/hospitua/habitaciones/
│   ├── domain/
│   │   └── ports/in/
│   │       └── ConsultRoomStateHistoryUseCase.java
│   ├── application/
│   │   ├── service/
│   │   │   └── RoomStateHistoryQueryService.java
│   │   └── dto/
│   │       ├── RoomStateHistoryFilter.java
│   │       ├── RoomStateHistoryRowDto.java
│   │       └── PagedResultDto.java                # Definido en plan-consultar-inventario-habitaciones.md
│   └── infrastructure/adapters/
│       ├── in/web/
│       │   └── RoomStateHistoryController.java
│       └── out/persistence/
│           └── repository/RoomStateHistoryQueryRepository.java   # Consulta con join a room y a user_account
└── resources/db/migration/
    └── V5__room_state_history_indexes.sql
frontend/src/
├── pages/gerencia/
│   ├── RoomStateHistoryPage.jsx                   # Filtros, tabla y paginación
│   └── RoomStateHistoryFilters.jsx                # Habitación, tipo, estado, "Desde" y "Hasta"
└── api/
    └── roomStateHistoryService.js
```

**Structure Decision**: La consulta une `room_state_history` con `room`, para obtener el número y el tipo, y con `user_account` (unión externa por `actor_id`), para el nombre del responsable; si `actor_id` es nulo, el responsable es "Sistema". El nombre legible del flujo sale del enum `SourceFlow` (plan base, T020). Reutiliza `Pagination.jsx` de `plan-consultar-inventario-habitaciones.md`. La página lee `roomId` de la URL para aplicar el filtro de habitación cuando llega desde "Ver historial" (FR-007).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Crear la migración `V5__room_state_history_indexes.sql` con índices sobre `room_state_history(room_id, start_date_time)` y `room_state_history(status, start_date_time)` (SC-001).
- [ ] T002 [P] Implementar `RoomStateHistoryQueryRepository` con los filtros combinables, el cruce de rango de fechas, el orden cronológico y la paginación de 15.

---

## Phase 3: User Story 1 - Consultar el historial de estados de una habitación (Priority: P1)

**Goal**: Que el Gerente consulte y filtre el historial de estados, también desde el inventario, sin afectar la operación.

**Independent Test**: Registrar la habitación 203 y llevarla por varias transiciones. Consultar su historial y verificar las filas en orden cronológico con el último periodo "En curso"; filtrar por `InCleaning` entre el 01-01-2026 y el 31-01-2026; enviar "Desde" posterior a "Hasta" y verificar el `400`.

### Tests for User Story 1

- [ ] T003 [P] [US1] Unit test en `RoomStateHistoryQueryServiceTest`: el historial de una habitación se devuelve en orden cronológico con número, tipo, estado, inicio, fin, responsable y flujo de origen; una transición autónoma llega con `actorName` nulo (esc. 1; FR-003; SC-002).
- [ ] T004 [P] [US1] Unit test: los filtros por habitación (número o identificador), tipo, estado y rango de fechas se combinan; el rango incluye los periodos que se cruzan con él (esc. 2; FR-002; SC-003).
- [ ] T005 [P] [US1] Unit test: un periodo abierto llega con `endDateTime` nulo (casos borde "Historial vacío" y "Transición sin fin"; FR-004).
- [ ] T006 [P] [US1] Unit test: `from` posterior a `to` se rechaza con `INVALID_DATE_RANGE` sin consultar (esc. 4; FR-006).
- [ ] T007 [P] [US1] Unit test: la paginación devuelve 15 por página con `totalElements` y `totalPages` correctos (FR-008).
- [ ] T008 [US1] Integration test con Testcontainers: la consulta no modifica datos y no bloquea una transición que ocurre al mismo tiempo en otra habitación (FR-005, caso borde "Concurrente"; SC-004).
- [ ] T009 [P] [US1] Component test en `RoomStateHistoryPage`:
  - Muestra habitación, tipo, estado en español, inicio, fin o "En curso", responsable o "Sistema", y flujo de origen.
  - Al llegar con `roomId` en la URL, el filtro de habitación ya viene aplicado.
  - Con "Desde" posterior a "Hasta" muestra el error y no muestra resultados.
  - Sin coincidencias muestra "No se encontraron resultados".
  - Al cambiar un filtro vuelve a la página 1.
  - (esc. 3 y 4; FR-003, FR-004, FR-006 a FR-008)

### Implementation for User Story 1

- [ ] T010 [US1] Implementar `RoomStateHistoryQueryService` con `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`, validando el rango antes de consultar (FR-002 a FR-006, FR-008).
- [ ] T011 [US1] Implementar `RoomStateHistoryController` con `GET /api/room-state-history`, restringido a `MANAGER` (FR-001).
- [ ] T012 [US1] Construir en el frontend `RoomStateHistoryFilters.jsx` y `RoomStateHistoryPage.jsx` con `Pagination.jsx`, leyendo `roomId` de la URL (FR-007).
- [ ] T013 [US1] Verificar que "Ver historial" de `ManagerRoomInventoryPage.jsx` navegue a esta página con el `roomId` (`plan-consultar-inventario-habitaciones.md`, T018).

**Checkpoint**: El Gerente puede auditar el historial de estados de cualquier habitación.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T014 Integration test con decenas de miles de periodos: la consulta con cualquier combinación de filtros responde en menos de 10 segundos (SC-001, caso borde "Gran rango de fechas").
- [ ] T015 Integration test: los periodos que registran los flujos de transición (registro, baja, reactivación, reservas, limpieza, mantenimiento, check-in y check-out) aparecen todos en la consulta, sin huecos entre el fin de un periodo y el inicio del siguiente (SC-002).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base `room_state_history` y `room` (T007), el registro de periodos de `TransitionRoomStateUseCase` (regla 8, T017) y el rol `MANAGER` (T015).
- **Planes de los que depende**:
  - `plan-registrar-habitacion.md`: periodo inicial de cada habitación.
  - `plan-consultar-inventario-habitaciones.md`: "Ver historial", `Pagination.jsx` y `PagedResultDto`.
  - Todos los planes que cambian el estado de una habitación alimentan el historial a través de la regla 8.
- **Orden**: Phase 2 → Phase 3 → Phase 4. Se implementa después de `plan-consultar-inventario-habitaciones.md`.
