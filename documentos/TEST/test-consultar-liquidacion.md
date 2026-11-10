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
Este caso de prueba verifica que durante el flujo de Check-Out el sistema consulte de manera síncrona y reactiva la liquidación de la estancia a Módulo 3 (`GET /api/settlements`), estructurando el desglose informativo para recepción y pago sin realizar cálculos financieros locales ni retener al huésped ante contingencias del servicio externo.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Liquidación | Specification Document | [documentos/SPEC/spec-consultar-liquidacion.md](../SPEC/spec-consultar-liquidacion.md) |
| Plan de Implementación de Consultar Liquidación | Plan Document | [documentos/PLAN/plan-consultar-liquidacion.md](../PLAN/plan-consultar-liquidacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere el backend de Módulo 1 en ejecución y disponibilidad de un mock o instancia activa de Módulo 3 para responder en `/api/settlements`.

##### Dependencies & Assumptions
* TC-003 Registrar check-in debe haber creado la estancia física de prueba (`Stay`) para la habitación a liquidar.
* Módulo 1 actúa exclusivamente como consumidor de los montos y no efectúa recálculos ni cobros de facturación.

##### Preconditions & Default Setup
* **Initial State** : Estancia activa registrada en base de datos para la habitación 201 (`stayId`: UUID válido, reserva `RSV-3F9A1C7B`, `source`: BOOKING o DIRECTA).
* **Environment** : Ambiente de pruebas del equipo / Sandbox con simulador de Módulo 3.
* **Test Accounts** : Usuario recepcionista autenticado; ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Consulta para Reserva Directa y OTA)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consultar liquidación de estancia con canal OTA | En el flujo de Check-Out, avanzar a la etapa de liquidación para una estancia originada en OTA | Estancia en hab. 201 (`source`: BOOKING, estancia de 2 noches) | Módulo 1 invoca `GET /api/settlements?reservationRef=RSV-3F9A1C7B&roomId=...` a M3; en pantalla se despliega "Información de la liquidación" con: Tipo de liquidación: "Liquidación informativa", Fuente: "Booking", Comisión Booking: "15 %", Valor comisión Booking: monto calculado por M3, e Ingreso neto exacto | [ ] | [ ] |
| 2 | Verificar tarjeta de revisión de pago | Observar la sección "Revisión de pago" en el mismo paso de liquidación | Datos de la estancia | Se muestra el valor de Hospedaje liquidado, noches de estadía, fechas de entrada y salida, y la nota informativa: "La factura se emite al confirmar la salida." sin mostrar IVA ni total fiscal | [ ] | [ ] |
| 3 | Consultar liquidación de estancia con canal Directo | Ejecutar la consulta para una estancia con origen Directo (`source`: DIRECTA) | Estancia directa sin intermediarios | El sistema muestra: Tipo de liquidación: "Liquidación informativa", Fuente: "Directa", Comisión: "No aplica", Valor comisión: "No aplica", e Ingreso neto igual al valor total de hospedaje | [ ] | [ ] |

--------------------------------------------------------------------------------

##### Section 2: Manejo de Contingencias e Indisponibilidad de Módulo 3
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Caída de conexión o timeout con Módulo 3 | Simular indisponibilidad o demora superior a 3 segundos en el servicio `/api/settlements` de Módulo 3 | Simular timeout / caída en M3 | Módulo 1 entrega un resultado controlado de estado `UNAVAILABLE` y la pantalla de Check-Out presenta el banner de contingencia: "La liquidación no está disponible en este momento. El servicio de facturación no respondió. Puedes continuar con la salida del huésped y liberar la habitación para limpieza; el cobro se revisa después con facturación." | [Pass] | Verificado por test automatizado (`SettlementClientAdapterTest` y `CheckOutPage.test.tsx`) |
| 2 | Error 404 de Módulo 3 (reserva o cotización no encontrada) | Simular respuesta HTTP 404 con `errorCode: "RESERVATION_NOT_FOUND"` desde Módulo 3 | Error controlado de M3 | El sistema traduce la respuesta a `UNAVAILABLE` sin interrumpir la interfaz con excepciones técnicas y muestra el banner de contingencia permitiendo continuar con la salida física | [Pass] | Verificado por test automatizado (`SettlementClientAdapterTest`) |
| 3 | Solicitud interna con identificador de estancia inexistente | Consultar el endpoint interno `GET /api/stays/{stayId}/settlement` con un UUID que no corresponde a una estancia activa | `stayId`: `00000000-0000-0000-0000-000000000000` | El sistema responde HTTP 404 con mensaje: "No hay una estancia activa con ese identificador." | [Pass] | Verificado por test automatizado (`SettlementControllerTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Salida anticipada del huésped: Módulo 3 calcula el hospedaje por lo cotizado originalmente sin reducción; Módulo 1 presenta el valor tal como lo recibe sin aplicar descuentos por noches no consumidas.
* **EC-002** : Salidas múltiples simultáneas: varias consultas de liquidación concurrentes son atendidas de forma independiente sin interferir entre estancias ni alterar saldos en base de datos.
* **EC-003** : Circuito abierto (Circuit Breaker): ante reiteradas caídas de Módulo 3, el circuito pasa a estado abierto y responde de inmediato con contingencia sin saturar la red externa.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
