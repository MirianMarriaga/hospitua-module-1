### Test Case Specification: Registrar Check-Out
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-registrar-check-out.md](../SPEC/spec-registrar-check-out.md) / [plan-registrar-check-out.md](../PLAN/plan-registrar-check-out.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-005-RegistrarCheckOut
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el flujo de Check-Out en pantalla (consulta de estancia, liquidación, confirmación y liberación de habitación), la transición atómica de Occupied a PendingCleaning, el cierre de la estancia en base de datos, y la emisión asíncrona vía Outbox de dos mensajes en `hospitua.events`: `habitacion.checkout` (hacia Módulo 2) y `facturacion.checkout` (hacia Módulo 3).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Registrar Check-Out | Specification Document | [documentos/SPEC/spec-registrar-check-out.md](../SPEC/spec-registrar-check-out.md) |
| Plan de Implementación de Registrar Check-Out | Plan Document | [documentos/PLAN/plan-registrar-check-out.md](../PLAN/plan-registrar-check-out.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere base de datos PostgreSQL, broker RabbitMQ y servicios backend Spring Boot en marcha.

##### Dependencies & Assumptions
* La habitación debe encontrarse en estado `Occupied` con estancia activa registrada.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado `Occupied` con reserva `RSV-HAB-101`.
* **Environment** : Ambiente de pruebas del equipo / Sandbox; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Usuario de Recepción (`recepcionista`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como recepcionista | Navegar a `/login` e ingresar credenciales | Usuario: `recepcionista` | Carga el Panel de Recepción en `/recepcion` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Seleccionar pestaña Salidas | En `/recepcion`, hacer clic en la pestaña "Salidas" | Pestaña Salidas | Se listan las habitaciones ocupadas con salida programada | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Iniciar proceso de Check-out | Hacer clic en "Check-out" en la fila de la habitación 101 | Habitación 101 | Navega a `/check-out?reservationRef=RSV-HAB-101&roomId=a0000000-0000-0000-0000-000000000101` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Paso 1: Consultar reserva | Verificar los datos de la estancia activa precargados | Habitación 101 | Muestra número de habitación, titular y fechas; habilita "Continuar a liquidación" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Paso 2: Liquidación | Revisar el resumen de cobro de hospedaje | Datos de liquidación | Se muestra desglose y se pulsa "Continuar a pago" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Paso 3: Pago | Validar estado de pago | Pago confirmado | Se habilita botón "Continuar a confirmación" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Paso 4: Confirmación | Revisar resumen final antes de liberar la habitación física | Resumen de cierre | Se muestra el botón "Confirmar Check-Out y liberar habitación" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 5 | Confirmar salida física | Hacer clic en "Confirmar Check-Out y liberar habitación" | Confirmación | Se envía `POST /api/check-out`, la habitación pasa a `PendingCleaning` y se despliega `CheckOutSuccess` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 6 | Paso 5: Pantalla de éxito (Check-out completado) | Visualizar tarjeta de confirmación de salida | Pantalla de éxito | Muestra título "Check-out completado", texto "La habitación 101 quedó en estado Pendiente de limpieza y ya está en la bandeja del personal de limpieza", badge y botón "Volver al inicio" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 7 | Retorno a recepción | Hacer clic en "Volver al inicio" | Botón "Volver al inicio" | Redirige a `/recepcion`; la habitación 101 ya no figura en Salidas y aparece como Pendiente de limpieza en el sistema | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Habitación no se encuentra ocupada | Intentar abrir check-out sobre una habitación en `Available` o `InCleaning` | Habitación no `Occupied` | Mensaje de rechazo: "La habitación no está ocupada; no se puede registrar check-out." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Estancia no encontrada | Ingresar referencia de reserva inexistente | Referencia no válida | Mensaje de error: "No se encontró una estancia activa para la habitación." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consultar estancia activa para check-out | `GET {BASE_URL}/api/check-out/active-stay?reservationRef=RSV-HAB-101&roomId=a0000000-0000-0000-0000-000000000101`<br>Headers: (sin X-User obligatorio) | Parámetros válidos | HTTP 200 OK con JSON `ActiveStayResponseDto` con datos locales de la estancia | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Registro exitoso de Check-Out | `POST {BASE_URL}/api/check-out`<br>Headers: `X-User: recepcionista`, `Content-Type: application/json`<br>Body: `{"reservationRef": "RSV-HAB-101", "roomId": "a0000000-0000-0000-0000-000000000101"}` | Datos válidos | HTTP 200 OK con JSON `CheckOutResultDto` conteniendo `stayId`, `roomNumber: "101"`, `roomStatus: "PendingCleaning"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Error por falta de X-User | `POST {BASE_URL}/api/check-out`<br>Headers: (sin X-User)<br>Body: `{"reservationRef": "RSV-HAB-101", "roomId": "..."}` | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"`, mensaje: "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Error por campos obligatorios faltantes | `POST {BASE_URL}/api/check-out`<br>Headers: `X-User: recepcionista`<br>Body: `{"reservationRef": ""}` | Falta roomId | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Error habitación no ocupada | `POST {BASE_URL}/api/check-out`<br>Headers: `X-User: recepcionista`<br>Body: `{"reservationRef": "RSV-HAB-102", "roomId": "a0000000-0000-0000-0000-000000000102"}` | Hab. en `Available` | HTTP 409 Conflict, `errorCode: "ROOM_INVALID_STATE"`, mensaje: "La habitación no está ocupada; no se puede registrar check-out." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 6 | Error estancia activa no encontrada | `POST {BASE_URL}/api/check-out`<br>Headers: `X-User: recepcionista`<br>Body: `{"reservationRef": "RSV-INEXISTENTE", "roomId": "a0000000-0000-0000-0000-000000000101"}` | Referencia no registrada | HTTP 404 Not Found, `errorCode: "ACTIVE_STAY_NOT_FOUND"`, mensaje: "No se encontró una estancia activa para la habitación." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Emisión de aviso de Check-Out hacia Módulo 2 | Ejecutar `POST /api/check-out` exitoso | Check-Out confirmado | Outbox encola y publica en exchange `hospitua.events` con routing key `habitacion.checkout` hacia `m2.habitacion.checkout.queue` con `reservationRef`, `roomId`, `movementType: DEPARTURE`, `movementDate` y ocupantes | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |
| 2 | Emisión de evento para facturación hacia Módulo 3 | Ejecutar `POST /api/check-out` exitoso | Check-Out confirmado | Outbox publica en exchange `hospitua.events` con routing key `facturacion.checkout` con `stayId`, `reservationRef`, `roomId`, `categoryRoom`, `checkInDate`, `checkOutDate` y `billingCustomer: { name, taxId }` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Desacoplamiento de Módulo 3 durante el Check-Out: si Facturación no responde, el Check-Out físico no se bloquea; se completa la salida, la habitación pasa a limpieza y el evento de facturación queda en Outbox para reintento automático.
* **EC-002 (API)** : Concurrencia de check-out: si dos usuarios intentan cerrar la misma estancia en simultáneo, el bloqueo pesimista garantiza que el segundo recibe `ROOM_INVALID_STATE` o `ACTIVE_STAY_NOT_FOUND`.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |

--------------------------------------------------------------------------------

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 20 | 20 | 0 | 0 | APROBADO |
