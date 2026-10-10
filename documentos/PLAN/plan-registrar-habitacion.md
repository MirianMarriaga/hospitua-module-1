# Implementation Plan: Registrar Habitación

**Date**: 2026-10-09  
**Spec**: [spec-registrar-habitacion.md](../SPEC/spec-registrar-habitacion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Registrar Habitación** para el Administrador. Desde el botón "Registrar habitación" del inventario (*Consultar inventario de habitaciones*, FR-008), el Administrador diligencia número, piso, tipo, capacidad máxima y tarifa base (FR-003). El sistema valida los datos (FR-004 a FR-007, FR-011) y, en una sola transacción:

- crea la habitación con un UUID generado por el sistema (FR-002) y estado `Available` (FR-008);
- abre su primer periodo en `room_state_history` con `previous_status` nulo, `source_flow = REGISTER_ROOM`, el Administrador como actor y la hora del servidor (FR-013). Ese periodo es el registro de la fecha y el usuario de la creación (FR-010), así que no hace falta la bitácora `room_audit_log`.

Al terminar muestra una confirmación con los datos de la habitación y las opciones "Registrar otra" y "Volver al inventario" (FR-012).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa `room` y `room_state_history` del plan base (T007); no crea tablas. No usa la bitácora `room_audit_log` del plan base: esa tabla es para eventos que no son transiciones o que necesitan datos que `room_state_history` no guarda, y el periodo inicial ya guarda todo lo que pide FR-010.
- **Performance Goals**: registrar una habitación en menos de 5 minutos de interacción (SC-001); la habitación aparece en el inventario sin recargar (SC-002).
- **Constraints**:
  - Solo el rol `ADMINISTRATOR` registra habitaciones (FR-001).
  - El UUID lo genera el servidor; el cuerpo de la petición no lo acepta (FR-002).
  - El número es único en todo el inventario, sin importar el estado ni el piso (FR-004). Se garantiza con el índice único de `room.room_number` además de la validación del servicio.
  - El estado inicial siempre es `Available`; la petición no permite elegirlo (FR-008; *Marcar habitación como reservada*, FR-009).
  - La creación y el primer periodo del historial van en una sola transacción; si algo falla no queda una habitación a medio crear (caso borde 5).
  - La fecha y el usuario de la creación se guardan, pero no se muestran en la confirmación (FR-010, FR-012).

---

## Convenciones de los endpoints

- **Autenticación**: `Authorization: Bearer <JWT>`; rol requerido `ADMINISTRATOR`. El Administrador se toma del token.
- **Fechas**: ISO 8601 con zona de Colombia (ej. `2026-10-09T10:05:00-05:00`).
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Estados en los mensajes**: la interfaz muestra los estados con su nombre en español (regla 12 del plan base).
- **Categorías**: la API usa `categoryRoom` con los valores `SENCILLA`, `DOBLE`, `SUITE` y `BOUTIQUE`, los mismos que usan Módulo 2 y Módulo 3; la interfaz los muestra como "Sencilla", "Doble", "Suite" y "Boutique".
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | No hay token o venció | "Tu sesión expiró. Inicia sesión de nuevo." |
| 403 | `FORBIDDEN` | El usuario no tiene el rol `ADMINISTRATOR` (FR-001) | "No tienes permiso para realizar esta acción." |

---

## POST /api/rooms

**Descripción:** Registra una habitación nueva en estado `Available` y abre su primer periodo en el historial de estados (FR-001 a FR-013).
**Rol autorizado:** `ADMINISTRATOR`

### Petición (Request)

**Headers:**

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Body (JSON):** todos los campos son obligatorios. `roomNumber` se guarda sin espacios al inicio ni al final. Cualquier `roomId` o `status` enviado se ignora (FR-002, FR-008).

| Campo | Tipo | Regla | Ejemplo |
| --- | --- | --- | --- |
| `roomNumber` | string | No vacío; único en todo el inventario (FR-004) | `"407"` |
| `floor` | integer | Entero mayor a 0 (FR-011) | `4` |
| `categoryRoom` | string | `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE` (FR-005); la interfaz los muestra como Sencilla, Doble, Suite y Boutique | `"DOBLE"` |
| `maxCapacity` | integer | Entero mayor a 0 (FR-006) | `2` |
| `baseRate` | number | Mayor a 0 (FR-007) | `250000` |

```json
{
  "roomNumber": "407",
  "floor": 4,
  "categoryRoom": "DOBLE",
  "maxCapacity": 2,
  "baseRate": 250000
}
```

### Respuesta (Response)

**Status Code:** `201 Created`

**Campos de la Respuesta:** los que muestra la confirmación (FR-012).

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `roomId` | UUID | Identificador generado por el sistema (FR-002) | `"9b2e7c1a-…"` |
| `status` | string | Siempre `Available` (FR-008) | `"Available"` |
| `roomNumber` | string | Número de habitación | `"407"` |
| `floor` | integer | Piso | `4` |
| `categoryRoom` | string | Categoría (tipo) | `"DOBLE"` |
| `maxCapacity` | integer | Capacidad máxima | `2` |
| `baseRate` | number | Tarifa base | `250000` |

**Body (JSON):**

```json
{
  "roomId": "9b2e7c1a-4d3f-4e8a-b1c2-3d4e5f607182",
  "status": "Available",
  "roomNumber": "407",
  "floor": 4,
  "categoryRoom": "DOBLE",
  "maxCapacity": 2,
  "baseRate": 250000
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Falta un campo obligatorio (FR-003, esc. 3); `details.fields` lista los campos pendientes | "Completa los campos obligatorios: {campos}." |
| 400 | `VALIDATION_ERROR` | Piso no entero, igual a 0 o negativo (FR-011, caso borde 1) | "El piso debe ser un número entero mayor a 0." |
| 400 | `VALIDATION_ERROR` | Capacidad no entera, igual a 0 o negativa (FR-006, caso borde 2) | "La capacidad máxima debe ser un número entero mayor a 0." |
| 400 | `VALIDATION_ERROR` | Tarifa igual a 0 o negativa (FR-007, esc. 4) | "La tarifa base debe ser mayor a 0." |
| 400 | `VALIDATION_ERROR` | Tipo fuera del catálogo (FR-005, caso borde 4) | "Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique." |
| 409 | `ROOM_NUMBER_ALREADY_EXISTS` | Ya existe una habitación con ese número, en cualquier estado o piso, incluida una registrada al mismo tiempo (FR-004, esc. 2, caso borde 3) | "Ya existe una habitación con el número {número}." |

```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Completa los campos obligatorios: tipo, capacidad máxima.",
  "timestamp": "2026-10-09T10:05:01-05:00",
  "path": "/api/rooms",
  "details": { "fields": ["categoryRoom", "maxCapacity"] }
}
```

Si falla la persistencia o la conexión, la transacción se revierte completa y no queda ninguna habitación creada (caso borde 5).

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-registrar-habitacion.md
│   └── spec-consultar-inventario-habitaciones.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-registrar-habitacion.md
    └── plan-consultar-inventario-habitaciones.md
```

