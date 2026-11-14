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
Este caso de prueba verifica el cierre de una limpieza desde la tarea activa (`/limpieza/tarea`): "Confirmar fin" (`POST /api/cleaning/tasks/{taskId}/complete`) sin daño (la habitación queda Disponible, o Reservada si tiene una reserva de hoy), con daño descrito (queda Inhabilitada por reparaciones) y "Liberar tarea" (`POST …/release`, vuelve a Pendiente de limpieza), con sus diálogos, avisos y rechazos.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Confirmar Fin de Limpieza | Specification Document | [documentos/SPEC/spec-confirmar-fin-de-limpieza.md](../SPEC/spec-confirmar-fin-de-limpieza.md) |
| Plan de Implementación de Confirmar Fin de Limpieza | Plan Document | [documentos/PLAN/plan-confirmar-fin-de-limpieza.md](../PLAN/plan-confirmar-fin-de-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Para el fin con reserva de hoy se publicó por RabbitMQ una actualización ADDED (`RES-TC-06`, 408) mientras la 408 estaba En limpieza (ver TC-001 para publicar mensajes).

##### Dependencies & Assumptions
* TC-010 dejó la 407 En limpieza a cargo de `tc.limpieza1`.

##### Preconditions & Default Setup
* **Initial State** : 407 En limpieza (`tc.limpieza1`); 408 Disponible.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : dos cuentas de Limpieza (en la ejecución: `tc.limpieza1` y `tc.limpieza2`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión con tarea activa | Ir a `/login` con el miembro que tiene la 407 | `tc.limpieza1` | Abre `/limpieza/tarea` directamente | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Botones de la tarea | Revisar la pantalla | N/A | Solo "Liberar tarea" y "Confirmar fin" | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Diálogo de liberar | Pulsar "Liberar tarea" | 407 | "¿Está seguro que desea liberar la limpieza de la habitación 407?" / "La habitación volverá a la lista como pendiente de limpieza y otro compañero podrá limpiarla." con "Cancelar" y "Liberar tarea"; "Cancelar" cierra sin cambios | [x] Pass | 13-11-2026: comprobado |
| 2 | Liberar | Pulsar "Liberar tarea" en el diálogo | N/A | `POST …/release` (204); abre `/limpieza` con "Dejaste la habitación 407 para otro compañero. Vuelve a aparecer como pendiente de limpieza." y la 407 Pendiente de limpieza | [x] Pass | 13-11-2026: comprobado |
| 3 | Diálogo de fin | Con `tc.limpieza2`, iniciar la 407 y pulsar "Confirmar fin" | 407 | "¿Está seguro que desea finalizar la limpieza de la habitación 407?" / "La limpieza quedará registrada como terminada.", el campo "Describir daño encontrado (opcional)" con "0 / 500" y "Déjalo vacío si no encontraste daños." | [x] Pass | 13-11-2026: comprobado |
| 4 | Fin sin daño | Dejar el campo vacío y pulsar "Confirmar fin" | Sin reserva de hoy | `POST …/complete` (204); abre `/limpieza` con "Limpieza de la habitación 407 finalizada." y la 407 Disponible | [x] Pass | 13-11-2026: comprobado |
| 5 | Fin con reserva de hoy | Limpiar la 408, publicar el ADDED de `RES-TC-06` para la 408 y confirmar el fin | 408 | Mientras está En limpieza, Llegadas muestra "En limpieza: se apartará al terminar la limpieza"; al terminar, la 408 queda Reservada | [x] Pass | 13-11-2026: comprobado (fin por API) |
| 6 | Fin con daño | Iniciar la 407, escribir un daño y pulsar "Confirmar fin" | "Lámpara de la mesa de noche rota" | Con texto, el diálogo avisa "Como describiste un daño, la habitación quedará fuera de servicio hasta que mantenimiento la repare."; al confirmar: "Limpieza de la habitación 407 finalizada con daño reportado. La habitación quedó fuera de servicio hasta que mantenimiento la repare." y la 407 Inhabilitada por reparaciones | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Daño de más de 500 caracteres | Escribir 501 caracteres en el campo | 501 caracteres | "501 / 500", "La descripción del daño admite máximo 500 caracteres." y "Confirmar fin" deshabilitado | [x] Pass | 13-11-2026: comprobado |
| 2 | Tarea ya cerrada | Confirmar el fin de una tarea que ya se cerró en otra pestaña | N/A | El diálogo muestra "Esta tarea ya fue cerrada."; al pulsar "Cancelar" vuelve a `/limpieza` | [x] Pass | 13-11-2026: comprobado (la tarea se cerró por API mientras la pantalla estaba abierta) |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Terminar | `POST {BASE_URL}/api/cleaning/tasks/{taskId}/complete` con `X-User` y `{}` | Tarea activa | 204 sin cuerpo | [x] Pass | 13-11-2026: comprobado |
| 2 | Liberar | `POST {BASE_URL}/api/cleaning/tasks/{taskId}/release` con `X-User` | Tarea activa | 204; la habitación vuelve a `PendingCleaning` | [x] Pass | 13-11-2026: comprobado |
| 3 | Tarea de otro miembro | `complete` y `release` de la tarea de `tc.limpieza1` con `X-User: tc.limpieza2` | N/A | 403 `TASK_NOT_OWNED`, "Esta habitación la está limpiando otro compañero." | [x] Pass | 13-11-2026: comprobado |
| 4 | Tarea inexistente | `taskId` `b0000000-0000-0000-0000-000000000999` | N/A | 404 `TASK_NOT_FOUND`, "No encontramos esta limpieza. Vuelve al panel." | [x] Pass | 13-11-2026: comprobado |
| 5 | Daño de 501 caracteres | `complete` con `damageDescription` de 501 caracteres | N/A | 400 `VALIDATION_ERROR`, "La descripción del daño admite máximo 500 caracteres.", `details.field` `damageDescription` | [x] Pass | 13-11-2026: comprobado |
| 6 | Tarea ya cerrada | Repetir `complete` o `release` de una tarea cerrada | N/A | 409 `TASK_ALREADY_CLOSED`, "Esta tarea ya fue cerrada." | [x] Pass | 13-11-2026: comprobado |
| 7 | Estados resultantes | `GET {BASE_URL}/api/rooms/inventory/{roomId}` tras cada cierre | 407, 408 | `Available` (fin sin reserva), `Reserved` (408 con reserva de hoy), `DisabledForRepairs` (fin con daño), `PendingCleaning` (liberada) | [x] Pass | 13-11-2026: comprobado |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Aviso por fin con daño | Revisar `m2.habitacion.mantenimiento.queue` tras el fin con daño | 407 | Mensaje `habitacion.mantenimiento` con `action` `SCHEDULED`, `roomId` de la 407, `maintenanceId`, `startDate` y `endDate` de hoy | [x] Pass | 13-11-2026: comprobado con la API de administración de RabbitMQ |
| 2 | Sin aviso en un fin normal | Revisar la misma cola tras los fines sin daño | 407, 408 | No se agrega ningún mensaje | [x] Pass | 13-11-2026: comprobado (los números de secuencia siguen sin huecos) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : En el historial, el fin con daño deja un periodo "Disponible" de duración cero antes de "Inhabilitada por reparaciones" (los dos con `sourceFlowName` "Confirmar fin de limpieza"); la liberación queda como "Confirmar fin de limpieza (liberar tarea)".
* **EC-002** : Un daño solo con espacios se trata como sin daño.

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
