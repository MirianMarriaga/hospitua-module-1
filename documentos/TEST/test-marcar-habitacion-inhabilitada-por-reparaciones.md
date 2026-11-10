### Test Case Specification: Marcar Habitación Inhabilitada por Reparaciones
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-habitacion-inhabilitada-por-reparaciones.md](../SPEC/spec-marcar-habitacion-inhabilitada-por-reparaciones.md) / [plan-marcar-habitacion-inhabilitada-por-reparaciones.md](../PLAN/plan-marcar-habitacion-inhabilitada-por-reparaciones.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-013-MarcarHabitacionInhabilitadaPorReparaciones
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el registro de reportes de daño en habitaciones disponibles (`POST /api/rooms/{roomId}/damage-reports`) y durante el fin de limpieza, asegurando la transición física a DisabledForRepairs, la creación inmutable de `DamageReport`, el registro en `RoomStateHistory`, la emisión del aviso asíncrono a Módulo 2 y la posterior consulta del informe de daño (`GET /api/rooms/{roomId}/damage-reports/latest`).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Habitación Inhabilitada por Reparaciones | Specification Document | [documentos/SPEC/spec-marcar-habitacion-inhabilitada-por-reparaciones.md](../SPEC/spec-marcar-habitacion-inhabilitada-por-reparaciones.md) |
| Plan de Implementación de Marcar Habitación Inhabilitada por Reparaciones | Plan Document | [documentos/PLAN/plan-marcar-habitacion-inhabilitada-por-reparaciones.md](../PLAN/plan-marcar-habitacion-inhabilitada-por-reparaciones.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere backend Spring Boot y base de datos PostgreSQL con la tabla `damage_report` (migración V3) y conexión al broker RabbitMQ para la cola `m2.habitacion.mantenimiento.queue`.

##### Dependencies & Assumptions
* La habitación objetivo debe encontrarse en estado Available para reportes directos desde los paneles.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado Available. Habitación 105 en estado Occupied.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario con área Limpieza o Mantenimiento autenticado; ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Reporte directo y consulta de informe)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Abrir diálogo de reporte de daño desde panel | En el Panel de Limpieza o Mantenimiento, ubicar la habitación 101 (Available) y hacer clic en "Reportar daño" | Habitación 101 (`Available`) | Se abre el diálogo modal "Reportar daño" con la advertencia: "La habitación 101 quedará fuera de servicio hasta que mantenimiento la repare." y el campo "Descripción del daño *" con contador de caracteres | [ ] | [ ] |
| 2 | Diligenciar y enviar reporte de daño | Ingresar la justificación del daño físico y hacer clic en el botón "Reportar daño" | Texto: "Humedad severa en el techo del baño con desprendimiento de pintura." | Se envía `POST /api/rooms/.../damage-reports`, la habitación transiciona a `DisabledForRepairs`, se guarda el registro inmutable en `damage_report`, se crea el periodo en `room_state_history` con `source_flow = REPORT_DAMAGE`, se encola el aviso a Módulo 2 y la habitación desaparece del panel de limpieza | [ ] | [ ] |
| 3 | Consultar informe de daño desde panel de mantenimiento | En el Panel de Mantenimiento, ubicar la habitación 101 en estado Inhabilitada y hacer clic en la acción "Ver informe" | Habitación 101 (`DisabledForRepairs`) | El sistema invoca `GET /api/rooms/.../damage-reports/latest` y despliega el modal con la descripción exacta del daño, el nombre completo del autor y la fecha y hora del reporte (`DD-MM-YYYY HH:MM`) | [ ] | [ ] |
| 4 | Reporte de daño integrado en fin de limpieza | Confirmar el fin de limpieza en una habitación diligenciando la descripción opcional de daño | Habitación en `InCleaning`, daño: "Vidrio de la ventana quebrado." | La habitación pasa de `InCleaning` a `Available` y en la misma transacción a `DisabledForRepairs` con `source_flow = CONFIRM_CLEANING_END`, sin apartarse a `Reserved` aunque tenga llegada hoy | [Pass] | Verificado por test automatizado (`CompleteCleaningIntegrationTest`) |

--------------------------------------------------------------------------------

##### Section 2: Errores, Validaciones y Rechazos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intento de reporte sin justificación | En el diálogo de reporte, intentar enviar el formulario con la descripción vacía o compuesta solo por espacios | Campo vacío o `"   "` | El botón permanece deshabilitado en interfaz o el sistema responde HTTP 400 `VALIDATION_ERROR`: "La descripción del daño es obligatoria (máximo 500 caracteres)." | [Pass] | Verificado por test automatizado (`ReportDamageDialog.test.tsx` y `ReportDamageIntegrationTest`) |
| 2 | Exceder longitud máxima de la descripción | Intentar enviar un reporte con una descripción superior a 500 caracteres | Texto de 501 caracteres | La interfaz bloquea el exceso o responde HTTP 400 `VALIDATION_ERROR`: "La descripción del daño es obligatoria (máximo 500 caracteres)." | [Pass] | Verificado por test automatizado (`ReportDamageDialog.test.tsx` y `ReportDamageIntegrationTest`) |
| 3 | Intento de reporte sobre habitación no disponible | Intentar reportar un daño sobre la habitación 105 que se encuentra en estado Occupied | Habitación 105 (`Occupied`) | El sistema responde HTTP 409 `ROOM_INVALID_STATE` con el mensaje: "La habitación está Ocupada; ahora no se puede reportar un daño en ella." | [Pass] | Verificado por test automatizado (`ReportDamageIntegrationTest`) |
| 4 | Consulta de informe de daño en habitación sin daño reportado | Invocar `GET /api/rooms/{roomId}/damage-reports/latest` sobre una habitación que se encuentra en Available | Habitación en `Available` | El sistema responde HTTP 404 `DAMAGE_REPORT_NOT_FOUND` indicando: "Esta habitación no tiene un daño reportado." | [Pass] | Verificado por test automatizado (`DamageReportControllerTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Inhabilitaciones simultáneas por dos usuarios: el bloqueo de fila a nivel de persistencia asegura que solo la primera transacción transiciona a `DisabledForRepairs`, y la segunda es rechazada con `ROOM_INVALID_STATE` informando el estado actual.
* **EC-002** : Falla de conectividad o caída de base de datos durante la inhabilitación: se aplica rollback completo en la transacción, la habitación sigue en `Available` y el formulario conserva la descripción ingresada por el usuario para facilitar el reintento.
* **EC-003** : Llegada pendiente hoy sobre habitación con daño: la habitación queda inhabilitada en `DisabledForRepairs` y el Panel de Recepción despliega la alerta informativa "No disponible: Inhabilitada por reparaciones", respetando el impedimento físico frente a la reserva.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
