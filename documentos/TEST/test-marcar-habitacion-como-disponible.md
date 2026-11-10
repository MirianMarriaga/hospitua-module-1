### Test Case Specification: Marcar Habitación como Disponible
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-marcar-habitacion-como-disponible.md](../SPEC/spec-marcar-habitacion-como-disponible.md) / [plan-marcar-habitacion-como-disponible.md](../PLAN/plan-marcar-habitacion-como-disponible.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-008-MarcarHabitacionComoDisponible
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la reactivación de habitaciones inactivas por el Administrador hacia el estado Available (`POST /api/rooms/{roomId}/reactivation`), la reintegración física al finalizar la limpieza y la liberación autónoma de habitaciones en Reserved ante cancelaciones o actualizaciones de reservas.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Habitación como Disponible | Specification Document | [documentos/SPEC/spec-marcar-habitacion-como-disponible.md](../SPEC/spec-marcar-habitacion-como-disponible.md) |
| Plan de Implementación de Marcar Habitación como Disponible | Plan Document | [documentos/PLAN/plan-marcar-habitacion-como-disponible.md](../PLAN/plan-marcar-habitacion-como-disponible.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere frontend y backend Spring Boot en ejecución, con la base de datos PostgreSQL inicializada con semilla V2.

##### Dependencies & Assumptions
* Las habitaciones deben encontrarse en estado Inactive (para reactivación administrativa), InCleaning (para fin de aseo) o Reserved (para liberación autónoma).

##### Preconditions & Default Setup
* **Initial State** : Habitación 104 en estado Inactive (motivo de baja: Decisión administrativa). Habitación 101 en estado Available. Habitación 201 en Reserved apartada para una reserva.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario con área Administración (para reactivación en interfaz); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Reactivación Administrativa de Habitación Inactiva)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Acceder al inventario y seleccionar habitación inactiva | En `/administracion/inventario`, ubicar la habitación 104 (Inactiva) y hacer clic en la acción "Marcar como disponible" | Habitación 104 (Inactive) | El sistema navega a `/administracion/reactivacion/a0000000-0000-0000-0000-000000000104`, mostrando el encabezado "Marcar como disponible", el resumen de la habitación 104 y la caja con fecha y motivo de la baja: "Decisión administrativa" | [ ] | [ ] |
| 2 | Confirmar la reactivación | Hacer clic en el botón "Reactivar habitación" | Clic en "Reactivar habitación" | Se envía `POST /api/rooms/.../reactivation`, la habitación transiciona a Available, se cierra el registro de baja en base de datos y se muestra la pantalla de éxito con: "Habitación 104 disponible" y "Ya puede recibir reservas y estancias." | [ ] | [ ] |
| 3 | Verificar actualización en el inventario | Hacer clic en "Volver al inventario" | Clic en "Volver al inventario" | En la tabla de inventario `/administracion/inventario`, la habitación 104 aparece en estado "Disponible" con insignia verde | [ ] | [ ] |
| 4 | Reintegración tras fin de limpieza (servicio interno) | Confirmar fin de limpieza sin daño en una habitación en estado InCleaning sin reserva pendiente para hoy | Habitación en `InCleaning` | Verificar en el sistema que la habitación transiciona a Available y queda habilitada de inmediato para asignaciones comerciales | [Pass] | Verificado por test automatizado (`CompleteCleaningIntegrationTest`) |
| 5 | Liberación autónoma por cancelación de reserva | Simular la recepción de un mensaje `REMOVED` de Módulo 2 para la reserva que apartaba la habitación 201 | Habitación 201 en `Reserved` | Verificar en el sistema que la habitación transiciona de Reserved a Available, se limpia `reserved_by_reservation_ref` y el historial registra el periodo con `source_flow = MARK_AVAILABLE` | [Pass] | Verificado por test automatizado (`DailyReservationIngestionServiceTest`) |

--------------------------------------------------------------------------------

##### Section 2: Errores, Validaciones y Casos de Idempotencia
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intento de reactivación directa sobre habitación disponible | Intentar acceder a la ruta de reactivación con el ID de una habitación que ya está en estado Available | Habitación 101 (Available) | La interfaz muestra la tarjeta informativa: "No se puede marcar como disponible la habitación 101. La habitación está en estado Disponible. Solo se pueden reactivar habitaciones inactivas." y no permite ejecutar el cambio | [Pass] | Verificado por test automatizado (`ReactivateRoomPage.test.tsx` y `ReactivateRoomIntegrationTest`) |
| 2 | Reactivación idempotente (habitación ya reactivada) | Simular el reenvío de la petición `POST /api/rooms/{roomId}/reactivation` para una habitación que ya fue reactivada | Habitación 104 ya en Available | El sistema responde exitosamente sin errores (`alreadyReactivated = true`) y la pantalla presenta: "La habitación ya había sido reactivada; no fue necesario hacer cambios." | [Pass] | Verificado por test automatizado (`ReactivateRoomIntegrationTest`) |
| 3 | Consulta con identificador inexistente | Intentar cargar la página de reactivación con un UUID inexistente | `roomId`: `00000000-0000-0000-0000-000000000999` | Se muestra la tarjeta: "Habitación no encontrada. No existe ninguna habitación con ese identificador." con botón "Volver al inventario" | [Pass] | Verificado por test automatizado (`ReactivateRoomPage.test.tsx`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Peticiones simultáneas de reactivación: dos administradores confirman la reactivación de la misma habitación inactiva concurrentemente; el control de bloqueo optimista o la comprobación de estado garantiza que el primero la reactiva a `Available` y el segundo recibe confirmación idempotente sin duplicar periodos en el historial.
* **EC-002** : Falla en la persistencia de base de datos durante la reactivación: la transacción se cancela (rollback completo) y la habitación permanece en estado `Inactive` preservando el detalle de la baja original.
* **EC-003** : Liberación de habitación en limpieza cancelada: si llega una actualización `REMOVED` de una reserva cuya habitación asignada está en limpieza, la reserva se retira de la copia local sin alterar el proceso físico de aseo en curso.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
