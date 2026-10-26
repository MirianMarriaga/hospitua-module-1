# Implementation Plan: Consultar Tarifa Base

**Date**: 2026-10-09  
**Spec**: [spec-consultar-tarifa-base.md](../SPEC/spec-consultar-tarifa-base.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso técnico de consulta **Consultar Tarifa Base**, expuesto como endpoint REST de solo lectura por Módulo 1 para consumo de Módulo 3 (Facturación).

Responsabilidades:

1. **Endpoint REST de solo lectura**: Exponer `GET /api/rooms/{roomId}/base-rate?date=YYYY-MM-DD` permitiendo la consulta por el identificador único de habitación (`roomId`); `date` es obligatorio y se devuelve tal cual, pero no cambia la tarifa (la temporada y la tarifa dinámica son de Módulo 3).
2. **Payload de respuesta**: Retornar únicamente los datos necesarios para la tarifación:
    - `roomId` (UUID)
    - `date` (`YYYY-MM-DD`, igual al de la petición)
    - `baseRate` (texto decimal con dos decimales, mayor que cero)
    - `currency` (constante `"COP"`)
3. **Consulta sin mutación de estado**: Operación idempotente de solo lectura (`@Transactional(readOnly = true)`) que no ejecuta transiciones de estado ni interfiere en la máquina de estados de la habitación.
4. **Manejo controlado de errores**: Si la habitación no existe en el inventario de Módulo 1, responder con HTTP 404 Not Found con el formato `ApiError` del plan base.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 4.1+, Spring Web MVC, Spring Data JPA, Hibernate Validator, Lombok
- **Storage**: PostgreSQL 16+ (Lectura directa sobre la tabla `room`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL)
- **Target Platform**: Servidor Linux/Windows
- **Project Type**: Web Application REST API endpoint
- **Performance Goals**:
  - Tiempo de respuesta < 200 ms p95.
  - Cero locks de base de datos (`readOnly = true`).
- **Constraints**:
  - Consulta estricta de solo lectura: sin modificación de atributos ni de estados en `Room`.
  - Tarifa base con exactamente dos decimales (`BigDecimal` en el dominio; texto en la respuesta).
  - Responde en cualquiera de los 8 estados, incluido `Inactive`: toda habitación tiene tarifa base porque es obligatoria al registrarla. A diferencia de `GET /api/rooms/{roomId}` (Módulo 2), que oculta las `Inactive` porque no son vendibles, este endpoint no las excluye; Módulo 3 no tiene casos en que deba consultar una habitación `Inactive`.

---

## Convenciones del endpoint

- **Formatos**: sin marcas de tiempo; `date` como `YYYY-MM-DD`; la respuesta expone la tarifa base como texto decimal de solo lectura.
- **Errores**: esquema `ApiError` del plan base, con `errorCode`.

---

## Integración M3 → M1: GET /api/rooms/{roomId}/base-rate

**Descripción:** Consulta de solo lectura de la tarifa base de una habitación (FR-001 a FR-006).

### Petición (Request)

**Headers:**

| Header | Valor | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `Accept` | `application/json` | Sí | Formato de respuesta esperado |

**Parámetros (path):**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `roomId` | path | UUID | Sí | Identificador único de la habitación | `1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d` |

**Parámetros (query):**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `date` | query | `YYYY-MM-DD` | Sí | Noche que Módulo 3 cotiza; se devuelve tal cual. La tarifa base no depende de la fecha: cualquier fecha válida devuelve la misma `baseRate` | `2026-10-08` |

**Body (JSON):** No aplica.

**Ejemplo de petición HTTP completo:**

```http
GET /api/rooms/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/base-rate?date=2026-10-08 HTTP/1.1
Host: localhost:8081
Accept: application/json
```

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `roomId` | UUID | Identificador único de la habitación (igual al de la petición) | `"1a2b3c4d-…"` |
| `date` | `YYYY-MM-DD` | Fecha recibida en la petición, devuelta tal cual | `"2026-10-08"` |
| `baseRate` | string (decimal) | Tarifa base almacenada, como texto decimal con dos decimales y mayor que cero | `"250000.00"` |
| `currency` | string | Constante | `"COP"` |

**Body (JSON):**

```json
{
  "roomId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "date": "2026-10-08",
  "baseRate": "250000.00",
  "currency": "COP"
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto |
| --- | --- | --- | --- | --- |
| 404 | `ROOM_NOT_FOUND` | `RoomNotFoundException` | El `roomId` no corresponde a una habitación registrada (FR-004). Acordado con Módulo 3: no lo reintenta ni lo cuenta como fallo | "No se encontró la habitación." |
| 400 | `VALIDATION_ERROR` | `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException` o `ConstraintViolationException` | Falta `date`, o no tiene formato de fecha o no existe en el calendario (ej. 2026-02-31) | "La fecha debe tener un formato válido (AAAA-MM-DD)." |

```json
{
  "errorCode": "ROOM_NOT_FOUND",
  "message": "No se encontró la habitación.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/rooms/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/base-rate"
}
```

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
│   │   └── exception/
│   │       └── RoomNotFoundException.java   # Definida en el plan base (catálogo de errores)
│   ├── application/
│   │   ├── ports/
│   │   │   ├── in/
│   │   │   │   └── GetRoomBaseRateUseCase.java
│   │   │   └── out/
│   │   │       └── RoomRepositoryPort.java
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
│       │           └── adapter/
│       │               └── RoomRepositoryAdapter.java
```

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar la configuración del repositorio `RoomRepositoryPort` en [PLAN/base/plan.md](base/plan.md).
- [ ] T002 Verificar el registro de `RoomNotFoundException` en `GlobalExceptionHandler` con mapeo a HTTP 404 (`ApiError`) (catálogo del plan base, T009).

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T003 Implementar el DTO de salida `RoomBaseRateResponseDto` con los atributos: `roomId`, `date`, `baseRate` (`String` construido con `baseRate.setScale(2).toPlainString()`, porque los DTO de aplicación no usan anotaciones de Jackson, regla 1 del plan base), `currency` (constante `"COP"`).
- [ ] T004 Definir la interfaz del puerto de entrada `GetRoomBaseRateUseCase`.

---

## Phase 3: User Story 1 - Consulta de tarifa base de una habitación existente (Priority: P1)

**Goal**: Permitir al Módulo 3 obtener la tarifa base de una habitación específica mediante una petición REST GET.

**Independent Test**: Invocar `GET /api/rooms/{roomId}/base-rate?date=YYYY-MM-DD` con el UUID de una habitación previamente persistida, verificando respuesta HTTP 200 OK con el valor exacto de `baseRate` y sus atributos correspondientes.

### Tests for User Story 1

- [ ] T005 [P] [US1] Unit test para `RoomBaseRateQueryService` comprobando la recuperación de la habitación y el mapeo exacto a `RoomBaseRateResponseDto`.
- [ ] T006 [P] [US1] Verificar que la consulta de una habitación existente retorna HTTP 200 con los cuatro atributos correctos (`roomId`, `date` igual al de la petición, `baseRate` como texto, `currency = "COP"`) y sin ningún otro campo, y que fechas distintas devuelven la misma `baseRate`.
- [ ] T007 [P] [US1] Unit test verificando que la consulta puede ejecutarse sobre habitaciones en cualquiera de los 8 estados canónicos sin alterar su estado.

### Implementation for User Story 1

- [ ] T008 [P] [US1] Implementar el método de consulta de tarifa base en el servicio de aplicación, garantizando que la operación es de solo lectura y no altera el estado de la habitación.
- [ ] T009 [P] [US1] Implementar en `RoomBaseRateController.java` el endpoint `GET /api/rooms/{roomId}/base-rate?date=YYYY-MM-DD` (`date` obligatorio; falta o inválido → 400 `VALIDATION_ERROR`).

---

## Phase 4: User Story 2 - Manejo de consultas inválidas (Priority: P1)

**Goal**: Responder de forma controlada con HTTP 404 cuando se consulte la tarifa de una habitación que no exista en el inventario, y con HTTP 400 cuando falte la fecha o no sea válida.

**Independent Test**: Invocar `GET /api/rooms/{roomId}/base-rate?date=YYYY-MM-DD` con un UUID inexistente y validar HTTP 404 con `ApiError`; invocarlo sin `date` o con una fecha inválida y validar HTTP 400 `VALIDATION_ERROR`.

### Tests for User Story 2

- [ ] T010 [P] [US2] Unit test para `RoomBaseRateQueryService` arrojando `RoomNotFoundException` ante ID no existente.
- [ ] T011 [P] [US2] Integration test para `GET /api/rooms/{randomUUID}/base-rate?date=YYYY-MM-DD` verificando status 404 y estructura `ApiError`.
- [ ] T012 [P] [US2] Integration test: sin `date`, con formato distinto de AAAA-MM-DD y con `2026-02-31`, la respuesta es `400 VALIDATION_ERROR` con `details.field = date` (escenario 4).

### Implementation for User Story 2

- [ ] T013 [P] [US2] Incorporar validación de existencia en `RoomBaseRateQueryService` con lanzamiento de `RoomNotFoundException`.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T014 Validar que `baseRate` (`BigDecimal`) se serializa como texto con exactamente dos decimales (p. ej. `"250000.00"`).
- [ ] T015 Asegurar que la operación no genere registros en la bitácora de auditoría de cambios de estado ni altere el historial de habitaciones.

---

## Dependencies & Execution Order

- **Foundational**: Requiere la entidad `Room` y tabla `room` de [PLAN/base/plan.md](base/plan.md).
- **Consumidores**: Consumido por Módulo 3 (Facturación) para la tarifación de estancias.

---

## Puntos abiertos

- (cerrado: formato de la respuesta acordado con Módulo 3 el 24-oct-2026: `{roomId, date, baseRate, currency}` con `baseRate` como texto decimal y `currency` `"COP"`, y 404 `ROOM_NOT_FOUND` como "no encontrado, no reintentable". `baseRate` se mantiene por habitación en la entidad Room sin cambios estructurales.)
