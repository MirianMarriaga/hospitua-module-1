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
Este caso de prueba verifica el apartado autónomo de habitaciones en estado Available hacia Reserved ante la llegada de reservas del día vía RabbitMQ (`m1.reservas.diarias.queue`), la asignación del campo `reservedByReservationRef`, el registro del periodo en `room_state_history` con `sourceFlow: ASSIGN_RESERVATION`, y la idempotencia ante mensajes duplicados sin emisión de eventos salientes en esta fase.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Habitación Reservada | Specification Document | [documentos/SPEC/spec-marcar-habitacion-reservada.md](../SPEC/spec-marcar-habitacion-reservada.md) |
| Plan de Implementación de Marcar Habitación Reservada | Plan Document | [documentos/PLAN/plan-marcar-habitacion-reservada.md](../PLAN/plan-marcar-habitacion-reservada.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Transición de dominio autónoma sin pantalla interactiva propia; sus efectos se observan en el Panel de Recepción e Inventario.

##### Dependencies & Assumptions
* Requiere broker RabbitMQ, base de datos PostgreSQL y servicio backend en ejecución.

##### Preconditions & Default Setup
* **Initial State** : Habitación 103 en estado `Available` con semilla V2 cargada.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Usuario de Recepción (`recepcionista`) y Administrador (`admin`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Navegar a `/login` e ingresar credenciales | Usuario: `admin` | Carga el Inventario en `/administracion/inventario` | [ ] | |
| 2 | Visualizar habitación en inventario | En `/administracion/inventario`, filtrar por número "103" | Habitación 103 | La fila muestra el estado actual de la habitación ("Disponible" o "Reservada") | [ ] | |
| 3 | Cambiar a área de Recepción | Cerrar sesión o ingresar con cuenta de recepción a `/recepcion` | Usuario: `recepcionista` | Carga el Panel de Recepción en `/recepcion` | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Apartado visible en Panel de Recepción | Tras la ingesta de la reserva del día para la habitación 103, consultar `/recepcion` | Reserva para hab. 103 | La habitación 103 aparece en la tabla de Llegadas con su botón "Check-in" habilitado | [ ] | |
| 2 | Apartado visible en Inventario | Consultar la habitación 103 en `/administracion/inventario` | Habitación 103 | La habitación 103 refleja el badge de estado "Reservada" y no permite edición mientras esté en ese estado | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Alerta operativa por conflicto de reserva en recepción | Observar una llegada cuya habitación asignada está inactiva | Habitación 104 (`Inactive`) | La fila de llegada muestra la alerta correspondiente y el botón "Check-in" deshabilitado | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Verificar estado reservado tras apartado | `GET {BASE_URL}/api/rooms/inventory/a0000000-0000-0000-0000-000000000103`<br>Headers: (sin X-User obligatorio) | `roomId` de habitación 103 | HTTP 200 OK, JSON con `{ "status": "Reserved", "reservedByReservationRef": "RSV-HAB-103" }` | [ ] | |
| 2 | Verificación de aptitud para check-in | `GET {BASE_URL}/api/check-in/validate?reservationRef=RSV-HAB-103&roomId=a0000000-0000-0000-0000-000000000103` | Habitación apartada | HTTP 200 OK, JSON con `valid: true` y datos de reserva | [ ] | |
| 3 | Consulta de habitación reservada desde Módulo 2 | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000103` | `roomId` de hab. 103 | HTTP 200 OK con datos vendibles de la habitación | [ ] | |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Apartado por mensaje de lista diaria (00:00) | Publicar mensaje en `hospitua.events` con routing key `reserva.lista-del-dia` hacia `m1.reservas.diarias.queue` | Reserva con llegada hoy para hab. 103 | La habitación pasa atómicamente a `Reserved`, asigna `reservedByReservationRef` y crea registro en `room_state_history` | [ ] | |
| 2 | Apartado por actualización ADDED | Publicar mensaje con routing key `reserva.lista-del-dia.actualizacion` con `action: "ADDED"` | `action: "ADDED"` para hab. 101 | Habitación 101 transiciona de `Available` a `Reserved` | [ ] | |
| 3 | Procesamiento idempotente ante evento repetido | Reenviar el mismo mensaje de apartado para la habitación ya reservada | Misma reserva y habitación | El sistema detecta duplicado en `daily_reservation_message_log` sin duplicar filas en el historial de estados | [ ] | |
| 4 | Conflicto por habitación ocupada o en reparación | Enviar apartado para habitación que se encuentra en `Occupied` o `DisabledForRepairs` | Habitación no `Available` | El estado físico de la habitación no cambia y se registra en `room_audit_log` el conflicto de reserva | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Concurrencia sobre habitación Available: dos procesos intentan apartar la misma habitación simultáneamente; el bloqueo de fila a nivel de base de datos (`SELECT FOR UPDATE`) asegura que solo uno aparta y el segundo detecta idempotencia o conflicto.
* **EC-002 (API)** : Transición encadenada al finalizar limpieza: al confirmar fin de limpieza en una habitación con llegada hoy, el backend ejecuta `InCleaning → Available → Reserved` en la misma transacción atómica.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
