# Implementation Plan: Consultar Inventario de Habitaciones

**Date**: 2026-10-09  
**Spec**: [spec-consultar-inventario-habitaciones.md](../SPEC/spec-consultar-inventario-habitaciones.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Consultar Inventario de Habitaciones**, de solo lectura (FR-006), para tres consumidores (FR-001):

1. **Gerente**: listado paginado con filtros y orden, y la opción "Ver historial" en cada habitación, que abre *Consultar historial de estados* filtrado por esa habitación (FR-007).
2. **Administrador**: el mismo listado con el botón "Registrar habitación" y, en cada habitación, las acciones según su estado: "Editar" y "Dar de baja" en `Available`, "Marcar como disponible" en `Inactive` y ninguna en los demás (FR-008).
3. **Módulo 2**: las habitaciones vendibles, por categoría o por identificador, en una lista simple sin paginar que nunca incluye habitaciones `Inactive` (FR-010, FR-011). Está descrito en los dos endpoints, en "Respuesta para `MODULE_2`".

En las vistas, el listado incluye por defecto las habitaciones `Inactive` (FR-003), no muestra el UUID en las vistas (FR-002) y se pagina de 10 en 10 (FR-009). Este plan expone además el detalle de una habitación, que usan los formularios de edición y baja.

---

## Technical Context

- **Storage**: PostgreSQL 16+ (tabla `room` del plan base; este caso de uso no crea tablas). Agrega índices de consulta sobre `room` en la migración `V4__room_inventory_indexes.sql`.
- **Performance Goals**: listado con filtros u orden en menos de 5 segundos para inventarios de hasta 5000 habitaciones (SC-001).
- **Constraints**:
  - Solo lectura: ningún endpoint de este plan modifica datos ni ejecuta transiciones (FR-006).
  - Una consulta por `roomNumber` devuelve como máximo una habitación; un número inexistente devuelve una lista vacía, no un error (FR-004, caso borde 3). Módulo 2 consulta con sus propios parámetros (FR-010).
  - En las vistas, sin filtros, el listado incluye las habitaciones `Inactive` (FR-003; SC-002). Módulo 2 nunca las recibe (FR-010; SC-003).
  - Al cambiar un filtro, el frontend vuelve a la página 1; al cambiar de página conserva filtros y orden (FR-009, esc. 7).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`. Roles autorizados según cada endpoint.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Estados**: la API usa el nombre en inglés; la interfaz muestra el nombre en español (regla 12 del plan base).
- **Categorías**: la API usa `categoryRoom` con los valores `SENCILLA`, `DOBLE`, `SUITE` y `BOUTIQUE`, los mismos que usan Módulo 2 y Módulo 3; la interfaz los muestra como "Sencilla", "Doble", "Suite" y "Boutique". Es un catálogo fijo (*Registrar habitación* FR-005), así que no hay un endpoint que lo liste.
- **Módulo 2**: es el GET "Consultar inventario de habitaciones" (M2 → M1) del diagrama `mod-1-2-3`, y coincide con el contrato de su plan `consult-room-inventory` (A1 y A2). Usa un JWT de servicio con rol `MODULE_2` (plan base, T015) y recibe solo habitaciones vendibles, nunca `Inactive`, con los campos `id`, `roomNumber`, `categoryRoom` y `maxCapacity` (FR-010, FR-011).
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | `AuthenticationException` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | `AccessDeniedException` | El usuario no tiene un rol autorizado | "No tienes permiso para realizar esta acción." |

---

## GET /api/rooms

**Descripción:** Para las vistas del Gerente y del Administrador, devuelve el inventario paginado con filtros y orden (FR-001 a FR-006, FR-009). Para Módulo 2, devuelve las habitaciones vendibles de una categoría (FR-010).
**Roles autorizados:** `MANAGER`, `ADMINISTRATOR`, `MODULE_2`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:** en las vistas, todos opcionales y combinables (esc. 2). Con el rol `MODULE_2` solo se usa `categoryRoom`, que es obligatorio (FR-010).

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomNumber` | query | string | No | Coincidencia exacta, sin espacios al inicio ni al final (FR-004) | `203` |
| `categoryRoom` | query | string | No (sí para `MODULE_2`) | `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE` | `DOBLE` |
| `floor` | query | integer | No | Piso | `2` |
| `status` | query | string | No | Uno de los 8 estados; sin este filtro se incluyen todos, también `Inactive` (FR-003) | `Available` |
| `sort` | query | string | No | `roomNumber` (por defecto), `floor`, `categoryRoom`, `maxCapacity`, `baseRate` o `status` (FR-005, esc. 3) | `baseRate` |
| `direction` | query | string | No | `asc` (por defecto) o `desc` | `asc` |
| `page` | query | integer | No | Página, desde 1 (por defecto 1) (FR-009) | `1` |
| `size` | query | integer | No | Tamaño de página; las vistas usan 10 (FR-009); máximo 100 | `10` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `items` | array | Habitaciones de la página | |
| `items[].roomId` | UUID | Identificador; el frontend lo usa para las acciones pero no lo muestra (FR-002) | `"9b2e7c1a-…"` |
| `items[].roomNumber` | string | Número | `"203"` |
| `items[].floor` | integer | Piso | `2` |
| `items[].categoryRoom` | string | Categoría (tipo) | `"DOBLE"` |
| `items[].maxCapacity` | integer | Capacidad máxima | `2` |
| `items[].baseRate` | number | Tarifa base | `250000` |
| `items[].status` | string | Estado actual | `"Available"` |
| `page` | integer | Página actual | `1` |
| `size` | integer | Tamaño de página | `10` |
| `totalElements` | integer | Total de habitaciones que cumplen los filtros | `24` |
| `totalPages` | integer | Total de páginas | `3` |

**Body (JSON):**

```json
{
  "items": [
    {
      "roomId": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182",
      "roomNumber": "203",
      "floor": 2,
      "categoryRoom": "DOBLE",
      "maxCapacity": 2,
      "baseRate": 250000,
      "status": "Available"
    }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 24,
  "totalPages": 3
}
```

Una consulta sin coincidencias responde `200` con `items` vacío. El frontend muestra "No hay habitaciones registradas" si no hay filtros (caso borde 1) o "Ninguna habitación cumple los filtros aplicados" si los hay (caso borde 2), siempre con las cabeceras de la tabla. Con `page`, `size` y `totalElements` el frontend arma "Mostrando X–Y de Z".

**Respuesta para `MODULE_2`** (FR-010, FR-011): arreglo simple, sin paginar ni ordenar, con las habitaciones vendibles de la categoría. Nunca incluye habitaciones `Inactive` (FR-003; SC-003). Cada elemento tiene solo `id`, `roomNumber`, `categoryRoom` y `maxCapacity`. Una categoría sin habitaciones vendibles devuelve `[]` (esc. 8).

```json
[
  { "id": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182", "roomNumber": "201", "categoryRoom": "DOBLE", "maxCapacity": 2 },
  { "id": "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f", "roomNumber": "202", "categoryRoom": "DOBLE", "maxCapacity": 3 }
]
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | `categoryRoom`, `status`, `sort` o `direction` fuera de sus valores; `floor`, `page` o `size` no son enteros positivos; `size` mayor a 100 | "Revisa los filtros aplicados." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | Con el rol `MODULE_2`: falta `categoryRoom` o no es una categoría válida (FR-011) | Sin interfaz (Módulo 2) |

```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Revisa los filtros aplicados.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/rooms",
  "details": { "field": "size" }
}
```

---

## GET /api/rooms/{roomId}

**Descripción:** Devuelve el detalle de una habitación. Lo usan el formulario de *Editar habitación* y el paso del motivo de *Dar de baja habitación*. Para Módulo 2, devuelve una habitación vendible por su ID (FR-010).
**Roles autorizados:** `ADMINISTRATOR` y `MODULE_2`, además de los consumidores que lista el plan base

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Habitación | `9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:** los mismos de `items[]` en `GET /api/rooms`, más `version` (bloqueo optimista que envía *Editar habitación*).

