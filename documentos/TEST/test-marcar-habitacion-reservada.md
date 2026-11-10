### Test Case Specification: Marcar Habitación como Reservada
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-habitacion-reservada.md](../SPEC/spec-marcar-habitacion-reservada.md) / [plan-marcar-habitacion-reservada.md](../PLAN/plan-marcar-habitacion-reservada.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-007-MarcarHabitacionReservada
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que el caso de uso interno y autónomo transicione reactivamente las habitaciones físicas del estado Available a Reserved asignando la referencia de reserva (`reserved_by_reservation_ref`), procese eventos duplicados de forma estrictamente idempotente y preserve la situación física real de la unidad ante estados conflictivos registrando el evento en la bitácora de auditoría.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Habitación como Reservada | Specification Document | [documentos/SPEC/spec-marcar-habitacion-reservada.md](../SPEC/spec-marcar-habitacion-reservada.md) |
| Plan de Implementación de Marcar Habitación como Reservada | Plan Document | [documentos/PLAN/plan-marcar-habitacion-reservada.md](../PLAN/plan-marcar-habitacion-reservada.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Caso de uso interno sin interfaz gráfica directa: se verifica a través del backend, la base de datos PostgreSQL y los flujos consumidores (ingesta de reservas diarias y fin de limpieza).

##### Dependencies & Assumptions
* El catálogo de habitaciones de la semilla V2 debe estar cargado.
* No existe invocación manual ni formularios de usuario para asignar directamente el estado Reserved.

##### Preconditions & Default Setup
* **Initial State** : Habitación 103 en estado Available. Habitación 104 en estado Inactive. Habitación 201 en estado Reserved con reserva `RSV-3F9A1C7B`.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Operación autónoma del sistema (sin actor humano directo; `actorId` nulo).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso y Transición Encadenada
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Apartado por lista diaria a las 00:00 | Ejecutar la ingesta de la lista diaria conteniendo una reserva para hoy asignada a una habitación disponible | Habitación: 103 (Available), Reserva: `RSV-NEW-103`, trigger: `DAILY_LIST` | Verificar en el sistema que la habitación 103 transiciona atómicamente a Reserved, asigna `reserved_by_reservation_ref` = `RSV-NEW-103` y se crea el periodo en `room_state_history` con `source_flow = MARK_RESERVED` | [Pass] | Verificado por test automatizado (`MarkRoomReservedServiceTest`) |
| 2 | Verificación de aptitud para Check-In | Consultar el estado de la habitación apartada en el paso anterior desde el Panel de Recepción | Habitación: 103 | La habitación se muestra con botón "Check-in" habilitado y es aceptada por el flujo de admisión física | [ ] | |
| 3 | Transición encadenada al terminar limpieza | Simular confirmación de fin de limpieza en una habitación con llegada pendiente hoy sin daño reportado | Habitación saliendo de `InCleaning` con reserva del día pendiente | Verificar en el sistema que dentro de la misma transacción la habitación pasa de `InCleaning` a `Available` y de inmediato de `Available` a `Reserved`, registrando ambos periodos en `room_state_history` | [Pass] | Verificado por test automatizado (`CompleteCleaningIntegrationTest`) |

--------------------------------------------------------------------------------

##### Section 2: Idempotencia y Manejo de Conflictos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Procesamiento idempotente ante evento duplicado | Ejecutar nuevamente la invocación para la habitación 103 con la misma reserva `RSV-NEW-103` | Habitación: 103, Reserva: `RSV-NEW-103` | Verificar en el sistema que la solicitud se reconoce como exitosa (`ALREADY_RESERVED`), sin modificar el estado ni duplicar filas en el historial de estados | [Pass] | Verificado por test automatizado (`MarkRoomReservedServiceTest`) |
| 2 | Conflicto por habitación ocupada o en limpieza | Invocar el apartado para una habitación que se encuentra físicamente en Occupied o InCleaning | Habitación en `Occupied` o `InCleaning`, Reserva: `RSV-CONFLICT-01` | Verificar en el sistema que el estado físico no se modifica y se registra un evento de conflicto `RESERVATION_STATE_CONFLICT` en `room_audit_log` con el estado real y la referencia de reserva | [Pass] | Verificado por test automatizado (`MarkRoomReservedServiceTest`) |
| 3 | Conflicto por habitación en mantenimiento o dada de baja | Invocar el apartado para la habitación 104 que se encuentra en Inactive o TechnicalBlock | Habitación 104 (`Inactive`), Reserva: `RSV-INACTIVE-01` | Verificar en el sistema que la habitación permanece en `Inactive`, se registra `RESERVATION_STATE_CONFLICT` en auditoría y el Panel de Recepción despliega la alerta visual "No disponible: Inactiva" | [Pass] | Verificado por test automatizado (`MarkRoomReservedServiceTest`) |
| 4 | Conflicto por habitación ya reservada con otra reserva | Invocar el apartado para la habitación 201 (ya reservada para `RSV-3F9A1C7B`) con una referencia de reserva distinta | Habitación 201, Reserva entrante: `RSV-OTHER-999` | Verificar en el sistema que la habitación mantiene la reserva original `RSV-3F9A1C7B` y se registra en `room_audit_log` el conflicto con ambas referencias | [Pass] | Verificado por test automatizado (`MarkRoomReservedServiceTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Concurrencia sobre habitación Available: dos procesos intentan apartar la misma habitación disponible simultáneamente; el bloqueo a nivel de fila (`SELECT ... FOR UPDATE`) garantiza que el primero aparta a `Reserved` y el segundo detecta el cambio tratándolo como idempotente o conflicto.
* **EC-002** : Falla transaccional durante la persistencia: si ocurre un corte de base de datos durante el cambio de estado, la transacción se revierte por completo (rollback) preservando la habitación en su estado `Available` previo.
* **EC-003** : Ausencia de confirmaciones o rechazos externos: Módulo 1 no emite mensajes HTTP ni colas de confirmación hacia Módulo 2; los conflictos se gestionan localmente en la bitácora de auditoría y en las alertas del panel de recepción.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
