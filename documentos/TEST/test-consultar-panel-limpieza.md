### Test Case Specification: Consultar Panel de Limpieza
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-panel-limpieza.md](../SPEC/spec-consultar-panel-limpieza.md) / [plan-consultar-panel-limpieza.md](../PLAN/plan-consultar-panel-limpieza.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-012-ConsultarPanelLimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el panel de limpieza (`/limpieza`, `GET /api/cleaning/panel`): el conteo de habitaciones Pendientes de limpieza y Disponibles, la tabla con número, tipo, estado, última limpieza y acciones ("Iniciar limpieza" en las dos, "Reportar daño" solo en las Disponibles), la búsqueda por número y el envío directo a la tarea activa cuando el miembro ya está limpiando una habitación.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Panel de Limpieza | Specification Document | [documentos/SPEC/spec-consultar-panel-limpieza.md](../SPEC/spec-consultar-panel-limpieza.md) |
| Plan de Implementación de Consultar Panel de Limpieza | Plan Document | [documentos/PLAN/plan-consultar-panel-limpieza.md](../PLAN/plan-consultar-panel-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* El error de conexión (backend caído) no se puede provocar en la plataforma publicada sin detener el servidor.
* El panel no tiene filtro por piso ni botón de recarga: se actualiza solo cada minuto y la búsqueda es por número de habitación.

##### Dependencies & Assumptions
* TC-005 dejó la 407 Pendiente de limpieza (check-out de `RES-TC-01`).

##### Preconditions & Default Setup
* **Initial State** : 407 Pendiente de limpieza; 408 y 409 Disponibles; el miembro de limpieza sin tarea activa.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : dos cuentas de Limpieza (en la ejecución: `tc.limpieza1` y `tc.limpieza2`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de limpieza | Ir a `/login`, usuario y contraseña, "Ingresar" | Cuenta de Limpieza sin tarea activa | Abre `/limpieza`: "Panel de limpieza", "Bienvenido, {nombre}", "Habitaciones por limpiar y disponibles" | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Conteo por estado | Revisar las tarjetas superiores | N/A | "Pendientes de limpieza" y "Disponibles" con su número | [x] Pass | 13-11-2026: comprobado (2 y 11) |
| 2 | Tabla de habitaciones | Revisar la sección "Habitaciones" | N/A | "La lista se actualiza sola cada minuto"; columnas Hab., Tipo, Estado, Última limpieza y Acciones; primero las Pendientes de limpieza y luego las Disponibles; "Sin registro" cuando no hay limpieza anterior | [x] Pass | 13-11-2026: comprobado |
| 3 | Acciones por estado | Revisar los botones de cada fila | 407 y 408 | 407 (Pendiente de limpieza): solo "Iniciar limpieza"; 408 (Disponible): "Reportar daño" e "Iniciar limpieza" | [x] Pass | 13-11-2026: comprobado |
| 4 | Buscar por número | Escribir en "Buscar habitación por número…" | `407` | Solo queda la fila de la 407 | [x] Pass | 13-11-2026: comprobado |
| 5 | Envío a la tarea activa | Con una limpieza iniciada (TC-010), abrir `/limpieza` | N/A | Abre `/limpieza/tarea` | [x] Pass | 13-11-2026: comprobado |
| 6 | Habitación tomada no aparece | Con la 407 En limpieza por `tc.limpieza1`, abrir el panel con `tc.limpieza2` | 407 | La 407 no aparece en el panel del otro miembro | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda sin coincidencias | Buscar un número que no está en el panel | `999` | "No se encontró la habitación 999" | [x] Pass | 13-11-2026: comprobado |
| 2 | Panel vacío | Abrir el panel sin habitaciones Pendientes ni Disponibles | N/A | "No hay habitaciones pendientes de limpieza ni disponibles" | N/C | No comprobable: el inventario publicado siempre tiene habitaciones disponibles (texto revisado en `CleaningPanelPage.tsx`) |
| 3 | Error de conexión | Abrir el panel con el backend detenido | N/A | Aviso de error con el texto común de red | N/C | No comprobable sin detener el servidor publicado |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Panel sin tarea activa | `GET {BASE_URL}/api/cleaning/panel` con `X-User` | `tc.limpieza1` | 200 con `rooms` (`roomId`, `roomNumber`, `categoryRoom`, `status`, `lastCleaningDateTime` si existe); sin `activeTask` | [x] Pass | 13-11-2026: comprobado |
| 2 | Panel con tarea activa | El mismo `GET` con la 407 En limpieza | `tc.limpieza1` | 200 con `activeTask` (`taskId`, `startDateTime`, `room` con `status` `InCleaning`) y `rooms` vacío | [x] Pass | 13-11-2026: comprobado |
| 3 | Sin usuario | `GET {BASE_URL}/api/cleaning/panel` sin `X-User` | N/A | 400 `CURRENT_USER_REQUIRED`, "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 13-11-2026: comprobado |
| 4 | Usuario inexistente | `X-User: noexiste` | N/A | 400 `CURRENT_USER_REQUIRED` | [x] Pass | 13-11-2026: comprobado |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : La búsqueda se hace en el navegador sobre la lista recibida (no hay parámetro de búsqueda en la API).
* **EC-002** : Las habitaciones En limpieza, Ocupadas, Reservadas, Inactivas o fuera de servicio no aparecen en el panel.

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
| H1 | Documento | La versión anterior describía tarjetas con piso, un filtro "Piso 1" / "Todos los pisos" y un botón de recarga que no existen; esperaba "No hay habitaciones pendientes de limpieza." y "No fue posible cargar el panel de limpieza. Intenta de nuevo."; y en la API un campo `pendingRooms` y `activeTask: null` (el campo es `rooms` y `activeTask` no viaja cuando no hay tarea) | Pasos reescritos con lo que muestra la página y lo que responde la API |
| H2 | Documento | La versión anterior marcaba como aprobados el panel vacío y el error de conexión, que no se pueden provocar en la plataforma | Quedan como no comprobables |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 14 | 12 | 0 | 2 | APROBADO PARCIAL (pasos no comprobables en la plataforma) |
