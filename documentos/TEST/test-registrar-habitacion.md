### Test Case Specification: Registrar Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-registrar-habitacion.md](../SPEC/spec-registrar-habitacion.md) / [plan-registrar-habitacion.md](../PLAN/plan-registrar-habitacion.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-018-RegistrarHabitacion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el registro de nuevas habitaciones en el inventario hotelero (`POST /api/rooms`), validando las reglas de unicidad de número, pisos mayores a cero, capacidades enteras positivas, categorías admitidas (SENCILLA, DOBLE, SUITE, BOUTIQUE), tarifas con hasta dos decimales, y la inicialización automática en estado Available con registro en el historial.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Registrar Habitación | Specification Document | [documentos/SPEC/spec-registrar-habitacion.md](../SPEC/spec-registrar-habitacion.md) |
| Plan de Implementación de Registrar Habitación | Plan Document | [documentos/PLAN/plan-registrar-habitacion.md](../PLAN/plan-registrar-habitacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot, frontend React y base de datos PostgreSQL activos. La ruta `/administracion/habitaciones/nueva` se encuentra implementada.

##### Dependencies & Assumptions
* Requiere sesión activa de Administrador enviando encabezado `X-User`.

##### Preconditions & Default Setup
* **Initial State** : Base de datos con semilla V2 cargada (14 habitaciones iniciales); número "407" disponible para registro.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Usuario Administrador (`admin`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Navegar a `/login`, ingresar credenciales de Administrador | Usuario: `admin` | Redirige al inventario físico en `/administracion/inventario` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Navegar al formulario de registro | En `/administracion/inventario`, hacer clic en el botón superior "Nueva habitación" | Botón "Nueva habitación" | Carga el formulario de creación en `/administracion/habitaciones/nueva` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Diligenciar datos de la habitación | En `/administracion/habitaciones/nueva`, completar:<br>- Número de habitación: "407"<br>- Piso: 4<br>- Tipo: "Boutique"<br>- Capacidad máxima: 2<br>- Tarifa base por noche: 320000.00 | Datos válidos | Los campos validan el formato y habilitan el botón "Guardar habitación" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Guardar registro | Hacer clic en "Guardar habitación" | Envío de formulario | Se envía la petición vía `POST /api/rooms`, se crea la habitación en `Available` y redirige a `/administracion/inventario` con la notificación: "Habitación 407 registrada exitosamente." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Verificar presencia en inventario | En `/administracion/inventario`, filtrar por número "407" | Habitación 407 | La fila muestra número 407, Piso 4, Boutique, Capacidad 2, Tarifa $320.000 y estado "Disponible" | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Campos obligatorios vacíos | Dejar campos en blanco y pulsar "Guardar habitación" | Formulario vacío | Mensaje de validación: "Completa los campos obligatorios: {campos}." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Número de más de 10 caracteres | Ingresar número "12345678901" | Número de 11 caracteres | Mensaje de validación: "El número de habitación debe tener máximo 10 caracteres." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Piso menor o igual a 0 | Ingresar piso "0" o "-1" | Piso 0 | Mensaje de validación: "El piso debe ser un número entero mayor a 0." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Capacidad menor o igual a 0 | Ingresar capacidad "0" o "-2" | Capacidad 0 | Mensaje de validación: "La capacidad máxima debe ser un número entero mayor a 0." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 5 | Tarifa menor o igual a cero o decimales excesivos | Ingresar tarifa "0.00" o "150000.555" | Tarifa 0 o 3 decimales | Mensaje de validación: "La tarifa base debe ser mayor a 0 y tener máximo dos decimales." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 6 | Tipo fuera del catálogo | Forzar tipo no estándar (ej. "Presidencial") | Tipo no admitido | Mensaje de validación: "Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 7 | Número de habitación ya existente | Ingresar número "101" (ya existente en la semilla) | Habitación 101 duplicada | Mensaje de conflicto: "Ya existe una habitación con el número 101." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 8 | Sesión expirada | Enviar formulario con token expirado o sin sesión activa | Sesión caducada | Mensaje de advertencia: "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registro exitoso de habitación | `POST {BASE_URL}/api/rooms`<br>Headers: `X-User: admin`, `Content-Type: application/json`<br>Body: `{"roomNumber": "407", "floor": 4, "categoryRoom": "BOUTIQUE", "maxCapacity": 2, "baseRate": 320000.00}` | Datos válidos | HTTP 201 Created con JSON `RoomDto` conteniendo `id`, `roomNumber: "407"`, `floor: 4`, `categoryRoom: "BOUTIQUE"`, `maxCapacity: 2`, `baseRate": 320000.00`, `status: "Available"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Error por falta de X-User | `POST {BASE_URL}/api/rooms`<br>Headers: (sin X-User)<br>Body: `{"roomNumber": "408", ...}` | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"`, mensaje: "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Error por campos obligatorios faltantes | `POST {BASE_URL}/api/rooms`<br>Headers: `X-User: admin`<br>Body: `{"floor": 4}` | Faltan campos | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, `details.fields` enumera los campos faltantes | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Error por número mayor a 10 caracteres | `POST {BASE_URL}/api/rooms`<br>Headers: `X-User: admin`<br>Body: `{"roomNumber": "HABITACION407", "floor": 4, "categoryRoom": "BOUTIQUE", "maxCapacity": 2, "baseRate": 320000.00}` | Longitud 13 | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, `details.field: "roomNumber"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Error por piso menor o igual a cero | `POST {BASE_URL}/api/rooms`<br>Headers: `X-User: admin`<br>Body: `{"roomNumber": "409", "floor": 0, "categoryRoom": "BOUTIQUE", "maxCapacity": 2, "baseRate": 320000.00}` | Piso 0 | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, `details.field: "floor"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 6 | Error por tarifa no positiva | `POST {BASE_URL}/api/rooms`<br>Headers: `X-User: admin`<br>Body: `{"roomNumber": "409", "floor": 4, "categoryRoom": "BOUTIQUE", "maxCapacity": 2, "baseRate": -50.00}` | Tarifa negativa | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, `details.field: "baseRate"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 7 | Error por categoría inexistente | `POST {BASE_URL}/api/rooms`<br>Headers: `X-User: admin`<br>Body: `{"roomNumber": "409", "floor": 4, "categoryRoom": "PENTHOUSE", "maxCapacity": 2, "baseRate": 500000.00}` | Categoría inválida | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, `details.field: "categoryRoom"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 8 | Error por número ya existente | `POST {BASE_URL}/api/rooms`<br>Headers: `X-User: admin`<br>Body: `{"roomNumber": "101", "floor": 1, "categoryRoom": "SENCILLA", "maxCapacity": 1, "baseRate": 150000.00}` | Hab. 101 duplicada | HTTP 409 Conflict, `errorCode: "ROOM_NUMBER_ALREADY_EXISTS"`, mensaje: "Ya existe una habitación con el número 101." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : Doble pulsación en botón Guardar: el botón queda en estado disabled durante la petición HTTP impidiendo dobles inserciones.
* **EC-002 (API)** : Registro simultáneo de dos habitaciones con el mismo número: la restricción única `uk_room_room_number` asegura que una responde 201 y la otra 409 `ROOM_NUMBER_ALREADY_EXISTS`.

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
| 21 | 21 | 0 | 0 | APROBADO |
