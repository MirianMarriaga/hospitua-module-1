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
4. **Manejo controlado de errores**: Si la habitación no existe en el inventario de Módulo 1, responder con HTTP 404 Not Found con el formato `ApiError` del plan base.

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

- [ ] T001 Verificar la configuración del repositorio `RoomPersistencePort` en [PLAN/base/plan.md](base/plan.md).
- [ ] T002 Asegurar el registro de `RoomNotFoundException` en `GlobalExceptionHandler` con mapeo a HTTP 404 (`ApiError`).

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
- [ ] T006 [P] [US1] Integration test con `MockMvc` para `GET /api/rooms/{roomId}/base-rate` verificando status 200 y JSON con `roomId`, `categoryRoom`, `maxCapacity`, `baseRate`, `status`, `floor`, `roomNumber`.
- [ ] T007 [P] [US1] Unit test verificando que la consulta puede ejecutarse sobre habitaciones en cualquiera de los 8 estados canónicos sin alterar su estado.

### Implementation for User Story 1

- [ ] T008 [P] [US1] Implementar en `RoomBaseRateQueryService` el método `@Transactional(readOnly = true) RoomBaseRateResponseDto getRoomBaseRate(UUID roomId)`.
- [ ] T009 [P] [US1] Implementar en `RoomBaseRateController.java` el endpoint `GET /api/rooms/{roomId}/base-rate`.

---

## Phase 4: User Story 2 - Manejo de consulta sobre habitación no encontrada (Priority: P1)

**Goal**: Responder de forma controlada con HTTP 404 cuando se consulte la tarifa de una habitación que no exista en el inventario.

**Independent Test**: Invocar `GET /api/rooms/{roomId}/base-rate` con un UUID inexistente y validar respuesta HTTP 404 Not Found con mensaje estructurado.

### Tests for User Story 2

- [ ] T010 [P] [US2] Unit test para `RoomBaseRateQueryService` arrojando `RoomNotFoundException` ante ID no existente.
- [ ] T011 [P] [US2] Integration test para `GET /api/rooms/{randomUUID}/base-rate` verificando status 404 y estructura `ApiError`.

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
