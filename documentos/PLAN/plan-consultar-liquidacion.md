# Implementation Plan: Consultar Liquidación

**Date**: 2026-10-08
**Spec**: [spec-consultar-liquidacion.md](../SPEC/spec-consultar-liquidacion.md)
**Plan general**: [base/plan.md](base/plan.md)
**Plan relacionado**: [plan-registrar-check-out.md](plan-registrar-check-out.md) (caso de uso que incluye este mediante `<<includes>>`; su frontend consume el Endpoint 2 en los pasos 2 y 3)

> Este es un **plan específico** de una feature. Todo lo transversal al proyecto (stack, estructura base, setup, infraestructura compartida, convenciones) vive en el plan general `base/plan.md` y aquí solo se referencia.

## Summary

Implementar el caso de uso interno **Consultar Liquidación**, incluido obligatoriamente por **Registrar Check-Out** en sus pasos 2 (Liquidación) y 3 (Pago). Módulo 1 consulta de forma reactiva (REST GET) a Módulo 3 la liquidación (siempre informativa durante el check-out; Módulo 3 emite la final y la factura al procesar el evento de check-out), y entrega la información estructurada para ambos pasos sin calcular, alterar ni recaudar.

Enfoque técnico:

- `SettlementQueryService` arma el `SettlementRequest` desde `Stay`, invoca a Módulo 3 mediante el puerto de salida y compone la respuesta para los pasos 2 y 3.
- `source` no viaja en la solicitud y Módulo 3 no lo devuelve: se muestra desde `Stay.source`.
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
| FR-001 (`<<includes>>` desde Check-Out) | Puerto `QuerySettlementUseCase` (T005, T012), consumido por el Endpoint 2 desde los pasos 2 y 3 del check-out (T013, T014) |
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

- Consumidor estricto de solo lectura: no recalcular ni redondear valores financieros de Módulo 3 (llegan como texto decimal y solo se convierten a `BigDecimal`).
- Timeout de lectura de 3 s (regla 5 del plan base) con circuit breaker; sin reintentos automáticos.
- Fechas de negocio como `LocalDate` (`YYYY-MM-DD`), sin horas.
- Sin factura durante el check-out: Módulo 3 la emite al procesar el evento de check-out.
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
│   │   └── exception/
│   │       └── ExternalModule3UnavailableException.java
│   ├── application/
│   │   ├── ports/
│   │   │   ├── in/QuerySettlementUseCase.java
│   │   │   └── out/SettlementRestQueryPort.java
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
- Entidad `Stay` con `reservationRef`, `roomId`, `source`, `checkInDate`, `expectedCheckinDate` y `expectedCheckoutDate`.

---

## Diseño técnico

### Flujo

1. El frontend de Registrar Check-Out llama a `GET /api/stays/{stayId}/settlement` (Endpoint 2); `SettlementController` invoca `QuerySettlementUseCase` con el identificador de la estancia. El backend del check-out no invoca este caso de uso.
2. `SettlementQueryService` carga `Stay` y construye `SettlementRequest` (`reservationRef` y `roomId`); toma `checkOutDate` = fecha actual sin hora solo para las noches.
3. `SettlementRestQueryPort` llama a Módulo 3 (Endpoint 1).
4. El servicio compone `SettlementResponseDto`: toma `source` de `Stay.source` y calcula `nights`.
5. Ante cualquier falla se devuelve `UNAVAILABLE` con `unavailableCause`, sin acciones sobre `Stay` ni `Room`.

### Estructuras de datos

No hay tablas ni migraciones. Modelos en memoria:

| Modelo | Campos |
| --- | --- |
| `SettlementRequest` | `reservationRef`, `roomId` |
| `SettlementSummary` | `settlementType` (`INFORMATIVE` \| `FINAL`), `lodgingAmount`, `otaCommissionPercentage` (null en `DIRECT`), `otaCommissionAmount`, `netIncome` (montos como `BigDecimal`, convertidos desde texto por el adaptador) |
| `SettlementAvailabilityStatus` | `AVAILABLE`, `UNAVAILABLE` |
| `SettlementResponseDto` | `status`, `settlementType`, `reservationInfo`, `guestSummary`, `stayData`, `unavailableCause` |

### Reglas de composición (sin recálculos financieros)

