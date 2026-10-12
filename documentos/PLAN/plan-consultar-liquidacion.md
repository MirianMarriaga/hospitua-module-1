# Implementation Plan: Consultar Liquidación

**Date**: 2026-10-08
**Spec**: [spec-consultar-liquidacion.md](../SPEC/spec-consultar-liquidacion.md)
**Plan general**: [base/plan.md](base/plan.md)
**Plan relacionado**: [plan-registrar-check-out.md](plan-registrar-check-out.md) (caso de uso que incluye este mediante `<<includes>>`)

> Este es un **plan específico** de una feature. Todo lo transversal al proyecto (stack, estructura base, setup, infraestructura compartida, convenciones) vive en el plan general `base/plan.md` y aquí solo se referencia.

## Summary

Implementar el caso de uso interno **Consultar Liquidación**, incluido obligatoriamente por **Registrar Check-Out** en sus pasos 2 (Liquidación) y 3 (Pago). Módulo 1 consulta de forma reactiva (REST GET) a Módulo 3 la liquidación y la factura definitiva, y entrega la información estructurada para ambos pasos sin calcular, alterar ni recaudar.

Enfoque técnico:

- `SettlementQueryService` arma el `SettlementRequest` desde `Stay` y `Room`, invoca a Módulo 3 mediante el puerto de salida y compone la respuesta para los pasos 2 y 3.
- `source` viaja en la solicitud, pero Módulo 3 no lo devuelve: se muestra desde `Stay.source`.
- Toda falla de Módulo 3 se traduce a un resultado controlado `UNAVAILABLE` con su causa; la decisión sobre la salida física queda en Registrar Check-Out.

### Alcance: plan general vs. este plan

| Tema | Dónde vive |
| --- | --- |
| Stack (Java 21, Spring Boot, React), estructura base del repositorio, linting | Plan general (`base/plan.md`) |
| Setup, formato unificado de errores, logging, configuración de entornos | Plan general |
| Contrato REST con Módulo 3, mapeo de errores, resultado `UNAVAILABLE` | **Este plan** |
| Modelos, puertos, servicio, adaptador y componentes UI de esta feature | **Este plan** |
| Estrategia de testing y tareas de las historias US1 y US2 | **Este plan** |

### Trazabilidad Spec → Plan

| Requisito del Spec | Cobertura en este plan |
| --- | --- |
| FR-001 (`<<includes>>` desde Check-Out) | Puerto `QuerySettlementUseCase` (T005, T012) |
| FR-002 (REST GET reactivo y parámetros) | Endpoint 1 y `SettlementRestAdapter` (T010) |
| FR-003 (estructura `SettlementSummary`) | Estructuras de datos y Endpoint 1 (T004) |
| FR-004 (presentación pasos 2 y 3) | Endpoint 2, `SettlementCard.tsx`, `PaymentReviewCard.tsx` (T011, T015, T016) |
| FR-005 (solo consulta, sin pagos ni cambios a cifras) | T008, T017 |
| FR-006 (sin liberación autónoma ante falla) | T021, T024 |
| SC-001, SC-004 | T007, T008 |
| SC-002 | T027 |
| SC-003 | T019, T020 |

## Technical Context

Hereda el stack y las convenciones del plan general. Solo se declara lo propio de esta feature:

**Storage**: N/A — consulta remota a Módulo 3; sin persistencia propia de liquidaciones ni cuentas contables
**Testing**: JUnit 5, Mockito, MockRestServiceServer y WireMock para simular Módulo 3; Vitest y React Testing Library para los componentes React
**Performance Goals**: p95 < 800 ms contra Módulo 3 en condiciones normales (techo del SC-002: 2 s)
**Constraints**:

