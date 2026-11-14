### Test Case Specification: Consultar Inventario de Habitaciones
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-inventario-habitaciones.md](../SPEC/spec-consultar-inventario-habitaciones.md) / [plan-consultar-inventario-habitaciones.md](../PLAN/plan-consultar-inventario-habitaciones.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-021-ConsultarInventarioHabitaciones
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la consulta del inventario de habitaciones: la pantalla `/administracion/inventario` (Administración, con acciones) y `/gerencia/inventario` (Gerencia, solo "Ver historial"), los filtros por número, tipo, piso y estado, el orden, la paginación de 10 en 10 y las consultas de API del inventario (`GET /api/rooms/inventory`, `GET /api/rooms/inventory/{roomId}`) y las que usa Módulo 2 (`GET /api/rooms`, `GET /api/rooms/{roomId}`), que excluyen las habitaciones dadas de baja.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Inventario | Specification Document | [documentos/SPEC/spec-consultar-inventario-habitaciones.md](../SPEC/spec-consultar-inventario-habitaciones.md) |
| Plan de Implementación de Consultar Inventario | Plan Document | [documentos/PLAN/plan-consultar-inventario-habitaciones.md](../PLAN/plan-consultar-inventario-habitaciones.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Solo lectura: ningún paso cambia datos.

##### Dependencies & Assumptions
* El número de habitaciones depende de lo registrado en la plataforma (en la ejecución: 15, entre ellas 103 y 104 inactivas y la 109 registrada por el equipo).

##### Preconditions & Default Setup
* **Initial State** : más de 10 habitaciones registradas, al menos una inactiva (104) y al menos dos dobles disponibles.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : una cuenta de Administración (en la ejecución: `cabo`) y una de Gerencia (`tc.gerencia`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Ir a `/login`, usuario y contraseña, "Ingresar" | Cuenta de Administración | Abre `/administracion/inventario` con el título "Inventario de habitaciones" y el botón "Registrar habitación" | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Iniciar sesión como Gerente | Cerrar sesión y entrar con la cuenta de Gerencia | Cuenta de Gerencia | Abre `/gerencia/inventario`; no aparece "Registrar habitación" y cada fila solo tiene "Ver historial" | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Revisar la tabla | Ver `/administracion/inventario` | N/A | Columnas Hab., Piso, Tipo, Capacidad, Tarifa base, Estado y Acciones; piso como "Piso 1", capacidad como "1 persona" / "2 personas", tarifa como "$ 120.000"; acciones "Editar" y "Dar de baja" en las Disponibles, "Marcar como disponible" en las Inactivas y "—" en las demás | [x] Pass | 13-11-2026: comprobado |
| 2 | Filtrar por número | Escribir en "Número" | `101` | La dirección pasa a `?roomNumber=101` y solo queda la fila de la 101 | [x] Pass | 13-11-2026: comprobado |
| 3 | Filtrar por tipo y estado | Elegir Tipo "Doble" y Estado "Disponible" | N/A | Las opciones de Tipo son Todos, Sencilla, Doble, Suite y Boutique; las de Estado, Todos y los 8 estados. Solo quedan las dobles disponibles (203 y 302) y la dirección guarda `categoryRoom=DOBLE&status=Available` | [x] Pass | 13-11-2026: comprobado |
| 4 | Revisar el orden | Abrir "Ordenar por" | N/A | Opciones: Número de habitación, Piso, Tipo, Capacidad, Tarifa (menor a mayor), Tarifa (mayor a menor) y Estado | [x] Pass | 13-11-2026: comprobado |
| 5 | Limpiar filtros | Pulsar "Limpiar filtros" | N/A | Se vacían los filtros y vuelve la lista completa ("Mostrando 1–10 de 15") | [x] Pass | 13-11-2026: comprobado |
| 6 | Paginación | Pulsar "Página siguiente" | 15 habitaciones | "Mostrando 11–15 de 15" en la página 2 | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda sin coincidencias | Escribir en "Número" | `999` | La tabla muestra "Ninguna habitación cumple los filtros seleccionados." con el botón "Limpiar filtros" | [x] Pass | 13-11-2026: comprobado |
| 2 | Parámetros inválidos en la dirección | Abrir `/administracion/inventario?floor=abc&status=Roto&page=-3` | Valores inválidos | Los valores inválidos se ignoran: la lista se muestra completa en la página 1, sin mensaje de error | [x] Pass | 13-11-2026: comprobado ("Mostrando 1–10 de 15") |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Inventario paginado | `GET {BASE_URL}/api/rooms/inventory?page=1&size=10` | N/A | 200 con `items`, `page`, `size`, `totalElements` y `totalPages`; cada ítem trae `roomId`, `status`, `roomNumber`, `floor`, `categoryRoom`, `maxCapacity`, `baseRate` y `version` | [x] Pass | 13-11-2026: comprobado |
| 2 | Detalle de una habitación | `GET {BASE_URL}/api/rooms/inventory/a0000000-0000-0000-0000-000000000101` | 101 | 200 con los mismos campos, incluida `version` | [x] Pass | 13-11-2026: comprobado |
| 3 | Detalle de una inactiva | `GET {BASE_URL}/api/rooms/inventory/a0000000-0000-0000-0000-000000000104` | 104 | 200 con `status` `Inactive` (el inventario sí muestra las dadas de baja) | [x] Pass | 13-11-2026: comprobado |
| 4 | Habitaciones vendibles para Módulo 2 | `GET {BASE_URL}/api/rooms?categoryRoom=DOBLE` | DOBLE | 200 con una lista de `id`, `roomNumber`, `categoryRoom` y `maxCapacity`, sin las inactivas | [x] Pass | 13-11-2026: comprobado (109, 202, 203, 302) |
| 5 | Una habitación para Módulo 2 | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101` | 101 | 200 con `{ "id", "roomNumber": "101", "categoryRoom": "SENCILLA", "maxCapacity": 1 }` | [x] Pass | 13-11-2026: comprobado |
| 6 | Inactiva para Módulo 2 | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000104` | 104 | 404 `ROOM_NOT_FOUND`, "No se encontró la habitación." | [x] Pass | 13-11-2026: comprobado |
| 7 | Filtros inválidos | `GET {BASE_URL}/api/rooms/inventory?floor=-1`, luego `?size=500` | Uno por consulta | 400 `VALIDATION_ERROR`, "Revisa los filtros aplicados.", `details.field` = `floor` / `size` | [x] Pass | 13-11-2026: comprobado |
| 8 | Habitación inexistente | `GET {BASE_URL}/api/rooms/inventory/b0000000-0000-0000-0000-000000000999` | UUID inexistente | 404 `ROOM_NOT_FOUND` | [x] Pass | 13-11-2026: comprobado |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Una habitación dada de baja responde 404 en `GET /api/rooms/{roomId}` (Módulo 2) y 200 en `GET /api/rooms/inventory/{roomId}` (inventario).
* **EC-002 (Interfaz)** : En `/gerencia/inventario` no aparecen "Registrar habitación", "Editar", "Dar de baja" ni "Marcar como disponible".
* **EC-003 (Interfaz)** : Un piso negativo escrito en el filtro "Piso" no se envía al backend: la lista sigue completa.

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
| H1 | Documento | La versión anterior esperaba una "barra de búsqueda superior", un selector de tamaño de página, el mensaje "No se encontraron habitaciones con los filtros aplicados.", "Revisa los filtros aplicados." en pantalla, el campo `total`, el botón "Nueva habitación" y los usuarios `admin` / `gerente`. La página usa el filtro "Número", páginas fijas de 10, "Ninguna habitación cumple los filtros seleccionados." y "Registrar habitación"; los parámetros inválidos se ignoran | Pasos reescritos con lo que muestra la página |
| H2 | Documento | El estado inicial "14 habitaciones de la semilla, 12 disponibles" no corresponde a la plataforma publicada (15 habitaciones, varios estados) | Precondiciones escritas sin depender de cantidades exactas |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 18 | 18 | 0 | 0 | APROBADO |
