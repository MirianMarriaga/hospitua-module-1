# Implementation Plan: Consultar Liquidación

**Date**: 2026-10-09  
**Spec**: [spec-consultar-liquidacion.md](../SPEC/spec-consultar-liquidacion.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso interno de consulta reactiva **Consultar Liquidación**, invocado de forma obligatoria como inclusión (`<<includes>>`) por los pasos 2 y 3 de **Registrar Check-Out** (`plan-registrar-check-out.md`).

Responsabilidades:

1. **Consulta síncrona REST GET a Módulo 3**: Invocar `GET /api/settlements` (sin `/v1/`, sin header `Authorization`) enviando los parámetros: `reservationRef`, `checkInDate`, `checkOutDate`, `source` (de `Stay.source`), `roomId` y `categoryRoom`. No se envían `eventType`, `startDate` ni `endDate`.
2. **Recepción de la estructura oficial `SettlementSummary`**:
   - Tipo de liquidación: `settlementType` (`"INFORMATIVA"` o `"FINAL"`).
   - Número de factura oficial emitido por Módulo 3: `invoiceNumber` (entero consecutivo o null si informativa / pendiente de facturación).
   - Hospedaje consolidado: `accommodationTotalAmount`.
   - Comisión OTA: `otaCommissionPercentage` y `otaCommissionAmount`.
   - Impuesto: `taxAmount` (null si informativa).
   - Ingreso neto: `netIncomeAmount` (fórmula: hospedaje − comisión OTA).
   - Total a pagar: `totalAmount` (calculado por M3, null si informativa; Módulo 1 **nunca** lo suma localmente).
   - Nota: El campo `source` no regresa en la respuesta de M3; Módulo 1 lo recupera de `Stay.source` local.
3. **Distribución de datos para la interfaz de Check-Out**:
   - *Paso 2 (Liquidación)*: Tipo de liquidación (`settlementType`), factura (`invoiceNumber` entero o "Pendiente de facturación"), canal local `Stay.source`, comisión OTA e ingreso neto (`netIncomeAmount`).
   - *Paso 3 (Pago)*: En liquidación final: número de factura, hospedaje, IVA, total a pagar provisto por M3 (sin recálculo local), noches y fechas reales (`checkInDate`, `checkOutDate`). En liquidación informativa: muestra únicamente hospedaje, comisión e ingreso neto; sin IVA, sin total y sin factura.
4. **Exclusión estricta de recaudos y consumos locales**: Cero transacciones monetarias, pasarelas de pago ni cargos de minibar o restaurante en Módulo 1.
5. **Manejo controlado de indisponibilidad externa**: Si Módulo 3 excede el tiempo límite (timeout 3 s), o retorna HTTP 404 (`RESERVATION_NOT_FOUND`, `QUOTE_NOT_FOUND`) o error de red, mapear a resultado controlado `UNAVAILABLE` con su causa (sin tratar como error 503, ya que M3 no emite 503). Esto permite que el flujo de Check-Out decida la liberación física a `PendingCleaning` sin bloquear al huésped ni provocar caídas técnicas no capturadas.

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
  - El número de factura (`invoiceNumber`) es un entero consecutivo oficial emitido exclusivamente por Módulo 3 (sin prefijos de texto).

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
│   │   │   ├── SettlementAvailabilityStatus.java
│   │   │   └── SettlementUnavailableCause.java
│   │   ├── exception/
│   │   │   └── SettlementUnavailableException.java
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

**Structure Decision**: El puerto `SettlementRestQueryPort` desacopla la comunicación HTTP. `SettlementQueryService` orquesta la consulta enviando los 6 parámetros acordados y mapeando la respuesta a `SettlementResponseDto` listo para ser consumido por las pantallas de Liquidación y Pago.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Configurar propiedades de integración con Módulo 3 en `application.yml`:
  ```yaml
  m3:
    settlements:
      base-url: "http://localhost:8083"
      connect-timeout-ms: 1000
      read-timeout-ms: 3000
  ```
- [ ] T002 Crear el cliente HTTP `RestClient` con soporte para timeouts (3 s) y captura controlada de errores 4xx/5xx en `SettlementRestAdapter.java`.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T003 Implementar los modelos de dominio:
  - `SettlementRequest`: `reservationRef`, `checkInDate`, `checkOutDate`, `source`, `roomId`, `categoryRoom`.
  - `SettlementSummary`: `settlementType` (`INFORMATIVA` | `FINAL`), `invoiceNumber` (Integer, nullable), `accommodationTotalAmount`, `otaCommissionPercentage`, `otaCommissionAmount`, `taxAmount` (nullable), `netIncomeAmount`, `totalAmount` (nullable).
  - Enums `SettlementAvailabilityStatus` (`AVAILABLE`, `UNAVAILABLE`) y `SettlementUnavailableCause` (`TIMEOUT`, `RESERVATION_NOT_FOUND`, `QUOTE_NOT_FOUND`, `MODULE2_UNAVAILABLE`, `CONNECTION_ERROR`).
- [ ] T004 Implementar interfaces de puertos: `QuerySettlementUseCase` y `SettlementRestQueryPort`.
- [ ] T005 Configurar el mapeo de excepciones en `GlobalExceptionHandler`.

---

## Phase 3: User Story 1 - Consulta del desglose de liquidación en Check-Out (Priority: P1)

**Goal**: Permitir la consulta síncrona a Módulo 3 mediante `GET /api/settlements` y desagregar la respuesta para los pasos de Liquidación y Pago respetando el tipo de liquidación.

**Independent Test**: Invocar `getSettlement(...)` con una reserva DIRECTA y una reserva OTA (BOOKING/EXPEDIA), verificando que la petición envía `categoryRoom` y los parámetros requeridos (sin `eventType`, `startDate` ni `endDate`), y que entrega el desglose correcto según si es informativa o final.

### Tests for User Story 1

- [ ] T006 [P] [US1] Unit test para `SettlementQueryService` validando el mapeo a las dos vistas (Liquidación y Pago) tanto para liquidación final como informativa.
- [ ] T007 [P] [US1] Integration test con `MockRestServiceServer` simulando respuesta HTTP 200 de Módulo 3 con factura entera 40001 y `settlementType = FINAL`.
- [ ] T008 [P] [US1] Contract test para la llamada REST `GET /api/settlements` verificando que no se envía `Authorization`, ni `eventType`, ni `startDate`/`endDate`, e incluyendo `categoryRoom`.
- [ ] T009 [P] [US1] Component test frontend para `SettlementCard.jsx` y `PaymentReviewCard.jsx` validando que en informativa no se muestran IVA ni total ni factura.

### Implementation for User Story 1

- [ ] T010 [P] [US1] Implementar en `SettlementRestAdapter` la consulta GET a `GET /api/settlements` con `RestClient` inyectando query params (`reservationRef`, `checkInDate`, `checkOutDate`, `source`, `roomId`, `categoryRoom`).
- [ ] T011 [US1] Implementar en `SettlementQueryService` el formateo de respuesta:
  - Recuperar `source` localmente desde `Stay.source`.
  - Si `settlementType == "INFORMATIVA"`: exponer únicamente hospedaje, comisión e ingreso neto; omitir IVA, total y factura.
  - Si `settlementType == "FINAL"`: exponer `invoiceNumber`, hospedaje, IVA, comisión, ingreso neto y `totalAmount` provisto por M3 (sin recálculo en M1).
- [ ] T012 [US1] Implementar el endpoint `GET /api/settlements/query` en `SettlementController.java`.
- [ ] T013 [US1] Construir los componentes frontend `SettlementCard.jsx` (Paso 2) y `PaymentReviewCard.jsx` (Paso 3).

---

## Phase 4: User Story 2 - Manejo controlado de la indisponibilidad de Módulo 3 (Priority: P2)

**Goal**: Responder con un estado degradado controlado ante demoras o caídas de Módulo 3 (`UNAVAILABLE`), delegando en Check-Out la decisión operativa sin lanzar 503 ni fallos 500.

**Independent Test**: Simular caída o timeout de 3000 ms en la petición a Módulo 3, o errores 404 (`RESERVATION_NOT_FOUND`, `QUOTE_NOT_FOUND`) o `MODULE2_UNAVAILABLE`, y comprobar que retorna respuesta con `status = UNAVAILABLE` y la causa correspondiente sin interrumpir el flujo del sistema.

### Tests for User Story 2

- [ ] T014 [P] [US2] Unit test: Simular excepción de conexión HTTP y verificar retorno de objeto con `status = UNAVAILABLE` y causa `CONNECTION_ERROR`.
- [ ] T015 [P] [US2] Integration test: Manejo de timeout configurado (3 s) con `ResourceAccessException` retornando `status = UNAVAILABLE` y causa `TIMEOUT`.
- [ ] T016 [P] [US2] Integration test: Simular HTTP 404 con error `QUOTE_NOT_FOUND` y verificar retorno `UNAVAILABLE` con causa `QUOTE_NOT_FOUND` (sin error 503).
- [ ] T017 [P] [US2] Component test frontend: visualización de aviso informativo de contingencia ante liquidación no disponible.

### Implementation for User Story 2

- [ ] T018 [P] [US2] Implementar en `SettlementRestAdapter` el bloque de captura de errores y timeout mapeando a `SettlementUnavailableException` con causa específica.
- [ ] T019 [US2] Implementar en `SettlementQueryService` el fallback controlado retornando `SettlementResponseDto` con flag `isUnavailable = true` y causa para consumo seguro en Check-Out.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T020 Validar que no se muestren desgloses por noche ni cargos de consumos locales.
- [ ] T021 Comprobar que todas las fechas operen como `LocalDate` sin exposición de horas.
- [ ] T022 Registro en bitácora de auditoría de la referencia de factura recibida de Módulo 3.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `Stay` de [PLAN/base/plan.md](base/plan.md).
- **Consumidor**: Invocado por [PLAN/plan-registrar-check-out.md](plan-registrar-check-out.md) en los pasos 2 (Liquidación) y 3 (Pago).