- Consumidor estricto de solo lectura: no recalcular ni redondear valores financieros de Módulo 3.
- Timeout de 3 s con circuit breaker; sin reintentos automáticos.
- Fechas de negocio como `LocalDate` (`YYYY-MM-DD`), sin horas.
- `invoiceNumber` inmutable, emitido exclusivamente por Módulo 3 (entero consecutivo, sin prefijo).
- Sin consumos locales (minibar, restaurante, lavandería, daños) ni recaudos.
- Consulta idempotente: repetirla no duplica registros ni persiste saldos.

**Scale/Scope**: 1 consulta por Check-Out; 2 componentes UI; 1 endpoint externo consumido y 1 endpoint interno expuesto.

## Project Structure

### Documentation (this feature)

```text
documentos/
├── SPEC/
│   └── spec-consultar-liquidacion.md
└── PLAN/
    └── plan-consultar-liquidacion.md      # Este archivo
```

### Source Code (archivos de esta feature)

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── SettlementRequest.java
│   │   │   ├── SettlementSummary.java
│   │   │   └── SettlementAvailabilityStatus.java
│   │   ├── exception/
│   │   │   └── ExternalModule3UnavailableException.java
│   │   └── ports/
│   │       ├── in/QuerySettlementUseCase.java
│   │       └── out/SettlementRestQueryPort.java
│   ├── application/
│   │   ├── service/SettlementQueryService.java
│   │   └── dto/
│   │       ├── SettlementQueryCommand.java
│   │       └── SettlementResponseDto.java
│   └── infrastructure/adapters/
│       ├── in/web/SettlementController.java
│       └── out/rest/
│           ├── SettlementRestAdapter.java
│           └── Module3Properties.java
└── test/java/com/hospitua/habitaciones/
    ├── application/
    │   └── SettlementQueryServiceTest.java
    └── infrastructure/rest/
        ├── SettlementRestAdapterTest.java
        ├── SettlementModule3ContractTest.java
        ├── SettlementQueryIntegrationTest.java
        └── SettlementUnavailabilityIntegrationTest.java