```json
{
  "roomId": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182",
  "roomNumber": "203",
  "floor": 2,
  "categoryRoom": "DOBLE",
  "maxCapacity": 2,
  "baseRate": 250000,
  "status": "Available",
  "version": 4
}
```

**Respuesta para `MODULE_2`** (FR-010): un objeto con solo `id`, `roomNumber`, `categoryRoom` y `maxCapacity` (esc. 4).

```json
{ "id": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182", "roomNumber": "201", "categoryRoom": "DOBLE", "maxCapacity": 2 }
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | `roomId` no tiene formato de UUID (FR-011) | "No se encontró la habitación." |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` | No existe la habitación; para `MODULE_2`, también si está `Inactive`, sin revelar sus datos (FR-011, esc. 9) | "No se encontró la habitación." |

```json
{
  "errorCode": "ROOM_NOT_FOUND",
  "message": "No se encontró la habitación.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/rooms/9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182"
}
```

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-consultar-inventario-habitaciones.md
│   ├── spec-consultar-historial-de-estados.md
│   ├── spec-registrar-habitacion.md
│   ├── spec-editar-habitacion.md
│   ├── spec-dar-de-baja-habitacion.md
│   └── spec-marcar-habitacion-como-disponible.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-consultar-inventario-habitaciones.md
    └── plan-consultar-historial-de-estados.md