### Source Code

```text
backend/src/main/
├── java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   └── RoomCategory.java                  # Enum: SENCILLA, DOBLE, SUITE, BOUTIQUE
│   │   ├── exception/
│   │   │   └── RoomNumberAlreadyExistsException.java   # 409
│   │   └── ports/
│   │       └── in/
│   │           └── RegisterRoomUseCase.java
│   ├── application/
│   │   ├── service/
│   │   │   └── RegisterRoomService.java
│   │   └── dto/
│   │       ├── RegisterRoomCommand.java
│   │       └── RoomDto.java
│   └── infrastructure/adapters/
│       └── in/web/
│           └── RoomController.java                # Se agrega POST /api/rooms
frontend/src/
├── components/
│   └── roomCategoryLabels.js                  # SENCILLA → "Sencilla", …; lo usan todas las vistas de habitaciones
├── pages/administracion/
│   ├── RegisterRoomPage.jsx                       # Formulario de registro
│   └── RegisterRoomConfirmation.jsx               # Confirmación con "Registrar otra" y "Volver al inventario"
└── api/
    └── roomService.js                             # Se agrega registerRoom()
```

**Structure Decision**: El servicio no escribe en `room_state_history` directamente: abre el primer periodo con el componente transversal del historial (regla 8 del plan base), al que este plan agrega la operación de periodo inicial (T001). `RegisterRoomCommand` contiene `roomNumber`, `floor`, `categoryRoom`, `maxCapacity`, `baseRate` y `administratorId` (del token).

---

## Phase 1: Setup (Shared Infrastructure)

No aplica: usa la infraestructura del plan base.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Agregar a `TransitionRoomStateUseCase` (plan base, regla 8) la operación `registerInitialStatus(roomId, actorId, sourceFlow)`, que abre el primer periodo de una habitación con `previous_status` nulo y devuelve la marca de tiempo del servidor. Coordinar con el dueño del plan base: la regla 8 cubre transiciones y una habitación nueva no tiene estado previo.
- [ ] T002 [P] Implementar `RoomCategory` y registrar en `GlobalExceptionHandler` `RoomNumberAlreadyExistsException` (`409 ROOM_NUMBER_ALREADY_EXISTS`), mapeando también la violación del índice único de `room.room_number`.

---

## Phase 3: User Story 1 - Creación de una nueva habitación en el inventario (Priority: P1)

