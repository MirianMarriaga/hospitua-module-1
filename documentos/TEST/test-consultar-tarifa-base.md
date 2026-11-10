### Test Case Specification: Consultar Tarifa Base
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-tarifa-base.md](../SPEC/spec-consultar-tarifa-base.md) / [plan-consultar-tarifa-base.md](../PLAN/plan-consultar-tarifa-base.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-006-ConsultarTarifaBase
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que el endpoint de solo lectura expuesto por Módulo 1 (`GET /api/rooms/{roomId}/base-rate?date=YYYY-MM-DD`) retorne con exactitud la tarifa base en pesos colombianos (COP) para el cálculo de facturación en Módulo 3, validando la obligatoriedad de la fecha y el rechazo ante habitaciones inexistentes.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Tarifa Base | Specification Document | [documentos/SPEC/spec-consultar-tarifa-base.md](../SPEC/spec-consultar-tarifa-base.md) |
| Plan de Implementación de Consultar Tarifa Base | Plan Document | [documentos/PLAN/plan-consultar-tarifa-base.md](../PLAN/plan-consultar-tarifa-base.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere el backend Spring Boot en funcionamiento respondiendo peticiones HTTP en el puerto configurado.

##### Dependencies & Assumptions
* El catálogo de habitaciones físicas de la semilla V2 debe estar cargado en la base de datos PostgreSQL.
* La operación es de solo lectura y no genera modificaciones en la entidad ni transiciones de estado.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 existente con ID `a0000000-0000-0000-0000-000000000101` y tarifa base configurada en $120.000,00. Habitación 104 (Inactive) con ID `a0000000-0000-0000-0000-000000000104` y tarifa $200.000,00.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Operación de servicio a servicio invocada con encabezado `Accept: application/json`.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Consulta de tarifa base por habitación existente)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consultar tarifa base de habitación disponible | Enviar petición HTTP GET al endpoint de tarifa base con una fecha válida | Endpoint: `GET /api/rooms/a0000000-0000-0000-0000-000000000101/base-rate?date=2026-11-07` | El sistema responde HTTP 200 OK con: `roomId`: `a0000000-0000-0000-0000-000000000101`, `date`: `2026-11-07`, `baseRate`: `120000.00` (texto decimal con dos decimales) y `currency`: `COP` | [Pass] | Verificado por test automatizado (`RoomBaseRateIntegrationTest`) |
| 2 | Consultar tarifa base con diferente fecha | Enviar petición GET para la misma habitación con una fecha futura | Query: `?date=2026-12-31` | El sistema responde HTTP 200 OK devolviendo la misma tarifa base `120000.00` y reflejando la fecha consultada `2026-12-31`, confirmando que la tarifa base no varía por fecha en Módulo 1 | [Pass] | Verificado por test automatizado (`RoomBaseRateIntegrationTest`) |
| 3 | Consultar tarifa base de habitación inactiva | Enviar petición GET para la habitación 104 que se encuentra en estado Inactive | `roomId`: `a0000000-0000-0000-0000-000000000104`, `date`: `2026-11-07` | El sistema responde HTTP 200 OK con `baseRate`: `200000.00`, comprobando que la tarifa base es consultable en cualquiera de los 8 estados de la habitación | [Pass] | Verificado por test automatizado (`RoomBaseRateIntegrationTest`) |

--------------------------------------------------------------------------------

##### Section 2: Errores y Validaciones de Parámetros
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consulta de habitación inexistente | Enviar petición GET con un UUID que no existe en el inventario | `roomId`: `00000000-0000-0000-0000-000000000999`, `date`: `2026-11-07` | El sistema responde HTTP 404 Not Found con cuerpo `ApiError`: `errorCode`: `ROOM_NOT_FOUND` y mensaje: "No se encontró la habitación." | [Pass] | Verificado por test automatizado (`RoomBaseRateIntegrationTest`) |
| 2 | Consulta omitiendo el parámetro obligatorio de fecha | Enviar petición GET sin incluir el query param `date` | Endpoint: `GET /api/rooms/a0000000-0000-0000-0000-000000000101/base-rate` | El sistema responde HTTP 400 Bad Request con `errorCode`: `VALIDATION_ERROR` y mensaje: "La fecha debe tener un formato válido (AAAA-MM-DD)." | [Pass] | Verificado por test automatizado (`RoomBaseRateIntegrationTest`) |
| 3 | Consulta con formato de fecha inválido o inexistente | Enviar petición GET con fecha en formato incorrecto o día inexistente en el calendario | Query: `?date=2026-02-31` o `?date=07-11-2026` | El sistema responde HTTP 400 Bad Request con `errorCode`: `VALIDATION_ERROR` y mensaje: "La fecha debe tener un formato válido (AAAA-MM-DD)." | [Pass] | Verificado por test automatizado (`RoomBaseRateIntegrationTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Concurrencia de consultas de facturación: múltiples instancias de Módulo 3 solicitando la tarifa base de la misma o diferentes habitaciones de manera simultánea; el sistema responde con transacciones `readOnly = true` sin bloqueos de fila ni demoras.
* **EC-002** : Precisión decimal: el sistema garantiza que la tarifa base se entrega como cadena de texto con exactamente dos posiciones decimales (`120000.00`), evitando pérdidas de precisión por redondeo en clientes externos.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
