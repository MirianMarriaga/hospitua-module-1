### Test Case Specification: Marcar Pendiente a Limpieza
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-pendiente-a-limpieza.md](../SPEC/spec-marcar-pendiente-a-limpieza.md) / [plan-marcar-pendiente-a-limpieza.md](../PLAN/plan-marcar-pendiente-a-limpieza.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-009-MarcarPendienteALimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que el servicio interno centralizado `MarkPendingCleaningUseCase` transicione correctamente y de forma atómica las habitaciones físicas al estado PendingCleaning tras un check-out, la finalización de reparaciones o la liberación de una tarea de limpieza activa, registrando el cambio en el historial de estados (`RoomStateHistory`) y rechazando orígenes no autorizados.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Pendiente a Limpieza | Specification Document | [documentos/SPEC/spec-marcar-pendiente-a-limpieza.md](../SPEC/spec-marcar-pendiente-a-limpieza.md) |
| Plan de Implementación de Marcar Pendiente a Limpieza | Plan Document | [documentos/PLAN/plan-marcar-pendiente-a-limpieza.md](../PLAN/plan-marcar-pendiente-a-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Servicio interno transaccional sin endpoint REST propio ni pantalla dedicada: se prueba a través de la integración con sus flujos invocadores o mediante pruebas de backend con la base de datos PostgreSQL en marcha.

##### Dependencies & Assumptions
* `RoomStateTransitionService` y la máquina de estados deben validar los orígenes permitidos hacia `PendingCleaning`.
* El flujo invocador debe ejecutar la llamada dentro de una transacción activa.

##### Preconditions & Default Setup
* **Initial State** : Habitación en estado Occupied, habitación en DisabledForRepairs o TechnicalBlock, y habitación en InCleaning con tarea de limpieza abierta.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Actor identificado según el flujo invocador (Recepcionista, Personal de mantenimiento, Personal de limpieza o Administrador).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Transiciones Válidas hacia PendingCleaning)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Transición automática desde Check-Out | Confirmar la salida física de un huésped en una habitación ocupada | Habitación en `Occupied`, flujo: `CHECK_OUT`, actor: recepcionista | Verificar en el sistema que la habitación transiciona atómicamente a `PendingCleaning` y se registra el periodo en `room_state_history` con `source_flow = CHECK_OUT` | [Pass] | Verificado por test automatizado (`CheckOutIntegrationTest`) |
| 2 | Transición automática desde Fin de Reparación | Confirmar la finalización de una reparación sobre una habitación inhabilitada o con bloqueo | Habitación en `DisabledForRepairs` o `TechnicalBlock`, flujo: `CONFIRM_REPAIR_END` | Verificar en el sistema que la habitación pasa a `PendingCleaning` y queda lista para el personal de aseo | [Pass] | Verificado por test automatizado (`CompleteRepairIntegrationTest`) |
| 3 | Transición por liberación de tarea de limpieza | Liberar una tarea de limpieza activa desde la vista de tarea de limpieza | Habitación en `InCleaning`, flujo: `RELEASE_CLEANING_TASK`, actor: personal de limpieza | Verificar en el sistema que la habitación vuelve de `InCleaning` a `PendingCleaning` para ser retomada por otro miembro | [Pass] | Verificado por test automatizado (`ReleaseCleaningTaskServiceTest`) |
| 4 | Transición por desactivación de cuenta de personal | Desactivar la cuenta de un miembro de limpieza que tiene una tarea activa abierta | Habitación en `InCleaning`, flujo: `RELEASE_CLEANING_TASK`, actor: administrador | Verificar en el sistema que la tarea se cierra con resultado `Released` y la habitación pasa a `PendingCleaning` con el Administrador como actor | [Pass] | Verificado por test automatizado (`StaffIntegrationTest`) |
| 5 | Visibilidad inmediata en Panel de Limpieza | Acceder al Panel de Limpieza tras cualquiera de las transiciones anteriores | Rol: Personal de limpieza, ruta: `/limpieza` | La habitación aparece inmediatamente listada en la sección de pendientes de limpieza | [ ] | |

--------------------------------------------------------------------------------

##### Section 2: Errores y Validaciones de Estado Inválido
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intento de transición desde estado no permitido | Invocar el puerto sobre una habitación que se encuentra en estado Available | Habitación en `Available` | El sistema rechaza la operación lanzando `InvalidRoomTransitionException` informando el estado actual de la habitación sin alterar los registros | [Pass] | Verificado por test automatizado (`MarkPendingCleaningServiceTest`) |
| 2 | Intento de paso desde InCleaning con flujo no autorizado | Invocar el puerto sobre una habitación en `InCleaning` utilizando un flujo distinto a `RELEASE_CLEANING_TASK` | Habitación en `InCleaning`, flujo: `CHECK_OUT` | El sistema bloquea la transición por regla de negocio FR-001 y mantiene la habitación en `InCleaning` | [Pass] | Verificado por test automatizado (`MarkPendingCleaningServiceTest`) |
| 3 | Rollback ante falla en el flujo invocador | Simular una caída de base de datos o error de validación en el flujo padre tras solicitar el marcado a limpieza | Fallo transaccional posterior | La transacción completa es revertida (rollback) y la habitación permanece en su estado de origen previo sin inconsistencias | [Pass] | Verificado por test automatizado (`MarkPendingCleaningIntegrationTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Concurrencia de check-out sobre la misma habitación ocupada: dos recepcionistas intentan confirmar la salida en el mismo milisegundo; el control de bloqueo optimista permite la primera mutación a `PendingCleaning` y rechaza la segunda informando el cambio de estado.
* **EC-002** : Generación de marca de tiempo en servidor: la fecha y hora de la transición es generada exclusivamente por el reloj del sistema (`Clock`), garantizando orden cronológico estricto en `RoomStateHistory`.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
