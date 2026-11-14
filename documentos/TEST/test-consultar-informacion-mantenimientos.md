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
Este caso de prueba verifica el endpoint de integración Módulo 2 → Módulo 1 (`GET /api/rooms/{roomId}/maintenances?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`), el cual entrega la disponibilidad de una habitación física frente a mantenimientos para una estadía y la lista de conflictos (`conflicts: [{ maintenanceStart, maintenanceEnd }]`), sin requerir encabezado X-User y sin mutar datos en el sistema.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Mantenimientos | Specification Document | [documentos/SPEC/spec-consultar-informacion-mantenimientos.md](../SPEC/spec-consultar-informacion-mantenimientos.md) |
| Plan de Implementación de Consultar Mantenimientos | Plan Document | [documentos/PLAN/plan-consultar-informacion-mantenimientos.md](../PLAN/plan-consultar-informacion-mantenimientos.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Endpoint consumido primordialmente por Módulo 2 y validadores de programación. <!-- PENDIENTE: Vista de calendario o cuadrícula dedicada independiente fuera del diálogo de programación y panel de mantenimiento -->

##### Dependencies & Assumptions
* Base de datos PostgreSQL con informes de mantenimiento y bloqueos técnicos cargados.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 con informe de daño registrado. Habitación 105 con bloqueo técnico programado del 2026-11-20 al 2026-11-23.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Personal de Mantenimiento (`mtorres`), Gerencia (`gerente`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como mantenimiento | Navegar a `/login` e ingresar credenciales | Usuario: `mtorres` | Redirige al panel `/mantenimiento` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Visualizar ventana de mantenimiento | En `/mantenimiento`, pulsar "Ver informe" en hab. 105 o abrir el diálogo modal `ScheduleTechnicalBlockDialog` | Habitación 105 | <!-- PENDIENTE: Vista de calendario o cuadrícula dedicada independiente fuera del diálogo de programación y panel de mantenimiento --> Se despliega el detalle de mantenimientos programados para la habitación | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Visualizar en historial de gerencia | Ingresar como gerente a `/gerencia/historial` y filtrar por hab. 105 | Habitación 105 | Se listan las transiciones asociadas a bloqueos técnicos | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consultar habitación sin mantenimientos | En el diálogo de programación, seleccionar fechas del 2026-12-01 al 2026-12-05 para hab. 101 | Fechas libres | Se indica que la habitación se encuentra libre de intervenciones técnicas en ese rango | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Consultar habitación con mantenimiento activo | Consultar hab. 105 en el rango del 2026-11-20 al 2026-11-23 | Fechas con bloqueo | Se presenta alerta de solapamiento indicando el rango del bloqueo programado | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Rango de fechas invertido | Ingresar fecha de fin anterior a la fecha de inicio | Fechas invertidas | Mensaje de validación: "La fecha inicial no puede ser posterior a la fecha final." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Rango superior a 365 días | Ingresar un rango superior a 1 año en los filtros de búsqueda | Rango > 365 días | Mensaje de error: "El rango de consulta no puede superar 365 días." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consulta de habitación disponible (sin cruces) | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/maintenances?startDate=2026-12-01&endDate=2026-12-05`<br>Headers: (sin X-User obligatorio) | Hab. 101, fechas libres | HTTP 200 OK con JSON `{ "roomId": "a0000000-0000-0000-0000-000000000101", "available": true, "conflicts": [] }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Consulta de habitación con cruces de mantenimiento | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/maintenances?startDate=2026-11-19&endDate=2026-11-22` | Hab. 105, rango solapado | HTTP 200 OK con JSON `{ "roomId": "...", "available": false, "conflicts": [{"maintenanceStart": "2026-11-20", "maintenanceEnd": "2026-11-23"}] }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Error por formato de fecha inválido | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/maintenances?startDate=2026-12-10&endDate=fecha-invalida` | Fecha no AAAA-MM-DD | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, mensaje: "La habitación y las fechas deben tener un formato válido (fechas AAAA-MM-DD)." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Error por fecha fin anterior a inicio | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/maintenances?startDate=2026-12-10&endDate=2026-12-01` | Fechas invertidas | HTTP 400 Bad Request, `errorCode: "INVALID_DATE_RANGE"`, mensaje: "La fecha "Desde" no puede ser posterior a "Hasta"." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Error por habitación inexistente | `GET {BASE_URL}/api/rooms/b0000000-0000-0000-0000-000000000999/maintenances?startDate=2026-11-10&endDate=2026-11-15` | UUID inexistente | HTTP 404 Not Found, `errorCode: "ROOM_NOT_FOUND"`, mensaje: "No se encontró ninguna habitación con ese identificador." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Salida el mismo día de llegada (`startDate == endDate`): la consulta evalúa la disponibilidad para ese único día inclusivo respondiendo HTTP 200 OK.
* **EC-002 (API)** : Exclusión de la fecha de salida: la fecha `endDate` de la estadía no se considera ocupada (el último día evaluado es `endDate - 1 día` salvo que sea el mismo día de llegada).

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |

--------------------------------------------------------------------------------

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 12 | 12 | 0 | 0 | APROBADO |
