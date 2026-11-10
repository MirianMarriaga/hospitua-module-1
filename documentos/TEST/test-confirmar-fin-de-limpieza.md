### Test Case Specification: Confirmar Fin de Limpieza de Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-confirmar-fin-de-limpieza.md](../SPEC/spec-confirmar-fin-de-limpieza.md) / [plan-confirmar-fin-de-limpieza.md](../PLAN/plan-confirmar-fin-de-limpieza.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-011-ConfirmarFinDeLimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Equipo Módulo 1
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que el Personal de limpieza pueda finalizar su tarea de aseo activa (`POST /api/cleaning/tasks/{taskId}/complete`), cerrando la `CleaningTask` con marca de tiempo del servidor y transicionando la habitación a Available, Reserved (si hay llegada hoy), DisabledForRepairs (si se reporta daño), o liberarla hacia PendingCleaning (`POST /api/cleaning/tasks/{taskId}/release`).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Confirmar Fin de Limpieza | Specification Document | [documentos/SPEC/spec-confirmar-fin-de-limpieza.md](../SPEC/spec-confirmar-fin-de-limpieza.md) |
| Plan de Implementación de Confirmar Fin de Limpieza | Plan Document | [documentos/PLAN/plan-confirmar-fin-de-limpieza.md](../PLAN/plan-confirmar-fin-de-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere backend Spring Boot y frontend en marcha, y base de datos PostgreSQL con la tabla `cleaning_task` y semilla V2.

##### Dependencies & Assumptions
* TC-010 Marcar habitación en limpieza debe haber asignado previamente la tarea activa al miembro que ejecuta la confirmación.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado InCleaning con tarea abierta a nombre del usuario de limpieza actual (`cstaff1`). Habitación 102 en InCleaning asignada a `cstaff2`.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario con área Limpieza (`cstaff1`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Confirmación sin daño, con reserva y liberación)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Acceder a la vista de tarea activa | Iniciar sesión como personal de limpieza teniendo una tarea abierta y navegar a `/limpieza` | Usuario: `cstaff1` con tarea activa en hab. 101 | El sistema redirige automáticamente a la vista de tarea activa `/limpieza/tarea` mostrando la habitación 101 y las opciones únicas: "Confirmar fin" y "Liberar tarea" | [Pass] | [ ] |
| 2 | Confirmar fin de limpieza sin daño reportado | Hacer clic en "Confirmar fin" y en el diálogo modal confirmar dejando el campo de daño vacío | Diálogo: "¿Confirmas el fin de la limpieza?", daño vacío | Se envía `POST /api/cleaning/tasks/{taskId}/complete`, se cierra la tarea con `outcome = Completed` y fecha/hora del servidor, la habitación pasa a Available y el usuario es redirigido a `/limpieza` con el aviso: "Limpieza de la habitación 101 finalizada." | [Pass] | [ ] |
| 3 | Confirmar fin de limpieza para habitación con llegada pendiente hoy | 1. En `/limpieza`, tomar de la lista una habitación en estado `PendingCleaning` que tenga reserva para hoy (ej. hab. 202 tras check-out).<br>2. Pulsar "Iniciar limpieza" para pasar a `/limpieza/tarea`.<br>3. Hacer clic en "Confirmar fin", dejar el campo de daño vacío y confirmar en el modal | Habitación 202 en `PendingCleaning` con reserva para hoy en la copia local; daño vacío | Se cierra la tarea con `outcome = Completed`. En la misma transacción, el backend pasa la habitación de `InCleaning` a `Available` y automáticamente a `Reserved` vinculando la reserva para Recepción. Se redirige a `/limpieza` con el aviso: "Limpieza de la habitación 202 finalizada." | [Pass] | Verificado por test automatizado (`CompleteCleaningIntegrationTest`) |
| 4 | Confirmar fin reportando daño | En `/limpieza/tarea`, hacer clic en "Confirmar fin", diligenciar la descripción del daño y confirmar | Daño: "Grifo del lavamanos roto con fuga continua de agua." | Se cierra la tarea con `outcome = DamageReported`, se crea el registro inmutable en `damage_report`, la habitación pasa a `DisabledForRepairs` y se redirige a `/limpieza` con el aviso: "Limpieza de la habitación 101 finalizada con daño reportado. La habitación quedó fuera de servicio hasta que mantenimiento la repare." | [ ] | |
| 5 | Liberar tarea activa | En `/limpieza/tarea`, hacer clic en "Liberar tarea" y confirmar en el diálogo | Diálogo: "¿Quieres dejar la limpieza de la habitación 101 para otro compañero?" | Se envía `POST /api/cleaning/tasks/{taskId}/release`, la tarea se cierra con `outcome = Released`, la habitación vuelve a `PendingCleaning` y se redirige a `/limpieza` con el aviso: "Limpieza de la habitación 101 liberada. La habitación quedó disponible para que otro miembro la tome." | [ ] | |

--------------------------------------------------------------------------------

##### Section 2: Errores, Validaciones y Rechazos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intento de confirmación o liberación de tarea ajena | Intentar invocar el cierre de la tarea de la habitación 102 (perteneciente a `cstaff2`) con el usuario `cstaff1` | Usuario: `cstaff1`, `taskId` de `cstaff2` | El sistema responde HTTP 403 `TASK_NOT_OWNED` con el mensaje: "Esta habitación la está limpiando otro compañero." | [Pass] | Verificado por test automatizado (`CompleteCleaningIntegrationTest`) |
| 2 | Intento de acción sobre tarea ya cerrada | Reenviar la petición de completar o liberar sobre una tarea que ya finalizó | `taskId` ya completado | El sistema responde HTTP 409 `TASK_ALREADY_CLOSED` con mensaje: "Esta tarea ya fue cerrada." | [Pass] | Verificado por test automatizado (`CompleteCleaningIntegrationTest`) |
| 3 | Descripción de daño con longitud excesiva | Intentar confirmar el fin ingresando un texto de reporte de daño superior a 500 caracteres | Texto de daño de 501 caracteres | El sistema bloquea el envío con HTTP 400 `VALIDATION_ERROR` indicando: "La descripción del daño admite máximo 500 caracteres." | [Pass] | Verificado por test automatizado (Vitest y `CompleteCleaningIntegrationTest`) |
| 4 | Tarea inexistente | Enviar la petición con un identificador de tarea aleatorio no existente | `taskId`: `00000000-0000-0000-0000-000000000000` | El sistema responde HTTP 404 `TASK_NOT_FOUND` mostrando: "No encontramos esta limpieza. Vuelve al panel." | [Pass] | Verificado por test automatizado (`CompleteCleaningIntegrationTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Invariante de tiempos: `EndDateTime` de la tarea es generado por el servidor al momento de la transacción y nunca puede ser anterior a `StartDateTime`.
* **EC-002** : Solicitudes simultáneas de cierre y liberación: dos peticiones concurrentes para la misma tarea activa compiten; solo la primera concreta la transacción y la segunda recibe `TASK_ALREADY_CLOSED`.
* **EC-003** : Falla en la persistencia del daño o de la transición: si ocurre un corte en la base de datos durante la confirmación, se ejecuta rollback completo preservando la habitación en `InCleaning` con la tarea activa intacta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
