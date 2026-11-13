### Test Case Specification: Programar Bloqueo Técnico para Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-programar-bloqueo-tecnico-para-habitacion.md](../SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md) / [plan-programar-bloqueo-tecnico-para-habitacion.md](../PLAN/plan-programar-bloqueo-tecnico-para-habitacion.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-015-ProgramarBloqueoTecnicoParaHabitacion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la programación de bloqueos técnicos para mantenimientos preventivos o correctivos (`POST /api/rooms/{roomId}/technical-blocks`), validando rangos de fechas (máximo 365 días), la consulta del informe actual (`GET /api/rooms/{roomId}/technical-blocks/current`), la verificación de conflictos de reservas con Módulo 2, la transición a TechnicalBlock si inicia hoy, y la publicación del aviso `habitacion.mantenimiento` hacia Módulo 2.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Programar Bloqueo Técnico | Specification Document | [documentos/SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md](../SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md) |
| Plan de Implementación de Programar Bloqueo Técnico | Plan Document | [documentos/PLAN/plan-programar-bloqueo-tecnico-para-habitacion.md](../PLAN/plan-programar-bloqueo-tecnico-para-habitacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot, frontend React, base de datos PostgreSQL y broker RabbitMQ activos. El diálogo `ScheduleTechnicalBlockDialog.tsx` se encuentra implementado en el frontend.

##### Dependencies & Assumptions
* Requiere comunicación con Módulo 2 para verificar disponibilidad de reservas en el rango de fechas programado.

##### Preconditions & Default Setup
* **Initial State** : Habitación 105 en estado `Available` sin reservas en las fechas seleccionadas.
* **Environment** : Ambiente de pruebas del equipo / Sandbox; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Personal de Mantenimiento (`mtorres`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de mantenimiento | Navegar a `/login`, ingresar credenciales de Mantenimiento | Usuario: `mtorres` | Carga el Panel de Mantenimiento en `/mantenimiento` | [ ] | |
| 2 | Abrir diálogo de programación | En `/mantenimiento`, hacer clic en el botón "Programar mantenimiento" | Botón de acción | Se despliega en pantalla el diálogo modal `ScheduleTechnicalBlockDialog` | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Diligenciar formulario de bloqueo | Seleccionar habitación 105, fecha de inicio (mañana), fecha de fin estimada (en 3 días) y justificación: "Mantenimiento preventivo de aire acondicionado" | Habitación 105, rango futuro | Los campos se validan en tiempo real y habilitan el botón "Programar bloqueo" | [ ] | |
| 2 | Confirmar programación | Hacer clic en "Programar bloqueo" y validar el diálogo de confirmación | Confirmación modal | Se envía `POST /api/rooms/{roomId}/technical-blocks`, se registra el informe en estado `Scheduled`, el diálogo se cierra y se presenta notificación de éxito | [ ] | |
| 3 | Programar con inicio hoy | Programar un bloqueo para la habitación 105 con fecha de inicio hoy (habitación en `Available`) | Fecha de inicio: hoy | La habitación transiciona de inmediato a `TechnicalBlock`, el informe queda en estado `Applied` y se emite el aviso a Módulo 2 | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Justificación vacía | Dejar el campo de justificación vacío | Justificación en blanco | Alerta en el formulario: "Ingresa la justificación del bloqueo técnico." | [ ] | |
| 2 | Fecha inicio posterior a fin | Seleccionar fecha de inicio posterior a la fecha de fin | Fechas invertidas | Mensaje de error: "La fecha inicial no puede ser posterior a la fecha final." | [ ] | |
| 3 | Fecha en el pasado | Ingresar una fecha de inicio anterior a la fecha actual del sistema | Fecha de ayer | Mensaje de error: "La fecha inicial no puede ser anterior a hoy." | [ ] | |
| 4 | Conflicto con reservas en Módulo 2 | Programar sobre una habitación y fechas con reservas confirmadas | Fechas con reserva | Mensaje de rechazo: "La habitación tiene reservas confirmadas en las fechas seleccionadas." | [ ] | |
| 5 | Solapamiento con otro mantenimiento | Programar sobre un rango que coincide con otro informe técnico | Rango solapado | Mensaje de conflicto: "La habitación ya tiene un mantenimiento programado en ese rango de fechas." | [ ] | |
| 6 | Caída de Módulo 2 | Intentar programar con el servicio de Módulo 2 no disponible | Módulo 2 apagado | Mensaje de alerta: "No fue posible verificar las reservas de la habitación con el módulo de reservas. Intenta más tarde." | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Programar bloqueo futuro exitoso | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/technical-blocks`<br>Headers: `X-User: mtorres`, `Content-Type: application/json`<br>Body: `{"startDate": "2026-11-20", "estimatedEndDate": "2026-11-23", "reason": "Pintura general"}` | Rango futuro válido | HTTP 201 Created con JSON `TechnicalBlockCreatedDto` conteniendo `technicalBlockId`, `status: "SCHEDULED"`, `startDate: "2026-11-20"` | [ ] | |
| 2 | Programar bloqueo con inicio hoy | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/technical-blocks`<br>Headers: `X-User: mtorres`<br>Body: `{"startDate": "2026-11-12", "estimatedEndDate": "2026-11-15", "reason": "Reparación urgente"}` | Inicio hoy, hab. Available | HTTP 201 Created con `{ "technicalBlockId": "...", "status": "APPLIED", "roomStatus": "TechnicalBlock" }` | [ ] | |
| 3 | Consulta de bloqueo activo actual | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/technical-blocks/current`<br>Headers: (sin X-User obligatorio) | Hab. con bloqueo | HTTP 200 OK con JSON `TechnicalBlockViewDto` conteniendo detalle del informe | [ ] | |
| 4 | Error sin encabezado X-User | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/technical-blocks`<br>Headers: (sin X-User) | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"` | [ ] | |
| 5 | Error por fechas inválidas | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/technical-blocks`<br>Headers: `X-User: mtorres`<br>Body: `{"startDate": "2026-11-25", "estimatedEndDate": "2026-11-20", "reason": "Revisión"}` | Fechas invertidas | HTTP 400 Bad Request, `errorCode: "INVALID_DATE_RANGE"` | [ ] | |
| 6 | Error por conflicto de reservas | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/technical-blocks`<br>Headers: `X-User: mtorres`<br>Body: `{"startDate": "2026-11-15", "estimatedEndDate": "2026-11-18", "reason": "Mantenimiento"}` | Con reservas en M2 | HTTP 409 Conflict, `errorCode: "RESERVATION_CONFLICT"` | [ ] | |
| 7 | Error por solapamiento técnico | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/technical-blocks`<br>Headers: `X-User: mtorres`<br>Body: (mismo rango de informe previo) | Rango solapado | HTTP 409 Conflict, `errorCode: "MAINTENANCE_CONFLICT"` | [ ] | |
| 8 | Error por caída de Módulo 2 | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000105/technical-blocks`<br>Headers: `X-User: mtorres` | M2 caído | HTTP 424 Failed Dependency, `errorCode: "MODULE_2_UNAVAILABLE"` | [ ] | |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Emisión de aviso por bloqueo técnico programado | Programar bloqueo técnico exitosamente | Habitación 105 | Outbox publica mensaje en exchange `hospitua.events` con routing key `habitacion.mantenimiento` hacia `m2.habitacion.mantenimiento.queue` notificando las fechas del mantenimiento a Módulo 2 | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Programación en el límite de 365 días: programar un bloqueo a 365 días debe ser aceptado; a 366 días debe ser rechazado con HTTP 400 `INVALID_DATE_RANGE`.
* **EC-002 (Interfaz)** : Cancelación defensiva del modal: presionar Escape o cerrar el modal con texto en justificación solicita confirmación antes de perder los datos.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
