### Test Case Specification: Consultar Liquidación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-liquidacion.md](../SPEC/spec-consultar-liquidacion.md) / [plan-consultar-liquidacion.md](../PLAN/plan-consultar-liquidacion.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-004-ConsultarLiquidacion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la consulta de liquidación de la estancia (`GET /api/stays/{stayId}/settlement`) durante el proceso de Check-Out, asegurando la presentación de los montos financieros provistos por Módulo 3 sin alteraciones ni cálculo local de impuestos en Módulo 1, y garantizando la entrega de respuesta HTTP 200 con estado UNAVAILABLE cuando Módulo 3 no está disponible.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Liquidación | Specification Document | [documentos/SPEC/spec-consultar-liquidacion.md](../SPEC/spec-consultar-liquidacion.md) |
| Plan de Implementación de Consultar Liquidación | Plan Document | [documentos/PLAN/plan-consultar-liquidacion.md](../PLAN/plan-consultar-liquidacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Módulo 1 no calcula impuestos ni recargos; los valores financieros provienen intactos del Módulo 3 (Facturación).

##### Dependencies & Assumptions
* Estancia activa en base de datos. Simulador o servicio de Módulo 3 disponible para respuestas AVAILABLE.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado `Occupied` con estancia activa (`stayId`).
* **Environment** : Ambiente de pruebas del equipo / Sandbox; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Usuario de Recepción (`recepcionista`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión y navegar a recepción | Iniciar sesión como recepcionista e ingresar a `/recepcion` | Usuario: `recepcionista` | Se carga el Panel de Recepción | [ ] | |
| 2 | Iniciar Check-out desde Salidas | En pestaña Salidas, pulsar "Check-out" en la estancia de hab. 101 | Estancia hab. 101 | Carga el wizard `/check-out` en el Paso 2: Liquidación | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Visualizar desglose de liquidación | En `/check-out` (Paso 2), revisar la tabla de liquidación | Datos de liquidación | Se muestran las noches calculadas, fecha de ingreso, fecha de salida, monto de hospedaje, comisión OTA (si aplica) e ingreso neto | [ ] | |
| 2 | Visualizar liquidación con Módulo 3 no disponible | Consultar liquidación cuando el servicio de Módulo 3 está caído | M3 desconectado | La interfaz muestra advertencia: "No fue posible conectar con Facturación." permitiendo avanzar con contingencia operativa | [ ] | |
| 3 | Continuar hacia el pago | Hacer clic en "Continuar a pago" | Botón continuar | Avanza al Paso 3 (Pago) del flujo de Check-Out | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Estancia no encontrada o cerrada | Abrir liquidación con un `stayId` inexistente o ya finalizado | UUID no válido | Se muestra mensaje de error: "No se encontró la estancia activa para liquidación." | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consulta de liquidación exitosa (AVAILABLE) | `GET {BASE_URL}/api/stays/{stayId}/settlement`<br>Headers: (sin X-User obligatorio) | `stayId` de estancia activa | HTTP 200 OK con JSON `SettlementResponseDto` conteniendo `status: "AVAILABLE"`, `settlementType`, `reservationInfo`, `guestSummary` y `stayData` | [ ] | |
| 2 | Consulta con Módulo 3 caído (UNAVAILABLE) | `GET {BASE_URL}/api/stays/{stayId}/settlement`<br>Headers: (sin X-User obligatorio) | Módulo 3 apagado | HTTP 200 OK con JSON `{ "status": "UNAVAILABLE", "unavailableCause": "No fue posible conectar con Facturación." }` | [ ] | |
| 3 | Encabezado de caché en respuesta | Verificar encabezados de respuesta HTTP en `GET /api/stays/{stayId}/settlement` | Estancia activa | Contiene encabezado `Cache-Control: no-store` para garantizar datos financieros en tiempo real | [ ] | |
| 4 | Error por estancia inexistente | `GET {BASE_URL}/api/stays/b0000000-0000-0000-0000-000000000999/settlement` | UUID inexistente | HTTP 404 Not Found, `errorCode: "ACTIVE_STAY_NOT_FOUND"`, mensaje: "No se encontró una estancia activa para la habitación." | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Salida el mismo día de llegada (`checkInDate == checkOutDate`): el cálculo de noches pernoctadas devuelve 0 sin romper el desglose financiero entregado por Módulo 3.
* **EC-002 (API)** : Reserva directa sin intermediario: `otaCommissionPercentage` y `otaCommissionAmount` son nulos, reflejando "No aplica" en la interfaz.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
