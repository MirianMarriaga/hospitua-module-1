### Test Case Specification: Marcar Habitación como Disponible
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-habitacion-como-disponible.md](../SPEC/spec-marcar-habitacion-como-disponible.md) / [plan-marcar-habitacion-como-disponible.md](../PLAN/plan-marcar-habitacion-como-disponible.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-008-MarcarHabitacionComoDisponible
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica las tres formas de dejar una habitación Disponible: la reactivación de una habitación Inactiva desde `/administracion/habitaciones/{roomId}/reactivacion` (`GET /api/rooms/{roomId}/decommission` y `POST /api/rooms/{roomId}/reactivation`), el fin de una limpieza sin reserva para hoy (ver TC-011) y la liberación de una habitación Reservada cuando su reserva se cancela (actualización REMOVED de Módulo 2).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Habitación como Disponible | Specification Document | [documentos/SPEC/spec-marcar-habitacion-como-disponible.md](../SPEC/spec-marcar-habitacion-como-disponible.md) |
| Plan de Implementación de Marcar Habitación como Disponible | Plan Document | [documentos/PLAN/plan-marcar-habitacion-como-disponible.md](../PLAN/plan-marcar-habitacion-como-disponible.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La liberación por cancelación la dispara un mensaje de Módulo 2 por RabbitMQ (ver TC-001 para publicarlo).

##### Dependencies & Assumptions
* TC-020 dio de baja la 409 (por pantalla) y la 408 (por API). TC-001 publicó `RES-TC-02` para la 408 y la 409.

##### Preconditions & Default Setup
* **Initial State** : 408 y 409 Inactivas con su baja vigente; después, las dos Reservadas por `RES-TC-02`.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : una cuenta de Administración (en la ejecución: `cabo`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Ir a `/login`, usuario y contraseña, "Ingresar" | Cuenta de Administración | Abre `/administracion/inventario` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Filtrar las inactivas | Estado "Inactiva" | N/A | Aparecen las inactivas (103, 104, 408, 409) con la acción "Marcar como disponible" | [x] Pass | 13-11-2026: comprobado |
| 3 | Abrir la reactivación | En la fila de la 409 pulsar "Marcar como disponible" | 409 | Abre `/administracion/habitaciones/{roomId}/reactivacion` | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Revisar la confirmación | Ver la pantalla | 409 | "Marcar como disponible" / "Reintegra una habitación dada de baja al inventario operativo.", "¿Reactivar la habitación 409?", "Volverá al inventario operativo y podrá recibir nuevas reservas y estancias.", Habitación, Piso, Tipo, Estado "Inactiva", "Dada de baja el", "Motivo de la baja", "Detalle", el aviso "La habitación pasará a estado Disponible." y los botones "Cancelar" y "Confirmar reactivación" (no hay diálogo adicional) | [x] Pass | 13-11-2026: comprobado |
| 2 | Cancelar | Pulsar "Cancelar" | N/A | Vuelve a `/administracion/inventario` sin cambios | [x] Pass | 13-11-2026: comprobado |
| 3 | Confirmar | Abrir de nuevo y pulsar "Confirmar reactivación" | N/A | `POST …/reactivation` (200) y "Habitación 409 disponible" / "Ya puede recibir reservas y estancias." con Estado "Disponible" y "Volver al inventario" | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Habitación que no está Inactiva | Abrir a mano la reactivación de la 409 ya disponible | 409 | Según la SPEC (FR-006): "No se puede marcar como disponible la habitación 409" / "La habitación está en estado Disponible. Solo se pueden reactivar habitaciones inactivas." | [x] Pass | 13-11-2026: comprobado con la 409 Reservada ("La habitación está en estado Reservada…") tras el arreglo F22 (PR #28, desplegado en la versión `1761e01`); antes la página mostraba "Habitación no encontrada" |
| 2 | Habitación inexistente | Abrir a mano la reactivación con un UUID que no existe | `b0000000-…-999` | "Habitación no encontrada" / "No existe ninguna habitación con ese identificador." | [x] Pass | 13-11-2026: comprobado |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consultar la baja vigente | `GET {BASE_URL}/api/rooms/{id de la 408}/decommission` | 408 Inactiva | 200 con la habitación, `decommissionId`, fecha, motivo y detalle | [x] Pass | 13-11-2026: comprobado |
| 2 | Sin usuario | `POST {BASE_URL}/api/rooms/{id}/reactivation` sin `X-User` | N/A | 400 `CURRENT_USER_REQUIRED` | [x] Pass | 13-11-2026: comprobado |
| 3 | Baja no reconocida | `decommissionId` `abc`, ausente o de otra baja | Uno por consulta | 400 `VALIDATION_ERROR`, "No se pudo identificar la baja de la habitación. Vuelve a abrir la confirmación.", `details.field` `decommissionId` | [x] Pass | 13-11-2026: comprobado |
| 4 | Reactivar | `POST …/reactivation` con el `decommissionId` vigente | 408 | 200 con `status` `Available` y `alreadyReactivated` false | [x] Pass | 13-11-2026: comprobado |
| 5 | Repetir | El mismo `POST` otra vez | 408 | 200 con `alreadyReactivated` true (sin otro cambio de estado) | [x] Pass | 13-11-2026: comprobado |
| 6 | Habitación inexistente | `POST {BASE_URL}/api/rooms/b0000000-0000-0000-0000-000000000999/reactivation` | N/A | 404 `ROOM_NOT_FOUND`, "No se encontró la habitación." | [x] Pass | 13-11-2026: comprobado |
| 7 | Baja de una habitación no inactiva | `GET {BASE_URL}/api/rooms/{id de la 105}/decommission` | 105 Disponible | 409 `ROOM_INVALID_STATE`, "La habitación no está Inactiva (estado actual: Disponible); no es posible marcarla como disponible." | [x] Pass | 13-11-2026: comprobado |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Liberación por cancelación | Con la 408 y la 409 Reservadas por `RES-TC-02`, publicar `reserva.lista-del-dia.actualizacion` con `updateType` `REMOVED` y `reservationRef` `RES-TC-02` | `RES-TC-02` | Las dos pasan de Reservada a Disponible; el historial registra "Marcar habitación como disponible" con Responsable "Sistema"; la reserva sale de Llegadas | [x] Pass | 13-11-2026: comprobado |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Fin de limpieza sin reserva para hoy: la habitación pasa de En limpieza a Disponible (comprobado en TC-011).
* **EC-002 (API)** : Tras reactivar, `GET …/decommission` responde 409 porque la habitación ya no está Inactiva.

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
| 16 | 16 | 0 | 0 | APROBADO |
