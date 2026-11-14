### Test Case Specification: Consultar Panel de Mantenimiento
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-panel-mantenimiento.md](../SPEC/spec-consultar-panel-mantenimiento.md) / [plan-consultar-panel-mantenimiento.md](../PLAN/plan-consultar-panel-mantenimiento.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-017-ConsultarPanelMantenimiento
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la carga y visualización del Panel de Mantenimiento (`GET /api/maintenance/panel`, ruta `/mantenimiento`), asegurando la presentación unificada de habitaciones inhabilitadas (`DisabledForRepairs`) y con bloqueo técnico (`TechnicalBlock`), la detección de tarea activa para redirección a `/mantenimiento/tarea` y los botones de acción operativa.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación del Panel de Mantenimiento | Specification Document | [documentos/SPEC/spec-consultar-panel-mantenimiento.md](../SPEC/spec-consultar-panel-mantenimiento.md) |
| Plan de Implementación del Panel de Mantenimiento | Plan Document | [documentos/PLAN/plan-consultar-panel-mantenimiento.md](../PLAN/plan-consultar-panel-mantenimiento.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot y frontend React en funcionamiento. La pantalla `/mantenimiento` está completamente desarrollada en React.

##### Dependencies & Assumptions
* Existencia de habitaciones en `DisabledForRepairs` o `TechnicalBlock`.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en `DisabledForRepairs`, habitación 105 con bloqueo técnico `TechnicalBlock`.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Personal de Mantenimiento (`mtorres`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de mantenimiento | Navegar a `/login`, ingresar credenciales | Usuario: `mtorres` | Redirige automáticamente a `/mantenimiento` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Verificar panel activo | Comprobar encabezado y lista de intervenciones | Ruta `/mantenimiento` | Se despliega el Panel de Mantenimiento con la lista de habitaciones a intervenir | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Visualizar habitaciones en lista | Revisar la tabla de habitaciones que requieren atención técnica | Habitaciones 101 y 105 | Muestra número de habitación, tipo, piso, estado actual, origen del reporte y botón de acción | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Visualizar acción para daño reportado | Ubicar la habitación 101 (`DisabledForRepairs`) | Habitación 101 | Se muestra el botón "Iniciar reparaciones" y el botón "Ver detalle del daño" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Visualizar acción para bloqueo técnico | Ubicar la habitación 105 (`TechnicalBlock`) | Habitación 105 | Se muestra el botón "Iniciar reparaciones" y el botón "Ver informe" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Redirección con tarea activa | Iniciar sesión teniendo ya una reparación en curso | Tarea activa en base de datos | El sistema detecta `activeTask != null` y redirige a `/mantenimiento/tarea` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Visualización cuando no hay averías | Ingresar al panel con todas las habitaciones disponibles o limpias | Sin averías | La tabla presenta estado vacío: "No hay habitaciones pendientes de reparación." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Error de conexión al cargar panel | Cargar `/mantenimiento` con el backend detenido | Backend caído | Mensaje de error en pantalla: "No fue posible cargar el panel de mantenimiento. Intenta de nuevo." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Carga exitosa del panel (sin tarea activa) | `GET {BASE_URL}/api/maintenance/panel`<br>Headers: `X-User: mtorres` | Usuario sin tarea activa | HTTP 200 OK con JSON `MaintenancePanelDto` `{ "activeTask": null, "pendingRooms": [...] }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Carga de panel con tarea activa | `GET {BASE_URL}/api/maintenance/panel`<br>Headers: `X-User: mtorres` | Usuario con tarea abierta | HTTP 200 OK con `{ "activeTask": { "taskId": "...", "roomId": "..." }, "pendingRooms": [] }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Error por falta de X-User | `GET {BASE_URL}/api/maintenance/panel`<br>Headers: (sin X-User) | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"`, mensaje: "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : Refresco reactivo: cuando otro técnico toma una habitación averiada, al recargar el panel la habitación desaparece de la lista de pendientes evitando duplicidades de intervención.
* **EC-002 (API)** : Ausencia de permisos por rol: el sistema valida la existencia y actividad de la cuenta mediante `X-User` sin rechazar por roles por área (`FORBIDDEN_AREA` no existe en la arquitectura actual).

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
| 11 | 11 | 0 | 0 | APROBADO |
