### Test Case Specification: Marcar Habitación Inhabilitada por Reparaciones
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-habitacion-inhabilitada-por-reparaciones.md](../SPEC/spec-marcar-habitacion-inhabilitada-por-reparaciones.md) / [plan-marcar-habitacion-inhabilitada-por-reparaciones.md](../PLAN/plan-marcar-habitacion-inhabilitada-por-reparaciones.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-013-MarcarHabitacionInhabilitadaPorReparaciones
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el reporte de averías (`POST /api/rooms/{roomId}/damage-reports`) desde una tarea de limpieza activa o desde el panel de mantenimiento, la transición inmediata de la habitación a DisabledForRepairs, la consulta del último reporte (`GET /api/rooms/{roomId}/damage-reports/latest`), y la emisión del aviso asíncrono hacia Módulo 2 con routing key `habitacion.mantenimiento` en el exchange `hospitua.events`.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Inhabilitada por Reparaciones | Specification Document | [documentos/SPEC/spec-marcar-habitacion-inhabilitada-por-reparaciones.md](../SPEC/spec-marcar-habitacion-inhabilitada-por-reparaciones.md) |
| Plan de Implementación de Marcar Inhabilitada por Reparaciones | Plan Document | [documentos/PLAN/plan-marcar-habitacion-inhabilitada-por-reparaciones.md](../PLAN/plan-marcar-habitacion-inhabilitada-por-reparaciones.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot, frontend React, base de datos PostgreSQL y broker RabbitMQ activos.

##### Dependencies & Assumptions
* Habitación en estado `InCleaning` o `Available` susceptible de reporte de daño.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado `InCleaning` con tarea asignada al usuario de limpieza (`limpiador1`).
* **Environment** : Ambiente de pruebas del equipo / Sandbox; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Personal de Limpieza (`limpiador1`), Personal de Mantenimiento (`mtorres`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de limpieza | Navegar a `/login` e ingresar credenciales de limpieza | Usuario: `limpiador1` | Carga el panel y redirige a la tarea activa `/limpieza/tarea` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Abrir diálogo de reporte de daño | En `/limpieza/tarea`, hacer clic en el botón "Reportar daño" | Botón "Reportar daño" | Se despliega el diálogo modal con el formulario de descripción de la avería | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Describir el daño encontrado | En el diálogo modal, ingresar: "Fuga importante en la tubería del lavamanos con filtración al suelo" | Descripción concreta | Se valida el campo y se habilita el botón "Inhabilitar habitación y reportar daño" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Confirmar reporte e inhabilitación | Hacer clic en "Inhabilitar habitación y reportar daño" y confirmar diálogo | Confirmación modal | Se envía `POST /api/rooms/{roomId}/damage-reports`, la habitación pasa a `DisabledForRepairs`, la tarea de limpieza se cierra y redirige a `/limpieza` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Verificar presencia en panel de mantenimiento | Cambiar de sesión a usuario de mantenimiento (`mtorres`) e ingresar a `/mantenimiento` | Usuario: `mtorres` | La habitación 101 aparece listada en el Panel de Mantenimiento con estado `DisabledForRepairs` y botón "Iniciar reparaciones" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Consultar informe de daño en mantenimiento | En `/mantenimiento`, hacer clic en "Ver detalle del daño" en la habitación 101 | Habitación 101 | Se despliega modal con la descripción reportada, autor y fecha/hora del incidente | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Descripción de daño vacía | Dejar el campo de descripción vacío y presionar confirmar | Campo vacío | Se resalta el campo con mensaje: "Describe el daño encontrado en la habitación." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Descripción que supera longitud máxima | Ingresar descripción de más de 500 caracteres | Texto > 500 caracteres | Mensaje de validación: "La descripción admite máximo 500 caracteres." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reporte exitoso de daño | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/damage-reports`<br>Headers: `X-User: limpiador1`, `Content-Type: application/json`<br>Body: `{"damageDescription": "Fuga en lavamanos"}` | Habitación en `InCleaning` | HTTP 201 Created con JSON `DamageReportCreatedDto` conteniendo `damageReportId`, `roomId`, `reportedBy: "limpiador1"`, `status: "DisabledForRepairs"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Consulta de último reporte de daño | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/damage-reports/latest`<br>Headers: (sin X-User obligatorio) | Hab. inhabilitada | HTTP 200 OK con JSON `DamageReportViewDto` conteniendo descripción, autor y fecha | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Error por falta de X-User en reporte | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/damage-reports`<br>Headers: (sin X-User)<br>Body: `{"damageDescription": "Fuga"}` | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Error por descripción en blanco | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/damage-reports`<br>Headers: `X-User: limpiador1`<br>Body: `{"damageDescription": "   "}` | Descripción vacía | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Error habitación inexistente | `POST {BASE_URL}/api/rooms/b0000000-0000-0000-0000-000000000999/damage-reports`<br>Headers: `X-User: limpiador1`<br>Body: `{"damageDescription": "Fuga"}` | UUID inexistente | HTTP 404 Not Found, `errorCode: "ROOM_NOT_FOUND"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 6 | Error reporte no encontrado en latest | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102/damage-reports/latest` | Hab. sin reportes | HTTP 404 Not Found, `errorCode: "DAMAGE_REPORT_NOT_FOUND"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Emisión de aviso SCHEDULED por reporte de daño | Ejecutar reporte de daño exitoso vía API o pantalla | Habitación 101 | Outbox publica mensaje en exchange `hospitua.events` con routing key `habitacion.mantenimiento` hacia `m2.habitacion.mantenimiento.queue` con `action: "SCHEDULED"`, `maintenanceId` (UUID del `DamageReport`), `roomId`, `categoryRoom`, `startDate = endDate = fecha_reporte`, más `messageId` y `sequenceNumber` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |
| 2 | Emisión de aviso SCHEDULED diario a las 00:00 | Ejecución programada de medianoche (`DailyMaintenanceNoticeService`) | Habitación en `DisabledForRepairs` | Se publica en Outbox aviso hacia `m2.habitacion.mantenimiento.queue` con routing key `habitacion.mantenimiento`, `action: "SCHEDULED"`, `maintenanceId` (UUID de su último `DamageReport`), `roomId`, `categoryRoom`, y `startDate = endDate = fecha_actual` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : Doble pulsación en botón "Inhabilitar habitación": el cliente desactiva el botón durante la mutación impidiendo reportes duplicados de avería.
* **EC-002 (API)** : Reporte sobre habitación en estado Inactive: el backend rechaza la petición con HTTP 409 `ROOM_INVALID_STATE` al no permitir registrar averías sobre habitaciones ya retiradas.

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
