### Test Case Specification: Marcar Habitación en Limpieza
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-habitacion-en-limpieza.md](../SPEC/spec-marcar-habitacion-en-limpieza.md) / [plan-marcar-habitacion-en-limpieza.md](../PLAN/plan-marcar-habitacion-en-limpieza.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-010-MarcarHabitacionEnLimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el inicio de labores de limpieza (`POST /api/cleaning/tasks`), la transición de la habitación de PendingCleaning a InCleaning, la creación de la tarea asociada asignada al camarero autenticado en `X-User`, la restricción de una sola tarea activa por operario y el bloqueo concurrente contra múltiples camareros sobre la misma habitación.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar en Limpieza | Specification Document | [documentos/SPEC/spec-marcar-habitacion-en-limpieza.md](../SPEC/spec-marcar-habitacion-en-limpieza.md) |
| Plan de Implementación de Marcar en Limpieza | Plan Document | [documentos/PLAN/plan-marcar-habitacion-en-limpieza.md](../PLAN/plan-marcar-habitacion-en-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot, frontend React y base de datos PostgreSQL activos.

##### Dependencies & Assumptions
* La habitación objetivo debe encontrarse en estado `PendingCleaning`.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado `PendingCleaning`.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Personal de Limpieza (`limpiador1`, `limpiador2`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de limpieza | Navegar a `/login` e ingresar credenciales | Usuario: `limpiador1` | Redirige al Panel de Limpieza en `/limpieza` | [ ] | |
| 2 | Ubicar habitación pendiente | En `/limpieza`, buscar la habitación 101 en la lista de pendientes | Habitación 101 | Muestra la tarjeta con número, tipo, piso y botón "Iniciar limpieza" | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar labor de limpieza | Hacer clic en "Iniciar limpieza" sobre la habitación 101 | Habitación 101 | Se envía `POST /api/cleaning/tasks`, la habitación pasa a `InCleaning` y navega a `/limpieza/tarea` | [ ] | |
| 2 | Visualizar tarea activa | En `/limpieza/tarea`, revisar los datos de la habitación en aseo | Tarea activa | Se muestra el cronómetro de tiempo transcurrido, detalles de la habitación y botones "Confirmar fin" y "Reportar daño" | [ ] | |
| 3 | Redirección defensiva | Si el operario intenta navegar a `/limpieza` teniendo una tarea activa | Tarea en curso | El sistema detecta la tarea activa en `CleaningPanelDto` y lo redirige automáticamente a `/limpieza/tarea` | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Operario con tarea ya activa intenta iniciar otra | Intentar iniciar limpieza de otra habitación teniendo una tarea abierta | Usuario con tarea abierta | Mensaje de advertencia: "Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra." | [ ] | |
| 2 | Habitación tomada por otro compañero | Intentar iniciar limpieza sobre una habitación que otro camarero acaba de tomar | Habitación en curso | Mensaje de advertencia: "Otro compañero ya está limpiando esta habitación." | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar limpieza exitoso | `POST {BASE_URL}/api/cleaning/tasks`<br>Headers: `X-User: limpiador1`, `Content-Type: application/json`<br>Body: `{"roomId": "a0000000-0000-0000-0000-000000000101"}` | Hab. en `PendingCleaning` | HTTP 201 Created con JSON `CleaningTaskStartedDto` conteniendo `taskId`, `roomId`, `memberId: "limpiador1"`, `startedAt` | [ ] | |
| 2 | Error por falta de X-User | `POST {BASE_URL}/api/cleaning/tasks`<br>Headers: (sin X-User)<br>Body: `{"roomId": "..."}` | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"` | [ ] | |
| 3 | Error habitación no pendiente | `POST {BASE_URL}/api/cleaning/tasks`<br>Headers: `X-User: limpiador1`<br>Body: `{"roomId": "a0000000-0000-0000-0000-000000000102"}` | Hab. en `Available` | HTTP 409 Conflict, `errorCode: "ROOM_INVALID_STATE"`, mensaje: "La habitación no está pendiente de limpieza." | [ ] | |
| 4 | Error operario con tarea activa | `POST {BASE_URL}/api/cleaning/tasks`<br>Headers: `X-User: limpiador1`<br>Body: `{"roomId": "a0000000-0000-0000-0000-000000000103"}` | Mismo usuario | HTTP 409 Conflict, `errorCode: "ACTIVE_TASK_EXISTS"`, mensaje: "Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra." | [ ] | |
| 5 | Error habitación tomada por otro camarero | `POST {BASE_URL}/api/cleaning/tasks`<br>Headers: `X-User: limpiador2`<br>Body: `{"roomId": "a0000000-0000-0000-0000-000000000101"}` | Distinto usuario | HTTP 409 Conflict, `errorCode: "ROOM_TASK_EXISTS"`, mensaje: "Otro compañero ya está limpiando esta habitación." | [ ] | |
| 6 | Consulta de estado en inventario | `GET {BASE_URL}/api/rooms/inventory/a0000000-0000-0000-0000-000000000101` | Hab. en limpieza | HTTP 200 OK con `status: "InCleaning"` | [ ] | |
| 7 | Tarea activa en panel de limpieza | `GET {BASE_URL}/api/cleaning/panel`<br>Headers: `X-User: limpiador1` | Usuario con tarea | HTTP 200 OK con campo `activeTask` no nulo conteniendo los datos de la tarea | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Concurrencia entre camareros: el índice único en base de datos (`uk_cleaning_task_open_by_member`) y el bloqueo pesimista en la habitación impiden dobles asignaciones bajo cualquier condición de carrera.
* **EC-002 (Interfaz)** : Pérdida de conectividad temporal durante la limpieza: al reconectar o refrescar `/limpieza/tarea`, el cronómetro recupera la hora original de inicio calculada contra el servidor.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