**Goal**: Que el Administrador registre una habitación válida, que quede en `Available` y visible en el inventario, y que los datos inválidos o duplicados se rechacen sin crear nada.

**Independent Test**: Registrar la habitación "407" y verificar el `201`, el estado `Available`, el UUID generado, y el primer periodo en `room_state_history` con el Administrador como actor. Repetir con el mismo número y verificar el `409` sin habitación nueva.

### Tests for User Story 1

- [ ] T003 [P] [US1] Unit test en `RegisterRoomServiceTest`: el registro válido crea la habitación con UUID del servidor y estado `Available`, ignora `roomId` y `status` del cliente y abre el periodo inicial con `REGISTER_ROOM` (esc. 1; FR-002, FR-008, FR-013).
- [ ] T004 [P] [US1] Unit test: rechazo con `ROOM_NUMBER_ALREADY_EXISTS` si el número existe en otra habitación, incluida una `Inactive` o de otro piso (esc. 2, caso borde 3; FR-004).
- [ ] T005 [P] [US1] Unit test: rechazo con `VALIDATION_ERROR` y la lista de campos faltantes (esc. 3; FR-003).
- [ ] T006 [P] [US1] Unit test: rechazo de piso no entero o menor o igual a 0, capacidad menor o igual a 0, tarifa menor o igual a 0 y tipo fuera del catálogo (esc. 4, casos borde 1, 2 y 4; FR-005 a FR-007, FR-011).
- [ ] T007 [P] [US1] Unit test con `Clock` fijo: el periodo inicial guarda el Administrador del token como actor y la hora del servidor como inicio (FR-010, FR-013).
- [ ] T008 [US1] Integration test con Testcontainers: dos registros simultáneos con el mismo número; solo uno tiene éxito y el otro recibe `409` (FR-004; SC-003).
- [ ] T009 [US1] Integration test: un fallo forzado al abrir el periodo inicial revierte la habitación; no queda una habitación sin su periodo (caso borde 5; SC-004).
- [ ] T010 [P] [US1] Component test en `RegisterRoomPage`: marca los campos pendientes, restringe el tipo a las 4 categorías y, tras el registro, muestra la confirmación con ID, estado, número, piso, tipo, capacidad y tarifa, sin fecha ni usuario, con "Registrar otra" y "Volver al inventario" (esc. 3 y 5; FR-005, FR-012).

### Implementation for User Story 1

- [ ] T011 [US1] Implementar `RegisterRoomService` (`@Transactional`) (FR-001 a FR-011, FR-013):
  - Validar los campos obligatorios y sus reglas.
  - Validar que el número no exista en ninguna habitación.
  - Crear la habitación con UUID generado y estado `Available`.
  - Abrir el primer periodo con `registerInitialStatus(roomId, administratorId, REGISTER_ROOM)`, que guarda el Administrador y la hora de `Clock` (FR-010, FR-013).
- [ ] T012 [US1] Agregar a `RoomController` el endpoint `POST /api/rooms`, restringido a `ADMINISTRATOR` (FR-001).
- [ ] T013 [US1] Construir en el frontend `RegisterRoomPage.jsx` (formulario con número, piso, tipo como lista de las 4 categorías con las etiquetas de `roomCategoryLabels.js`, capacidad y tarifa) y `RegisterRoomConfirmation.jsx` ("Registrar otra" limpia el formulario; "Volver al inventario" regresa al listado) (FR-005, FR-012).
- [ ] T014 [US1] Mostrar los errores del endpoint con el texto de su tabla, señalando cada campo pendiente o inválido (esc. 2 a 4).

**Checkpoint**: El Administrador puede registrar habitaciones y verlas en el inventario.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T015 Integration test: tras el registro, `GET /api/rooms?roomNumber=407` devuelve la habitación en `Available` sin recargar el inventario (FR-009; SC-002).
- [ ] T016 Integration test: ninguna habitación registrada queda sin UUID, sin estado o sin su periodo inicial en `room_state_history` (SC-004).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base la tabla `room` con su índice único de `room_number` (T007), `TransitionRoomStateUseCase` con el registro en el historial (regla 8, T010 y T017), `SourceFlow` con `REGISTER_ROOM` (T020), el rol `ADMINISTRATOR` (T015) y `ClockConfig`.
- **Planes que dependen de este**:
  - `plan-consultar-inventario-habitaciones.md`: botón "Registrar habitación" y listado al que se vuelve.
- **Nota**: las habitaciones que cargue la semilla `V2__seed_rooms.sql` del plan base también necesitan su periodo inicial en `room_state_history` para que *Consultar historial de estados* las muestre.
- **Orden**: Phase 2 → Phase 3 → Phase 4. Este es el primer plan de gestión de habitaciones.
