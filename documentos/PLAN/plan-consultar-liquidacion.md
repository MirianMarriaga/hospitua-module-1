# Implementation Plan: Consultar Liquidación

**Date**: 2026-10-06  
**Spec**: [spec-consultar-liquidacion.md](../SPEC/spec-consultar-liquidacion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso interno de consulta reactiva **Consultar Liquidación**, invocado de forma obligatoria como inclusión (`<<includes>>`) por los pasos 2 y 3 de **Registrar Check-Out** (`plan-registrar-check-out.md`).

Responsabilidades:

1. **Consulta síncrona REST GET a Módulo 3**: Enviar los parámetros contractuales y físicos de la estancia: `reservationRef`, `eventType = CHECK_OUT`, fechas esperadas (`startDate`, `endDate`), fechas reales sin horas (`checkInDate`, `checkOutDate`), canal de origen (`source`: `DIRECTA` o nombre de la OTA, recuperado localmente de `Stay`) y `roomId`.
2. **Recepción de la estructura oficial `SettlementSummary`**:
   - Factura definitiva oficial emitida por Módulo 3 (`invoiceNumber`, ej. `FAC-40001`), visible en la parte superior tanto en el paso Liquidación como en el paso Pago.
   - Hospedaje total consolidado (`accommodationTotalAmount`) ya resuelto por Módulo 3 (incluyendo políticas de estadía o penalizaciones de salida anticipada).
   - Impuesto al Valor Agregado (`taxAmount`) y Total a pagar.
   - Desglose de canal de intermediación: comisión OTA en porcentaje y valor monetario, e ingreso neto (`netIncomeAmount`).
3. **Distribución de datos para la interfaz de Check-Out**:
   - *Paso 2 (Liquidación)*: Factura arriba + Información de la reserva (canal `source`, comisión OTA e ingreso neto).
   - *Paso 3 (Pago)*: Factura arriba + Resumen para el huésped (hospedaje, IVA, total a pagar) + Datos de estadía (noches reales, `checkInDate`, `checkOutDate`).
4. **Exclusión estricta de recaudos y consumos locales**: Cero transacciones monetarias, pasarelas de pago ni cargos de minibar o restaurante en Módulo 1.
5. **Manejo controlado de indisponibilidad externa**: Si Módulo 3 excede el tiempo límite (timeout 1 s conexión, 3 s lectura) o responde con error, retornar un estado de contingencia (`status = UNAVAILABLE`, `settlementPending = true`) para que el flujo de Check-Out decida la liberación física a limpieza sin bloquear al huésped ni provocar caídas técnicas no controladas.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, RestClient, Lombok, Resilience4j
- **Storage**: Consulta remota a Módulo 3 (sin persistencia propia de cuentas contables)
- **Testing**: JUnit 5, Mockito, MockRestServiceServer, WireMock
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / JSX)
- **Project Type**: Web Application backend service
- **Performance Goals**:
  - Consulta reactiva a Módulo 3 < 800 ms p95 en condiciones de red normales.
  - Timeout estricto de 3 s ante fallas externas.
- **Constraints**:
  - Módulo 1 actúa como consumidor estricto de solo lectura: no recalcular ni redondear valores financieros de Módulo 3.
  - No exponer marcas de tiempo con horas en contratos de negocio (`LocalDate` / YYYY-MM-DD).
  - La factura definitiva (`invoiceNumber`) es emitida exclusivamente por Módulo 3 y tratada como inmutable.

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-consultar-liquidacion.md
│   └── spec-registrar-check-out.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-consultar-liquidacion.md
    └── plan-registrar-check-out.md
```

### Source Code

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
│   │       ├── in/
│   │       │   └── QuerySettlementUseCase.java
│   │       └── out/
│   │           └── SettlementRestQueryPort.java
│   ├── application/
│   │   ├── service/
│   │   │   └── SettlementQueryService.java
│   │   └── dto/
│   │       ├── SettlementQueryCommand.java
│   │       └── SettlementResponseDto.java
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   └── web/
│       │   │       └── SettlementController.java
│       │   └── out/
│       │       └── rest/
│       │           ├── SettlementRestAdapter.java
│       │           └── Module3Properties.java
frontend/src/
├── components/settlement/
│   ├── SettlementCard.jsx
│   └── PaymentReviewCard.jsx
└── services/
    └── settlementService.js
```