```

### Source Code

```text
backend/src/main/
├── java/com/hospitua/habitaciones/
│   ├── domain/
│   │   └── ports/in/
│   │       └── ConsultRoomInventoryUseCase.java
│   ├── application/
│   │   ├── service/
│   │   │   └── RoomInventoryQueryService.java
│   │   └── dto/
│   │       ├── RoomInventoryFilter.java
│   │       ├── RoomDto.java                       # Definido en plan-registrar-habitacion.md
│   │       ├── Module2RoomDto.java                # Respuesta para Módulo 2: id, roomNumber, categoryRoom, maxCapacity
│   │       └── PagedResultDto.java
│   └── infrastructure/adapters/
│       ├── in/web/
│       │   └── RoomController.java                # Se agregan GET /api/rooms y GET /api/rooms/{roomId}
│       └── out/persistence/
│           └── repository/RoomInventorySpecifications.java   # Filtros dinámicos (JPA Specifications)
└── resources/db/migration/
    └── V4__room_inventory_indexes.sql
frontend/src/
├── components/
│   ├── RoomInventoryTable.jsx                     # Tabla compartida: número, piso, tipo, capacidad, tarifa y estado
│   ├── RoomInventoryFilters.jsx                   # Número, tipo, piso y estado
│   └── Pagination.jsx                             # "Mostrando X–Y de Z", "Anterior", "Siguiente" y número de página
├── pages/
│   ├── administracion/
│   │   └── AdminRoomInventoryPage.jsx             # Botón "Registrar habitación" y acciones por estado
│   └── gerencia/
│       └── ManagerRoomInventoryPage.jsx           # "Ver historial" en cada fila
└── api/
    └── roomService.js                             # Se agregan getRooms() y getRoom()
