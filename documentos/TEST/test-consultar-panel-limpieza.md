### Test Case Specification: Consultar Panel de Limpieza
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-panel-limpieza.md](../SPEC/spec-consultar-panel-limpieza.md) / [plan-consultar-panel-limpieza.md](../PLAN/plan-consultar-panel-limpieza.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-012-ConsultarPanelLimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la carga y visualización del Panel de Limpieza (`GET /api/cleaning/panel`, ruta `/limpieza`), asegurando la presentación de habitaciones en estado PendingCleaning, la detección de la tarea activa del usuario para redirección automática a `/limpieza/tarea`, y los filtros por piso y estado sin comprobaciones de roles por área.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación del Panel de Limpieza | Specification Document | [documentos/SPEC/spec-consultar-panel-limpieza.md](../SPEC/spec-consultar-panel-limpieza.md) |
| Plan de Implementación del Panel de Limpieza | Plan Document | [documentos/PLAN/plan-consultar-panel-limpieza.md](../PLAN/plan-consultar-panel-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot y frontend React activos.

##### Dependencies & Assumptions
* Base de datos con habitaciones en diversos estados de aseo.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en `PendingCleaning`, Habitación 201 en `PendingCleaning`.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Personal de Limpieza (`limpiador1`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de limpieza | Navegar a `/login` e ingresar credenciales | Usuario: `limpiador1` | Redirige al Panel de Limpieza en `/limpieza` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Visualizar estructura del panel | Comprobar encabezado, filtros y lista de habitaciones | Ruta `/limpieza` | Se despliega el panel con la lista de habitaciones pendientes de aseo | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Visualizar lista de habitaciones pendientes | Revisar las tarjetas de habitaciones en `/limpieza` | Habitaciones 101 y 201 | Cada tarjeta muestra número de habitación, tipo, piso y botón "Iniciar limpieza" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Filtrar por piso | Seleccionar filtro de piso "Piso 1" | Filtro: Piso 1 | La lista se actualiza mostrando únicamente la habitación 101 | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Limpiar filtro | Seleccionar opción "Todos los pisos" | Todos los pisos | La lista vuelve a mostrar las habitaciones de todos los pisos | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Redirección con tarea activa | Iniciar sesión teniendo ya una tarea de limpieza abierta | Tarea activa en base de datos | El sistema detecta `activeTask != null` y redirige de inmediato a `/limpieza/tarea` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 5 | Actualizar panel manualmente | Hacer clic en el botón de recarga del panel | Acción de recarga | Se refrescan los datos de las habitaciones pendientes desde el servidor | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Visualización cuando no hay habitaciones pendientes | Cargar el panel cuando todas las habitaciones están limpias | Sin pendientes | Se muestra el estado vacío amigable: "No hay habitaciones pendientes de limpieza." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Error de conexión al cargar panel | Cargar `/limpieza` con backend detenido | Backend caído | Mensaje de error en pantalla: "No fue posible cargar el panel de limpieza. Intenta de nuevo." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Sesión expirada | Cargar el panel sin sesión activa o con token vencido | Sin sesión | Mensaje de alerta: "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consulta de panel exitosa (sin tarea activa) | `GET {BASE_URL}/api/cleaning/panel`<br>Headers: `X-User: limpiador1` | Usuario sin tarea | HTTP 200 OK con JSON `CleaningPanelDto` `{ "activeTask": null, "pendingRooms": [...] }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Consulta de panel con tarea activa | `GET {BASE_URL}/api/cleaning/panel`<br>Headers: `X-User: limpiador1` | Usuario con tarea abierta | HTTP 200 OK con `{ "activeTask": { "taskId": "...", "roomId": "..." }, "pendingRooms": [] }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Error por falta de encabezado X-User | `GET {BASE_URL}/api/cleaning/panel`<br>Headers: (sin X-User) | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"`, mensaje: "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : Refresco reactivo: cuando otro camarero toma una habitación, al actualizar el panel la habitación desaparece de la lista de pendientes evitando colisiones visuales.
* **EC-002 (API)** : Usuario inexistente o desactivado en X-User: responde HTTP 400 `CURRENT_USER_REQUIRED` obligando a reiniciar sesión.

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
| 13 | 13 | 0 | 0 | APROBADO |
