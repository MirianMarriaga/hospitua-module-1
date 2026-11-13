### Test Case Specification: Dar de Baja Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-dar-de-baja-habitacion.md](../SPEC/spec-dar-de-baja-habitacion.md) / [plan-dar-de-baja-habitacion.md](../PLAN/plan-dar-de-baja-habitacion.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-020-DarDeBajaHabitacion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el retiro formal de una habitación del inventario activo hotelero (`POST /api/rooms/{roomId}/decommission`), exigiendo que la habitación se encuentre en estado Available, validando la consulta síncrona a Módulo 2 para asegurar la inexistencia de reservas activas en los próximos 365 días, la transición a Inactive y la consulta de la baja vigente (`GET /api/rooms/{roomId}/decommission`).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Dar de Baja Habitación | Specification Document | [documentos/SPEC/spec-dar-de-baja-habitacion.md](../SPEC/spec-dar-de-baja-habitacion.md) |
| Plan de Implementación de Dar de Baja Habitación | Plan Document | [documentos/PLAN/plan-dar-de-baja-habitacion.md](../PLAN/plan-dar-de-baja-habitacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot, frontend React, base de datos PostgreSQL y servicio externo de reservas (Módulo 2) activo o simulado. La pantalla `/administracion/habitaciones/:roomId/baja` se encuentra implementada.

##### Dependencies & Assumptions
* La habitación debe estar en estado `Available` y sin reservas asignadas ni futuras.

##### Preconditions & Default Setup
* **Initial State** : Habitación 102 en estado `Available` sin reservas en los próximos 365 días.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Administrador (`admin`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Navegar a `/login` e ingresar credenciales de Administrador | Usuario: `admin` | Carga el Inventario en `/administracion/inventario` | [ ] | |
| 2 | Seleccionar acción Dar de baja | En `/administracion/inventario`, ubicar la habitación 102 (`Available`) y pulsar "Dar de baja" | Habitación 102 | Navega a `/administracion/habitaciones/a0000000-0000-0000-0000-000000000102/baja` | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Completar motivo de baja | En `/administracion/habitaciones/:roomId/baja`, seleccionar Motivo: "Remodelación estructural" e ingresar Detalle: "Integración física con habitación contigua para ampliación de suite" | Motivo y detalle válidos | Se habilita el botón de confirmación "Confirmar retiro del inventario" | [ ] | |
| 2 | Confirmar diálogo de advertencia | Hacer clic en "Confirmar retiro del inventario" y aceptar el diálogo de advertencia "¿Estás seguro de dar de baja la habitación 102?" | Confirmación modal | Se ejecuta la baja vía API, la habitación transiciona a `Inactive`, se registra en el historial y redirige a `/administracion/inventario` con aviso: "Habitación 102 dada de baja exitosamente." | [ ] | |
| 3 | Comprobar exclusión de disponibles | En `/administracion/inventario`, filtrar por estado "Disponible" | Filtro "Disponible" | La habitación 102 ya no aparece en el listado de disponibles | [ ] | |
| 4 | Comprobar presencia en inactivas | En `/administracion/inventario`, filtrar por estado "Inactiva" | Filtro "Inactiva" | La habitación 102 aparece con badge "Inactiva" y opción de reactivación | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Motivo no seleccionado | Dejar el selector de motivo sin elegir | Motivo vacío | Mensaje de validación: "Selecciona un motivo válido para la baja." | [ ] | |
| 2 | Detalle explicativo vacío | Dejar el campo de texto de detalle en blanco | Detalle vacío | Mensaje de validación: "Ingresa el detalle explicativo del motivo de baja." | [ ] | |
| 3 | Habitación no disponible | Intentar dar de baja una habitación que no está en `Available` (ej. en limpieza u ocupada) | Habitación en otro estado | Mensaje de rechazo: "Solo se pueden dar de baja habitaciones en estado Disponible (estado actual: {estado})." | [ ] | |
| 4 | Conflicto con reservas futuras en Módulo 2 | Intentar dar de baja una habitación que tiene reservas confirmadas en los próximos 365 días | Habitación con reserva futura | Mensaje de conflicto: "No se puede dar de baja la habitación: tiene reservas confirmadas en los próximos 365 días." | [ ] | |
| 5 | Caída o falla de Módulo 2 | Intentar dar de baja con el servicio de Módulo 2 caído o con timeout | Módulo 2 apagado | Mensaje de alerta: "No fue posible verificar las reservas de la habitación con el módulo de reservas. Intenta más tarde." | [ ] | |
| 6 | Sesión terminada | Enviar la baja sin sesión activa | Sin sesión | Mensaje de advertencia: "Tu sesión terminó. Inicia sesión de nuevo." | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Retiro exitoso de habitación | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102/decommission`<br>Headers: `X-User: admin`, `Content-Type: application/json`<br>Body: `{"reasonCategory": "REMODELING", "reasonDetail": "Integración con habitación contigua"}` | Hab. 102 en `Available` sin reservas | HTTP 200 OK con JSON `RoomDecommissionDto` `{ "roomId": "...", "status": "Inactive", "decommissionedAt": "..." }` | [ ] | |
| 2 | Consulta de baja vigente | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102/decommission`<br>Headers: (sin X-User obligatorio) | Hab. recién dada de baja | HTTP 200 OK con JSON `RoomDecommissionInfoDto` conteniendo motivo y detalles de la baja | [ ] | |
| 3 | Error por falta de X-User | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102/decommission`<br>Headers: (sin X-User)<br>Body: `{"reasonCategory": "REMODELING", "reasonDetail": "..."}` | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"` | [ ] | |
| 4 | Error por campos obligatorios faltantes | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102/decommission`<br>Headers: `X-User: admin`<br>Body: `{"reasonDetail": ""}` | Sin motivo ni detalle | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, `details.fields: ["reasonCategory", "reasonDetail"]` | [ ] | |
| 5 | Error habitación inexistente | `POST {BASE_URL}/api/rooms/b0000000-0000-0000-0000-000000000999/decommission`<br>Headers: `X-User: admin`<br>Body: `{"reasonCategory": "REMODELING", "reasonDetail": "..."}` | UUID inexistente | HTTP 404 Not Found, `errorCode: "ROOM_NOT_FOUND"` | [ ] | |
| 6 | Error habitación no en Available | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101/decommission`<br>Headers: `X-User: admin`<br>Body: `{"reasonCategory": "REMODELING", "reasonDetail": "..."}` | Habitación en `Occupied` | HTTP 409 Conflict, `errorCode: "ROOM_INVALID_STATE"`, mensaje: "Solo se pueden dar de baja habitaciones en estado Disponible (estado actual: Occupied)." | [ ] | |
| 7 | Error por conflicto con reservas en Módulo 2 | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000103/decommission`<br>Headers: `X-User: admin`<br>Body: `{"reasonCategory": "REMODELING", "reasonDetail": "..."}` | Habitación con reserva activa | HTTP 409 Conflict, `errorCode: "RESERVATION_CONFLICT"`, mensaje: "No se puede dar de baja la habitación: tiene reservas confirmadas en los próximos 365 días." | [ ] | |
| 8 | Error por servicio Módulo 2 no disponible | `POST {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102/decommission`<br>Headers: `X-User: admin`<br>Body: `{"reasonCategory": "REMODELING", "reasonDetail": "..."}` | M2 desconectado | HTTP 424 Failed Dependency, `errorCode: "MODULE_2_UNAVAILABLE"` | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : Asignación de reserva en el instante previo a confirmar: si una reserva entra al sistema justo antes de pulsar confirmar, el backend cancela la baja con HTTP 409 y la interfaz refresca el estado.
* **EC-002 (API)** : Exclusión estricta de habitaciones inactivas para Módulo 2: verificar que tras la baja, `GET /api/rooms` no incluye la habitación dada de baja en ninguna de sus consultas.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