- `reservationInfo.source` proviene de `Stay.source`.
- `nights` = días entre `checkInDate` y `checkOutDate` (dato informativo, no financiero).
- Durante el check-out `settlementType` es siempre `INFORMATIVE` con `invoice: null`: se muestran hospedaje (`lodgingAmount`), comisión OTA e ingreso neto (`netIncome`) como valores exactos (Módulo 3 calcula siempre con lo cotizado, así que coinciden con la final) y la nota "La factura se emite al confirmar la salida". No se muestran IVA, total ni número de factura.
- En `DIRECT`, `otaCommissionPercentage` es null y la comisión se muestra "No aplica".

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
| `roomId` | string (UUID) | Sí | `Stay.roomId` | Habitación física |

Módulo 3 ignora cualquier otro parámetro, por lo que no se envían otros.

**Ejemplo de petición:**

```http
GET /api/settlements?reservationRef=RES-000123&roomId=123e4567-e89b-12d3-a456-426614174000 HTTP/1.1
Host: modulo3.sistema.local
```

**Respuesta `200 OK`** (no incluye `source`; durante el check-out siempre `INFORMATIVE`):

```json
{
  "settlementType": "INFORMATIVE",
  "reservationRef": "RES-000123",
  "roomId": "123e4567-e89b-12d3-a456-426614174000",
  "categoryRoom": null,
  "channel": "OTA",
  "otaId": "BOOKING",
  "currency": "COP",
  "breakdown": {
    "baseLodgingAmount": "500000.00",
    "dynamicAdjustmentAmount": "0.00",
    "lodgingAmount": "500000.00",
    "otaCommissionPercentage": "15.00",
    "otaCommissionAmount": "75000.00",
    "netIncome": "425000.00"
  },
  "invoice": null,
  "generatedAt": "2026-10-06T11:30:00-05:00"
}
```

- `settlementType`: `FINAL` \| `INFORMATIVE`. Módulo 3 genera la `FINAL` y la factura al procesar el evento de check-out, que se publica después de esta consulta; por eso durante el check-out la respuesta es siempre `INFORMATIVE`, con `invoice: null` y `categoryRoom: null`.
- `channel`: `DIRECT` \| `OTA`. En `DIRECT`: `otaId` y `otaCommissionPercentage` son null y `otaCommissionAmount` es `"0.00"`.
- Todos los montos de `breakdown` son texto decimal; el adaptador los convierte a `BigDecimal` sin redondear.
- `invoice` (solo en `FINAL`): `{invoiceId, invoiceNumber, issuedAt, vatRateApplied, vatAmount, totalAmount, status}`. Módulo 1 no lo usa.
- El adaptador solo mapea a `SettlementSummary` `settlementType` y, desde `breakdown`, `lodgingAmount`, `otaCommissionPercentage`, `otaCommissionAmount` y `netIncome`; `categoryRoom`, `channel` y el resto no se usan (la fuente se muestra desde `Stay.source`).

> Fórmula confirmada (FR-003): ingreso neto = hospedaje − comisión OTA (500000 − 75000 = 425000).

**Headers de respuesta**: `Content-Type: application/json`, `Cache-Control: no-store`.

**Errores de Módulo 3 y su traducción en Módulo 1** (nunca se propaga una excepción técnica al recepcionista). Regla general: cualquier respuesta no 2xx con cuerpo `ApiError` → `UNAVAILABLE` con causa = `errorCode`; sin cuerpo `ApiError`, o con un `errorCode` que no tiene la forma de un código (`[A-Z0-9_]`, hasta 50 caracteres) → causa `HTTP_ERROR`; timeout de lectura → `TIMEOUT`; error de red o de conexión → `CONNECTION_ERROR`; liquidación ilegible → `INVALID_RESPONSE`; circuito abierto → `CIRCUIT_OPEN`. Códigos que devuelve Módulo 3:

| Código | Caso | Excepción en Módulo 1 | Resultado en Módulo 1 |
| --- | --- | --- | --- |
| `400` | Parámetros inválidos (`INVALID_QUERY_PARAMS`) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `INVALID_QUERY_PARAMS` |
| `404` | Reserva no encontrada (`RESERVATION_NOT_FOUND`) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `RESERVATION_NOT_FOUND` |
| `404` | Cotización no encontrada (`QUOTE_NOT_FOUND`) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `QUOTE_NOT_FOUND` |
| `409` | Comisión OTA faltante o inválida (`MISSING_COMMISSION` / `INVALID_COMMISSION`) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `MISSING_COMMISSION` / `INVALID_COMMISSION` |
| `422` | Error inesperado (`UNEXPECTED_ERROR`) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `UNEXPECTED_ERROR` |
| `424` | Módulo 2 no disponible (`MODULE2_UNAVAILABLE`) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `MODULE2_UNAVAILABLE` |
| Otro no 2xx | Sin cuerpo `ApiError` | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `HTTP_ERROR` |
| Timeout | Sin respuesta en 3 s (timeout de lectura) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `TIMEOUT` |
| Red | Error de red, incluido no poder conectar en 1 s (timeout de conexión) | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `CONNECTION_ERROR` |
| `200` ilegible | La liquidación no se puede leer, o le falta el tipo, el desglose o un monto obligatorio, o un monto no es decimal | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `INVALID_RESPONSE` |
| Circuito abierto | El circuito `module3` no deja llamar; no se consulta a Módulo 3 | `ExternalModule3UnavailableException` (causa en `unavailableCause`) | `UNAVAILABLE` + causa `CIRCUIT_OPEN` |

**Paginación**: no aplica.

### Endpoint 2 — Módulo 1: consulta interna para el frontend

```
GET /api/stays/{stayId}/settlement
```

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `stayId` (path) | UUID | Sí | Estancia activa devuelta por `GET /api/check-out/active-stay` (`plan-registrar-check-out.md`, Endpoint 1) |

Sin query params ni body. Módulo 1 arma los parámetros de Módulo 3 desde `Stay` (`reservationRef` y `roomId`) y llama al Endpoint 1; `checkOutDate` = fecha actual solo se usa para las noches.

**Respuesta `200 OK` (`AVAILABLE`):**

```json
{
  "status": "AVAILABLE",
  "settlementType": "INFORMATIVE",
  "reservationInfo": {
    "source": "BOOKING",
    "otaCommissionPercentage": 15.00,
    "otaCommissionAmount": 75000.00,
    "netIncome": 425000.00
  },
  "guestSummary": {
    "lodgingAmount": 500000.00
  },
  "stayData": {
    "nights": 2,
    "checkInDate": "2026-10-04",
    "checkOutDate": "2026-10-06"
  },
  "unavailableCause": null
}
```

Los montos se muestran como valores exactos con la nota "La factura se emite al confirmar la salida". En `DIRECT`, `otaCommissionPercentage` es null y la comisión se muestra "No aplica" (`otaCommissionAmount` = 0).

**Respuesta controlada ante falla** (también `200 OK`, para que Registrar Check-Out decida sin tratarlo como error técnico):