```

**Structure Decision**: Las dos vistas comparten `RoomInventoryTable`, `RoomInventoryFilters` y `Pagination`; cada página agrega solo sus acciones por fila. Las acciones del Administrador navegan a las páginas de `plan-registrar-habitacion.md`, `plan-editar-habitacion.md`, `plan-dar-de-baja-habitacion.md` y `plan-marcar-habitacion-como-disponible.md`; "Ver historial" navega a `plan-consultar-historial-de-estados.md` con `roomId` en la URL. Con el rol `MODULE_2`, el controlador devuelve `Module2RoomDto` (arreglo o objeto) y el servicio excluye las habitaciones `Inactive` antes de consultar.

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Crear la migración `V4__room_inventory_indexes.sql` con índices sobre `room(status)`, `room(room_category)` y `room(floor)` (SC-001).
- [ ] T002 [P] Implementar `RoomInventorySpecifications` con los filtros combinables por `roomNumber`, `categoryRoom`, `floor` y `status`, y el orden por las columnas permitidas.
- [ ] T003 [P] Crear las rutas del frontend para las vistas de administración y gerencia dentro de sus layouts por rol (plan base, T016).

---

## Phase 3: User Story 1 - Consulta del inventario general (Priority: P1)

**Goal**: Que el Gerente y el Administrador consulten el inventario completo con filtros, orden y paginación, que cada vista ofrezca sus acciones, y que Módulo 2 reciba las habitaciones vendibles con su contrato.

**Independent Test**: Cargar 24 habitaciones en distintos estados, incluidas 2 `Inactive`. Consultar sin filtros y verificar 24 resultados en 3 páginas con las `Inactive` incluidas; filtrar por tipo, piso y estado y verificar el subconjunto; ordenar por tarifa; consultar como `MODULE_2` por categoría y por `roomId` y verificar que nunca llega una `Inactive`. Revisar las acciones por fila de cada vista.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `RoomInventoryQueryServiceTest`: sin filtros devuelve todas las habitaciones, incluidas las `Inactive` (esc. 1; FR-003; SC-002).
- [ ] T005 [P] [US1] Unit test: los filtros por tipo, piso y estado se combinan (esc. 2; FR-004).
- [ ] T006 [P] [US1] Unit test: el orden se aplica sin alterar los filtros (esc. 3; FR-005).
- [ ] T007 [P] [US1] Unit test: el filtro por `roomNumber` devuelve como máximo una habitación; un número inexistente devuelve una lista vacía, sin error (caso borde 3; FR-004).
- [ ] T008 [P] [US1] Unit test: la paginación devuelve 10 por página con `totalElements` y `totalPages` correctos (esc. 7; FR-009).
- [ ] T009 [US1] Integration test del contrato con Módulo 2: `GET /api/rooms?categoryRoom=DOBLE` devuelve un arreglo sin paginar con solo `id`, `roomNumber`, `categoryRoom` y `maxCapacity`, sin la habitación `Inactive` de esa categoría; una categoría sin habitaciones devuelve `[]`; sin `categoryRoom` o con una categoría inválida, `400`; `GET /api/rooms/{roomId}` de una `Inactive` responde `404` para `MODULE_2` y `200` para `ADMINISTRATOR` (esc. 4, 8 y 9; FR-010, FR-011; SC-003).
- [ ] T010 [US1] Integration test: ninguna consulta modifica `room` ni `room_state_history` (FR-006).
- [ ] T011 [P] [US1] Component test en `ManagerRoomInventoryPage`: la tabla muestra número, piso, tipo, capacidad, tarifa y estado en español, no muestra el UUID y "Ver historial" navega al historial con la habitación (esc. 5; FR-002, FR-007).
- [ ] T012 [P] [US1] Component test en `AdminRoomInventoryPage`: "Editar" y "Dar de baja" solo en `Available`, "Marcar como disponible" solo en `Inactive`, ninguna acción en los demás estados y "Registrar habitación" sobre el listado (esc. 6; FR-008).
- [ ] T013 [P] [US1] Component test en `Pagination` y `RoomInventoryFilters`: "Mostrando 1–10 de 24", "Anterior", "Siguiente" y número de página; al cambiar un filtro vuelve a la página 1 y al cambiar de página conserva filtros y orden (esc. 7; FR-009).
- [ ] T014 [P] [US1] Component test: inventario vacío y filtros sin resultados muestran las cabeceras con su mensaje, sin error (casos borde 1 y 2).

### Implementation for User Story 1

- [ ] T015 [US1] Implementar `RoomInventoryQueryService` (`@Transactional(readOnly = true)`) sobre `RoomInventorySpecifications`, con paginación y orden (FR-003 a FR-006, FR-009), y con el filtro `status != Inactive` cuando el rol es `MODULE_2` (FR-010).
- [ ] T016 [US1] Agregar a `RoomController` `GET /api/rooms` (con `categoryRoom` obligatorio para `MODULE_2`) y `GET /api/rooms/{roomId}`; con el rol `MODULE_2` responder con `Module2RoomDto`, en arreglo sin paginar o en objeto, y `404` para una `Inactive` (FR-001, FR-004, FR-010).
- [ ] T017 [US1] Construir en el frontend `RoomInventoryTable.jsx`, `RoomInventoryFilters.jsx` y `Pagination.jsx`, con los estados y las categorías en español (FR-002, FR-004, FR-009).
- [ ] T018 [US1] Construir `ManagerRoomInventoryPage.jsx` con "Ver historial" por fila (FR-007).
- [ ] T019 [US1] Construir `AdminRoomInventoryPage.jsx` con "Registrar habitación" y las acciones por estado (FR-008).

**Checkpoint**: El inventario es consultable por los tres consumidores y sirve de entrada a los demás casos de uso de gestión de habitaciones.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T020 Integration test con 5000 habitaciones: el listado con filtros y orden responde en menos de 5 segundos (SC-001), y la consulta de Módulo 2 por categoría o por ID responde en menos de 1 segundo (SC-003).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base la tabla `room` (T007), los roles `MANAGER`, `ADMINISTRATOR` y `MODULE_2` (T015) y los layouts por rol del frontend (T016).
- **Planes relacionados**:
  - `plan-registrar-habitacion.md`: `RoomDto` y la página de registro a la que lleva el botón.
  - `plan-editar-habitacion.md` y `plan-dar-de-baja-habitacion.md`: usan `GET /api/rooms/{roomId}`.
  - `plan-marcar-habitacion-como-disponible.md`: acción "Marcar como disponible".
  - `plan-consultar-historial-de-estados.md`: destino de "Ver historial".
  - Los planes de limpieza y mantenimiento usan `GET /api/rooms?status=…` en sus pruebas de integración.
- **Nota**: la tabla de endpoints del plan base lista a Recepcionista como consumidor de `GET /api/rooms`, pero el FR-001 del spec solo autoriza a Gerente, Administrador y Módulo 2. Si Recepción lo necesita, hay que agregarlo al spec.
- **Orden**: Phase 2 → Phase 3 → Phase 4. Se implementa después de `plan-registrar-habitacion.md`.