frontend/src/
├── components/settlement/
│   ├── SettlementCard.tsx             # Paso 2: Liquidación
│   └── PaymentReviewCard.tsx          # Paso 3: Pago (solo revisión)
└── api/settlementService.ts
```

**Structure Decision**: Se respeta la arquitectura hexagonal (domain / application / infrastructure) y la convención de puertos `in` / `out` definidas en el plan general. Este plan solo agrega los archivos listados.

**Prerrequisitos del plan general** (deben existir antes de iniciar las tareas de este plan):

- Estructura base del módulo `habitaciones` y del frontend.
- Dependencias de Spring Web, Resilience4j, Lombok, WireMock, Vitest y React Testing Library.
- Formato unificado de errores y convención de logging.
- Entidades `Stay` y `Room` con `source`, `reservationRef`, `checkInDate`, `expectedCheckinDate`, `expectedCheckoutDate` y `category`.

---

## Diseño técnico

### Flujo

1. Registrar Check-Out invoca `QuerySettlementUseCase` con el identificador de la estancia.
2. `SettlementQueryService` carga `Stay` y `Room` y construye `SettlementRequest` (`checkOutDate` = fecha actual sin hora).
3. `SettlementRestQueryPort` llama a Módulo 3 (Endpoint 1).
4. El servicio compone `SettlementResponseDto`: toma `source` de `Stay.source`, calcula `nights` y toma `totalToPay` desde `totalAmount` de Módulo 3.
5. Ante cualquier falla se devuelve `UNAVAILABLE` con `unavailableCause`, sin acciones sobre `Stay` ni `Room`.

### Estructuras de datos

No hay tablas ni migraciones. Modelos en memoria:

| Modelo | Campos |
| --- | --- |
| `SettlementRequest` | `reservationRef`, `checkInDate`, `checkOutDate`, `source`, `roomId`, `categoryRoom` |
| `SettlementSummary` | `invoiceNumber` (Integer consecutivo o null si informativa), `accommodationTotalAmount`, `otaCommissionPercentage`, `otaCommissionAmount`, `taxAmount` (null si informativa), `netIncomeAmount`, `totalAmount` (null si informativa), `settlementType` (`INFORMATIVA` \| `FINAL`; nombre y valores confirmados por Módulo 3) (montos como `BigDecimal`) |
| `SettlementAvailabilityStatus` | `AVAILABLE`, `UNAVAILABLE` |
| `SettlementResponseDto` | `status`, `settlementType`, `invoiceNumber`, `reservationInfo`, `guestSummary`, `stayData`, `unavailableCause` |

### Reglas de composición (sin recálculos financieros)

- `reservationInfo.source` proviene de `Stay.source`.
- `totalToPay` = `totalAmount` devuelto por Módulo 3. Ya **no** se calcula en Módulo 1 (la suma `accommodationTotalAmount` + `taxAmount` era incorrecta). Si `totalAmount` es null (liquidación informativa), se muestra "Pendiente de facturación".
- `nights` = días entre `checkInDate` y `checkOutDate` (dato informativo, no financiero).
- En liquidación `settlementType = INFORMATIVA`: `taxAmount` y `totalAmount` son null; se muestra únicamente hospedaje (`accommodationTotalAmount`), comisión OTA e ingreso neto (`netIncomeAmount`).

---

## Contratos de API

### Endpoint 1 — Módulo 3: solicitar liquidación de la estancia

```
GET /api/settlements
```

**Headers**:

| Nombre | Obligatorio | Descripción |
|---|---|---|
| `Accept` | No | `application/json` (por defecto) |

**Query Parameters** (sin path params ni body):

| Nombre | Tipo | Obligatorio | Origen en Módulo 1 | Descripción |
| --- | --- | --- | --- | --- |
| `reservationRef` | string (RES-000123) | Sí | `Stay.reservationRef` | Identificador de la reserva |
| `checkInDate` | `YYYY-MM-DD` | Sí | `Stay.checkInDate` | Check-In real |
| `checkOutDate` | `YYYY-MM-DD` | Sí | Fecha actual | Check-Out real |
| `source` | string | Sí | `Stay.source` | `DIRECTA`, `BOOKING`, `EXPEDIA`, etc. |
| `roomId` | string (UUID) | Sí | `Room.id` | Habitación física |
| `categoryRoom` | string | Sí | `Room.category` | Ej. `Sencilla`, `Suite` |

**Ejemplo de petición:**

```http
GET /api/settlements?reservationRef=RES-000123&checkInDate=2026-10-04&checkOutDate=2026-10-06&source=BOOKING&roomId=123e4567-e89b-12d3-a456-426614174000&categoryRoom=Suite HTTP/1.1
Host: modulo3.sistema.local
```

**Respuesta `200 OK`** (no incluye `source`):

```json
{
  "invoiceNumber": 40001,
  "settlementType": "FINAL",
  "accommodationTotalAmount": 500.00,
  "otaCommissionPercentage": 15.00,
  "otaCommissionAmount": 75.00,
  "taxAmount": 95.00,
  "netIncomeAmount": 425.00,
  "totalAmount": 595.00
}
```

> Fórmula confirmada (FR-003): ingreso neto = hospedaje − comisión OTA (500 − 75 = 425).

**Headers de respuesta**: `Content-Type: application/json`, `Cache-Control: no-store`.

**Errores de Módulo 3 y su traducción en Módulo 1** (nunca se propaga una excepción técnica al recepcionista):

| Código | Caso | Excepción en Módulo 1 | Resultado en Módulo 1 |
| --- | --- | --- | --- |
| `404` | Reserva no encontrada (`RESERVATION_NOT_FOUND`) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `RESERVATION_NOT_FOUND` |
| `404` | Cotización no encontrada (`QUOTE_NOT_FOUND`) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `QUOTE_NOT_FOUND` |
| `404` | Módulo 2 no disponible (`MODULE2_UNAVAILABLE`) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `MODULE2_UNAVAILABLE` |
| Timeout | Sin respuesta en 3 s | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `TIMEOUT` |
| Red | Error de red | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `CONNECTION_ERROR` |

Los códigos `400`, `422` y el circuit breaker no son códigos confirmados por Módulo 3.

**Paginación**: no aplica.

### Endpoint 2 — Módulo 1: consulta interna para el frontend

```
GET /api/stays/{stayId}/settlement
```

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `stayId` (path) | UUID | Sí | Estancia activa devuelta por `GET /api/check-out/active-stay` (`plan-registrar-check-out.md`, Endpoint 1) |

Sin query params ni body. Módulo 1 arma los parámetros de Módulo 3 desde `Stay` y `Room` (`checkOutDate` = fecha actual) y llama al Endpoint 1.

**Respuesta `200 OK` (`AVAILABLE`):**

```json
{
  "status": "AVAILABLE",
  "settlementType": "FINAL",
  "invoiceNumber": 40001,
  "reservationInfo": {
    "source": "BOOKING",
    "otaCommissionPercentage": 15.00,
    "otaCommissionAmount": 75.00,
    "netIncomeAmount": 425.00
  },
  "guestSummary": {
    "accommodationTotalAmount": 500.00,
    "taxAmount": 95.00,
    "totalToPay": 595.00
  },
  "stayData": {
    "nights": 2,
    "checkInDate": "2026-10-04",
    "checkOutDate": "2026-10-06"
  },
  "unavailableCause": null
}
```

`totalToPay` proviene de `totalAmount` de Módulo 3; si es null (liquidación informativa), se muestra "Pendiente de facturación".

**Respuesta controlada ante falla** (también `200 OK`, para que Registrar Check-Out decida sin tratarlo como error técnico):

```json
{
  "status": "UNAVAILABLE",
  "invoiceNumber": null,
  "reservationInfo": null,
  "guestSummary": null,
  "stayData": null,
  "unavailableCause": "TIMEOUT"
}
```

Errores propios de Módulo 1 (formato `ApiError` del plan base); las fallas de Módulo 3 nunca son errores, sino la respuesta controlada anterior:

| Status Code | errorCode | Excepción | Cuándo ocurre |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` o `ConstraintViolationException` | `stayId` no es un UUID |
| 404 | `ACTIVE_STAY_NOT_FOUND` | `ActiveStayNotFoundException` (`plan-registrar-check-out.md`) | No existe una estancia activa con ese `stayId` (mismo código que `plan-registrar-check-out.md`) |