**Structure Decision**: El puerto `SettlementRestQueryPort` desacopla la comunicación HTTP. `SettlementQueryService` orquesta la consulta enviando los parámetros de la estancia y mapeando la respuesta a `SettlementResponseDto` listo para ser consumido por las pantallas de Liquidación y Pago.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Configurar propiedades de integración con Módulo 3 en `application.yml` (`m3.settlements.base-url`, `m3.settlements.connect-timeout-ms=1000`, `m3.settlements.read-timeout-ms=3000`).
- [ ] T002 Crear el cliente HTTP `RestClient` con soporte para timeouts y captura de fallas 4xx/5xx en `SettlementRestAdapter.java`.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T003 Implementar los modelos de dominio `SettlementRequest` y `SettlementSummary` con validaciones de campos inmutables.
- [ ] T004 Implementar el puerto de entrada `QuerySettlementUseCase` y el puerto de salida `SettlementRestQueryPort`.
- [ ] T005 Configurar el mapeo de excepción `ExternalModule3UnavailableException` a RFC 7807 (`ApiError`) en `GlobalExceptionHandler`.

---

## Phase 3: User Story 1 - Consulta del desglose de liquidación y factura definitiva en Check-Out (Priority: P1)

**Goal**: Permitir la consulta síncrona a Módulo 3 y desagregar la respuesta para los pasos de Liquidación y Pago, visualizando la factura definitiva en la parte superior.

**Independent Test**: Invocar `getSettlement(...)` con una estancia directa y una estancia OTA, verificando que entrega `invoiceNumber`, comisión OTA correspondiente, ingreso neto, valor de hospedaje consolidado e IVA.

### Tests for User Story 1

- [ ] T006 [P] [US1] Unit test para `SettlementQueryService` validando el mapeo a las dos vistas (Liquidación y Pago).
- [ ] T007 [P] [US1] Integration test con `MockRestServiceServer` simulando respuesta HTTP 200 de Módulo 3 con factura `FAC-40001`.
- [ ] T008 [P] [US1] Contract test para la llamada REST `GET /api/settlements` con los parámetros obligatorios.
- [ ] T009 [P] [US1] Component test frontend para `SettlementCard.jsx` y `PaymentReviewCard.jsx` validando posición de `invoiceNumber` arriba.

### Implementation for User Story 1

- [ ] T010 [P] [US1] Implementar en `SettlementRestAdapter` la consulta GET a Módulo 3 con `RestClient`.
- [ ] T011 [US1] Implementar en `SettlementQueryService` la lógica para clasificar canales: `DIRECTA` (0% comisión) y cualquier otro valor de `source`, que corresponde al nombre de la OTA (con comisión e ingreso neto).
- [ ] T012 [US1] Implementar el endpoint `GET /api/settlements/query` en `SettlementController.java`.
- [ ] T013 [US1] Construir los componentes frontend `SettlementCard.jsx` (Paso 2) y `PaymentReviewCard.jsx` (Paso 3).

---

## Phase 4: User Story 2 - Manejo controlado de la indisponibilidad de Módulo 3 (Priority: P2)

**Goal**: Responder con un estado degradado controlado ante demoras o caídas de Módulo 3, delegando en Check-Out la decisión operativa.

**Independent Test**: Simular caída o timeout de 3000 ms en la petición a Módulo 3 y comprobar que retorna respuesta con `availabilityStatus = UNAVAILABLE` y no causa un fallo no controlado del servidor.

### Tests for User Story 2

- [ ] T014 [P] [US2] Unit test: Simular excepción de conexión HTTP y verificar retorno de objeto con `status = UNAVAILABLE`.
- [ ] T015 [P] [US2] Integration test: Manejo de timeout configurado con `ResourceAccessException`.
- [ ] T016 [P] [US2] Component test frontend: visualización de aviso informativo de servicio de facturación no disponible.

### Implementation for User Story 2

- [ ] T017 [P] [US2] Implementar en `SettlementRestAdapter` el bloque de captura de errores y timeout, arrojando `ExternalModule3UnavailableException`.
- [ ] T018 [US2] Implementar en `SettlementQueryService` el fallback controlado retornando `SettlementResponseDto` con flag `isPendingRegularization = true`.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T019 Validar que no se muestren desgloses por noche ni cargos de consumos locales.
- [ ] T020 Comprobar que todas las fechas operen como `LocalDate` sin exposición de horas.
- [ ] T021 Registro en bitácora de auditoría de la referencia de factura recibida de Módulo 3.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Stay` de [PLAN/base/plan.md](base/plan.md).
- **Consumidor**: Invocado por [PLAN/plan-registrar-check-out.md](plan-registrar-check-out.md) en los pasos 2 (Liquidación) y 3 (Pago).
