### Test Case Specification: Consultar Panel de Recepción
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-panel-recepcion.md](../SPEC/spec-consultar-panel-recepcion.md) / [plan-consultar-panel-recepcion.md](../PLAN/plan-consultar-panel-recepcion.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-002-ConsultarPanelRecepcion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la carga, visualización y búsqueda en las pestañas del Panel de Recepción (`/recepcion`), incluyendo Llegadas (`GET /api/reception/arrivals`), Salidas (`GET /api/reception/departures`) y los indicadores del turno (`GET /api/reception/panel/kpis`), garantizando que la información se sirve exclusivamente desde la persistencia local de Módulo 1 sin llamadas directas a Módulo 2.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación del Panel de Recepción | Specification Document | [documentos/SPEC/spec-consultar-panel-recepcion.md](../SPEC/spec-consultar-panel-recepcion.md) |
| Plan de Implementación del Panel de Recepción | Plan Document | [documentos/PLAN/plan-consultar-panel-recepcion.md](../PLAN/plan-consultar-panel-recepcion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Todas las consultas del panel leen datos locales de la base de datos de Módulo 1; no dependen de la disponibilidad síncrona de Módulo 2 en tiempo de ejecución.

##### Dependencies & Assumptions
* Requiere base de datos PostgreSQL y frontend React en marcha.

##### Preconditions & Default Setup
* **Initial State** : Reservas del día cargadas en `daily_reservation` y estancias activas en `stay`.
* **Environment** : Ambiente de pruebas del equipo / Sandbox; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Personal de Recepción (`recepcionista`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como recepcionista | Navegar a `/login`, ingresar credenciales de recepción | Usuario: `recepcionista` | El sistema redirige a `/recepcion` (ReceptionPanel) | [ ] | |
| 2 | Visualizar estructura del panel | Comprobar encabezado, métricas KPI y pestañas principales en `/recepcion` | Ruta `/recepcion` | Se muestra el panel con métricas superiores y pestañas: Llegadas, Huéspedes y Salidas | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Verificar indicadores KPI | Observar tarjetas de KPI en la parte superior del panel | Datos locales | Se muestran los 4 contadores: Llegadas hoy, Huéspedes activos, Salidas hoy y Check-ins pendientes | [ ] | |
| 2 | Consultar pestaña Llegadas | Clic en la pestaña "Llegadas" | Pestaña Llegadas | Lista las habitaciones de reservas de hoy pendientes de check-in con botón "Check-in" | [ ] | |
| 3 | Buscar en Llegadas | Escribir término de búsqueda en el campo de texto | Filtro "Gómez" | Filtra en tiempo real las llegadas coincidentes | [ ] | |
| 4 | Consultar pestaña Salidas | Clic en la pestaña "Salidas" | Pestaña Salidas | Lista las estancias activas con salida prevista para hoy o vencidas, mostrando botón "Check-out" | [ ] | |
| 5 | Identificar salidas vencidas | Observar estancias cuya fecha de salida es anterior a hoy | Salidas vencidas | Se resaltan visualmente al inicio de la lista con badge o alerta de salida vencida | [ ] | |
| 6 | Buscar en Salidas | Escribir número de habitación en el buscador de Salidas | Filtro "101" | Filtra mostrando la estancia activa asociada a la habitación 101 | [ ] | |
| 7 | Navegar a Check-out | Hacer clic en botón "Check-out" de una estancia en la pestaña Salidas | Estancia de hab. 101 | Redirige a `/check-out?reservationRef=...&roomId=...` cargando el flujo de salida | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda sin coincidencias en Salidas | Ingresar criterio no existente en buscador de salidas | Búsqueda: "NoExiste999" | La tabla muestra estado vacío: "No hay salidas programadas para el criterio seleccionado." | [ ] | |
| 2 | Validación de longitud en buscador de Llegadas | Ingresar más de 100 caracteres en el campo de búsqueda | 101 caracteres | Mensaje de validación: "La búsqueda admite máximo 100 caracteres." | [ ] | |
| 3 | Validación de longitud en buscador de Salidas | Ingresar más de 100 caracteres en el campo de salidas | 101 caracteres | Mensaje de validación: "La búsqueda admite máximo 100 caracteres." | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consultar llegadas pendientes | `GET {BASE_URL}/api/reception/arrivals`<br>Headers: (sin X-User obligatorio) | Sin parámetros | HTTP 200 OK con JSON `PendingArrivalsDto` conteniendo reservas locales pendientes de check-in | [ ] | |
| 2 | Consultar llegadas con búsqueda | `GET {BASE_URL}/api/reception/arrivals?query=Carlos` | Query válido | HTTP 200 OK con resultados coincidentes | [ ] | |
| 3 | Error por búsqueda de llegadas excesiva | `GET {BASE_URL}/api/reception/arrivals?query=` (101 caracteres) | 101 caracteres | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, mensaje: "La búsqueda admite máximo 100 caracteres." | [ ] | |
| 4 | Consultar salidas del día y vencidas | `GET {BASE_URL}/api/reception/departures`<br>Headers: (sin X-User obligatorio) | Sin parámetros | HTTP 200 OK con JSON `DeparturesDto` con estancias activas con salida hoy o vencidas | [ ] | |
| 5 | Consultar salidas con búsqueda | `GET {BASE_URL}/api/reception/departures?query=101` | Query "101" | HTTP 200 OK con estancias coincidentes | [ ] | |
| 6 | Error por búsqueda de salidas excesiva | `GET {BASE_URL}/api/reception/departures?query=` (101 caracteres) | 101 caracteres | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, mensaje: "La búsqueda admite máximo 100 caracteres." | [ ] | |
| 7 | Consultar indicadores KPI del turno | `GET {BASE_URL}/api/reception/panel/kpis`<br>Headers: (sin X-User obligatorio) | Sin parámetros | HTTP 200 OK con JSON conteniendo `arrivalsToday`, `activeStays`, `departuresToday`, `pendingCheckIns` | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : Salidas vencidas sin check-out: estancias cuya fecha de salida prevista expiró ayer deben ordenarse estrictamente antes que las salidas de hoy en la pestaña de Salidas.
* **EC-002 (API)** : Ausencia de lista diaria a las 00:00: si la ingesta nocturna no llegó, el campo `arrivalsToday` en los KPIs se devuelve nulo sin fallar la petición (HTTP 200 OK).

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
