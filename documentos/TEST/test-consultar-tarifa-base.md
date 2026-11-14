### Test Case Specification: Consultar Tarifa Base
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-tarifa-base.md](../SPEC/spec-consultar-tarifa-base.md) / [plan-consultar-tarifa-base.md](../PLAN/plan-consultar-tarifa-base.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-006-ConsultarTarifaBase
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P3 - Minor

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la consulta de la tarifa base de una habitación que hace Módulo 3 (`GET /api/rooms/{roomId}/base-rate?date=YYYY-MM-DD`): la respuesta con `roomId`, la fecha recibida, `baseRate` como texto con dos decimales y `currency` "COP"; la fecha obligatoria; y la habitación inexistente. En pantalla, la tarifa se ve en el inventario y se valida al registrar o editar.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Tarifa Base | Specification Document | [documentos/SPEC/spec-consultar-tarifa-base.md](../SPEC/spec-consultar-tarifa-base.md) |
| Plan de Implementación de Consultar Tarifa Base | Plan Document | [documentos/PLAN/plan-consultar-tarifa-base.md](../PLAN/plan-consultar-tarifa-base.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Módulo 3 no está desplegado: la consulta se hace directamente contra la API.

##### Dependencies & Assumptions
* La 407 tiene tarifa base $ 340.000 (TC-018 y TC-019). La 104 está Inactiva.

##### Preconditions & Default Setup
* **Initial State** : 407 con tarifa 340000.00.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : Administración (`cabo`) para los pasos de pantalla; la API no pide `X-User`.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Ir a `/login`, usuario y contraseña, "Ingresar" | Cuenta de Administración | Abre `/administracion/inventario` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Tarifa en el inventario | Filtrar por la 407 | 407 | Columna "Tarifa base": "$ 340.000" | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Coincide con la API | Comparar la tarifa del inventario con `GET …/base-rate` (Sección D, paso 1) | 407 | El mismo valor: 340000.00 | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Tarifa 0 o con tres decimales | En la edición de una habitación Disponible, escribir la tarifa y pulsar "Guardar cambios" | `0`; `100.555` | "La tarifa base debe ser mayor a 0 y tener máximo dos decimales."; no se envía la petición | [x] Pass | 13-11-2026: comprobado en la 105 sin guardar |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Tarifa de hoy | `GET {BASE_URL}/api/rooms/{id de la 407}/base-rate?date=2026-11-13` sin `X-User` | N/A | 200 con `{"roomId": …, "date": "2026-11-13", "baseRate": "340000.00", "currency": "COP"}` | [x] Pass | 13-11-2026: comprobado |
| 2 | Otra fecha | `…?date=2026-12-01` y `…?date=2026-11-01` | N/A | 200 con la misma tarifa y la `date` recibida (la tarifa no depende de la fecha) | [x] Pass | 13-11-2026: comprobado |
| 3 | Habitación Inactiva | `GET …/{id de la 104}/base-rate?date=2026-11-13` | 104 | 200 con su tarifa ("200000.00") | [x] Pass | 13-11-2026: comprobado |
| 4 | Sin fecha | `GET …/{id de la 407}/base-rate` | N/A | 400 `VALIDATION_ERROR`, "La fecha debe tener un formato válido (AAAA-MM-DD).", `details.field` `date` | [x] Pass | 13-11-2026: comprobado |
| 5 | Fecha inválida o inexistente | `?date=fecha`; `?date=2026-02-31` | N/A | 400 `VALIDATION_ERROR` con el mismo texto | [x] Pass | 13-11-2026: comprobado |
| 6 | Habitación inexistente | `GET …/b0000000-0000-0000-0000-000000000999/base-rate?date=2026-11-13` | N/A | 404 `ROOM_NOT_FOUND`, "No se encontró la habitación." | [x] Pass | 13-11-2026: comprobado |
| 7 | Identificador mal formado | `GET {BASE_URL}/api/rooms/abc/base-rate?date=2026-11-13` | N/A | 400 `VALIDATION_ERROR`, `details.field` `roomId` | [x] Pass | 13-11-2026: comprobado |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : `baseRate` viaja como texto con dos decimales exactos (`"340000.00"`), no como número.
* **EC-002 (API)** : Sin `date`, el 404 de una habitación inexistente no se alcanza: primero se valida la fecha (400).

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
| H1 | Documento | La versión anterior consultaba sin `date` y esperaba 200 (el parámetro es obligatorio: responde 400), esperaba `roomNumber`, `categoryRoom`, `effectiveDate` y `baseRate` numérico (la respuesta trae `roomId`, `date`, `baseRate` como texto y `currency`), "No se encontró ninguna habitación con ese identificador." y el formato "$120.000,00"; usaba la 101 y la 105, que son datos del equipo | Pasos reescritos con la respuesta real |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 11 | 11 | 0 | 0 | APROBADO |