```json
{
  "errorCode": "ACTIVE_STAY_NOT_FOUND",
  "message": "No hay una estancia activa con ese identificador.",
  "timestamp": "2026-10-08T10:00:00-05:00",
  "path": "/api/stays/7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f/settlement"
}
```

---

## Estrategia de testing

| Tipo | Qué cubre | Archivo |
| --- | --- | --- |
| Contrato | Query params enviados, headers y mapeo de `SettlementSummary` contra WireMock | `infrastructure/rest/SettlementModule3ContractTest.java` |
| Unitario | No alteración de montos; composición de `nights`, `totalToPay` y `source`; traducción de errores del adaptador | `application/SettlementQueryServiceTest.java`, `infrastructure/rest/SettlementRestAdapterTest.java` |
| Integración | Escenarios DIRECTA y OTA; timeout y 4xx → `UNAVAILABLE`; ausencia de efectos secundarios | `infrastructure/rest/*IntegrationTest.java` |
| Frontend | Render de pasos 2 y 3 en estados `AVAILABLE` y `UNAVAILABLE`; sin campos de captura de pago | `frontend/src/test/settlement/*.test.tsx` |
| Rendimiento | p95 < 800 ms y respuesta controlada < 3 s ante falla | T027 |

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar los componentes transversales de comunicación REST, Resilience4j y WireMock en [PLAN/base/plan.md](base/plan.md).
- [ ] T002 Configurar `Module3Properties` (base-url y timeout síncrono de 3000 ms) y el bean `RestClient` hacia Módulo 3 (`GET /api/settlements`) en `infrastructure/adapters/out/rest/` (plan base, T013).
- [ ] T003 Configurar el entorno de pruebas con WireMock y `MockRestServiceServer` para simular las respuestas del contrato `GET /api/settlements` de Módulo 3.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T004 Implementar los modelos de dominio `SettlementRequest`, `SettlementSummary` (sin `source`), `SettlementAvailabilityStatus` y la excepción `ExternalModule3UnavailableException` en `domain/model/` y `domain/exception/`.
- [ ] T005 Definir la interfaz del puerto de entrada `QuerySettlementUseCase` y del puerto de salida `SettlementRestQueryPort` para `GET /api/settlements` en `domain/ports/`.
- [ ] T006 Implementar los DTOs de aplicación `SettlementQueryCommand` y `SettlementResponseDto` en `application/dto/`.

