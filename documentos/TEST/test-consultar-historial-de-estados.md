### Test Case Specification: Consultar Historial de Estados
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-historial-de-estados.md](../SPEC/spec-consultar-historial-de-estados.md) / [plan-consultar-historial-de-estados.md](../PLAN/plan-consultar-historial-de-estados.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-022-ConsultarHistorialDeEstados
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la auditoría cronológica del ciclo de vida de las habitaciones (`GET /api/room-state-history`, ruta `/gerencia/historial`), asegurando la consulta paginada de periodos de estado (de 15 en 15), el uso de los parámetros de fecha `from` y `to`, el cálculo de duraciones, la visualización del flujo de origen (`sourceFlow`) y usuario responsable, sin requerir encabezado X-User en la consulta.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Historial de Estados | Specification Document | [documentos/SPEC/spec-consultar-historial-de-estados.md](../SPEC/spec-consultar-historial-de-estados.md) |
| Plan de Implementación de Historial de Estados | Plan Document | [documentos/PLAN/plan-consultar-historial-de-estados.md](../PLAN/plan-consultar-historial-de-estados.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot y frontend React activos. La pantalla `/gerencia/historial` se encuentra implementada en React.

##### Dependencies & Assumptions
* Requiere registros históricos generados por transiciones previas en `room_state_history`.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 con al menos 3 transiciones históricas (registro, reserva, check-in).
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Usuario Gerente (`gerente`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Gerente | Navegar a `/login` e ingresar credenciales de Gerencia | Usuario: `gerente` | Redirige a `/gerencia/inventario` | [ ] | |
| 2 | Navegar a la pantalla de historial | En el menú de navegación superior o lateral, hacer clic en "Historial de Estados" | Enlace "Historial de Estados" | Carga la vista de auditoría en `/gerencia/historial` | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Inspeccionar tabla de historial | Revisar el listado cronológico de eventos en `/gerencia/historial` | Transiciones registradas | Se presentan filas con: Número de habitación, Estado, Fecha y hora de inicio, Fecha y hora de fin, Duración calculada, Usuario responsable y Flujo de origen | [ ] | |
| 2 | Filtrar por habitación concreta | En el filtro de habitación, seleccionar la habitación 101 y pulsar "Filtrar" | Habitación 101 | La tabla actualiza sus filas mostrando únicamente el ciclo de vida de la habitación 101 | [ ] | |
| 3 | Filtrar por rango de fechas y estado | Seleccionar rango Desde: hace 3 días, Hasta: hoy, y Estado: "Ocupada" | Rango y estado | Se listan exclusivamente las estancias en las que la habitación estuvo ocupada en el periodo | [ ] | |
| 4 | Visualizar periodo actualmente activo | Ubicar la fila del estado actual de la habitación | Estado actual | La columna de Fecha de fin muestra badge "En curso" o texto indicando periodo abierto | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fecha inicial posterior a final | Seleccionar Fecha Desde posterior a Fecha Hasta | Fechas invertidas | Mensaje de validación: "La fecha "Desde" no puede ser posterior a "Hasta"." | [ ] | |
| 2 | Filtros sin resultados | Filtrar con criterios que no coinciden con ningún registro | Criterios sin coincidencias | Mensaje en tabla: "No se encontraron registros de historial para los filtros seleccionados." | [ ] | |
| 3 | Parámetros de filtro no permitidos | Modificar valores en la barra de consulta a formatos incorrectos | Formatos inválidos | Mensaje de validación: "Revisa los filtros aplicados." | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consulta general paginada (15 en 15) | `GET {BASE_URL}/api/room-state-history?page=1`<br>Headers: (sin X-User obligatorio) | Paginación básica | HTTP 200 OK con JSON `PagedResultDto<RoomStateHistoryRowDto>` `{ "items": [...], "total": 14, "page": 1, "totalPages": 1 }` | [ ] | |
| 2 | Consulta filtrada por parámetros from y to | `GET {BASE_URL}/api/room-state-history?roomId=a0000000-0000-0000-0000-000000000101&from=2026-10-01&to=2026-11-12&status=Occupied` | `from` y `to` válidos | HTTP 200 OK con registros filtrados de ocupación de la habitación 101 | [ ] | |
| 3 | Error por rango de fechas invertido | `GET {BASE_URL}/api/room-state-history?from=2026-11-20&to=2026-11-10` | Fechas invertidas | HTTP 400 Bad Request, `errorCode: "INVALID_DATE_RANGE"`, mensaje: "La fecha "Desde" no puede ser posterior a "Hasta"." | [ ] | |
| 4 | Error por parámetros de página inválidos | `GET {BASE_URL}/api/room-state-history?page=-1` | Página negativa | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, mensaje: "Revisa los filtros aplicados." | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Periodo activo sin fecha de cierre (`endDate: null`): comprobar que la API serializa correctamente el valor nulo y que el cálculo de duración en frontend muestra "En curso" sin errores de parseo de fechas.
* **EC-002 (API)** : Continuidad temporal entre transiciones: verificar que la fecha/hora de inicio de un nuevo estado coincide exactamente con la fecha/hora de fin del estado predecesor en nanosegundos/milisegundos.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
