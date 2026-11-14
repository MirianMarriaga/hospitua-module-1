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
Este caso de prueba verifica los flujos que retornan una habitación al estado Available: liberación por cancelación de reserva recibida desde RabbitMQ (`reserva.lista-del-dia.actualizacion` REMOVED), finalización de limpieza ordinaria sin reserva para hoy, o reactivación administrativa de una habitación dada de baja (`POST /api/rooms/{roomId}/reactivation`), garantizando la actualización del historial de estados y la habilitación comercial.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar como Disponible | Specification Document | [documentos/SPEC/spec-marcar-habitacion-como-disponible.md](../SPEC/spec-marcar-habitacion-como-disponible.md) |
| Plan de Implementación de Marcar como Disponible | Plan Document | [documentos/PLAN/plan-marcar-habitacion-como-disponible.md](../PLAN/plan-marcar-habitacion-como-disponible.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot, frontend React y base de datos PostgreSQL activos.

##### Dependencies & Assumptions
* Para liberación: habitación en `Reserved`. Para reactivación: habitación en `Inactive`.

##### Preconditions & Default Setup
* **Initial State** : Habitación 103 en `Reserved`. Habitación 104 en `Inactive` con baja registrada.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Administrador (`admin`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Navegar a `/login` e ingresar credenciales | Usuario: `admin` | Redirige al inventario en `/administracion/inventario` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Filtrar habitaciones inactivas | En `/administracion/inventario`, filtrar por estado "Inactiva" | Filtro: "Inactiva" | Se muestra la habitación 104 con botón "Reactivar" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Iniciar reactivación | Hacer clic en "Reactivar" en la fila de la habitación 104 | Habitación 104 | Navega a `/administracion/habitaciones/a0000000-0000-0000-0000-000000000104/reactivacion` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Revisar datos de baja previa | En `/administracion/habitaciones/:roomId/reactivacion`, consultar la confirmación | Datos de baja de hab. 104 | Se presenta el resumen de la habitación, los datos de la baja vigente (fecha, motivo, detalle) y el diálogo de confirmación de reactivación (sin campos de justificación adicional) | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Confirmar reactivación | Pulsar el botón "Confirmar reactivación" | Confirmación en pantalla | Se envía `POST /api/rooms/{roomId}/reactivation` con el `decommissionId` vigente, la habitación pasa a `Available` y muestra la pantalla de éxito con botón de retorno al inventario | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Verificar disponibilidad comercial | Consultar la fila de la habitación 104 en el inventario | Habitación 104 | Refleja badge de estado "Disponible" y vuelve a estar habilitada para reservas y asignaciones | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Identificador de baja inválido o no reconocido | Intentar reactivar enviando un `decommissionId` con formato erróneo o que no corresponde a la baja | Formato no UUID | Mensaje de error: "No se pudo identificar la baja de la habitación. Vuelve a abrir la confirmación." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Habitación no se encuentra inactiva | Intentar acceder a la reactivación de una habitación que ya está disponible | Hab. en `Available` | La pantalla muestra advertencia: "La habitación está en estado Disponible. Solo se pueden reactivar habitaciones inactivas." e impide continuar | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consultar datos de baja vigente | `GET {BASE_URL}/api/rooms/{roomId}/decommission`<br>Headers: (sin X-User obligatorio) | `roomId` hab. 104 (`Inactive`) | HTTP 200 OK con JSON `RoomDecommissionInfoDto` conteniendo motivo y fecha de la baja | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Reactivar habitación exitosamente | `POST {BASE_URL}/api/rooms/{roomId}/reactivation`<br>Headers: `X-User: admin`, `Content-Type: application/json`<br>Body: `{"decommissionId": "7d6c5b4a-3e2f-4a1b-9c8d-7e6f5a4b3c2d"}` | `roomId` hab. 104 e id de baja vigente | HTTP 200 OK con JSON `ReactivationResultDto` conteniendo `roomId`, `roomNumber`, `status: "Available"`, `alreadyReactivated: false` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Error por falta de X-User en reactivación | `POST {BASE_URL}/api/rooms/{roomId}/reactivation`<br>Headers: (sin X-User)<br>Body: `{"decommissionId": "7d6c5b4a-3e2f-4a1b-9c8d-7e6f5a4b3c2d"}` | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Error reactivar habitación no inactiva | `POST {BASE_URL}/api/rooms/{roomId}/reactivation`<br>Headers: `X-User: admin`<br>Body: `{"decommissionId": "b0000000-0000-0000-0000-000000000999"}` | Hab. en `Available` con baja ajena | HTTP 409 Conflict, `errorCode: "ROOM_INVALID_STATE"`, mensaje: "La habitación no está Inactiva (estado actual: Disponible); no es posible marcarla como disponible." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Reintento idempotente de reactivación | Repetir `POST {BASE_URL}/api/rooms/{roomId}/reactivation` con el mismo `decommissionId` de la baja ya revertida | Hab. recién reactivada | HTTP 200 OK con JSON conteniendo `status: "Available"` y `alreadyReactivated: true` sin duplicar periodos en historial | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 6 | Consulta de habitación reactivada en inventario | `GET {BASE_URL}/api/rooms/inventory/{roomId}` | Hab. reactivada | HTTP 200 OK con `status: "Available"` y `reservedByReservationRef: null` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Liberación por cancelación REMOVED desde RabbitMQ | Publicar mensaje en `hospitua.events` hacia `m1.reservas.diarias.queue` con routing key `reserva.lista-del-dia.actualizacion` y `action: "REMOVED"` | Cancelación de reserva para hab. 103 | La habitación 103 transiciona de `Reserved` a `Available`, blanquea `reservedByReservationRef` y cierra el periodo en `room_state_history` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Cancelación de reserva con habitación ya ocupada: si llega un REMOVED para una reserva que ya completó check-in (`Occupied`), el estado de la habitación no se altera y se audita el evento como conflicto.
* **EC-002 (Interfaz)** : Refresco inmediato en inventario: la reactivación actualiza de inmediato la vista del administrador sin dejar badges inconsistentes de inactividad.

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
| 15 | 15 | 0 | 0 | APROBADO |
