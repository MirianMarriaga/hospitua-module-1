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
Este caso de prueba verifica la transición autónoma de habitaciones al estado PendingCleaning como resultado directo de dos eventos de negocio: la formalización de un Check-Out (`POST /api/check-out`) o la confirmación de finalización de una reparación técnica (`POST /api/maintenance/tasks/{taskId}/complete`), garantizando el ingreso inmediato al panel de limpieza y la trazabilidad en el historial.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Marcar Pendiente a Limpieza | Specification Document | [documentos/SPEC/spec-marcar-pendiente-a-limpieza.md](../SPEC/spec-marcar-pendiente-a-limpieza.md) |
| Plan de Implementación de Marcar Pendiente a Limpieza | Plan Document | [documentos/PLAN/plan-marcar-pendiente-a-limpieza.md](../PLAN/plan-marcar-pendiente-a-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Transición de dominio autónoma; no posee endpoint de mutación directa ni formulario interactivo propio.

##### Dependencies & Assumptions
* Requiere TC-005 Registrar Check-Out o TC-014 Confirmar Reparación Finalizada.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado `Occupied`. Habitación 102 en estado `DisabledForRepairs`.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Usuario de Recepción (`recepcionista`), Mantenimiento (`mtorres`), Limpieza (`limpiador`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como personal de limpieza | Navegar a `/login` e ingresar credenciales de limpieza | Usuario: `limpiador` | Redirige al Panel de Limpieza en `/limpieza` | [ ] | |
| 2 | Visualizar habitaciones pendientes | Observar la cuadrícula o lista en `/limpieza` | Bandeja de aseo | Muestra las tarjetas de habitaciones en estado `PendingCleaning` con botón "Iniciar limpieza" | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Generación de pendiente por Check-Out | Realizar el Check-Out de la habitación 101 desde recepción | Check-out completado | En `/limpieza`, la tarjeta de la habitación 101 aparece de inmediato como "Pendiente de limpieza" | [ ] | |
| 2 | Generación de pendiente por reparación técnica | Completar la reparación de la habitación 102 desde mantenimiento | Reparación finalizada | En `/limpieza`, la habitación 102 ingresa en la bandeja de pendientes de limpieza | [ ] | |
| 3 | Visualización en Inventario | Consultar `/administracion/inventario` | Inventario | Las habitaciones reflejan el badge ámbar "Pendiente de limpieza" | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intento de check-in en habitación pendiente | Intentar iniciar check-in sobre habitación en `PendingCleaning` | Habitación no limpia | La fila de recepción alerta: "No disponible: Pendiente de limpieza" y bloquea el check-in | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Verificación de estado tras Check-Out | `GET {BASE_URL}/api/rooms/inventory/a0000000-0000-0000-0000-000000000101` | Hab. tras check-out | HTTP 200 OK con `{ "status": "PendingCleaning" }` | [ ] | |
| 2 | Verificación en Panel de Limpieza | `GET {BASE_URL}/api/cleaning/panel`<br>Headers: `X-User: limpiador` | Usuario de limpieza | HTTP 200 OK con `CleaningPanelDto` conteniendo la habitación 101 en `pendingRooms` | [ ] | |
| 3 | Rechazo de asignación comercial para M2 | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101` | Habitación pendiente | HTTP 200 OK con datos físicos pero no disponible para ocupación inmediata | [ ] | |
| 4 | Verificación de trazabilidad en historial | `GET {BASE_URL}/api/room-state-history?roomId=a0000000-0000-0000-0000-000000000101` | Historial | HTTP 200 OK mostrando transición con `sourceFlow: "CHECK_OUT"` o `sourceFlow: "COMPLETE_REPAIR"` | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Atomicidad transaccional: el paso a `PendingCleaning` se ejecuta en la misma transacción que el check-out o fin de reparación; si el cambio de estado falla, toda la operación se revierte.
* **EC-002 (API)** : Inmutabilidad de pendientes: la habitación permanece en `PendingCleaning` hasta que un camarero inicia explícitamente su tarea vía `POST /api/cleaning/tasks`.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
