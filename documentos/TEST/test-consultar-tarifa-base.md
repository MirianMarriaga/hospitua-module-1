### Test Case Specification: Consultar Tarifa Base
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-tarifa-base.md](../SPEC/spec-consultar-tarifa-base.md) / [plan-consultar-tarifa-base.md](../PLAN/plan-consultar-tarifa-base.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-006-ConsultarTarifaBase
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P3 - Minor

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el endpoint de integración Módulo 3 → Módulo 1 (`GET /api/rooms/{roomId}/base-rate`), el cual entrega la tarifa base oficial por noche de una habitación física para la tarifación de estancias, sin requerir encabezado X-User y rechazando habitaciones inexistentes con HTTP 404.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Tarifa Base | Specification Document | [documentos/SPEC/spec-consultar-tarifa-base.md](../SPEC/spec-consultar-tarifa-base.md) |
| Plan de Implementación de Consultar Tarifa Base | Plan Document | [documentos/PLAN/plan-consultar-tarifa-base.md](../PLAN/plan-consultar-tarifa-base.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Endpoint puramente REST consumido por sistemas externos (Módulo 3); en el frontend la tarifa base se visualiza en la tabla de Inventario (`/administracion/inventario`).

##### Dependencies & Assumptions
* Requiere base de datos con habitaciones de la semilla V2 cargadas.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 con tarifa $120.000, Habitación 105 con tarifa $320.000.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Usuario Administrador (`admin`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Navegar a `/login` e ingresar credenciales | Usuario: `admin` | Carga el Inventario en `/administracion/inventario` | [ ] | |
| 2 | Visualizar tarifas en tabla | Revisar la columna "Tarifa base" en la tabla de inventario | Inventario físico | Muestra la tarifa por noche de cada habitación formateada en moneda (ej. $120.000,00) | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Verificar concordancia de tarifas | Comparar el valor mostrado en pantalla para la habitación 101 con la respuesta de la API | Habitación 101 | Coincide exactamente con el valor $120.000,00 registrado en el catálogo | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Edición de tarifa base inválida | En el formulario de edición de habitación, ingresar tarifa igual a 0 | Tarifa: 0 | Se bloquea el guardado con mensaje: "La tarifa base debe ser mayor a 0 y tener máximo dos decimales." | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consulta de tarifa base por roomId (hoy) | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/base-rate`<br>Headers: (sin X-User obligatorio) | `roomId` de hab. 101 | HTTP 200 OK con JSON `{ "roomId": "a0000000-0000-0000-0000-000000000101", "roomNumber": "101", "categoryRoom": "SENCILLA", "baseRate": 120000.00, "effectiveDate": "2026-11-12" }` | [ ] | |
| 2 | Consulta de tarifa base con fecha específica | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/base-rate?date=2026-12-01` | `roomId` hab. 105, fecha futura | HTTP 200 OK con `baseRate: 320000.00` y `effectiveDate: "2026-12-01"` | [ ] | |
| 3 | Consulta sin encabezado X-User | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/base-rate`<br>Headers: (sin X-User) | Sin usuario | HTTP 200 OK (no requiere X-User por ser integración directa intermódulo) | [ ] | |
| 4 | Error habitación inexistente | `GET {BASE_URL}/api/rooms/b0000000-0000-0000-0000-000000000999/base-rate` | UUID no registrado | HTTP 404 Not Found, `errorCode: "ROOM_NOT_FOUND"`, mensaje: "No se encontró ninguna habitación con ese identificador." | [ ] | |
| 5 | Error formato de fecha inválido | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/base-rate?date=fecha-invalida` | Fecha texto | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"` | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Consulta de habitación en estado Inactive: la tarifa base sigue siendo consultable históricamente para liquidar consumos de estancias previas, respondiendo HTTP 200 OK.
* **EC-002 (API)** : Precisión decimal: verificar que los montos siempre se devuelven como números de punto flotante con dos decimales exactos (`NUMERIC(12,2)`).

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
