# Implementation Plan: Consultar Tarifa Base

**Date**: 2026-10-09  
**Spec**: [spec-consultar-tarifa-base.md](../SPEC/spec-consultar-tarifa-base.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso técnico de consulta **Consultar Tarifa Base**, expuesto como endpoint REST de solo lectura por Módulo 1 para consumo de Módulo 3 (Facturación) y otros servicios autorizados.

Responsabilidades:
1. **Endpoint REST de solo lectura**: Exponer `GET /api/rooms/{roomId}/base-rate` permitiendo la consulta por el identificador único de habitación (`roomId`) o número de habitación.
2. **Payload de respuesta**: Retornar los atributos estructurales y comerciales de la habitación:
   - `roomId` (UUID)
   - `roomNumber` (String)
   - `categoryRoom` (String)
   - `maxCapacity` (Integer)
   - `baseRate` (BigDecimal con hasta dos decimales)
   - `status` (String con uno de los 8 estados canónicos de `RoomStatus`)
   - `floor` (Integer)
3. **Consulta sin mutación de estado**: Operación idempotente de solo lectura (`@Transactional(readOnly = true)`) que no ejecuta transiciones de estado ni interfiere en la máquina de estados de la habitación.
4. **Manejo controlado de errores**: Si la habitación no existe en el inventario de Módulo 1, responder con HTTP 404 Not Found estructurado según RFC 7807 (`ApiError`).

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, Spring Data JPA, Hibernate Validator, Lombok
- **Storage**: PostgreSQL 16+ (Lectura directa sobre la tabla `room`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL)
- **Target Platform**: Servidor Linux/Windows
- **Project Type**: Web Application REST API endpoint
- **Performance Goals**:
  - Tiempo de respuesta < 200 ms p95.
  - Cero locks de base de datos (`readOnly = true`).
- **Constraints**:
  - Consulta estricta de solo lectura: sin modificación de atributos ni de estados en `Room`.
  - Tarifa base con hasta dos decimales (`BigDecimal`).

---

## Convenciones del endpoint

- **Autenticación**: integración entre módulos M3 → M1 **sin** header `Authorization` (FR-001).
- **Formatos**: sin marcas de tiempo; la respuesta expone la tarifa base como decimal de solo lectura.
- **Errores**: esquema `ApiError` del plan base (RFC 7807) con `errorCode`.

---

## Integración M3 → M1: GET /api/rooms/{roomId}/base-rate

**Descripción:** Consulta de solo lectura de la tarifa base y los atributos de una habitación (FR-001 a FR-006).
**Rol autorizado:** `MODULE_3` (Módulo 3 — Facturación).

### Petición (Request)

**Headers:** no se envía `Authorization` entre módulos.

| Header | Valor | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `Accept` | `application/json` | Sí | Formato de respuesta esperado |
| `Authorization` | — | No | **No se envía** entre módulos (FR-001) |

**Parámetros (path):**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Identificador único de la habitación | `1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d` |

**Body (JSON):** No aplica.

**Ejemplo de petición HTTP completo:**

```http
GET /api/rooms/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/base-rate HTTP/1.1
Host: localhost:8081
Accept: application/json
```

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `roomId` | UUID | Identificador único de la habitación | `"1a2b3c4d-…"` |
| `roomNumber` | string | Número de habitación | `"101"` |
| `categoryRoom` | string | Categoría de la habitación | `"Suite"` |
| `maxCapacity` | integer | Capacidad máxima de personas | `4` |
| `baseRate` | decimal | Tarifa base almacenada, con hasta dos decimales | `150000.00` |
| `status` | string | Uno de los 8 estados canónicos de `RoomStatus` | `"Available"` |
| `floor` | integer | Piso de la habitación | `3` |

**Body (JSON):**

```json
{
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "roomNumber": "101",
  "categoryRoom": "Suite",
  "maxCapacity": 4,
  "baseRate": 150000.00,
  "status": "Available",
  "floor": 3
}
```

### Respuestas de error

| Status Code | errorCode | Cuándo ocurre | Texto |
| --- | --- | --- | --- |
| 404 | `ROOM_NOT_FOUND` | El `roomId` no corresponde a una habitación registrada (FR-004) | "No se encontró la habitación." |

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   └── spec-consultar-tarifa-base.md
└── PLAN/
    ├── base/
    │   └── plan.md
    └── plan-consultar-tarifa-base.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Room.java
│   │   │   └── RoomStatus.java
│   │   ├── exception/
│   │   │   └── RoomNotFoundException.java
│   │   └── ports/
│   │       ├── in/
│   │       │   └── GetRoomBaseRateUseCase.java
│   │       └── out/
│   │           └── RoomPersistencePort.java
│   ├── application/
│   │   ├── service/
│   │   │   └── RoomBaseRateQueryService.java
│   │   └── dto/
│   │       └── RoomBaseRateResponseDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── RoomBaseRateController.java
│       │   └── out/
│       │       └── persistence/
│       │           └── RoomRepositoryAdapter.java
```

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar que `RoomPersistencePort` del plan base ya expone la consulta por `roomId`. No crear un puerto nuevo; reutilizar el existente.
- [ ] T002 Asegurar el registro de `RoomNotFoundException` en `GlobalExceptionHandler` con mapeo a HTTP 404 (RFC 7807).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T003 Implementar el DTO de salida `RoomBaseRateResponseDto` con los atributos: `roomId`, `roomNumber`, `categoryRoom`, `maxCapacity`, `baseRate`, `status`, `floor`.
- [ ] T004 Definir la interfaz del puerto de entrada `GetRoomBaseRateUseCase`.

---

## Phase 3: User Story 1 - Consulta de tarifa base de una habitación existente (Priority: P1)

**Goal**: Permitir al Módulo 3 obtener la tarifa base y atributos complementarios de una habitación específica mediante una petición REST GET.

**Independent Test**: Invocar `GET /api/rooms/{roomId}/base-rate` con el UUID de una habitación previamente persistida, verificando respuesta HTTP 200 OK con el valor numérico exacto de `baseRate` y sus atributos correspondientes.

### Tests for User Story 1

- [ ] T005 [P] [US1] Unit test para `RoomBaseRateQueryService` comprobando la recuperación de la habitación y el mapeo exacto a `RoomBaseRateResponseDto`.
- [ ] T006 [P] [US1] Verificar que la consulta de una habitación existente retorna HTTP 200 con los siete atributos correctos (`roomId`, `roomNumber`, `categoryRoom`, `maxCapacity`, `baseRate`, `status`, `floor`).
- [ ] T007 [P] [US1] Unit test verificando que la consulta puede ejecutarse sobre habitaciones en cualquiera de los 8 estados canónicos sin alterar su estado.

### Implementation for User Story 1

- [ ] T008 [P] [US1] Implementar el método de consulta de tarifa base en el servicio de aplicación, garantizando que la operación es de solo lectura y no altera el estado de la habitación.
- [ ] T009 [P] [US1] Implementar en `RoomBaseRateController.java` el endpoint `GET /api/rooms/{roomId}/base-rate`.

---

## Phase 4: User Story 2 - Manejo de consulta sobre habitación no encontrada (Priority: P1)

**Goal**: Responder de forma controlada con HTTP 404 cuando se consulte la tarifa de una habitación que no exista en el inventario.

**Independent Test**: Invocar `GET /api/rooms/{roomId}/base-rate` con un UUID inexistente y validar respuesta HTTP 404 Not Found con mensaje estructurado.

### Tests for User Story 2

- [ ] T010 [P] [US2] Unit test para `RoomBaseRateQueryService` arrojando `RoomNotFoundException` ante ID no existente.
- [ ] T011 [P] [US2] Integration test para `GET /api/rooms/{randomUUID}/base-rate` verificando status 404 y estructura RFC 7807 (`ApiError`).

### Implementation for User Story 2

- [ ] T012 [P] [US2] Incorporar validación de existencia en `RoomBaseRateQueryService` con lanzamiento de `RoomNotFoundException`.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T013 Validar precisión de dos decimales en `baseRate` (`BigDecimal`).
- [ ] T014 Asegurar que la operación no genere registros en la bitácora de auditoría de cambios de estado ni altere el historial de habitaciones.

---

## Dependencies & Execution Order

- **Foundational**: Requiere la entidad `Room` y tabla `room` de [PLAN/base/plan.md](base/plan.md).
- **Consumidores**: Consumido por Módulo 3 (Facturación) para la tarifación de estancias.

---

## Puntos abiertos

- Formato exacto de empaquetado que Módulo 3 prefiere para la respuesta de tarifa base. Mientras no responden, se expone el objeto completo de Room con todos sus atributos (roomId, roomNumber, categoryRoom, maxCapacity, baseRate, status, floor). baseRate se mantiene por habitación en la entidad Room sin cambios estructurales. Cualquier ajuste queda pendiente para el próximo ciclo de cambios.
