### Test Case Specification: Confirmar Fin de Reparación de Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-confirmar-reparacion-finalizada.md](../SPEC/spec-confirmar-reparacion-finalizada.md) / [plan-confirmar-reparacion-finalizada.md](../PLAN/plan-confirmar-reparacion-finalizada.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-014-ConfirmarReparacionFinalizada
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el inicio de intervenciones técnicas (`POST /api/maintenance/tasks`) y la posterior confirmación del fin de reparación (`POST /api/maintenance/tasks/{taskId}/complete`), asegurando el cierre de la tarea (`ReparationTask`), la transición de la habitación a PendingCleaning mediante *Marcar Pendiente a Limpieza*, el cierre del informe de bloqueo técnico cuando aplique, la emisión del aviso a Módulo 2 y la opción de liberar la tarea activa (`POST /api/maintenance/tasks/{taskId}/release`).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Confirmar Fin de Reparación | Specification Document | [documentos/SPEC/spec-confirmar-reparacion-finalizada.md](../SPEC/spec-confirmar-reparacion-finalizada.md) |
| Plan de Implementación de Confirmar Fin de Reparación | Plan Document | [documentos/PLAN/plan-confirmar-reparacion-finalizada.md](../PLAN/plan-confirmar-reparacion-finalizada.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere backend Spring Boot y frontend React en funcionamiento, con las tablas de mantenimiento y base de datos PostgreSQL en marcha. La pantalla `/mantenimiento` y `/mantenimiento/tarea` están completamente implementadas en React.

##### Dependencies & Assumptions
* La habitación objetivo debe encontrarse en estado DisabledForRepairs o TechnicalBlock.
* TC-009 Marcar pendiente a limpieza debe estar implementado para recibir la habitación reparada.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado DisabledForRepairs (por daño reportado). Habitación 103 en TechnicalBlock con informe de bloqueo técnico en estado Applied.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario con área Mantenimiento autenticado (ej. `mtorres`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Inicio, confirmación de fin y liberación de reparación)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar reparaciones desde el Panel de Mantenimiento | En el Panel de Mantenimiento `/mantenimiento`, ubicar la habitación 101 (`DisabledForRepairs`) y hacer clic en "Iniciar reparaciones" | Habitación 101, usuario: `mtorres` | Se envía `POST /api/maintenance/tasks`, se crea la tarea de reparación con fecha/hora de inicio del servidor y el usuario es redirigido a la vista de tarea activa `/mantenimiento/tarea` | [ ] | [ ] |
| 2 | Confirmar fin de reparaciones | En la vista de tarea activa `/mantenimiento/tarea`, hacer clic en "Confirmar fin" y confirmar en el modal de confirmación | Diálogo: "¿Confirmas que terminaste la reparación de la habitación 101?" con advertencia "La habitación pasará a limpieza antes de volver a recibir huéspedes." | Se envía `POST /api/maintenance/tasks/{taskId}/complete`, se cierra la tarea, la habitación transiciona a `PendingCleaning`, se emite el aviso a Módulo 2 y se redirige a `/mantenimiento` con el aviso: "Reparación de la habitación 101 finalizada. La habitación pasó a limpieza antes de volver a recibir huéspedes." | [ ] | [ ] |
| 3 | Confirmar fin de reparación en bloqueo técnico | Iniciar y confirmar reparación sobre la habitación 103 en estado TechnicalBlock | Habitación 103 (`TechnicalBlock`) | El sistema transiciona la habitación a `PendingCleaning`, marca el informe `TechnicalBlockReport` como `Completed`, notifica a Módulo 2 y muestra el aviso en el panel: "Reparación de la habitación 103 finalizada. La habitación pasó a limpieza y el mantenimiento quedó terminado." | [Pass] | Verificado por test automatizado (`CompleteRepairIntegrationTest`) |
| 4 | Liberar tarea de reparación activa | Iniciar reparaciones en una habitación inhabilitada, hacer clic en "Liberar tarea" en `/mantenimiento/tarea` y confirmar en el diálogo | Diálogo: "¿Quieres dejar la reparación de la habitación 101 para otro compañero?" con texto "La habitación seguirá fuera de servicio para que otro compañero la tome." | Se envía `POST /api/maintenance/tasks/{taskId}/release`, se cierra la tarea como liberada, la habitación permanece en `DisabledForRepairs` sin cambios de estado y se redirige al panel con el aviso: "Dejaste la habitación 101 para otro compañero." | [ ] | [ ] |

--------------------------------------------------------------------------------

##### Section 2: Errores, Validaciones y Rechazos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intento de inicio sobre habitación en estado no permitido | Intentar iniciar reparaciones sobre una habitación que se encuentra en estado Available u Occupied | Habitación en `Available` | El sistema rechaza la petición con HTTP 409 `ROOM_INVALID_STATE` informando: "La habitación está Disponible; no necesita reparación." | [Pass] | Verificado por test automatizado (`StartMaintenanceIntegrationTest`) |
| 2 | Miembro con tarea activa intenta tomar otra habitación | Intentar iniciar una segunda reparación teniendo ya una `ReparationTask` abierta | Usuario con tarea de reparación activa | El sistema rechaza la operación con HTTP 409 `ACTIVE_TASK_EXISTS` mostrando: "Ya estás reparando otra habitación. Termínala o déjala antes de empezar otra." | [Pass] | Verificado por test automatizado (`StartMaintenanceIntegrationTest`) |
| 3 | Habitación con reparación activa por otro compañero | Intentar iniciar reparaciones en una habitación que ya está siendo intervenida por otro técnico | Habitación con tarea abierta por otro usuario | El sistema responde HTTP 409 `ROOM_TASK_EXISTS` mostrando: "Otro compañero ya está reparando esta habitación." | [Pass] | Verificado por test automatizado (`StartMaintenanceIntegrationTest`) |
| 4 | Intento de confirmación o liberación de tarea ajena | Intentar confirmar o liberar la tarea perteneciente a otro técnico de mantenimiento | `taskId` perteneciente a otro usuario | El sistema responde HTTP 403 `TASK_NOT_OWNED` con mensaje: "Esta reparación la está haciendo otro compañero." | [Pass] | Verificado por test automatizado (`CompleteRepairIntegrationTest`) |
| 5 | Intento de acción sobre tarea ya cerrada | Reenviar la petición de confirmar o liberar sobre una tarea que ya fue completada o liberada | `taskId` ya cerrado | El sistema responde HTTP 409 `TASK_ALREADY_CLOSED` con mensaje: "Esta tarea ya fue cerrada." | [Pass] | Verificado por test automatizado (`CompleteRepairIntegrationTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Invariante cronológica: `EndDateTime` de la tarea de reparación es generado exclusivamente por el servidor al momento de la confirmación o liberación y nunca puede ser anterior a `StartDateTime`.
* **EC-002** : Falla de red o caída de base de datos durante la confirmación: la transacción se cancela (rollback completo) y la habitación permanece en `DisabledForRepairs` con la tarea activa para reintentar la acción.
* **EC-003** : Desactivación de cuenta de técnico con tarea abierta: al desactivarse la cuenta desde *Gestionar personal*, la `ReparationTask` se cierra automáticamente con `outcome = Released` sin alterar el estado físico de la habitación.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
