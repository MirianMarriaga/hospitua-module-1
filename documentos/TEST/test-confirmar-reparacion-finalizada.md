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
Este caso de prueba verifica las reparaciones desde el panel de mantenimiento: "Iniciar reparaciones" (`POST /api/maintenance/tasks`), la tarea activa (`/mantenimiento/tarea`), "Liberar tarea" (`POST …/release`) y "Confirmar fin" (`POST …/complete`), que deja la habitación Pendiente de limpieza tanto desde Inhabilitada por reparaciones como desde Bloqueo técnico (con el mantenimiento terminado), con los rechazos y los avisos `habitacion.mantenimiento` a Módulo 2.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Confirmar Reparación Finalizada | Specification Document | [documentos/SPEC/spec-confirmar-reparacion-finalizada.md](../SPEC/spec-confirmar-reparacion-finalizada.md) |
| Plan de Implementación de Confirmar Reparación Finalizada | Plan Document | [documentos/PLAN/plan-confirmar-reparacion-finalizada.md](../PLAN/plan-confirmar-reparacion-finalizada.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Ninguna: todos los pasos se pueden hacer en la plataforma publicada. Los bloqueos técnicos de la 407 se programaron con inicio hoy (TC-015).

##### Dependencies & Assumptions
* TC-013 dejó la 407 y la 409 Inhabilitadas por reparaciones. TC-015 puso la 407 en Bloqueo técnico (del 13-11-2026 al 15-11-2026 y luego del 13-11-2026 al 13-11-2026).

##### Preconditions & Default Setup
* **Initial State** : 407 y 409 Inhabilitadas por reparaciones; `tc.mant1` y `tc.mant2` sin tarea activa.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : dos cuentas de Mantenimiento (en la ejecución: `tc.mant1` y `tc.mant2`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como mantenimiento | Ir a `/login`, usuario y contraseña, "Ingresar" | `tc.mant1` | Abre `/mantenimiento` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Iniciar reparaciones | Pulsar "Iniciar reparaciones" en la 407 | 407 | `POST /api/maintenance/tasks` (201) sin diálogo previo y abre `/mantenimiento/tarea` | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Tarea activa por daño | Revisar la pantalla | 407 Inhabilitada | "Tarea activa" / "Estás reparando esta habitación. Cuando termines, confírmalo; si no puedes terminar, déjala para otro compañero."; "Habitación en reparación" con 407, Boutique, "Inhabilitada por reparaciones", Empezaste, Tiempo transcurrido, A cargo de; "Reporte de daño" con Qué se dañó, Reportado por y Fecha y hora; botones "Liberar tarea" y "Confirmar fin" | [x] Pass | 13-11-2026: comprobado |
| 2 | Liberar | Pulsar "Liberar tarea" y confirmar | N/A | Diálogo "¿Está seguro que desea liberar la reparación de la habitación 407?" / "La habitación seguirá fuera de servicio y otro compañero podrá continuar la reparación."; al confirmar, `/mantenimiento` con "Dejaste la habitación 407 para otro compañero." | [x] Pass | 13-11-2026: comprobado |
| 3 | Confirmar fin de un daño | Con `tc.mant2`, iniciar la 407, pulsar "Confirmar fin" y confirmar | 407 | Diálogo "¿Está seguro que desea finalizar las reparaciones de la habitación 407?" / "La habitación pasará a limpieza antes de volver a recibir huéspedes."; al confirmar, "Reparación de la habitación 407 finalizada. La habitación pasó a limpieza antes de volver a recibir huéspedes." y la 407 Pendiente de limpieza | [x] Pass | 13-11-2026: comprobado |
| 4 | Confirmar fin de un bloqueo técnico | Con la 407 en Bloqueo técnico (TC-015), iniciar y confirmar el fin | 407 | La tarea muestra "Mantenimiento programado" con Motivo, Programado por y Fechas; al confirmar: "Reparación de la habitación 407 finalizada. La habitación pasó a limpieza y el mantenimiento quedó terminado." | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Volver al panel con una tarea | Con la tarea abierta, abrir `/mantenimiento` | N/A | Vuelve a `/mantenimiento/tarea` (no se puede iniciar otra desde la pantalla) | [x] Pass | 13-11-2026: comprobado |
| 2 | Cancelar los diálogos | Pulsar "Cancelar" en "Confirmar fin" | N/A | Se cierra sin enviar nada | [x] Pass | 13-11-2026: comprobado |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar | `POST {BASE_URL}/api/maintenance/tasks` con `X-User` y `{"roomId": …}` | 409 Inhabilitada | 201 con `{"taskId": …}` | [x] Pass | 13-11-2026: comprobado |
| 2 | Ya tiene una tarea | El mismo técnico, otra habitación | `tc.mant1`, 407 | 409 `ACTIVE_TASK_EXISTS`, "Ya estás reparando otra habitación. Termínala o déjala antes de empezar otra." | [x] Pass | 13-11-2026: comprobado |
| 3 | Habitación tomada por otro | Otro técnico, la misma habitación | `tc.mant2`, 409 | 409 `ROOM_TASK_EXISTS`, "Otro compañero ya está reparando esta habitación." | [x] Pass | 13-11-2026: comprobado |
| 4 | Habitación que no necesita reparación | `roomId` de una Disponible | 102 | 409 `ROOM_INVALID_STATE`, "La habitación está Disponible; no necesita reparación." | [x] Pass | 13-11-2026: comprobado |
| 5 | Sin usuario, sin habitación o inexistente | Sin `X-User`; body `{}`; `roomId` `b0000000-…-999` | N/A | 400 `CURRENT_USER_REQUIRED`; 400 `VALIDATION_ERROR` "Selecciona una habitación válida."; 404 `ROOM_NOT_FOUND` "No se encontró la habitación." | [x] Pass | 13-11-2026: comprobado |
| 6 | Tarea de otro técnico | `complete` y `release` de la tarea de `tc.mant1` con `X-User: tc.mant2` | N/A | 403 `TASK_NOT_OWNED`, "Esta reparación la está haciendo otro compañero." | [x] Pass | 13-11-2026: comprobado |
| 7 | Tarea inexistente | `taskId` `b0000000-0000-0000-0000-000000000999` | N/A | 404 `TASK_NOT_FOUND`, "No encontramos esta reparación. Vuelve al panel." | [x] Pass | 13-11-2026: comprobado |
| 8 | Terminar y repetir | `complete` (204); repetirlo | 409 | 204 y la 409 `PendingCleaning`; la repetición: 409 `TASK_ALREADY_CLOSED`, "Esta tarea ya fue cerrada." | [x] Pass | 13-11-2026: comprobado |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fin de un daño | Revisar `m2.habitacion.mantenimiento.queue` | 409, 407 | `habitacion.mantenimiento` con `action` `CANCELLED` y el mismo `maintenanceId` del `SCHEDULED` del daño | [x] Pass | 13-11-2026: comprobado con la API de administración de RabbitMQ |
| 2 | Fin de un bloqueo en fecha distinta a la estimada | Terminar hoy el bloqueo del 13-11-2026 al 15-11-2026 | 407 | `UPDATED` con `endDate` 2026-11-13 | [x] Pass | 13-11-2026: comprobado |
| 3 | Fin en la fecha estimada | Terminar hoy el bloqueo del 13-11-2026 al 13-11-2026 | 407 | No se publica ningún mensaje | [x] Pass | 13-11-2026: comprobado (la secuencia no avanzó) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Cuando dos técnicos piden la misma habitación, el segundo recibe 409 `ROOM_TASK_EXISTS` (en mantenimiento la habitación no cambia de estado al iniciar, por eso sale este código).
* **EC-002** : Tras el fin, la habitación aparece de inmediato en el panel de limpieza (TC-009).

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
| 19 | 19 | 0 | 0 | APROBADO |
