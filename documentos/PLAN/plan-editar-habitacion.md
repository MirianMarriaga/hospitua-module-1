# Implementation Plan: Editar Habitación

**Date**: 2026-10-09  
**Spec**: [spec-editar-habitacion.md](../SPEC/spec-editar-habitacion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Editar Habitación** para el Administrador. Desde la acción "Editar" de una habitación `Available` en el inventario (*Consultar inventario de habitaciones*, FR-008), el Administrador modifica número, piso, tipo, capacidad máxima o tarifa base (FR-001). El sistema:

- rechaza la edición si la habitación no está en `Available`, indicando su estado actual (FR-002 a FR-004);
- valida los valores nuevos con las reglas del registro (FR-005) y la unicidad del número en todo el inventario, sin contar la propia habitación (FR-009);
- si no hubo cambios, lo informa sin guardar ni auditar (FR-010);
- si los hay, guarda solo los campos modificados (FR-006), registra la edición en la bitácora con el valor anterior y el nuevo de cada campo (FR-008) y muestra la confirmación "Antes / Ahora" con los datos actualizados (FR-007).

Editar no cambia el estado de la habitación, así que no escribe en `room_state_history`.

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `room` y `room_audit_log` del plan base (T007; su puerto en T017); no crea tablas. La edición no es una transición de estado, así que se registra en `room_audit_log` como `ROOM_EDITED`, uno de los eventos que admite esa tabla.
- **Performance Goals**: editar una habitación en menos de 2 minutos de interacción (SC-001); los cambios se ven en el inventario al confirmar (SC-004).
- **Constraints**:
  - Solo el rol `ADMINISTRATOR` edita habitaciones.
  - El UUID no es editable (FR-001).
  - La validación del estado y de los datos ocurre antes de persistir; un rechazo no modifica nada (SC-002, SC-003).
  - Ediciones simultáneas: la petición envía la `version` leída; si otra edición la cambió, se rechaza con `409` (caso borde "Ediciones simultáneas").
  - El número conservado no se considera duplicado de la propia habitación; cambiar solo el piso nunca genera conflicto de duplicidad (casos borde 1 y 6).
  - La fecha y el usuario de la edición se guardan en la bitácora, pero no se muestran en la confirmación (FR-007).
  - *Decisión aplazada en el spec*: no se consultan las reservas de la habitación al editar.

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `ADMINISTRATOR`. El Administrador se toma del token.
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Estados en los mensajes**: `{estado}` se reemplaza por el nombre en español del estado (regla 12 del plan base).
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | El usuario no tiene el rol `ADMINISTRATOR` | "No tienes permiso para realizar esta acción." |

El formulario se carga con `GET /api/rooms/{roomId}` (`plan-consultar-inventario-habitaciones.md`), que devuelve los datos actuales y la `version`.

---

## PATCH /api/rooms/{roomId}

**Descripción:** Edita los atributos de una habitación `Available` y devuelve los cambios aplicados (FR-001 a FR-010).
**Rol autorizado:** `ADMINISTRATOR`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Habitación a editar | `9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182` |

**Body (JSON):** `version` es obligatoria. Los atributos son opcionales: el frontend envía los valores del formulario y el servidor compara con los actuales para saber qué cambió (FR-006). Las reglas son las de `POST /api/rooms` (FR-005); `roomNumber` no puede quedar vacío.

```json
{
  "version": 4,
  "roomNumber": "101",
  "floor": 1,
  "categoryRoom": "SENCILLA",
  "maxCapacity": 1,
  "baseRate": 195000
}
```

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `changed` | boolean | `false` si no se modificó ningún dato (FR-010) | `true` |
| `changes` | array | Campos modificados; vacío si `changed` es `false` | |
| `changes[].field` | string | `roomNumber`, `floor`, `categoryRoom`, `maxCapacity` o `baseRate` | `"baseRate"` |
| `changes[].previousValue` | string \| number | Valor anterior | `180000` |
| `changes[].newValue` | string \| number | Valor nuevo | `195000` |
| `room` | object | Datos actualizados de la habitación (mismos campos de `GET /api/rooms/{roomId}`) | |

**Body (JSON):**

```json
{
  "changed": true,
  "changes": [
    { "field": "baseRate", "previousValue": 180000, "newValue": 195000 }
  ],
  "room": {
    "roomId": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182",
    "roomNumber": "101",
    "floor": 1,
    "categoryRoom": "SENCILLA",
    "maxCapacity": 1,
    "baseRate": 195000,
    "status": "Available",
    "version": 5
  }
}
```

Con `changed: false` el frontend muestra "No se modificó ningún dato" y no hay registro en la bitácora (esc. 6; FR-010).

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Número vacío (caso borde 2) | "El número de habitación es obligatorio." |
| 400 | `VALIDATION_ERROR` | Piso no entero o menor o igual a 0 (FR-005, caso borde 6) | "El piso debe ser un número entero mayor a 0." |
| 400 | `VALIDATION_ERROR` | Tipo fuera del catálogo (esc. 10) | "Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique." |
| 400 | `VALIDATION_ERROR` | Tarifa menor o igual a 0 (esc. 11) | "La tarifa base debe ser mayor a 0." |
| 400 | `VALIDATION_ERROR` | Capacidad menor o igual a 0 (esc. 12) | "La capacidad máxima debe ser un número entero mayor a 0." |
| 404 | `ROOM_NOT_FOUND` | No existe la habitación (caso borde 3) | "No se encontró ninguna habitación con ese identificador." |
| 409 | `ROOM_INVALID_STATE` | La habitación no está en `Available` (FR-002 a FR-004, esc. 7 a 9) | "La habitación no está disponible para edición (estado actual: {estado})." |
| 409 | `ROOM_NUMBER_ALREADY_EXISTS` | Otra habitación, activa o inactiva, ya tiene ese número (FR-009, esc. 13) | "Ya existe una habitación con el número {número} (aunque esté inactiva)." |
| 409 | `CONCURRENT_UPDATE` | Otra edición cambió la habitación después de abrir el formulario (caso borde "Ediciones simultáneas") | "La información de la habitación ya fue actualizada por otro usuario. Recarga para ver los datos actuales." |

```json
{
  "errorCode": "ROOM_INVALID_STATE",
  "message": "La habitación no está disponible para edición (estado actual: Ocupada).",
  "timestamp": "2026-10-09T11:20:01-05:00",
  "path": "/api/rooms/9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182",
  "details": { "currentStatus": "Occupied" }
}
```

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-editar-habitacion.md
│   └── spec-registrar-habitacion.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-editar-habitacion.md
    ├── plan-registrar-habitacion.md
    └── plan-consultar-inventario-habitaciones.md
```

### Source Code

```text
backend/src/main/java/com/hospitua/habitaciones/
├── domain/
│   ├── model/
│   │   └── RoomAttributeChange.java               # Value Object: campo, valor anterior y valor nuevo
│   └── ports/in/
│       └── EditRoomUseCase.java
├── application/
│   ├── service/
│   │   └── EditRoomService.java
│   └── dto/
│       ├── EditRoomCommand.java
│       └── EditRoomResultDto.java
└── infrastructure/adapters/in/web/
    └── RoomController.java                        # Se agrega PATCH /api/rooms/{roomId}
frontend/src/
├── pages/administracion/
│   ├── EditRoomPage.jsx                           # Formulario precargado
│   └── EditRoomConfirmation.jsx                   # Confirmación "Antes / Ahora" con los datos actualizados
└── api/
    └── roomService.js                             # Se agrega editRoom()
```

**Structure Decision**: Las reglas de los atributos (piso, tipo, capacidad y tarifa) se validan en el modelo de dominio `Room`, de modo que *Registrar* y *Editar* comparten la misma validación (FR-005). `Room.applyChanges(...)` devuelve la lista de `RoomAttributeChange` y no toca `status`. `EditRoomCommand` contiene `roomId`, `version`, los atributos y `administratorId` (del token).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Verificar que `room_audit_log` y su puerto `RoomAuditLogPort` estén disponibles (plan base, T007 y T017) y que exista `RoomNumberAlreadyExistsException` (`plan-registrar-habitacion.md`, T002).
- [ ] T002 [P] Mover a `Room` las validaciones de piso, tipo, capacidad y tarifa, si `plan-registrar-habitacion.md` las dejó en el servicio, e implementar `Room.applyChanges(...)` con `RoomAttributeChange`.
- [ ] T003 [P] Registrar en `GlobalExceptionHandler` el mapeo del bloqueo optimista de `room.version` a `409 CONCURRENT_UPDATE`.

---

## Phase 3: User Story 1 - Edición exitosa de una habitación disponible (Priority: P1)

**Goal**: Que el Administrador edite cualquier atributo de una habitación `Available`, vea qué cambió y que la edición quede auditada.

**Independent Test**: Editar la tarifa de la habitación "101" de 180000 a 195000 y verificar la respuesta con el cambio, la tarifa persistida y el evento `ROOM_EDITED`. Cambiar el número a "110" y verificar que conserva el UUID. Guardar sin cambios y verificar `changed: false` sin evento.

### Tests for User Story 1

- [ ] T004 [P] [US1] Unit test en `EditRoomServiceTest`: editar tarifa, tipo o capacidad persiste solo ese campo y la habitación sigue `Available` (esc. 1 a 3; FR-006).
- [ ] T005 [P] [US1] Unit test: cambiar el número a uno libre conserva el UUID y los demás atributos (esc. 4; FR-001, FR-009).
- [ ] T006 [P] [US1] Unit test: la respuesta lista cada campo modificado con su valor anterior y nuevo, y el evento `ROOM_EDITED` guarda lo mismo con el Administrador y la hora de `Clock` (esc. 5; FR-007, FR-008).
- [ ] T007 [P] [US1] Unit test: sin cambios devuelve `changed: false` y no persiste ni audita (esc. 6; FR-010).
- [ ] T008 [P] [US1] Unit test: conservar el propio número o cambiar solo el piso no genera conflicto de duplicidad (casos borde 1 y 6).
- [ ] T009 [P] [US1] Component test en `EditRoomPage` y `EditRoomConfirmation`: el formulario llega precargado, el UUID no es editable, la confirmación muestra "Antes / Ahora" por cada dato modificado y los datos actualizados, sin fecha ni usuario, y "Sin cambios" muestra el aviso (esc. 5 y 6; FR-007, FR-010).

### Implementation for User Story 1

- [ ] T010 [US1] Implementar `EditRoomService` (`@Transactional`) (FR-001 a FR-010):
  - Cargar la habitación y comparar su `version` con la del comando.
  - Validar que esté en `Available`.
  - Calcular los cambios con `Room.applyChanges(...)`; si no hay, devolver `changed: false` sin persistir.
  - Validar la unicidad del número nuevo en todo el inventario, sin contar la propia habitación.
  - Persistir y registrar `ROOM_EDITED` en `room_audit_log` con los cambios en `details`, el Administrador como `actor_id` y la hora de `Clock` como `event_date_time`.
- [ ] T011 [US1] Agregar a `RoomController` el endpoint `PATCH /api/rooms/{roomId}`.
- [ ] T012 [US1] Construir en el frontend `EditRoomPage.jsx` (formulario precargado con `GET /api/rooms/{roomId}`) y `EditRoomConfirmation.jsx` ("Antes / Ahora" y datos actualizados) (FR-001, FR-007).

**Checkpoint**: El Administrador puede editar habitaciones disponibles con trazabilidad.

---

## Phase 4: User Story 2 - Intento de edición sobre habitación no disponible (Priority: P1)

**Goal**: Que la edición se rechace en cualquier estado distinto de `Available`, informando el estado actual.

**Independent Test**: Intentar editar una habitación en cada uno de los 7 estados no editables y verificar el `409 ROOM_INVALID_STATE` con el estado actual, sin cambios en la base de datos.

### Tests for User Story 2

- [ ] T013 [P] [US2] Unit test: rechazo en `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` e `Inactive`, con el estado actual en `details` (esc. 7 a 9; FR-002 a FR-004; SC-002).
- [ ] T014 [P] [US2] Component test: el error muestra el estado en español.

### Implementation for User Story 2

- [ ] T015 [US2] Lanzar `InvalidRoomTransitionException` o una excepción de estado equivalente con `currentStatus`, traducida a `409 ROOM_INVALID_STATE`, antes de cualquier validación de datos (FR-002, FR-004).

**Checkpoint**: Ninguna habitación comprometida puede editarse.

---

## Phase 5: User Story 3 - Edición con datos inválidos (Priority: P2)

**Goal**: Que los valores inválidos se rechacen sin alterar los datos originales.

**Independent Test**: Enviar un tipo fuera del catálogo, tarifa 0, capacidad 0, número vacío y el número de una habitación `Inactive`; verificar cada rechazo y que la habitación quede igual.

### Tests for User Story 3

- [ ] T016 [P] [US3] Unit test: rechazo de tipo fuera del catálogo, tarifa menor o igual a 0, capacidad menor o igual a 0, piso inválido y número vacío, sin modificar la habitación (esc. 10 a 12, caso borde 2; FR-005; SC-003).
- [ ] T017 [P] [US3] Unit test: rechazo con `ROOM_NUMBER_ALREADY_EXISTS` si otra habitación, incluida una `Inactive`, ya tiene el número (esc. 13; FR-009; SC-006).
- [ ] T018 [US3] Integration test con Testcontainers: dos ediciones simultáneas con la misma `version`; la primera se guarda y la segunda recibe `409 CONCURRENT_UPDATE` (caso borde "Ediciones simultáneas").

### Implementation for User Story 3

- [ ] T019 [US3] Mostrar en el frontend los errores con el texto de la tabla, marcando el campo inválido y conservando los demás valores del formulario (SC-005).

**Checkpoint**: Las tres historias funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T020 Integration test: tras una edición exitosa, `GET /api/rooms` refleja los valores nuevos de inmediato (SC-004).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base la tabla `room` con `version` (T007), el rol `ADMINISTRATOR` (T015) y `ClockConfig`.
- **Planes de los que depende**:
  - Plan base: `room_audit_log` (T007) y `RoomAuditLogPort` (T017).
  - `plan-registrar-habitacion.md`: `RoomCategory` y `RoomNumberAlreadyExistsException`.
  - `plan-consultar-inventario-habitaciones.md`: acción "Editar" y `GET /api/rooms/{roomId}` para cargar el formulario.
- **Orden**: Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6.