```json
{
  "status": "UNAVAILABLE",
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
| Unitario | No alteración de montos; composición de `nights` y `source`; traducción de errores del adaptador | `application/SettlementQueryServiceTest.java`, `infrastructure/rest/SettlementRestAdapterTest.java` |
| Integración | Escenarios DIRECTA y OTA; timeout y 4xx → `UNAVAILABLE`; ausencia de efectos secundarios | `infrastructure/rest/*IntegrationTest.java` |
| Frontend | Render de pasos 2 y 3 en estados `AVAILABLE` y `UNAVAILABLE`; sin campos de captura de pago | `frontend/src/test/settlement/*.test.tsx` |
| Rendimiento | p95 < 800 ms y respuesta controlada < 3 s ante falla | T027 |

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar los componentes transversales de comunicación REST, Resilience4j y WireMock en [PLAN/base/plan.md](base/plan.md).
- [x] T002 Verificar `Module3Properties` (`hospitua.module3.*`: `base-url` y `read-timeout` de `3s`) y `RestClientConfig` (plan base, T013), y crear el `RestClient` hacia Módulo 3 (`GET /api/settlements`) que usa `SettlementRestAdapter`.
  - Hecho (L13): `Module3Properties` y `module3RestClient` ya existían (plan base, T013); `SettlementRestAdapter` usa ese cliente.
- [x] T003 Configurar el entorno de pruebas con WireMock y `MockRestServiceServer` para simular las respuestas del contrato `GET /api/settlements` de Módulo 3.
  - Hecho (L13): WireMock en `SettlementRestAdapterTest` y `SettlementCircuitBreakerIntegrationTest`; `MockRestServiceServer` no hizo falta.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [x] T004 Implementar los modelos de dominio `SettlementRequest`, `SettlementSummary` (sin `source`), `SettlementAvailabilityStatus` y la excepción `ExternalModule3UnavailableException` en `domain/model/` y `domain/exception/`.
  - Hecho (L13): `SettlementRequest` (con `of(stay)`), `SettlementSummary`, `SettlementAvailabilityStatus` y `ExternalModule3UnavailableException` (con `unavailableCause()`; error `MODULE_3_UNAVAILABLE`, que no llega al recepcionista). Se agregó el enum `SettlementType` (`INFORMATIVE`, `FINAL`).
- [ ] T005 Definir la interfaz del puerto de entrada `QuerySettlementUseCase` en `application/ports/in/` y del puerto de salida `SettlementRestQueryPort` para `GET /api/settlements` en `application/ports/out/` (regla 1 del plan base).
  - Avance (L13): `SettlementRestQueryPort`. `QuerySettlementUseCase` va en L14, porque devuelve `SettlementResponseDto` (T006).
- [ ] T006 Implementar los DTOs de aplicación `SettlementQueryCommand` y `SettlementResponseDto` en `application/dto/`.

---

## Phase 3: User Story 1 - Consulta del desglose de liquidación en Check-Out (Priority: P1) 🎯 MVP

**Goal**: Consultar a Módulo 3 la liquidación informativa y entregar la información para el paso Liquidación (fuente, comisión, ingreso neto) y el paso Pago (hospedaje, datos de la estadía), sin calcular, alterar ni recaudar.

**Independent Test**: Invocar `QuerySettlementUseCase` de forma aislada con una estancia válida (fuente `DIRECTA` y fuente OTA) contra un Módulo 3 simulado y verificar que los montos son idénticos a los recibidos, la fuente proviene de `Stay.source` y las noches están bien calculadas.

### Tests for User Story 1

- [x] T007 [P] [US1] Contract test del adaptador contra WireMock (solo los query params `reservationRef` y `roomId`, headers, mapeo de `breakdown` a `SettlementSummary` con conversión de texto a `BigDecimal`) en `infrastructure/rest/SettlementModule3ContractTest.java`
  - Hecho (L13): en `infrastructure/adapters/out/rest/SettlementRestAdapterTest.java` (la carpeta de los adaptadores REST del proyecto): solo `reservationRef` y `roomId`, sin `X-User`, y el desglose OTA y directo como `BigDecimal` con la escala recibida.
- [ ] T008 [P] [US1] Test unitario de no alteración: montos de salida idénticos a Módulo 3 y ausencia de consumos locales, en `application/SettlementQueryServiceTest.java`
- [ ] T009 [P] [US1] Test de integración de los escenarios 1 y 2 (DIRECTA con `otaCommissionPercentage` null, comisión `"0.00"` e ingreso neto = hospedaje; OTA con comisión) en `infrastructure/rest/SettlementQueryIntegrationTest.java`

### Implementation for User Story 1

- [x] T010 [US1] Implementar `SettlementRestAdapter` (GET con `reservationRef` y `roomId` desde `SettlementRequest`, mapeo de `settlementType` y `breakdown` a `SettlementSummary` convirtiendo los textos decimales a `BigDecimal`) (depende de T002, T004, T005)
  - Hecho (L13): lee solo `settlementType` y `breakdown`; los montos se leen como texto y se convierten con `new BigDecimal(...)`.
- [ ] T011 [P] [US1] Implementar `settlementService.ts` en `frontend/src/api/`
- [ ] T012 [US1] Implementar `SettlementQueryService`: cargar `Stay`, construir `SettlementRequest`, invocar el puerto, tomar `source` de `Stay.source` y calcular `nights` (depende de T005, T006, T010)
- [ ] T013 [US1] Verificar que el único consumidor de `QuerySettlementUseCase` sea `SettlementController` (Endpoint 2), que usa el frontend del check-out en los pasos 2 y 3; el caso de uso no tiene lógica de liberación de habitación (depende de T012)
- [ ] T014 [US1] Implementar `SettlementController` (Endpoint 2) (depende de T012)
- [ ] T015 [P] [US1] Implementar `SettlementCard.tsx` (Paso 2): "Liquidación informativa", Fuente, % y valor de comisión OTA ("No aplica" en directa), ingreso neto
- [ ] T016 [P] [US1] Implementar `PaymentReviewCard.tsx` (Paso 3): hospedaje, noches, fechas de entrada y salida y la nota "La factura se emite al confirmar la salida"; sin IVA, total ni factura, y sin captura de pago
- [ ] T017 [US1] Garantizar idempotencia de reconsultas: sin escrituras en servicio ni controlador y `Cache-Control: no-store` respetado
- [ ] T018 [US1] Agregar logging (referencia de reserva, `source`, estado, latencia; sin montos)

**Checkpoint**: La historia 1 es funcional y verificable de forma independiente.

---

## Phase 4: User Story 2 - Manejo controlado de la indisponibilidad de Módulo 3 (Priority: P2)

**Goal**: Ante lentitud o error de Módulo 3, devolver un resultado controlado `UNAVAILABLE` con la causa, sin excepciones técnicas, sin montos inventados y sin acciones autónomas sobre la habitación.

**Independent Test**: Simular timeout y 4xx de Módulo 3 y verificar que el caso de uso retorna `UNAVAILABLE` con la causa, sin lanzar excepciones al llamador y sin modificar `Stay` ni `Room`.

### Tests for User Story 2

- [ ] T019 [P] [US2] Test de integración de timeout (respuesta con retraso > 3 s) → `UNAVAILABLE` / `TIMEOUT` en `infrastructure/rest/SettlementUnavailabilityIntegrationTest.java`
  - Avance (L13): `SettlementRestAdapterTest` cubre el timeout en el adaptador (causa `TIMEOUT`); falta la prueba con el servicio (`UNAVAILABLE`), en L14.
- [x] T020 [P] [US2] Test unitario del adaptador: regla general (cualquier no 2xx con `ApiError` → causa = `errorCode`, p. ej. 400 `INVALID_QUERY_PARAMS`, 404 `RESERVATION_NOT_FOUND`/`QUOTE_NOT_FOUND`, 409 `MISSING_COMMISSION`/`INVALID_COMMISSION`, 422 `UNEXPECTED_ERROR`, 424 `MODULE2_UNAVAILABLE`; sin `ApiError` → `HTTP_ERROR`) y errores de red/tiempo a su causa, en `infrastructure/rest/SettlementRestAdapterTest.java`
  - Hecho (L13): `SettlementRestAdapterTest`, con todos los códigos de la tabla, un 5xx con `ApiError` y respuestas sin `ApiError`.
- [ ] T021 [P] [US2] Test de no efectos secundarios: tras `UNAVAILABLE` no se invoca ningún repositorio de escritura (Mockito `verifyNoInteractions`)

### Implementation for User Story 2

- [x] T022 [US2] Traducir errores HTTP, timeouts y errores de red a `ExternalModule3UnavailableException` en `SettlementRestAdapter` (depende de T004, T010)
  - Hecho (L13): además de las causas de la tabla, `INVALID_RESPONSE` para una liquidación ilegible o con un monto ausente o no decimal, y `CIRCUIT_OPEN` cuando el circuito no deja llamar. Un connect-timeout cuenta como `CONNECTION_ERROR`; solo el read-timeout es `TIMEOUT`.
- [x] T023 [US2] Aplicar `@CircuitBreaker` de Resilience4j a la llamada, sin reintentos; el límite de 3 s lo da el timeout de lectura de `RestClient` (regla 5 del plan base), porque `@TimeLimiter` solo funciona con llamadas asíncronas. El circuito solo cuenta las caídas (sin respuesta, error de red o 5xx); los 4xx llegan rápido y son respuestas de negocio de una estancia, así que no lo abren
  - Hecho (L13): circuito `module3` en `application.yml` (ventana de 10 llamadas, mínimo 5, 50 % de fallas, 30 s abierto, sin reintentos). `Module3OutagePredicate` solo cuenta las caídas (timeout, red y 5xx), no los 4xx de negocio. `SettlementCircuitBreakerIntegrationTest` lo prueba con la configuración real.
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
- **Puntos cerrados**: (1) fórmula de `netIncome`: CERRADO → ingreso neto = hospedaje − comisión OTA; (2) total a pagar oficial: SUPERADO → durante el check-out no hay total ni factura (la liquidación es `INFORMATIVE` con `invoice: null`); (3) formato del número de factura: SUPERADO → Módulo 1 no muestra número de factura; (4) parámetros, forma de la respuesta y errores: CERRADO → acordados con Módulo 3 (24-oct-2026).
