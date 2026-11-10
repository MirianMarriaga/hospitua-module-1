### Test Case Specification: Marcar Habitación en Limpieza
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-habitacion-en-limpieza.md](../SPEC/spec-marcar-habitacion-en-limpieza.md) / [plan-marcar-habitacion-en-limpieza.md](../PLAN/plan-marcar-habitacion-en-limpieza.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-010-MarcarHabitacionEnLimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que el Personal de limpieza pueda formalizar el inicio de labores de aseo en una habitación física (`POST /api/cleaning/tasks`), transicionando su estado a InCleaning desde PendingCleaning o Available, vinculando la tarea (`CleaningTask`) con marca de tiempo de servidor y excluyéndola del panel general para redirigir a la vista de tarea activa.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Habitación en Limpieza | Specification Document | [documentos/SPEC/spec-marcar-habitacion-en-limpieza.md](../SPEC/spec-marcar-habitacion-en-limpieza.md) |
| Plan de Implementación de Marcar Habitación en Limpieza | Plan Document | [documentos/PLAN/plan-marcar-habitacion-en-limpieza.md](../PLAN/plan-marcar-habitacion-en-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere backend Spring Boot en funcionamiento y base de datos PostgreSQL con migraciones V1 a V3 ejecutadas.

##### Dependencies & Assumptions
* Las habitaciones a limpiar deben encontrarse en estado PendingCleaning o Available.
* El miembro de limpieza que ejecuta la acción no debe tener otra tarea de limpieza abierta simultáneamente.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado PendingCleaning (sin tarea abierta). Habitación 102 en estado Available. Habitación 105 en estado Occupied.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario con área Limpieza activo (ej. `cstaff1`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Inicio de Limpieza desde Pendiente y Disponible)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar limpieza de habitación pendiente de aseo | En el Panel de Limpieza `/limpieza`, en la sección de pendientes, hacer clic en "Iniciar limpieza" para la habitación 101 y confirmar en el modal | Habitación 101 (`PendingCleaning`), usuario: `cstaff1` | El sistema envía `POST /api/cleaning/tasks`, crea la tarea `CleaningTask` con `start_date_time` del servidor, transiciona la habitación a `InCleaning`, registra el periodo en `room_state_history` con `source_flow = START_CLEANING` y redirige a la vista de tarea activa `/limpieza/tarea` | [ ] | [ ] |
| 2 | Iniciar limpieza de habitación disponible | En el Panel de Limpieza `/limpieza`, seleccionar una habitación en estado Available, hacer clic en "Iniciar limpieza" y confirmar | Habitación 102 (`Available`), usuario: `cstaff2` (sin tarea abierta) | La habitación transiciona exitosamente a `InCleaning`, se le asigna la tarea y el miembro queda redirigido a su vista de tarea activa `/limpieza/tarea` sin exigir check-out previo | [ ] | [ ] |
| 3 | Exclusión del panel general de limpieza | Iniciar sesión con otro miembro del personal de limpieza y consultar el panel general | Usuario distinto a los asignados | Las habitaciones 101 y 102 ya no aparecen en el listado de habitaciones para iniciar limpieza | [ ] | [ ] |

--------------------------------------------------------------------------------

##### Section 2: Errores, Validaciones y Bloqueos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intento de inicio sobre habitación en estado no permitido | Intentar iniciar la limpieza sobre una habitación en estado Occupied | Habitación 105 (`Occupied`) | El sistema rechaza la petición respondiendo HTTP 409 `ROOM_INVALID_STATE` y la interfaz presenta el mensaje: "La habitación está Ocupada; ahora no se puede limpiar." | [Pass] | Verificado por test automatizado (`StartCleaningIntegrationTest`) |
| 2 | Miembro con tarea activa intenta tomar otra habitación | Intentar iniciar la limpieza de una habitación teniendo ya una `CleaningTask` abierta sin finalizar | Usuario con tarea abierta | El sistema bloquea la acción con HTTP 409 `ACTIVE_TASK_EXISTS` y muestra: "Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra." | [Pass] | Verificado por test automatizado (`StartCleaningIntegrationTest`) |
| 3 | Concurrencia sobre la misma habitación | Dos miembros de limpieza intentan hacer clic simultáneamente en "Iniciar limpieza" de la misma habitación | Habitación en `PendingCleaning` | El primer usuario obtiene la asignación exitosa; el segundo usuario es bloqueado con mensaje: "La habitación está En limpieza; ahora no se puede limpiar." (o "Otro compañero ya está limpiando esta habitación.") | [Pass] | Verificado por test automatizado (`StartCleaningIntegrationTest`) |
| 4 | Solicitud con identificador de habitación inexistente | Enviar la petición de inicio con un UUID no registrado en inventario | `roomId`: `00000000-0000-0000-0000-000000000999` | El sistema responde HTTP 404 `ROOM_NOT_FOUND` mostrando: "No se encontró la habitación." | [Pass] | Verificado por test automatizado (`StartCleaningIntegrationTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Desfase de reloj del cliente: el sistema descarta cualquier marca de tiempo enviada por la interfaz o dispositivo móvil, generando `StartDateTime` de la tarea e inicio de periodo en `RoomStateHistory` exclusivamente a través del reloj central del servidor (`Clock`).
* **EC-002** : Falla de red o caída transaccional al momento de iniciar la limpieza: si ocurre un fallo durante la escritura, se efectúa rollback completo y la habitación permanece en su estado previo (`PendingCleaning` o `Available`) sin tareas huérfanas creadas.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
