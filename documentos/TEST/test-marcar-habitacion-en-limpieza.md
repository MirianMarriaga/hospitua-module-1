### Test Case Specification: Marcar Habitación en Limpieza
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-habitacion-en-limpieza.md](../SPEC/spec-marcar-habitacion-en-limpieza.md) / [plan-marcar-habitacion-en-limpieza.md](../PLAN/plan-marcar-habitacion-en-limpieza.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-010-MarcarHabitacionEnLimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que un miembro de limpieza inicie la limpieza de una habitación Pendiente de limpieza o Disponible desde el panel (`POST /api/cleaning/tasks`): el diálogo de confirmación, el paso a En limpieza, la pantalla de tarea activa (`/limpieza/tarea`), el historial y los rechazos (otra tarea abierta, habitación en un estado que no se puede limpiar, habitación inexistente, sin usuario).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Habitación en Limpieza | Specification Document | [documentos/SPEC/spec-marcar-habitacion-en-limpieza.md](../SPEC/spec-marcar-habitacion-en-limpieza.md) |
| Plan de Implementación de Marcar Habitación en Limpieza | Plan Document | [documentos/PLAN/plan-marcar-habitacion-en-limpieza.md](../PLAN/plan-marcar-habitacion-en-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* `ROOM_TASK_EXISTS` ("Otro compañero ya está limpiando esta habitación.") solo sale en una carrera entre dos miembros que pulsan a la vez: cuando la habitación ya está En limpieza, el backend responde primero `ROOM_INVALID_STATE`.

##### Dependencies & Assumptions
* TC-005 dejó la 407 Pendiente de limpieza. La 408 está Disponible y la 410 Reservada.

##### Preconditions & Default Setup
* **Initial State** : 407 Pendiente de limpieza; `tc.limpieza1` y `tc.limpieza2` sin tarea activa.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : dos cuentas de Limpieza (en la ejecución: `tc.limpieza1` y `tc.limpieza2`) y una de Gerencia para el historial.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de limpieza | Ir a `/login`, usuario y contraseña, "Ingresar" | `tc.limpieza1` | Abre `/limpieza` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Ubicar la habitación | Buscar la 407 en el panel | 407 | Fila "407 · Boutique · Pendiente de limpieza" con "Iniciar limpieza" | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Abrir la confirmación | Pulsar "Iniciar limpieza" en la 407 | 407 | Diálogo "Iniciar limpieza" / "La habitación 407 quedará a tu cargo hasta que termines la limpieza o la dejes para otro compañero." con "Cancelar" e "Iniciar limpieza" | [x] Pass | 13-11-2026: comprobado |
| 2 | Cancelar | Pulsar "Cancelar" | N/A | Se cierra el diálogo y la 407 sigue en el panel | [x] Pass | 13-11-2026: comprobado |
| 3 | Iniciar | Abrir de nuevo y pulsar "Iniciar limpieza" | N/A | `POST /api/cleaning/tasks` (201) y abre `/limpieza/tarea` | [x] Pass | 13-11-2026: comprobado |
| 4 | Tarea activa | Revisar la pantalla | N/A | "Tarea activa" / "Estás limpiando esta habitación. Cuando termines, confírmalo; si no puedes terminar, déjala para otro compañero."; "Habitación en limpieza" con 407, Boutique, "En limpieza", Empezaste, Tiempo transcurrido ("0 min"), A cargo de; el aviso "Si encuentras algo dañado, descríbelo al terminar…" y los botones "Liberar tarea" y "Confirmar fin" | [x] Pass | 13-11-2026: comprobado |
| 5 | Volver al panel | Abrir `/limpieza` | N/A | Vuelve a `/limpieza/tarea` | [x] Pass | 13-11-2026: comprobado |
| 6 | Historial | Consultar el historial de la 407 | 407 | Periodo "En limpieza" con `sourceFlowName` "Marcar habitación en limpieza" y el actor "Prueba Limpieza Uno" | [x] Pass | 13-11-2026: comprobado en `GET /api/room-state-history` |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar otra teniendo una tarea | Con la 407 a cargo, intentar iniciar otra habitación | N/A | La pantalla no lo permite: el panel lleva a `/limpieza/tarea` y no muestra otras habitaciones; la API rechaza (Sección D, paso 3) | [x] Pass | 13-11-2026: comprobado |
| 2 | Habitación que otro ya tomó | Pulsar "Iniciar limpieza" en un panel desactualizado, justo después de que otro miembro la tomó | N/A | "Otro compañero ya está limpiando esta habitación." | N/C | No comprobable: solo en una carrera de dos clics simultáneos (ver limitaciones) |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar | `POST {BASE_URL}/api/cleaning/tasks` con `X-User` y `{"roomId": …}` | 407 Pendiente de limpieza | 201 con `{"taskId": …}` | [x] Pass | 13-11-2026: comprobado |
| 2 | Estado en el inventario | `GET {BASE_URL}/api/rooms/inventory/{id de la 407}` | N/A | 200 con `status` `InCleaning` | [x] Pass | 13-11-2026: comprobado |
| 3 | Ya tiene una tarea | El mismo miembro, `roomId` de la 408 | `tc.limpieza1` | 409 `ACTIVE_TASK_EXISTS`, "Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra." | [x] Pass | 13-11-2026: comprobado |
| 4 | Habitación ya En limpieza | Otro miembro, `roomId` de la 407 | `tc.limpieza2` | 409 `ROOM_INVALID_STATE`, "La habitación está En limpieza; ahora no se puede limpiar.", `details.currentStatus` `InCleaning` | [x] Pass | 13-11-2026: comprobado |
| 5 | Habitación Reservada | `roomId` de la 410 | `tc.limpieza2` | 409 `ROOM_INVALID_STATE`, "La habitación está Reservada; ahora no se puede limpiar." | [x] Pass | 13-11-2026: comprobado |
| 6 | Sin usuario | Sin `X-User` | N/A | 400 `CURRENT_USER_REQUIRED` | [x] Pass | 13-11-2026: comprobado |
| 7 | Sin habitación o con id mal formado | Body `{}`; y `{"roomId": "abc"}` | N/A | 400 `VALIDATION_ERROR`: "Selecciona una habitación válida." (`{}`) y "Cuerpo de la petición inválido o mal formado." (`abc`), `details.field` `roomId` | [x] Pass | 13-11-2026: comprobado |
| 8 | Habitación inexistente | `roomId` `b0000000-0000-0000-0000-000000000999` | N/A | 404 `ROOM_NOT_FOUND`, "No se encontró la habitación." | [x] Pass | 13-11-2026: comprobado |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Una habitación Disponible también se puede limpiar (repaso): se comprobó con la 408 en TC-011.
* **EC-002 (API)** : `ROOM_TASK_EXISTS` queda para la carrera entre dos miembros; el índice único por habitación con tarea abierta lo garantiza.

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
| 18 | 17 | 0 | 1 | APROBADO PARCIAL (pasos no comprobables en la plataforma) |
