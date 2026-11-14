### Test Case Specification: Confirmar Fin de Limpieza
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-confirmar-fin-de-limpieza.md](../SPEC/spec-confirmar-fin-de-limpieza.md) / [plan-confirmar-fin-de-limpieza.md](../PLAN/plan-confirmar-fin-de-limpieza.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-011-ConfirmarFinDeLimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la finalización de tareas de aseo (`POST /api/cleaning/tasks/{taskId}/complete`), la transición condicional de la habitación (a Reserved si tiene reserva del día asignada, o a Available si no tiene), el reporte de daño desde la tarea de limpieza activa, la liberación de tarea (`POST /api/cleaning/tasks/{taskId}/release`), la respuesta HTTP 204 No Content, y la emisión condicional del aviso de mantenimiento por Outbox con routing key `habitacion.mantenimiento` hacia RabbitMQ cuando se reporta daño (sin aviso cuando concluye sin daño).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Confirmar Fin de Limpieza | Specification Document | [documentos/SPEC/spec-confirmar-fin-de-limpieza.md](../SPEC/spec-confirmar-fin-de-limpieza.md) |
| Plan de Implementación de Confirmar Fin de Limpieza | Plan Document | [documentos/PLAN/plan-confirmar-fin-de-limpieza.md](../PLAN/plan-confirmar-fin-de-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot, frontend React, base de datos PostgreSQL y broker RabbitMQ activos.

##### Dependencies & Assumptions
* Debe existir una tarea de limpieza abierta (`InCleaning`) para el camarero autenticado.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en `InCleaning` con reserva asignada para hoy. Habitación 102 en `InCleaning` sin reserva.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Personal de Limpieza (`limpiador1`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de limpieza | Navegar a `/login` e ingresar credenciales | Usuario: `limpiador1` | Redirige automáticamente a `/limpieza/tarea` por tener tarea activa | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Visualizar controles de tarea activa | Comprobar botones de acción en `/limpieza/tarea` | Vista de tarea activa | Se muestran los botones "Confirmar fin", "Liberar tarea" y "Reportar daño" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar fin con reserva hoy | En `/limpieza/tarea`, hacer clic en "Confirmar fin" para la habitación 101 | Hab. 101 con reserva hoy | Se envía `POST /api/cleaning/tasks/{taskId}/complete` (204), la habitación pasa a `Reserved` y redirige a `/limpieza` con el aviso: "Limpieza terminada. La habitación quedó reservada para la llegada de hoy." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Confirmar fin sin reserva hoy | Realizar y confirmar limpieza sobre habitación 102 (sin reserva para hoy) | Hab. 102 sin reserva | La habitación pasa a `Available` y se muestra el aviso: "Limpieza terminada. La habitación quedó disponible para asignar." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Liberar tarea de limpieza | En `/limpieza/tarea`, hacer clic en "Liberar tarea" y confirmar diálogo | Habitación en aseo | Se envía `POST /api/cleaning/tasks/{taskId}/release` (204), la habitación regresa a `PendingCleaning` y redirige a `/limpieza` con aviso: "Dejaste la habitación para otro compañero." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Verificación en Panel de Limpieza | Observar la tabla de `/limpieza` tras completar | Panel de limpieza | La tarea activa se cierra y la habitación ya no aparece en curso para el operario | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Tarea ya finalizada o cerrada | Intentar completar una tarea que ya fue cerrada en otra pestaña | Tarea cerrada | Mensaje de advertencia: "La tarea de limpieza ya no se encuentra activa." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Completar limpieza exitosamente | `POST {BASE_URL}/api/cleaning/tasks/{taskId}/complete`<br>Headers: `X-User: limpiador1` | `taskId` activo | HTTP 204 No Content (cuerpo vacío) | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Liberar tarea exitosamente | `POST {BASE_URL}/api/cleaning/tasks/{taskId}/release`<br>Headers: `X-User: limpiador1` | `taskId` activo | HTTP 204 No Content (cuerpo vacío) | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Error por falta de X-User | `POST {BASE_URL}/api/cleaning/tasks/{taskId}/complete`<br>Headers: (sin X-User) | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Error tarea no encontrada | `POST {BASE_URL}/api/cleaning/tasks/b0000000-0000-0000-0000-000000000999/complete`<br>Headers: `X-User: limpiador1` | TaskId inexistente | HTTP 404 Not Found, `errorCode: "TASK_NOT_FOUND"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Error completar tarea de otro operario | `POST {BASE_URL}/api/cleaning/tasks/{taskId}/complete`<br>Headers: `X-User: limpiador2` | Tarea de `limpiador1` | HTTP 403 Forbidden, `errorCode: "TASK_NOT_OWNED"`, mensaje: "La tarea pertenece a otro miembro del personal." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 6 | Verificar transición a Reserved en inventario | `GET {BASE_URL}/api/rooms/inventory/a0000000-0000-0000-0000-000000000101` | Hab. 101 con reserva | HTTP 200 OK con `{ "status": "Reserved" }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 7 | Verificar transición a Available en inventario | `GET {BASE_URL}/api/rooms/inventory/a0000000-0000-0000-0000-000000000102` | Hab. 102 sin reserva | HTTP 200 OK con `{ "status": "Available" }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 8 | Liberación devuelve a PendingCleaning | `GET {BASE_URL}/api/rooms/inventory/{roomId}` tras release | Hab. liberada | HTTP 200 OK con `{ "status": "PendingCleaning" }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Emisión de aviso SCHEDULED al reportar daño en limpieza | Completar limpieza marcando daño o reportar daño desde la tarea activa | Tarea de aseo activa con avería | Outbox publica mensaje en exchange `hospitua.events` con routing key `habitacion.mantenimiento` hacia `m2.habitacion.mantenimiento.queue` con `action: "SCHEDULED"`, `maintenanceId` (UUID del reporte de daño generado), `roomId`, `categoryRoom`, `startDate = endDate = hoy` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |
| 2 | Verificación de no emisión de aviso en limpieza ordinaria | Completar tarea de limpieza sin reporte de daño | Habitación en aseo sin novedad | No se encola ningún aviso de mantenimiento en Outbox ni en RabbitMQ | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Encadenamiento atómico a Reserved: si la habitación tiene reserva asignada para hoy, el backend ejecuta la transición `InCleaning → Available → Reserved` en la misma transacción sin dejar ventanas intermedias en `Available`.
* **EC-002 (API)** : Idempotencia de respuesta 204: tras responder 204 No Content, la tarea queda con fecha y hora de fin registrada en `cleaning_task` y cerrada permanentemente.

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
| 17 | 17 | 0 | 0 | APROBADO |
