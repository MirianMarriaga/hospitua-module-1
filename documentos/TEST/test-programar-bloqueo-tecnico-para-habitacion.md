### Test Case Specification: Programar Bloqueo Técnico para Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-programar-bloqueo-tecnico-para-habitacion.md](../SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md) / [plan-programar-bloqueo-tecnico-para-habitacion.md](../PLAN/plan-programar-bloqueo-tecnico-para-habitacion.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-015-ProgramarBloqueoTecnicoParaHabitacion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica "Programar mantenimiento" desde el panel de mantenimiento (`POST /api/rooms/{roomId}/technical-blocks`): un mantenimiento futuro (queda `Scheduled`), uno que inicia hoy sobre una habitación Disponible (queda `Applied` y la habitación pasa a Bloqueo técnico), las validaciones del formulario y del backend (motivo, fechas, 90 días, 365 días, estado de la habitación, solapamiento) y el aviso `habitacion.mantenimiento` a Módulo 2.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Programar Bloqueo Técnico para Habitación | Specification Document | [documentos/SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md](../SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md) |
| Plan de Implementación de Programar Bloqueo Técnico para Habitación | Plan Document | [documentos/PLAN/plan-programar-bloqueo-tecnico-para-habitacion.md](../PLAN/plan-programar-bloqueo-tecnico-para-habitacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* El simulador de Módulo 2 siempre responde "sin reservas": el conflicto con reservas (`RESERVATION_CONFLICT`) y la caída de Módulo 2 (`MODULE_2_UNAVAILABLE`, "Reintentar") no se pueden provocar.

##### Dependencies & Assumptions
* La 409 quedó Pendiente de limpieza tras su reparación (TC-014); la 407 se limpió y quedó Disponible.

##### Preconditions & Default Setup
* **Initial State** : 409 Pendiente de limpieza sin mantenimientos; 407 Disponible.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : una cuenta de Mantenimiento (en la ejecución: `tc.mant1`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como mantenimiento | Ir a `/login`, usuario y contraseña, "Ingresar" | `tc.mant1` | Abre `/mantenimiento` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Abrir el diálogo | Pulsar "Programar mantenimiento" en la fila de la 409 | 409 | "Programar mantenimiento" / "Habitación 409 · Pendiente de limpieza. Desde ahora no se podrán reservar esas fechas."; "Motivo del mantenimiento *" (0 / 500), "Fecha de inicio *" ("Si inicia hoy, la habitación debe estar disponible."), "Fecha estimada de fin *" ("Máximo 90 días."), "Cancelar" y "Programar" | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Programar a futuro | Motivo, inicio 20-11-2026, fin 22-11-2026 y "Programar" | 409 | `POST …/technical-blocks` (201) y "Mantenimiento programado del 20-11-2026 al 22-11-2026 para la habitación 409."; "Con mantenimiento programado" sube a 1 y la fila muestra "20-11-2026 a 22-11-2026" | [x] Pass | 13-11-2026: comprobado |
| 2 | Ver lo programado | Pulsar "Ver informe" en la 409 | N/A | Motivo, Programado por, Fechas, Programado el y Situación "Programado" | [x] Pass | 13-11-2026: comprobado |
| 3 | Programar con inicio hoy | En la 407 (Disponible): motivo, inicio 13-11-2026, fin 15-11-2026 y "Programar" | 407 | "Mantenimiento iniciado. La habitación 407 queda fuera de servicio hasta el 15-11-2026 (fecha estimada)."; la 407 pasa a "Bloqueo técnico" en "Requieren intervención" y su informe dice Situación "En curso" | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Campos vacíos | Pulsar "Programar" sin llenar nada | N/A | "Escribe el motivo del mantenimiento (máximo 500 caracteres)." y "Elige una fecha válida (DD-MM-YYYY)." en las dos fechas | [x] Pass | 13-11-2026: comprobado |
| 2 | Inicio en el pasado | Inicio 12-11-2026 | N/A | "La fecha de inicio no puede ser anterior a hoy." | [x] Pass | 13-11-2026: comprobado |
| 3 | Inicio hoy con la habitación no disponible | Inicio 13-11-2026 en la 409 (Pendiente de limpieza) | N/A | "Habitación pendiente de limpieza hoy. Elige otra fecha de inicio." | [x] Pass | 13-11-2026: comprobado |
| 4 | Fechas invertidas | Inicio 25-11-2026, fin 22-11-2026 | N/A | "La fecha de fin no puede ser anterior a la fecha de inicio." | [x] Pass | 13-11-2026: comprobado |
| 5 | Más de 90 días | Inicio 25-11-2026, fin 01-03-2027 | N/A | "El mantenimiento no puede durar más de 90 días." | [x] Pass | 13-11-2026: comprobado |
| 6 | Más de 365 días de anticipación | Inicio 10-01-2028 | N/A | "Solo se puede programar con hasta 365 días de anticipación." | [x] Pass | 13-11-2026: comprobado |
| 7 | Motivo de más de 500 caracteres | 501 caracteres | N/A | "501 / 500" y "Escribe el motivo del mantenimiento (máximo 500 caracteres)." | [x] Pass | 13-11-2026: comprobado |
| 8 | Solapamiento | Programar en la 409 del 21-11-2026 al 23-11-2026 | N/A | El backend responde 409 y el diálogo muestra "Ya hay un mantenimiento programado o en curso del 20-11-2026 al 22-11-2026. Elige otras fechas." | [x] Pass | 13-11-2026: comprobado |
| 9 | Conflicto con reservas | Programar en fechas con reservas en Módulo 2 | N/A | "La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas." con la lista de reservas | N/C | No comprobable: el simulador de Módulo 2 no devuelve reservas |
| 10 | Módulo 2 caído | Programar con Módulo 2 sin responder | N/A | "Servicio de reservas temporalmente no disponible. No se programó el mantenimiento; inténtalo de nuevo." y el botón "Reintentar" | N/C | No comprobable sin apagar el simulador |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Inicio hoy por API | `POST {BASE_URL}/api/rooms/{id de la 407}/technical-blocks` con `{"startDate":"2026-11-13","estimatedEndDate":"2026-11-13","reason":…}` | 407 Disponible | 201 con `technicalBlockId` y `status` `Applied` | [x] Pass | 13-11-2026: comprobado |
| 2 | Mantenimiento actual | `GET {BASE_URL}/api/rooms/{id de la 409}/technical-blocks/current` | 409 | 200 con `roomNumber`, `reason`, `responsibleName`, `startDate`, `estimatedEndDate`, `reportDateTime` | [x] Pass | 13-11-2026: comprobado |
| 3 | Sin mantenimiento | `GET …/technical-blocks/current` de una habitación sin mantenimiento | N/A | 404 `TECHNICAL_BLOCK_NOT_FOUND`, "Esta habitación no tiene un mantenimiento programado ni en curso." | [x] Pass | 13-11-2026: comprobado |
| 4 | Sin usuario | `POST …/technical-blocks` sin `X-User` | N/A | 400 `CURRENT_USER_REQUIRED` | [x] Pass | 13-11-2026: comprobado |
| 5 | Reglas de fechas | Fin antes del inicio; inicio en el pasado; inicio a 366 días; fin a 91 días del inicio | N/A | 400 `INVALID_DATE_RANGE` con `details.rule` `END_BEFORE_START`, `START_IN_PAST`, `START_TOO_FAR` y `RANGE_TOO_LONG`, cada uno con su texto de la Sección C | [x] Pass | 13-11-2026: comprobado |
| 6 | Motivo en blanco | `"reason": "  "` | N/A | 400 `VALIDATION_ERROR`, `details.field` `reason` | [x] Pass | 13-11-2026: comprobado |
| 7 | Solapamiento | Rango que cruza el de la 409 | N/A | 409 `MAINTENANCE_CONFLICT` con `details.conflicts` (`maintenanceStart`, `maintenanceEnd`) | [x] Pass | 13-11-2026: comprobado |
| 8 | Estado de la habitación | Inicio hoy en la 409 (Pendiente de limpieza); cualquier fecha en la 104 (Inactiva) | N/A | 409 `ROOM_INVALID_STATE`: "Hoy la habitación está Pendiente de limpieza. Para empezar hoy debe estar disponible; elige una fecha de inicio posterior." y "La habitación está dada de baja; no se puede programar mantenimiento." | [x] Pass | 13-11-2026: comprobado |
| 9 | Habitación inexistente | `roomId` `b0000000-0000-0000-0000-000000000999` | N/A | 404 `ROOM_NOT_FOUND` | [x] Pass | 13-11-2026: comprobado |
| 10 | Conflicto con reservas o Módulo 2 caído | Con reservas vigentes o sin Módulo 2 | N/A | 409 `RESERVATION_CONFLICT` o 424 `MODULE_2_UNAVAILABLE` | N/C | No comprobable con el simulador de Módulo 2 |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Aviso al programar | Revisar `m2.habitacion.mantenimiento.queue` | 407 | `habitacion.mantenimiento` con `action` `SCHEDULED`, `maintenanceId`, `startDate` y `endDate` del bloqueo | [x] Pass | 13-11-2026: comprobado con la API de administración de RabbitMQ |
| 2 | Aviso diario de bloqueos vencidos | Revisar la cola después de medianoche | N/A | Nuevo `SCHEDULED` por cada bloqueo vencido | N/C | No comprobable dentro de la ejecución |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Según el plan, un inicio a 365 días y un fin a 90 días del inicio se aceptan; en la ejecución solo se comprobaron los rechazos (366 y 91) para no dejar bloqueos de prueba a un año.
* **EC-002** : Un mantenimiento futuro no cambia el estado de la habitación hasta su fecha de inicio.

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
| H1 | Documento | La versión anterior esperaba "seleccionar la habitación" en el diálogo (se abre desde la fila), un diálogo de confirmación adicional, "La habitación ya tiene un mantenimiento programado en ese rango de fechas." y que un rango de 366 días se rechace con el límite de 365 (el límite de 365 es para la anticipación del inicio; la duración máxima es de 90 días); además usaba la 105, que es dato del equipo | Pasos reescritos con los textos y las reglas reales |
| H2 | Documento | La versión anterior marcaba como aprobados el conflicto con reservas y la caída de Módulo 2 | Quedan como no comprobables en la plataforma |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 27 | 23 | 0 | 4 | APROBADO PARCIAL (pasos no comprobables en la plataforma) |
