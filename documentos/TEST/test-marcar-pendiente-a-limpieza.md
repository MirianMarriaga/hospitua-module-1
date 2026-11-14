### Test Case Specification: Marcar Pendiente a Limpieza
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-pendiente-a-limpieza.md](../SPEC/spec-marcar-pendiente-a-limpieza.md) / [plan-marcar-pendiente-a-limpieza.md](../PLAN/plan-marcar-pendiente-a-limpieza.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-009-MarcarPendienteALimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que una habitación pase a Pendiente de limpieza en sus dos orígenes: el check-out (desde Ocupada, TC-005) y el fin de una reparación (desde Inhabilitada por reparaciones o Bloqueo técnico, TC-014). También verifica que aparezca en el panel de limpieza, que el inventario y el historial lo reflejen y que una llegada para esa habitación quede apartada hasta terminar la limpieza.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Pendiente a Limpieza | Specification Document | [documentos/SPEC/spec-marcar-pendiente-a-limpieza.md](../SPEC/spec-marcar-pendiente-a-limpieza.md) |
| Plan de Implementación de Marcar Pendiente a Limpieza | Plan Document | [documentos/PLAN/plan-marcar-pendiente-a-limpieza.md](../PLAN/plan-marcar-pendiente-a-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* No hay una acción propia de pantalla: el paso lo disparan el check-out y el fin de reparación.
* Para la llegada sobre una habitación Pendiente de limpieza se publicó un ADDED (`RES-TC-07`, 409) por RabbitMQ (ver TC-001).

##### Dependencies & Assumptions
* TC-005 (check-out de la 407) y TC-014 (fin de las reparaciones de la 407 y la 409).

##### Preconditions & Default Setup
* **Initial State** : 407 Ocupada; después, 407 y 409 Inhabilitadas por reparaciones.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : Recepción (`tc.recepcion`), Mantenimiento (`tc.mant1`, `tc.mant2`), Limpieza (`tc.limpieza1`) y Administración (`cabo`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de limpieza | Ir a `/login`, usuario y contraseña, "Ingresar" | `tc.limpieza1` | Abre `/limpieza` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Pendiente por check-out | Hacer el check-out de la 407 (TC-005) y abrir el panel de limpieza | 407 | La 407 aparece "Pendiente de limpieza" con "Iniciar limpieza"; el paso 5 del check-out dice "…quedó en estado Pendiente de limpieza y ya está en la bandeja del personal de limpieza." | [x] Pass | 13-11-2026: comprobado |
| 2 | Pendiente por fin de reparación | Confirmar el fin de la reparación de la 407 y de la 409 (TC-014) y abrir el panel de limpieza | 407, 409 | Las dos aparecen "Pendiente de limpieza"; el aviso de mantenimiento dice "…La habitación pasó a limpieza antes de volver a recibir huéspedes." | [x] Pass | 13-11-2026: comprobado (la 407 muestra la última limpieza "13-11-2026 22:00") |
| 3 | Inventario | Como Administrador, filtrar el inventario por la 407 | 407 | Estado "Pendiente de limpieza" y acciones "—" | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Llegada para una habitación pendiente | Publicar el ADDED de `RES-TC-07` para la 409 (Pendiente de limpieza) y buscarla en Llegadas | `RES-TC-07` | "Pendiente de limpieza: se apartará al terminar la limpieza" y "Check-in" deshabilitado | [x] Pass | 13-11-2026: comprobado |
| 2 | Se aparta al terminar | Limpiar la 409 | 409 | Al terminar la limpieza la 409 queda Reservada para `RES-TC-07` | [x] Pass | 13-11-2026: comprobado |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Estado tras el check-out | `GET {BASE_URL}/api/rooms/inventory/{id de la 407}` | N/A | 200 con `status` `PendingCleaning` | [x] Pass | 13-11-2026: comprobado |
| 2 | Panel de limpieza | `GET {BASE_URL}/api/cleaning/panel` con `X-User` | `tc.limpieza1` | 200 con la 407 en `rooms` y `status` `PendingCleaning` | [x] Pass | 13-11-2026: comprobado |
| 3 | Historial | `GET {BASE_URL}/api/room-state-history?roomId={id de la 407}` | N/A | Periodos "Pendiente de limpieza" con `sourceFlowName` "Registrar check-out" (actor "Prueba Recepción") y "Confirmar reparación finalizada" (actor "Prueba Mantenimiento Dos") | [x] Pass | 13-11-2026: comprobado |
| 4 | Sigue consultable para Módulo 2 | `GET {BASE_URL}/api/rooms/{id de la 407}` | N/A | 200 con los datos físicos de la habitación | [x] Pass | 13-11-2026: comprobado |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : La habitación sigue Pendiente de limpieza hasta que un miembro inicia su limpieza (`POST /api/cleaning/tasks`).
* **EC-002** : El paso a Pendiente de limpieza ocurre en la misma operación que el check-out o el fin de reparación.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |

--------------------------------------------------------------------------------

#### Hallazgos de la revisión (13-11-2026)

| ID | Tipo | Hallazgo | Corrección |
| :---: | ------ | ------ | ------ |
| H1 | Documento | La versión anterior esperaba "No disponible: Pendiente de limpieza" en Llegadas (el aviso real es "Pendiente de limpieza: se apartará al terminar la limpieza"), un "badge ámbar", "tarjetas" en el panel y `sourceFlow: "CHECK_OUT"` / `"CONFIRM_REPAIR_END"` en el historial (la API devuelve `sourceFlowName` con el nombre del caso de uso); usaba la 101 y la 102, que son datos del equipo | Pasos reescritos con lo que muestra la página y lo que responde la API |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 10 | 10 | 0 | 0 | APROBADO |
