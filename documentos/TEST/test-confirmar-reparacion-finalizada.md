### Test Case Specification: Confirmar Fin de Reparación de Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-confirmar-reparacion-finalizada.md](../SPEC/spec-confirmar-reparacion-finalizada.md) / [plan-confirmar-reparacion-finalizada.md](../PLAN/plan-confirmar-reparacion-finalizada.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-014-ConfirmarReparacionFinalizada
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el ciclo de intervenciones técnicas bajo `/api/maintenance`: inicio de tarea (`POST /api/maintenance/tasks`), confirmación de fin de reparación (`POST /api/maintenance/tasks/{taskId}/complete`) respondiendo HTTP 204 No Content y transicionando la habitación a PendingCleaning, la opción de liberación (`POST /api/maintenance/tasks/{taskId}/release`) respondiendo HTTP 204 No Content, y la publicación del aviso de mantenimiento por Outbox hacia RabbitMQ (`m2.habitacion.mantenimiento.queue` con routing key `habitacion.mantenimiento`).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Confirmar Fin de Reparación | Specification Document | [documentos/SPEC/spec-confirmar-reparacion-finalizada.md](../SPEC/spec-confirmar-reparacion-finalizada.md) |
| Plan de Implementación de Confirmar Fin de Reparación | Plan Document | [documentos/PLAN/plan-confirmar-reparacion-finalizada.md](../PLAN/plan-confirmar-reparacion-finalizada.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere PostgreSQL, Spring Boot, broker RabbitMQ y frontend React en marcha. Las vistas `/mantenimiento` y `/mantenimiento/tarea` se encuentran implementadas.

##### Dependencies & Assumptions
* La habitación debe encontrarse en estado `DisabledForRepairs` o `TechnicalBlock`.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado `DisabledForRepairs`. Habitación 103 en `TechnicalBlock` con informe `Applied`.
* **Environment** : Ambiente de pruebas del equipo / Sandbox; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Personal de Mantenimiento (`mtorres`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de mantenimiento | Navegar a `/login`, autenticarse con usuario de área Mantenimiento | Usuario: `mtorres` | Carga el Panel de Mantenimiento en `/mantenimiento` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Iniciar tarea de reparación | En `/mantenimiento`, ubicar la habitación 101 y pulsar botón "Iniciar reparaciones" | Habitación 101 | Se genera la tarea vía API y se redirige a `/mantenimiento/tarea` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Visualizar tarea activa | En `/mantenimiento/tarea`, revisar los detalles de la habitación 101, motivo del daño y cronómetro | Habitación 101 | Se muestran los controles "Confirmar fin" y "Liberar tarea" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Confirmar fin de reparaciones | Hacer clic en "Confirmar fin" y confirmar en el diálogo "¿Confirmas que terminaste la reparación de la habitación 101?" | Diálogo de confirmación | Se envía `POST /api/maintenance/tasks/{taskId}/complete` (204), la habitación pasa a `PendingCleaning` y redirige a `/mantenimiento` con el mensaje: "Reparación de la habitación 101 finalizada. La habitación pasó a limpieza antes de volver a recibir huéspedes." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Finalizar reparación en bloqueo técnico | Iniciar y confirmar reparación en hab. 103 con estado `TechnicalBlock` | Habitación 103 | La habitación pasa a `PendingCleaning`, el informe `TechnicalBlockReport` pasa a `Completed` y se muestra: "Reparación de la habitación 103 finalizada. La habitación pasó a limpieza y el mantenimiento quedó terminado." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Liberar tarea de reparación | En `/mantenimiento/tarea`, pulsar "Liberar tarea" y confirmar en el modal | Diálogo de liberación | Se envía `POST /api/maintenance/tasks/{taskId}/release` (204), la tarea se libera, la habitación se mantiene en su estado previo para otro técnico y se redirige al panel con aviso: "Dejaste la habitación 101 para otro compañero." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Técnico con tarea ya activa | Intentar iniciar una segunda reparación teniendo ya una tarea en curso | Usuario con tarea activa | Mensaje de advertencia: "Ya estás reparando otra habitación. Termínala o déjala antes de empezar otra." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Habitación tomada por otro técnico | Intentar iniciar reparación sobre habitación con tarea abierta por otro usuario | Habitación en curso | Mensaje de advertencia: "Otro compañero ya está reparando esta habitación." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Habitación en estado no reparable | Intentar iniciar reparación en habitación disponible | Habitación en `Available` | Mensaje de rechazo: "La habitación está Disponible; no necesita reparación." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar tarea de reparación | `POST {BASE_URL}/api/maintenance/tasks`<br>Headers: `X-User: mtorres`, `Content-Type: application/json`<br>Body: `{"roomId": "a0000000-0000-0000-0000-000000000101"}` | Hab. 101 en `DisabledForRepairs` | HTTP 201 Created con JSON `RepairTaskStartedDto` conteniendo `taskId`, `roomId`, `status: "IN_PROGRESS"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Completar tarea de reparación | `POST {BASE_URL}/api/maintenance/tasks/{taskId}/complete`<br>Headers: `X-User: mtorres` | `taskId` activo | HTTP 204 No Content (cuerpo vacío) | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Liberar tarea de reparación | `POST {BASE_URL}/api/maintenance/tasks/{taskId}/release`<br>Headers: `X-User: mtorres` | `taskId` activo | HTTP 204 No Content (cuerpo vacío) | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Error por falta de X-User | `POST {BASE_URL}/api/maintenance/tasks`<br>Headers: (sin X-User)<br>Body: `{"roomId": "..."}` | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Error habitación en estado inválido | `POST {BASE_URL}/api/maintenance/tasks`<br>Headers: `X-User: mtorres`<br>Body: `{"roomId": "a0000000-0000-0000-0000-000000000102"}` | Hab. en `Available` | HTTP 409 Conflict, `errorCode: "ROOM_INVALID_STATE"`, mensaje: "La habitación está Disponible; no necesita reparación." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 6 | Error técnico con tarea activa | `POST {BASE_URL}/api/maintenance/tasks`<br>Headers: `X-User: mtorres`<br>Body: `{"roomId": "..."}` | Con tarea abierta | HTTP 409 Conflict, `errorCode: "ACTIVE_TASK_EXISTS"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 7 | Error tarea no encontrada | `POST {BASE_URL}/api/maintenance/tasks/b0000000-0000-0000-0000-000000000999/complete`<br>Headers: `X-User: mtorres` | Task ID inexistente | HTTP 404 Not Found, `errorCode: "TASK_NOT_FOUND"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 8 | Error completar tarea de otro técnico | `POST {BASE_URL}/api/maintenance/tasks/{taskId}/complete`<br>Headers: `X-User: otro_tecnico` | Tarea de `mtorres` | HTTP 403 Forbidden, `errorCode: "TASK_NOT_OWNED"`, mensaje: "La tarea pertenece a otro miembro del personal." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Emisión de aviso CANCELLED al finalizar reparación de daño | Confirmar fin de reparación sobre habitación en `DisabledForRepairs` vía API o pantalla | Habitación 101, daño previo | Outbox publica mensaje en exchange `hospitua.events` con routing key `habitacion.mantenimiento` hacia `m2.habitacion.mantenimiento.queue` con `action: "CANCELLED"`, `maintenanceId` (UUID del reporte de daño), `roomId`, `categoryRoom`, `startDate = endDate = fecha_cierre` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |
| 2 | Emisión de aviso UPDATED al finalizar bloqueo técnico con fecha distinta | Confirmar fin de reparación sobre habitación en `TechnicalBlock` cerrando en fecha distinta a la estimada | Habitación 103, cierre anticipado o retrasado | Outbox publica mensaje en `m2.habitacion.mantenimiento.queue` con routing key `habitacion.mantenimiento`, `action: "UPDATED"`, `maintenanceId` (UUID del bloqueo técnico), `roomId`, `categoryRoom`, `startDate = previousStartDate`, `endDate = fecha_cierre`, `previousEndDate = fecha_estimada_original` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |
| 3 | Verificación de no emisión de aviso en fin en fecha estimada | Confirmar fin de reparación sobre habitación en `TechnicalBlock` exactamente en su fecha de fin estimada | Habitación con fecha cierre = fecha estimada | No se encola ningún mensaje en el Outbox ni en RabbitMQ; Módulo 2 ya dispone del rango estimado correcto | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Concurrencia entre técnicos: el bloqueo pesimista en base de datos garantiza que solo un técnico inicia la tarea y el segundo recibe HTTP 409 `ROOM_TASK_EXISTS`.
* **EC-002 (API)** : Transición a PendingCleaning tras reparación: verificar que la habitación queda inmediatamente visible para el personal de aseo sin requerir intervención manual adicional.

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
| 20 | 20 | 0 | 0 | APROBADO |
