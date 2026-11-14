### Test Case Specification: Consultar Historial de Estados
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-historial-de-estados.md](../SPEC/spec-consultar-historial-de-estados.md) / [plan-consultar-historial-de-estados.md](../PLAN/plan-consultar-historial-de-estados.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-022-ConsultarHistorialDeEstados
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la consulta de solo lectura del historial de estados de las habitaciones (`GET /api/room-state-history`, pantalla `/gerencia/historial`): la paginación de 15 en 15, los filtros por habitación, tipo, estado y rango de fechas (`from`, `to`), el orden "Más antiguo primero / Más reciente primero", la columna Responsable ("Sistema" en las transiciones automáticas) y los rechazos de parámetros inválidos. La consulta no exige el encabezado `X-User`.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Historial de Estados | Specification Document | [documentos/SPEC/spec-consultar-historial-de-estados.md](../SPEC/spec-consultar-historial-de-estados.md) |
| Plan de Implementación de Historial de Estados | Plan Document | [documentos/PLAN/plan-consultar-historial-de-estados.md](../PLAN/plan-consultar-historial-de-estados.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Solo lectura: ninguna acción de este caso cambia el estado de una habitación.
* La interfaz no muestra el flujo de origen (`sourceFlow`) ni una duración calculada; el flujo de origen solo viaja en la respuesta de la API.

##### Dependencies & Assumptions
* Los periodos se generan con las transiciones de los demás casos de uso (check-in, limpieza, daño, etc.).

##### Preconditions & Default Setup
* **Initial State** : al menos una habitación con 3 o más periodos (en la ejecución: la 101, con 7 periodos) y una habitación ocupada (la 109).
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : una cuenta del área Gerencia (en la ejecución: `tc.gerencia`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Gerente | Ir a `/login`, escribir usuario y contraseña y pulsar "Ingresar" | Cuenta de Gerencia | Abre `/gerencia/inventario`; el menú lateral muestra "Inventario" e "Historial de estados" | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Abrir el historial | Pulsar "Historial de estados" en el menú lateral | N/A | Abre `/gerencia/historial` con el título "Historial de estados", el subtítulo "Consulta por cuánto tiempo estuvo cada habitación en cada estado." y las columnas Hab., Tipo, Estado, Inicio, Fin y Responsable | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 3 | Llegar desde el inventario | En "Inventario", en la fila de la 203 pulsar "Ver historial" | `203` | Abre `/gerencia/historial?roomId=…&roomNumber=203` con "Habitación" = 203 y solo filas de la 203 | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Revisar filas cerradas y abiertas | Revisar la tabla sin filtros | N/A | Inicio y Fin en formato `DD-MM-YYYY HH:MM`; el periodo actual dice "En curso" en Fin; Responsable muestra el nombre de la persona o "Sistema" | [x] Pass | 13-11-2026: comprobado (por ejemplo, 104 "Inactiva", Fin "En curso", Responsable "Sistema") |
| 2 | Paginación | Pulsar "Página siguiente" dos veces | 45 periodos | "Mostrando 1–15 de 45", luego "Mostrando 16–30 de 45" y "Mostrando 31–45 de 45"; en la última página "Página siguiente" queda deshabilitado | [x] Pass | 13-11-2026: comprobado con 45 periodos |
| 3 | Filtrar por número de habitación | Escribir el número en "Habitación" (no hay botón "Filtrar": se aplica al dejar de escribir) | `101` | La dirección pasa a `?roomNumber=101`, la lista vuelve a la página 1 y solo aparecen filas de la 101 ("Mostrando 1–7 de 7") | [x] Pass | 13-11-2026: comprobado |
| 4 | Filtrar por estado y rango | Estado "Ocupada", Desde y Hasta | Desde 10-11-2026, Hasta 13-11-2026 | Solo los periodos en Ocupada que se cruzan con el rango | [x] Pass | 13-11-2026: comprobado ("109 · Ocupada · En curso") |
| 5 | Ordenar más reciente primero | Con la 101 filtrada, pulsar el botón "Más antiguo primero" | N/A | El botón cambia a "Más reciente primero", la dirección agrega `direction=desc` y la primera fila es el periodo más reciente ("Pendiente de limpieza · En curso") | [x] Pass | 13-11-2026: comprobado |
| 6 | Limpiar filtros | Pulsar "Limpiar filtros" | N/A | Se vacían los filtros, el botón vuelve a "Más antiguo primero" y la lista vuelve a "Mostrando 1–15 de 45" | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Rango de fechas invertido | "Desde" posterior a "Hasta" | Desde 31-10-2026, Hasta 01-10-2026 | Junto a las fechas aparece "La fecha "Desde" no puede ser posterior a "Hasta"." y no se envía la consulta con el rango inválido | [x] Pass | 13-11-2026: comprobado. Observación: la tabla sigue mostrando el resultado de la consulta anterior (ver hallazgo H2) |
| 2 | Filtros sin resultados | Habitación 101 y Estado "Bloqueo técnico" | `?roomNumber=101&status=TechnicalBlock` | La tabla muestra "No se encontraron resultados" | [x] Pass | 13-11-2026: comprobado |
| 3 | Parámetro inválido en la dirección | Abrir a mano `/gerencia/historial?status=Roto` y `/gerencia/historial?page=-1` | Valores inválidos | El filtro inválido se ignora: la lista se muestra sin ese filtro, en la página 1, sin mensaje de error | [x] Pass | 13-11-2026: comprobado ("Mostrando 1–15 de 45") |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consulta sin filtros | `GET {BASE_URL}/api/room-state-history?page=1` (sin `X-User`) | N/A | 200 con `items` (máximo 15), `page` 1, `size` 15, `totalElements` y `totalPages`; cada ítem trae `roomId`, `roomNumber`, `categoryRoom`, `status`, `startDateTime`, `endDateTime` (ausente si sigue abierto), `actorName` (ausente si fue "Sistema"), `sourceFlow` y `sourceFlowName` | [x] Pass | 13-11-2026: comprobado (`totalElements` 45, `totalPages` 3) |
| 2 | Filtros combinados | `GET {BASE_URL}/api/room-state-history?roomNumber=101&status=PendingCleaning&from=2026-11-01&to=2026-11-30` | Número, estado y rango | 200 solo con periodos de la 101 en `PendingCleaning` dentro del rango | [x] Pass | 13-11-2026: comprobado |
| 3 | Filtro por identificador | `GET {BASE_URL}/api/room-state-history?roomId=a0000000-0000-0000-0000-000000000203` | `roomId` de la 203 | 200 solo con periodos de la 203 | [x] Pass | 13-11-2026: comprobado |
| 4 | Orden descendente | `GET {BASE_URL}/api/room-state-history?direction=desc` | N/A | 200 con `startDateTime` de mayor a menor | [x] Pass | 13-11-2026: comprobado |
| 5 | Orden por fin | `GET {BASE_URL}/api/room-state-history?sort=endDateTime&direction=desc` | N/A | 200; primero los periodos abiertos y después por fin de mayor a menor (la interfaz no usa este orden) | [x] Pass | 13-11-2026: comprobado |
| 6 | Rango invertido | `GET {BASE_URL}/api/room-state-history?from=2026-11-20&to=2026-11-10` | Fechas invertidas | 400 `INVALID_DATE_RANGE`, mensaje "La fecha "Desde" no puede ser posterior a "Hasta".", `details.rule` = `FROM_AFTER_TO` | [x] Pass | 13-11-2026: comprobado |
| 7 | Valores fuera de lo permitido | `GET` con `page=0`, `page=-1`, `sort=fecha`, `direction=arriba` y `status=Roto` (uno por consulta) | Uno por consulta | 400 `VALIDATION_ERROR` con el campo en `details.field` (`page`, `sort`, `direction`, `status`) | [x] Pass | 13-11-2026: comprobado. Con `status=Roto` el mensaje es "Tipo de dato inválido para el campo: status" en vez de "Revisa los filtros aplicados." (hallazgo H3) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Periodo abierto: `endDateTime` no viaja en la respuesta y la interfaz muestra "En curso".
* **EC-002 (API)** : Continuidad: el inicio de un periodo coincide con el fin del anterior de la misma habitación (comprobado en la 101: 11-11-2026 23:03 → 23:03).
* **EC-003** : Solo lectura: consultar no cambia `totalElements` ni el estado de ninguna habitación.

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
| H1 | Documento | La versión anterior esperaba columnas "Duración calculada" y "Flujo de origen", un botón "Filtrar", los mensajes "No se encontraron registros de historial para los filtros seleccionados." y "Revisa los filtros aplicados." en pantalla, el campo `total` y el usuario `gerente`. Nada de eso existe en la plataforma | Pasos reescritos con lo que muestra la página |
| H2 | Plataforma (S) | Con el rango invertido, la tabla sigue mostrando el resultado de la consulta anterior junto al aviso | Propuesta: ocultar la tabla mientras el rango sea inválido. No se cambió la plataforma |
| H3 | Plataforma (S) | Con un estado inexistente, la API responde "Tipo de dato inválido para el campo: status" en vez del mensaje común "Revisa los filtros aplicados." | Propuesta: unificar el mensaje. No se cambió la plataforma |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 19 | 19 | 0 | 0 | APROBADO (con observaciones H2 y H3) |