---

## Phase 3: User Story 1 - Consulta del desglose de liquidación y factura definitiva en Check-Out (Priority: P1) 🎯 MVP

**Goal**: Consultar a Módulo 3 la liquidación y la factura definitiva y entregar la información para el paso Liquidación (factura, fuente, comisión, ingreso neto) y el paso Pago (factura, resumen para el huésped, datos de la estadía), sin calcular, alterar ni recaudar.

**Independent Test**: Invocar `QuerySettlementUseCase` de forma aislada con una estancia válida (fuente `DIRECTA` y fuente OTA) contra un Módulo 3 simulado y verificar que los montos son idénticos a los recibidos, la fuente proviene de `Stay.source` y las noches están bien calculadas.

### Tests for User Story 1

- [ ] T007 [P] [US1] Contract test del adaptador contra WireMock (query params incluidos `source` y `categoryRoom`, headers, mapeo de respuesta) en `infrastructure/rest/SettlementModule3ContractTest.java`
- [ ] T008 [P] [US1] Test unitario de no alteración: montos de salida idénticos a Módulo 3 y ausencia de consumos locales, en `application/SettlementQueryServiceTest.java`
- [ ] T009 [P] [US1] Test de integración de los escenarios 1 y 2 (DIRECTA con comisión 0 e ingreso neto = hospedaje; OTA con comisión) en `infrastructure/rest/SettlementQueryIntegrationTest.java`

### Implementation for User Story 1

- [ ] T010 [US1] Implementar `SettlementRestAdapter` (GET con query params desde `SettlementRequest`, mapeo a `SettlementSummary`) (depende de T002, T004, T005)
- [ ] T011 [P] [US1] Implementar `settlementService.ts` en `frontend/src/api/`
- [ ] T012 [US1] Implementar `SettlementQueryService`: cargar `Stay` y `Room`, construir `SettlementRequest`, invocar el puerto, tomar `source` de `Stay.source`, calcular `nights` y tomar `totalToPay` desde `totalAmount` de Módulo 3 (depende de T005, T006, T010)
- [ ] T013 [US1] Exponer el caso de uso para su inclusión desde Registrar Check-Out, sin lógica de liberación de habitación (depende de T012)
- [ ] T014 [US1] Implementar `SettlementController` (Endpoint 2) (depende de T012)
- [ ] T015 [P] [US1] Implementar `SettlementCard.tsx` (Paso 2): factura arriba, Fuente, % y valor de comisión OTA, ingreso neto
- [ ] T016 [P] [US1] Implementar `PaymentReviewCard.tsx` (Paso 3): factura arriba, hospedaje, IVA, total a pagar, noches, fechas de entrada y salida; sin captura de pago
- [ ] T017 [US1] Garantizar idempotencia de reconsultas: sin escrituras en servicio ni controlador y `Cache-Control: no-store` respetado
- [ ] T018 [US1] Agregar logging (referencia de reserva, `source`, estado, latencia; sin montos)

**Checkpoint**: La historia 1 es funcional y verificable de forma independiente.

---

## Phase 4: User Story 2 - Manejo controlado de la indisponibilidad de Módulo 3 (Priority: P2)

