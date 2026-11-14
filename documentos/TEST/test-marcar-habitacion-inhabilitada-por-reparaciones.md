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
Este caso de prueba verifica que una habitación quede Inhabilitada por reparaciones cuando se reporta un daño: desde "Reportar daño" en el panel de limpieza o de mantenimiento (habitaciones Disponibles, `POST /api/rooms/{roomId}/damage-reports`) y al confirmar el fin de una limpieza con un daño descrito (TC-011). También verifica el informe del daño en mantenimiento (`GET …/damage-reports/latest`), los rechazos y el aviso a Módulo 2.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Habitación Inhabilitada por Reparaciones | Specification Document | [documentos/SPEC/spec-marcar-habitacion-inhabilitada-por-reparaciones.md](../SPEC/spec-marcar-habitacion-inhabilitada-por-reparaciones.md) |
| Plan de Implementación de Marcar Habitación Inhabilitada por Reparaciones | Plan Document | [documentos/PLAN/plan-marcar-habitacion-inhabilitada-por-reparaciones.md](../PLAN/plan-marcar-habitacion-inhabilitada-por-reparaciones.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Ninguna: todos los pasos se pueden hacer en la plataforma publicada.

##### Dependencies & Assumptions
* La 409 está Disponible. TC-011 deja la 407 con un daño reportado al terminar la limpieza.

##### Preconditions & Default Setup
* **Initial State** : 409 Disponible; 104 Inactiva; 410 Reservada.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : Limpieza (`tc.limpieza1`, `tc.limpieza2`) y Mantenimiento (`tc.mant1`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de limpieza | Ir a `/login`, usuario y contraseña, "Ingresar" | `tc.limpieza1` | Abre `/limpieza` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Abrir el reporte | En la fila de la 409 pulsar "Reportar daño" | 409 | Diálogo "Reportar daño" / "La habitación 409 quedará fuera de servicio hasta que mantenimiento la repare.", "Descripción del daño *" con "0 / 500", "Cancelar" y "Reportar daño" | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reportar desde el panel | Escribir la descripción y pulsar "Reportar daño" | "Fuga en la tubería del lavamanos" | `POST …/damage-reports` (201) y "Daño reportado. La habitación 409 quedó fuera de servicio para que mantenimiento la repare."; la 409 sale del panel de limpieza | [x] Pass | 13-11-2026: comprobado |
| 2 | Reportar al terminar la limpieza | Confirmar el fin de la limpieza de la 407 con un daño (TC-011, Sección B, paso 6) | "Lámpara de la mesa de noche rota" | La 407 queda Inhabilitada por reparaciones | [x] Pass | 13-11-2026: comprobado |
| 3 | Ver en mantenimiento | Iniciar sesión como mantenimiento | `tc.mant1` | La 407 y la 409 aparecen en "Requieren intervención" con "Inhabilitada por reparaciones" y las acciones "Ver informe", "Programar mantenimiento" e "Iniciar reparaciones" | [x] Pass | 13-11-2026: comprobado |
| 4 | Ver el informe | Pulsar "Ver informe" en la 407 | 407 | "Reporte de daño · Habitación 407" / "Daño reportado en la habitación." con Qué se dañó, Reportado por ("Prueba Limpieza Dos"), Fecha y hora, y "Cerrar" | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Descripción vacía o solo espacios | Dejar el campo vacío; luego escribir espacios | N/A | "Reportar daño" sigue deshabilitado | [x] Pass | 13-11-2026: comprobado |
| 2 | Más de 500 caracteres | Escribir 501 caracteres | 501 caracteres | "501 / 500", "La descripción del daño es obligatoria (máximo 500 caracteres)." y "Reportar daño" deshabilitado | [x] Pass | 13-11-2026: comprobado |
| 3 | Cancelar | Pulsar "Cancelar" | N/A | Se cierra el diálogo sin enviar nada | [x] Pass | 13-11-2026: comprobado |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Estado resultante | `GET {BASE_URL}/api/rooms/inventory/{roomId}` | 407, 409 | 200 con `status` `DisabledForRepairs` | [x] Pass | 13-11-2026: comprobado |
| 2 | Último reporte | `GET {BASE_URL}/api/rooms/{id de la 409}/damage-reports/latest` | N/A | 200 con `roomNumber`, `damageDescription`, `authorName` y `reportDateTime` | [x] Pass | 13-11-2026: comprobado |
| 3 | Sin reporte | `GET …/damage-reports/latest` de una habitación sin daño | 102 | 404 `DAMAGE_REPORT_NOT_FOUND`, "Esta habitación no tiene un daño reportado." | [x] Pass | 13-11-2026: comprobado |
| 4 | Sin usuario | `POST …/damage-reports` sin `X-User` | N/A | 400 `CURRENT_USER_REQUIRED` | [x] Pass | 13-11-2026: comprobado |
| 5 | Descripción en blanco o de 501 caracteres | `{"damageDescription": "   "}`; y 501 caracteres | N/A | 400 `VALIDATION_ERROR`, "La descripción del daño es obligatoria (máximo 500 caracteres).", `details.field` `damageDescription` | [x] Pass | 13-11-2026: comprobado |
| 6 | Habitación inexistente | `roomId` `b0000000-0000-0000-0000-000000000999` | N/A | 404 `ROOM_NOT_FOUND`, "No se encontró la habitación." | [x] Pass | 13-11-2026: comprobado |
| 7 | Habitación en otro estado | Reportar en la 409 ya inhabilitada, en la 104 (Inactiva) y en la 410 (Reservada) | N/A | 409 `ROOM_INVALID_STATE`: "La habitación está Inhabilitada por reparaciones; ahora no se puede reportar un daño en ella." (y "…está Inactiva…", "…está Reservada…") con `details.currentStatus` | [x] Pass | 13-11-2026: comprobado |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Aviso a Módulo 2 | Revisar `m2.habitacion.mantenimiento.queue` tras cada reporte | 409, 407 | Un mensaje `habitacion.mantenimiento` por habitación con `action` `SCHEDULED`, `roomId`, `categoryRoom`, `maintenanceId`, `startDate` y `endDate` de hoy | [x] Pass | 13-11-2026: comprobado con la API de administración de RabbitMQ |
| 2 | Aviso diario de las 00:00 | Revisar la cola después de medianoche con una habitación aún inhabilitada | N/A | Nuevo `SCHEDULED` para la habitación inhabilitada | N/C | No comprobable en la ejecución: las dos habitaciones se repararon antes de medianoche |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : "Reportar daño" solo aparece en las habitaciones Disponibles; las demás se reportan al terminar su limpieza.
* **EC-002** : El botón queda deshabilitado mientras se envía el reporte, lo que evita reportes repetidos.

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
| H1 | Documento | La versión anterior ponía "Reportar daño" en la tarea activa de limpieza (está en el panel, para habitaciones Disponibles), un botón "Inhabilitar habitación y reportar daño", "Ver detalle del daño" (es "Ver informe") y los mensajes "Describe el daño encontrado en la habitación." y "La descripción admite máximo 500 caracteres."; decía que el reporte se hace sobre una habitación En limpieza (el backend lo rechaza) | Pasos reescritos con lo que muestra la página |
| H2 | Documento | La versión anterior marcaba como aprobado el aviso diario de las 00:00 sin poder verlo | Queda como no comprobable |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 18 | 17 | 0 | 1 | APROBADO PARCIAL (pasos no comprobables en la plataforma) |
