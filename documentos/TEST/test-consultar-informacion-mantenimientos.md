### Test Case Specification: Consultar Información de Mantenimientos
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-informacion-mantenimientos.md](../SPEC/spec-consultar-informacion-mantenimientos.md) / [plan-consultar-informacion-mantenimientos.md](../PLAN/plan-consultar-informacion-mantenimientos.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-016-ConsultarInformacionMantenimientos
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la consulta de mantenimientos de una habitación en un rango de fechas (`GET /api/rooms/{roomId}/maintenances?startDate=…&endDate=…`), que usan Módulo 2 antes de vender noches y "Programar mantenimiento" antes de programar: disponibilidad, cruces con su inicio y fin, exclusión del día de salida, evaluación desde hoy cuando el rango empieza antes, y rechazos de fechas inválidas. En pantalla, la información se ve en "Ver informe" del panel de mantenimiento y en el aviso de solapamiento al programar.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Información de Mantenimientos | Specification Document | [documentos/SPEC/spec-consultar-informacion-mantenimientos.md](../SPEC/spec-consultar-informacion-mantenimientos.md) |
| Plan de Implementación de Consultar Información de Mantenimientos | Plan Document | [documentos/PLAN/plan-consultar-informacion-mantenimientos.md](../PLAN/plan-consultar-informacion-mantenimientos.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* No hay una pantalla propia de consulta: es un servicio para Módulo 2 y para "Programar mantenimiento".

##### Dependencies & Assumptions
* TC-015 programó en la 409 un mantenimiento del 20-11-2026 al 22-11-2026. La 201 (dato del equipo) tenía un bloqueo del 13-11-2026 al 13-11-2026; solo se consultó.

##### Preconditions & Default Setup
* **Initial State** : 409 con el mantenimiento del 20-11-2026 al 22-11-2026.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : Mantenimiento (`tc.mant1`) para los pasos de pantalla; la API no pide `X-User`.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como mantenimiento | Ir a `/login`, usuario y contraseña, "Ingresar" | `tc.mant1` | Abre `/mantenimiento` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Mantenimiento en la tabla | Revisar la fila de la 409 | 409 | Columna "Mantenimiento programado": "20-11-2026 a 22-11-2026" | [x] Pass | 13-11-2026: comprobado |
| 2 | Detalle | Pulsar "Ver informe" en la 409 | 409 | "Mantenimiento · Habitación 409" con Motivo, Programado por, Fechas "20-11-2026 a 22-11-2026", Programado el y Situación "Programado" | [x] Pass | 13-11-2026: comprobado |
| 3 | Cruce al programar | Programar en la 409 del 21-11-2026 al 23-11-2026 | 409 | "Ya hay un mantenimiento programado o en curso del 20-11-2026 al 22-11-2026. Elige otras fechas." | [x] Pass | 13-11-2026: comprobado (TC-015, Sección C, paso 8) |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fechas inválidas al programar | Ver TC-015, Sección C, pasos 1 a 6 | N/A | Los mensajes de cada regla de fechas | [x] Pass | 13-11-2026: comprobado en TC-015 |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Sin cruces | `GET {BASE_URL}/api/rooms/{id de la 409}/maintenances?startDate=2026-12-01&endDate=2026-12-05` | N/A | 200 con `roomId`, `available` true y `conflicts` vacío | [x] Pass | 13-11-2026: comprobado |
| 2 | Con cruce | `…?startDate=2026-11-19&endDate=2026-11-22` | N/A | 200 con `available` false y `conflicts` `[{"maintenanceStart":"2026-11-20","maintenanceEnd":"2026-11-22"}]` (sin motivo ni responsable) | [x] Pass | 13-11-2026: comprobado |
| 3 | Mismo día | `…?startDate=2026-11-22&endDate=2026-11-22` | N/A | 200 con `available` false (se evalúa ese día) | [x] Pass | 13-11-2026: comprobado |
| 4 | Día de salida excluido | `…?startDate=2026-11-18&endDate=2026-11-20` | N/A | 200 con `available` true: el 20 es la salida y no se evalúa | [x] Pass | 13-11-2026: comprobado |
| 5 | Rango que empieza antes de hoy | `GET …/{id de la 201}/maintenances?startDate=2026-11-10&endDate=2026-11-14` | 201 | 200 evaluado desde hoy: `available` false con el bloqueo del 13-11-2026 | [x] Pass | 13-11-2026: comprobado |
| 6 | Rango que termina antes de hoy | `…?startDate=2026-11-01&endDate=2026-11-10` | N/A | 400 `INVALID_DATE_RANGE`, "El rango consultado termina antes de hoy.", `details.rule` `RANGE_IN_PAST` | [x] Pass | 13-11-2026: comprobado |
| 7 | Fechas invertidas | `…?startDate=2026-12-10&endDate=2026-12-01` | N/A | 400 `INVALID_DATE_RANGE`, "La fecha de fin no puede ser anterior a la fecha de inicio.", `details.rule` `END_BEFORE_START` | [x] Pass | 13-11-2026: comprobado |
| 8 | Formato o fecha inexistente | `endDate=fecha-invalida`; `startDate=2027-02-31`; sin parámetros | N/A | 400 `VALIDATION_ERROR`, "La habitación y las fechas deben tener un formato válido (fechas AAAA-MM-DD).", con `details.fields` | [x] Pass | 13-11-2026: comprobado |
| 9 | Habitación inexistente | `roomId` `b0000000-0000-0000-0000-000000000999` | N/A | 404 `ROOM_NOT_FOUND`, "No se encontró la habitación." | [x] Pass | 13-11-2026: comprobado |
| 10 | Rango largo | `…?startDate=2026-12-01&endDate=2027-12-05` | N/A | 200: la consulta no aplica los límites de 90 y 365 días de la programación | [x] Pass | 13-11-2026: comprobado |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Los mantenimientos terminados (`Completed`) o caducados (`Expired`) no generan cruces.
* **EC-002** : La disponibilidad solo considera mantenimientos; no evalúa reservas, ocupación ni bajas.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |

--------------------------------------------------------------------------------

#### Hallazgos de la revisión (13-11-2026)

| ID | Tipo | Hallazgo | Corrección |
| :---: | ------ | ------ | ------ |
| H1 | Documento | La versión anterior esperaba un límite de consulta de 365 días ("El rango de consulta no puede superar 365 días.") que la especificación descarta, "La fecha inicial no puede ser posterior a la fecha final.", "La fecha "Desde" no puede ser posterior a "Hasta"." y "No se encontró ninguna habitación con ese identificador."; también una vista de consulta en el diálogo de programación y en el historial de Gerencia que no existe | Pasos reescritos con los textos y las reglas reales |
| H2 | Documento | La versión anterior no probaba el día de salida excluido ni el rango que empieza antes de hoy | Se agregan esos pasos |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 15 | 15 | 0 | 0 | APROBADO |
