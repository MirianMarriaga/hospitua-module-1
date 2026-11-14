### Test Case Specification: Consultar Reservas
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-reservas.md](../SPEC/spec-consultar-reservas.md) / [plan-consultar-reservas.md](../PLAN/plan-consultar-reservas.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-001-ConsultarReservas
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la ingesta asíncrona de la lista diaria de reservas desde Módulo 2 vía RabbitMQ (`m1.reservas.diarias.queue`), la consulta local en memoria/base de datos de llegadas pendientes (`GET /api/reception/arrivals`), y la verificación síncrona de conflictos de reservas (`Module2ReservationConflictClientPort`) para bloqueos técnicos y bajas sin exponer llamadas a Módulo 2 en la carga del panel.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Reservas | Specification Document | [documentos/SPEC/spec-consultar-reservas.md](../SPEC/spec-consultar-reservas.md) |
| Plan de Implementación de Consultar Reservas | Plan Document | [documentos/PLAN/plan-consultar-reservas.md](../PLAN/plan-consultar-reservas.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Las llegadas de recepción se consultan exclusivamente desde la copia local (`daily_reservation`); la consulta externa REST a Módulo 2 solo ocurre internamente al validar conflictos de bloqueo técnico y bajas.

##### Dependencies & Assumptions
* Broker RabbitMQ con exchange `hospitua.events` y cola `m1.reservas.diarias.queue` activos. Servicio de Módulo 2 disponible para pruebas de cliente REST.

##### Preconditions & Default Setup
* **Initial State** : Base de datos con habitaciones V2 inicializadas.
* **Environment** : Ambiente de pruebas del equipo / Sandbox; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Usuario de Recepción (`recepcionista`), Administrador (`admin`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión en el sistema | Navegar a `/login` e ingresar credenciales válidas de recepcionista | Usuario: `recepcionista` | Redirige automáticamente al Panel de Recepción en `/recepcion` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Visualizar pestaña de Llegadas | En `/recepcion`, comprobar que la pestaña activa por defecto es "Llegadas" | Pestaña "Llegadas" activa | Se muestra el listado de reservas del día pendientes de check-in cargadas desde la copia local | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Visualizar reservas cargadas | Observar la tabla de llegadas tras la ingesta de las 00:00 | Copia local de reservas | Se muestran filas con la información de llegada: Habitación, Huésped principal (nombre y documento), Noches, Huéspedes, Categoría, Canal y botón "Check-in" (la referencia de reserva no se muestra como texto en pantalla) | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Filtrar llegadas por nombre | Escribir en el campo de búsqueda "Carlos" | Búsqueda: "Carlos" | La tabla filtra localmente mostrando únicamente las reservas de huéspedes cuyo nombre o referencia coincide | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Limpiar filtro de búsqueda | Borrar el contenido del campo de búsqueda | Campo vacío | Se vuelve a mostrar la lista completa de llegadas pendientes del día | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Navegación hacia Check-in | Hacer clic en el botón "Check-in" correspondiente a la habitación 101 | Habitación 101 | Navega a la ruta `/check-in?reservationRef=RSV-HAB-101&roomId=...` cargando el wizard de registro | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda sin coincidencias | Ingresar un término de búsqueda inexistente en el filtro de llegadas | Texto: "ZzZzZz" | La tabla muestra el estado vacío: "No se encontraron reservas con el criterio de búsqueda." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Búsqueda superior a 100 caracteres | Ingresar una cadena de 101 caracteres en el campo de búsqueda | Longitud > 100 | El sistema valida la longitud y muestra mensaje de validación: "La búsqueda admite máximo 100 caracteres." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consultar llegadas de hoy sin filtro | `GET {BASE_URL}/api/reception/arrivals`<br>Headers: (sin X-User obligatorio) | Sin parámetros | HTTP 200 OK con JSON `PendingArrivalsDto` conteniendo `items` desde la tabla local `daily_reservation` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Consultar llegadas con filtro de búsqueda | `GET {BASE_URL}/api/reception/arrivals?query=Carlos` | Búsqueda válida | HTTP 200 OK con las reservas filtradas que coinciden en titular o referencia | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Búsqueda que supera el límite de caracteres | `GET {BASE_URL}/api/reception/arrivals?query=` (101 caracteres) | Cadena de 101 caracteres | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, mensaje: "La búsqueda admite máximo 100 caracteres." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Consulta interna de cliente REST a Módulo 2 (sin conflictos) | Consulta interna de verificación de reservas hacia Módulo 2 para hab. 101 sin reservas futuras | Rango de fechas libre | HTTP 200 OK de Módulo 2 con `items: []`, permitiendo continuar el flujo de bloqueo o baja | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Consulta interna de cliente REST a Módulo 2 (con conflicto) | Invocación interna con fechas donde Módulo 2 reporta reserva activa | Rango con reserva | Módulo 2 responde 200 con `items` activos; Módulo 1 lanza `ReservationConflictException` (HTTP 409 `RESERVATION_CONFLICT`) | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 6 | Caída o indisponibilidad de Módulo 2 | Invocación interna de verificación con Módulo 2 no disponible o timeout | Servicio M2 caído | El cliente REST captura timeout/falla y lanza `ExternalModule2UnavailableException` (HTTP 424 `MODULE_2_UNAVAILABLE`) | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

##### Sección E — Mensajería
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Ingesta de lista diaria de reservas (00:00) | Publicar mensaje en exchange `hospitua.events` con routing key `reserva.lista-del-dia` hacia cola `m1.reservas.diarias.queue` | Lista de reservas de hoy en formato JSON plano | El listener consume el mensaje, limpia/actualiza `daily_reservation`, persiste en `daily_reservation_message_log` y transiciona habitaciones a `Reserved` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |
| 2 | Ingesta de actualización ADDED | Publicar mensaje con routing key `reserva.lista-del-dia.actualizacion` con `action: "ADDED"` | Nueva reserva para hoy | Se agrega la reserva a la copia local y la habitación pasa a `Reserved` si estaba en `Available` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |
| 3 | Ingesta de actualización REMOVED | Publicar mensaje con routing key `reserva.lista-del-dia.actualizacion` con `action: "REMOVED"` | Cancelación de reserva de hoy | Se retira la reserva de la copia local y la habitación pasa a `Available` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |
| 4 | Mensaje duplicado por reintento de broker | Reenviar mensaje con mismo `messageId` y `sequenceNumber` | Mensaje ya procesado | Idempotencia: el sistema descarta el duplicado consultando `daily_reservation_message_log` sin error | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |
| 5 | Mensaje con formato inválido | Enviar mensaje con JSON corrupto a `m1.reservas.diarias.queue` | JSON malformado | Se lanza `AmqpRejectAndDontRequeueException` y el mensaje es enrutado al Dead-Letter Exchange `hospitua.events.dlx` hacia `m1.reservas.diarias.dlq` | [x] Pass | 2026-11-13: Verificado con broker local RabbitMQ y Dead-Letter Exchange |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Desfase de reloj a las 00:00: la ingesta nocturna procesa primero la lista diaria antes de aplicar los bloqueos técnicos del nuevo día para evitar colisiones de estado.
* **EC-002 (API)** : Timeout de lectura de Módulo 2: llamadas al cliente REST de Módulo 2 aplican timeout estricto (1s conexión, 2s lectura) devolviendo 424 `MODULE_2_UNAVAILABLE` sin bloquear hilos.

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
| 19 | 19 | 0 | 0 | APROBADO |
