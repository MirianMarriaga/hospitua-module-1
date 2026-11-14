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
Este caso de prueba verifica el panel de mantenimiento (`/mantenimiento`, `GET /api/maintenance/panel`): el conteo de habitaciones Inhabilitadas por reparaciones, En bloqueo técnico (con las vencidas) y Con mantenimiento programado; la tabla dividida en "Requieren intervención" y "Demás habitaciones" con sus acciones ("Ver informe", "Programar mantenimiento", "Iniciar reparaciones", "Reportar daño"); la búsqueda por número; los informes de daño y de mantenimiento; y el envío directo a la tarea activa.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Panel de Mantenimiento | Specification Document | [documentos/SPEC/spec-consultar-panel-mantenimiento.md](../SPEC/spec-consultar-panel-mantenimiento.md) |
| Plan de Implementación de Consultar Panel de Mantenimiento | Plan Document | [documentos/PLAN/plan-consultar-panel-mantenimiento.md](../PLAN/plan-consultar-panel-mantenimiento.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* El panel vacío y el error de conexión no se pueden provocar en la plataforma publicada.

##### Dependencies & Assumptions
* TC-013 dejó la 407 y la 409 Inhabilitadas por reparaciones. Las 201 y 202 (datos del equipo) estaban en Bloqueo técnico; solo se consultaron.

##### Preconditions & Default Setup
* **Initial State** : 407 y 409 Inhabilitadas por reparaciones; `tc.mant1` sin tarea activa.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : dos cuentas de Mantenimiento (en la ejecución: `tc.mant1` y `tc.mant2`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como mantenimiento | Ir a `/login`, usuario y contraseña, "Ingresar" | `tc.mant1` | Abre `/mantenimiento`: "Panel de mantenimiento", "Bienvenido, {nombre}", "Reparaciones y mantenimientos programados" | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Conteo | Revisar las tarjetas superiores | N/A | "Inhabilitadas por reparaciones", "En bloqueo técnico · {n} vencido" y "Con mantenimiento programado" | [x] Pass | 13-11-2026: comprobado (2, 2 con 1 vencido, 0) |
| 2 | Tabla | Revisar la sección "Habitaciones" | N/A | "La lista se actualiza sola cada minuto"; columnas Hab., Tipo, Estado, Mantenimiento programado ("Sin programar" o "dd-mm-aaaa a dd-mm-aaaa") y Acciones; grupos "Requieren intervención" (inhabilitadas y en bloqueo técnico, con "(vencido)" cuando pasó la fecha estimada) y "Demás habitaciones" | [x] Pass | 13-11-2026: comprobado |
| 3 | Acciones por estado | Revisar los botones | 407, 201, 102, 109 | Inhabilitada: "Ver informe", "Programar mantenimiento", "Iniciar reparaciones"; Bloqueo técnico: las mismas; Disponible: "Reportar daño" y "Programar mantenimiento"; Ocupada, Reservada o Pendiente de limpieza: solo "Programar mantenimiento" | [x] Pass | 13-11-2026: comprobado |
| 4 | Informe de daño | Pulsar "Ver informe" en la 407 | 407 | "Reporte de daño · Habitación 407" con Qué se dañó, Reportado por y Fecha y hora | [x] Pass | 13-11-2026: comprobado |
| 5 | Informe de mantenimiento | Pulsar "Ver informe" en una habitación en bloqueo técnico | 201 | "Mantenimiento · Habitación 201" / "Mantenimiento programado o en curso de la habitación." con Motivo, Programado por, Fechas, Programado el y Situación "En curso" | [x] Pass | 13-11-2026: comprobado |
| 6 | Buscar por número | Escribir en "Buscar habitación por número…" | `409` | Solo queda la fila de la 409 | [x] Pass | 13-11-2026: comprobado |
| 7 | Envío a la tarea activa | Con una reparación iniciada (TC-014), abrir `/mantenimiento` | N/A | Abre `/mantenimiento/tarea` | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda sin coincidencias | Buscar un número que no existe | `999` | "No se encontró la habitación 999" | [x] Pass | 13-11-2026: comprobado |
| 2 | Error de conexión | Abrir el panel con el backend detenido | N/A | Aviso de error con el texto común de red | N/C | No comprobable sin detener el servidor publicado |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Panel sin tarea activa | `GET {BASE_URL}/api/maintenance/panel` con `X-User` | `tc.mant1` | 200 con `rooms` (estado, `overdue`, `hasOpenTask` y el mantenimiento programado cuando lo hay); sin `activeTask` | [x] Pass | 13-11-2026: comprobado |
| 2 | Panel con tarea activa | El mismo `GET` con la 409 en reparación | `tc.mant1` | 200 con `activeTask` (`taskId`, `startDateTime`, `room` con `hasOpenTask` true) y `rooms` vacío | [x] Pass | 13-11-2026: comprobado |
| 3 | Sin usuario | `GET {BASE_URL}/api/maintenance/panel` sin `X-User` | N/A | 400 `CURRENT_USER_REQUIRED`, "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 13-11-2026: comprobado |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : El panel de mantenimiento muestra todas las habitaciones activas (no solo las que requieren intervención), porque desde él se programan mantenimientos.
* **EC-002 (API)** : No hay permisos por área: la cuenta solo debe existir y estar activa (regla base 14 del plan).

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
| 13 | 12 | 0 | 1 | APROBADO PARCIAL (pasos no comprobables en la plataforma) |