**Goal**: Ante lentitud o error de Módulo 3, devolver un resultado controlado `UNAVAILABLE` con la causa, sin excepciones técnicas, sin montos inventados y sin acciones autónomas sobre la habitación.

**Independent Test**: Simular timeout y 4xx de Módulo 3 y verificar que el caso de uso retorna `UNAVAILABLE` con la causa, sin lanzar excepciones al llamador y sin modificar `Stay` ni `Room`.

### Tests for User Story 2

- [ ] T019 [P] [US2] Test de integración de timeout (respuesta con retraso > 3 s) → `UNAVAILABLE` / `TIMEOUT` en `infrastructure/rest/SettlementUnavailabilityIntegrationTest.java`
- [ ] T020 [P] [US2] Test unitario del adaptador: mapeo de los códigos confirmados (`RESERVATION_NOT_FOUND`, `QUOTE_NOT_FOUND`, `MODULE2_UNAVAILABLE`) y de errores de red/tiempo a su causa, en `infrastructure/rest/SettlementRestAdapterTest.java`
- [ ] T021 [P] [US2] Test de no efectos secundarios: tras `UNAVAILABLE` no se invoca ningún repositorio de escritura (Mockito `verifyNoInteractions`)

### Implementation for User Story 2

- [ ] T022 [US2] Traducir errores HTTP, timeouts y errores de red a `ExternalModule3UnavailableException` en `SettlementRestAdapter` (depende de T004, T010)
- [ ] T023 [US2] Aplicar `@CircuitBreaker` y `@TimeLimiter` (3 s) de Resilience4j a la llamada, sin reintentos
- [ ] T024 [US2] En `SettlementQueryService`, capturar la excepción y devolver `status=UNAVAILABLE` con `unavailableCause`, sin montos parciales ni calculados
- [ ] T025 [US2] Mapear `UNAVAILABLE` como respuesta `200` controlada en `SettlementController` y registrar la contingencia
- [ ] T026 [P] [US2] Mostrar en las dos tarjetas el mensaje de indisponibilidad del servicio de facturación y devolver el control a Registrar Check-Out

**Checkpoint**: Las historias 1 y 2 funcionan de forma independiente.

---

## Phase 5: Polish de esta feature

- [ ] T027 [P] Prueba de rendimiento: p95 < 800 ms normal y respuesta controlada < 3 s ante falla (SC-002, SC-003)
- [ ] T028 [P] Tests de componentes React en `frontend/src/test/settlement/` (estados `AVAILABLE` y `UNAVAILABLE`)
- [ ] T029 Agregar referencia cruzada en `spec-registrar-check-out.md` y `plan-registrar-check-out.md`

---

## Dependencies & Execution Order

- **Prerrequisitos del plan general**: deben estar completos antes de T001.
- **US1 (P1)**: depende de la fase Setup y Foundational (T001-T006).
- **US2 (P2)**: extiende el adaptador y el servicio de US1 (T010, T012), pero se valida de forma independiente con Módulo 3 simulado.
- **Polish**: depende de US1 y US2.
- Dentro de cada historia: modelos y puertos → servicios → controlador → frontend; tests tras la implementación y antes del checkpoint.
- Paralelo: T007–T009, T015–T016; T019–T021.

## Notes

- La etiqueta [US1]/[US2] mapea cada tarea a su historia del Spec.
- Cada historia debe ser completable y verificable de forma independiente.
- Si el equipo prefiere separar tareas, esta sección puede trasladarse a `tasks-consultar-liquidacion.md` sin modificar el resto.
- **Puntos cerrados**: (1) fórmula de `netIncomeAmount`: CERRADO → ingreso neto = hospedaje − comisión OTA; (2) total a pagar oficial: CERRADO → Módulo 3 devuelve `totalAmount` (Módulo 1 no lo calcula); (3) formato del número de factura: CERRADO → entero consecutivo sin prefijo (ej. `40001`).
