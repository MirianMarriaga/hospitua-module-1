### Test Case Specification: Registrar Check-In
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-registrar-check-in.md](../SPEC/spec-registrar-check-in.md) / [plan-registrar-check-in.md](../PLAN/plan-registrar-check-in.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-003-RegistrarCheckIn
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el flujo de Check-In en 4 pasos (validación de reserva, captura de huéspedes, confirmación y pantalla de éxito), la transición atómica de la habitación a Occupied, el registro de estancia y ocupantes, y la emisión asíncrona mediante Outbox del mensaje plano hacia Módulo 2 con routing key `habitacion.checkin` en el exchange `hospitua.events`.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Registrar Check-In | Specification Document | [documentos/SPEC/spec-registrar-check-in.md](../SPEC/spec-registrar-check-in.md) |
| Plan de Implementación de Registrar Check-In | Plan Document | [documentos/PLAN/plan-registrar-check-in.md](../PLAN/plan-registrar-check-in.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere base de datos PostgreSQL, broker RabbitMQ y servicio backend Spring Boot en ejecución.

##### Dependencies & Assumptions
* La habitación debe encontrarse previamente en estado `Reserved` vinculada a la reserva del día (`reservedByReservationRef`).

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado `Reserved` con reserva asignada para hoy (`RSV-HAB-101`).
* **Environment** : Ambiente de pruebas del equipo / Sandbox; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Usuario con área Recepción (`recepcionista`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión y acceder a recepción | Iniciar sesión como recepcionista y abrir `/recepcion` | Usuario: `recepcionista` | Se carga el Panel de Recepción con la lista de llegadas del día | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Iniciar Check-in desde la fila de llegada | En la pestaña Llegadas, ubicar la fila correspondiente a la habitación 101 (titular Carlos Gómez) y hacer clic en "Check-in" | Habitación 101, titular Carlos Gómez | Redirige a `/check-in?reservationRef=RSV-HAB-101&roomId=a0000000-0000-0000-0000-000000000101` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Paso 1: Validar reserva | En `/check-in`, revisar los datos precargados de la reserva (Huésped principal, Documento, Fechas, Fuente, Habitación 101 y Estado) | Parámetros en URL | El sistema valida automáticamente contra la copia local y habilita el botón "Continuar" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Paso 2: Datos de huéspedes | Completar los datos del titular y acompañantes:<br>- Nombre: Carlos, Apellido: Gómez<br>- Tipo Doc: CC, Número: 1098765432<br>- Nacionalidad: Colombiana, Fecha Nac: 1988-05-14<br>- Origen: Bogotá, Destino: Cartagena | Datos de 1 huésped | Los campos se validan y se habilita el botón "Continuar" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Paso 3: Confirmación | Revisar el resumen completo de la estancia (habitación, fechas y lista de ocupantes) | Resumen de estancia | Se presenta el botón "Confirmar Check-In" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Confirmar Check-In | Hacer clic en "Confirmar Check-In" | Acción en pantalla | Se envía `POST /api/check-in`, se crea la estancia, la habitación pasa a `Occupied` y se pasa a la pantalla de éxito | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 5 | Paso 4: Pantalla de éxito (Check-In completado) | Visualizar pantalla de confirmación `CheckInSuccess` | Resultado exitoso | Muestra tarjeta de éxito con título "Check-in completado", número de habitación 101, badge "Ocupada", reserva "En curso" y botón "Volver al inicio" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 6 | Retorno a recepción | Hacer clic en "Volver al inicio" | Botón de retorno | Redirige a `/recepcion`; la llegada desaparece de Llegadas y la habitación 101 figura como Ocupada | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Huésped con datos obligatorios incompletos | Dejar el número de documento en blanco en el paso 2 | Documento vacío | Se resalta el campo con mensaje: "El número de documento es obligatorio." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Fecha de nacimiento en el futuro | Ingresar una fecha de nacimiento posterior a hoy | Fecha futura | Mensaje de validación: "La fecha de nacimiento no puede ser futura." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Documentos duplicados entre ocupantes | Ingresar el mismo tipo y número de documento para dos huéspedes | Documento repetido | Mensaje de error: "Hay documentos de identidad duplicados entre los huéspedes." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Superar la capacidad máxima de la habitación | Intentar registrar 2 ocupantes en habitación 101 (capacidad máxima: 1) | 2 huéspedes en sencilla | Mensaje de rechazo: "El número de huéspedes supera la capacidad máxima de la habitación (1)." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 5 | Habitación ya no disponible (conflicto concurrente) | Intentar confirmar check-in sobre habitación que cambió de estado | Habitación en `Occupied` | Diálogo de conflicto: "La habitación no está disponible para check-in (estado actual: Ocupada)." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 6 | Reserva no encontrada en la copia local | Abrir check-in con referencia inexistente | Referencia "RSV-999" | Mensaje de error: "No se encontró la reserva indicada." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Validar reserva para check-in (Paso 1) | `GET {BASE_URL}/api/check-in/validate?reservationRef=RSV-HAB-101&roomId=a0000000-0000-0000-0000-000000000101`<br>Headers: (sin X-User obligatorio) | Parámetros válidos | HTTP 200 OK con JSON `CheckInValidationDto` conteniendo `valid: true`, datos de titular y habitación | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Registro exitoso de Check-In (Paso 2) | `POST {BASE_URL}/api/check-in`<br>Headers: `X-User: recepcionista`, `Content-Type: application/json`<br>Body: `{"reservationRef": "RSV-HAB-101", "roomId": "a0000000-0000-0000-0000-000000000101", "guests": [{"firstName": "Carlos", "lastName": "Gómez", "documentType": "CC", "documentNumber": "1098765432", "nationality": "Colombiana", "birthDate": "1988-05-14", "originPlace": "Bogotá", "destinationPlace": "Cartagena", "isReservationGuest": true}]}` | Datos válidos | HTTP 201 Created con JSON `CheckInResultDto` conteniendo `stayId`, `roomNumber: "101"`, `roomStatus: "Occupied"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Error por falta de encabezado X-User | `POST {BASE_URL}/api/check-in`<br>Headers: (sin X-User)<br>Body: (payload válido) | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"`, mensaje: "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Error por capacidad de habitación excedida | `POST {BASE_URL}/api/check-in`<br>Headers: `X-User: recepcionista`<br>Body: (payload con 2 huéspedes para hab. 101) | Capacidad excedida | HTTP 400 Bad Request, `errorCode: "ROOM_CAPACITY_EXCEEDED"`, mensaje: "El número de huéspedes supera la capacidad máxima de la habitación (1)." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Error por documentos duplicados | `POST {BASE_URL}/api/check-in`<br>Headers: `X-User: recepcionista`<br>Body: (payload con 2 huéspedes con mismo doc) | Doc duplicado | HTTP 400 Bad Request, `errorCode: "DUPLICATE_GUEST_DOCUMENT"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 6 | Error por habitación en estado inválido | `POST {BASE_URL}/api/check-in`<br>Headers: `X-User: recepcionista`<br>Body: (roomId de habitación en `InCleaning`) | Habitación no reservada | HTTP 409 Conflict, `errorCode: "ROOM_INVALID_STATE"`, mensaje: "La habitación no está disponible para check-in (estado actual: En limpieza)." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 7 | Error por estancia ya registrada | `POST {BASE_URL}/api/check-in`<br>Headers: `X-User: recepcionista`<br>Body: (misma reserva ya procesada) | Check-in repetido | HTTP 409 Conflict, `errorCode: "STAY_ALREADY_EXISTS"`, mensaje: "Ya existe un check-in registrado para esta reserva." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Emisión de mensaje plano de Check-In hacia Módulo 2 | Completar exitosamente `POST /api/check-in` | Check-in registrado | Módulo 1 encola en Outbox y publica en exchange `hospitua.events` con routing key `habitacion.checkin` hacia la cola `m2.habitacion.checkin.queue` el payload plano con `reservationRef`, `roomId`, `movementType: ENTRY`, `movementDate` y la lista de ocupantes | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : Doble clic consecutivo en "Confirmar Check-In": el frontend desactiva el botón durante el envío impidiendo peticiones concurrentes duplicadas desde el mismo cliente.
* **EC-002 (API)** : Concurrencia de dos recepcionistas sobre la misma habitación: el bloqueo pesimista en base de datos asegura que la primera transacción confirma y la segunda recibe HTTP 409 `ROOM_INVALID_STATE`.

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
| 22 | 22 | 0 | 0 | APROBADO |
