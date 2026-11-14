### Test Case Specification: Dar de Baja Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-dar-de-baja-habitacion.md](../SPEC/spec-dar-de-baja-habitacion.md) / [plan-dar-de-baja-habitacion.md](../PLAN/plan-dar-de-baja-habitacion.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-020-DarDeBajaHabitacion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la baja de una habitación Disponible desde `/administracion/habitaciones/{roomId}/baja` (`POST /api/rooms/{roomId}/decommission`) en sus 4 pasos (Motivo, Confirmación, Verificación, Resultado): el motivo obligatorio, el detalle obligatorio solo con "Otro", la consulta de reservas vigentes a Módulo 2 antes de aplicar la baja, el paso a Inactiva, la consulta de la baja vigente y los rechazos (habitación no Disponible, inexistente, sin usuario, Módulo 2 sin respuesta).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Dar de Baja Habitación | Specification Document | [documentos/SPEC/spec-dar-de-baja-habitacion.md](../SPEC/spec-dar-de-baja-habitacion.md) |
| Plan de Implementación de Dar de Baja Habitación | Plan Document | [documentos/PLAN/plan-dar-de-baja-habitacion.md](../PLAN/plan-dar-de-baja-habitacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* En la plataforma publicada, Módulo 2 es un simulador que siempre responde "sin reservas": el rechazo por reservas vigentes no se puede provocar sin cambiar el simulador en el servidor.
* Cada baja deja la habitación Inactiva; usar habitaciones de prueba (en la ejecución: 409 por pantalla y 408 por API, creadas en TC-018). TC-008 las reactiva.

##### Dependencies & Assumptions
* TC-018 creó las habitaciones 408 y 409 en Disponible. La 101 no está Disponible.

##### Preconditions & Default Setup
* **Initial State** : 408 y 409 Disponibles; 101 en otro estado (en la ejecución: Pendiente de limpieza).
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : una cuenta de Administración (en la ejecución: `cabo`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Ir a `/login`, usuario y contraseña, "Ingresar" | Cuenta de Administración | Abre `/administracion/inventario` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Abrir la baja | En la fila de la 409 pulsar "Dar de baja" | 409 | Abre `/administracion/habitaciones/{roomId}/baja` con "Dar de baja habitación", los pasos 1 Motivo · 2 Confirmación · 3 Verificación · 4 Resultado y "1. Motivo de la baja · Habitación 409" con Habitación, Piso, Tipo y Estado | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Elegir el motivo | Abrir "Motivo de la baja", elegir y escribir el detalle; pulsar "Continuar" | Cierre definitivo de piso; "Prueba TC-020: retiro de la habitación de prueba" | Opciones: Remodelación permanente, Cierre definitivo de piso, Decisión administrativa y Otro; el detalle es "(opcional)" con "Máximo 500 caracteres." Pasa a "2. ¿Confirmas la baja de la habitación 409?" con el motivo, el detalle y el aviso "La habitación pasará a estado Inactiva y dejará de aparecer entre las habitaciones disponibles. Antes de aplicar la baja se comprobará que no tenga reservas vigentes en los próximos 365 días." | [x] Pass | 13-11-2026: comprobado |
| 2 | Volver atrás | Pulsar "Atrás" | N/A | Vuelve al paso 1 con el motivo y el detalle conservados | [x] Pass | 13-11-2026: comprobado |
| 3 | Confirmar la baja | Pulsar "Continuar" y luego "Confirmar baja" | N/A | Paso 3: "Comprobando reservas de la habitación 409…" / "No cierres esta ventana."; luego paso 4: "Habitación 409 dada de baja" / "Ya no aparece entre las habitaciones disponibles." con Estado "Inactiva", Motivo y Detalle, y "Volver al inventario" | [x] Pass | 13-11-2026: comprobado (`POST …/decommission` 200) |
| 4 | Verificar en el inventario | Filtrar el inventario por Estado "Inactiva" | N/A | La 409 aparece "Inactiva" con "Marcar como disponible" y ya no aparece al filtrar "Disponible" | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Sin motivo | Pulsar "Continuar" sin elegir motivo | N/A | "Selecciona un motivo para continuar." | [x] Pass | 13-11-2026: comprobado |
| 2 | "Otro" sin detalle | Elegir "Otro" y dejar el detalle vacío o con espacios | N/A | El campo pasa a llamarse "Motivo de la baja *" y aparece "Escribe el motivo de la baja." | [x] Pass | 13-11-2026: comprobado |
| 3 | Habitación que no está Disponible | Abrir a mano la baja de la 101, y la de la 409 ya inactiva | 101; 409 | "No se puede dar de baja la habitación 101" / "La habitación está en estado Pendiente de limpieza. Solo se pueden dar de baja habitaciones disponibles." (y lo mismo con "Inactiva" para la 409) | [x] Pass | 13-11-2026: comprobado |
| 4 | Reservas vigentes en Módulo 2 | Dar de baja una habitación con una reserva vigente en Módulo 2 | Reserva vigente | "No se puede dar de baja la habitación {n}", "La habitación tiene una reserva vigente. Reasígnalas a otra habitación y vuelve a intentar la baja. La habitación sigue disponible." y la lista de reservas | N/C | No comprobable en la plataforma: el simulador de Módulo 2 publicado no devuelve reservas |
| 5 | Módulo 2 sin respuesta | Confirmar la baja cuando Módulo 2 no responde a tiempo | N/A | Paso 4: "No se pudo completar la baja" / "No fue posible comprobar las reservas de la habitación {n} en este momento. No se aplicó ningún cambio y la habitación sigue disponible." con "Volver al inventario" y "Reintentar" | [x] Pass | 13-11-2026: ocurrió sin provocarlo en el primer intento con la 408 (424); el segundo intento funcionó |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Baja exitosa | `POST {BASE_URL}/api/rooms/{roomId}/decommission`, `X-User` de Administración, body `{"reasonCategory":"OTHER","reasonDetail":"Prueba TC-020 API"}` | 408 | 200 con `decommissionId`, `roomId`, `roomNumber`, `status` `Inactive`, `reasonCategory`, `reasonDetail` y `decommissionDateTime` | [x] Pass | 13-11-2026: comprobado |
| 2 | Consultar la baja vigente | `GET {BASE_URL}/api/rooms/{roomId}/decommission` | 408 | 200 con los datos de la habitación, `decommissionId`, fecha, motivo y detalle | [x] Pass | 13-11-2026: comprobado |
| 3 | Consultar la baja de una no inactiva | `GET {BASE_URL}/api/rooms/{id de la 105}/decommission` | 105 Disponible | 409 `ROOM_INVALID_STATE` con `details.currentStatus` | [x] Pass | 13-11-2026: comprobado |
| 4 | Sin usuario | El `POST` sin `X-User` | N/A | 400 `CURRENT_USER_REQUIRED` | [x] Pass | 13-11-2026: comprobado |
| 5 | Sin motivo o motivo inexistente | Body `{"reasonDetail":""}`, o `"reasonCategory":"REMODELING"` | N/A | 400 `VALIDATION_ERROR`, "Selecciona el motivo de la baja.", `details.field` `reasonCategory`. Valores válidos: `PERMANENT_REMODELING`, `FLOOR_CLOSURE`, `ADMINISTRATIVE_DECISION`, `OTHER` | [x] Pass | 13-11-2026: comprobado |
| 6 | "Otro" sin detalle | `{"reasonCategory":"OTHER","reasonDetail":"  "}` | N/A | 400 `VALIDATION_ERROR`, "Escribe el motivo de la baja.", `details.field` `reasonDetail` | [x] Pass | 13-11-2026: comprobado |
| 7 | Detalle de 501 caracteres | `reasonDetail` de 501 caracteres | N/A | 400 `VALIDATION_ERROR`, "El detalle admite máximo 500 caracteres." | [x] Pass | 13-11-2026: comprobado |
| 8 | Habitación inexistente | `POST {BASE_URL}/api/rooms/b0000000-0000-0000-0000-000000000999/decommission` con motivo válido | N/A | 404 `ROOM_NOT_FOUND`, "No se encontró la habitación." | [x] Pass | 13-11-2026: comprobado |
| 9 | Habitación no Disponible | `POST` a la 101 con motivo válido | 101 | 409 `ROOM_INVALID_STATE`, "No es posible dar de baja la habitación: está en estado Pendiente de limpieza y debe estar Disponible." | [x] Pass | 13-11-2026: comprobado |
| 10 | Reservas vigentes en Módulo 2 | `POST` a una habitación con reserva vigente | Reserva vigente | 409 `RESERVATION_CONFLICT` con la lista de reservas en `details` | N/C | No comprobable en la plataforma: el simulador de Módulo 2 no devuelve reservas |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : "Reintentar" tras la falla de Módulo 2 vuelve a consultar sin pedir otra vez el motivo.
* **EC-002 (API)** : La baja conserva el historial: la habitación sigue en el historial de estados con el periodo "Inactiva" abierto.

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
| 21 | 19 | 0 | 2 | APROBADO PARCIAL (pasos no comprobables en la plataforma) |
