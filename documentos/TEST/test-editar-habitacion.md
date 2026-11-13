### Test Case Specification: Editar Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-editar-habitacion.md](../SPEC/spec-editar-habitacion.md) / [plan-editar-habitacion.md](../PLAN/plan-editar-habitacion.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-019-EditarHabitacion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la modificación de los atributos físicos y comerciales de una habitación (`PATCH /api/rooms/{roomId}`), exigiendo que la habitación se encuentre exclusivamente en estado Available, el control de concurrencia optimista mediante el atributo `version`, la pantalla de confirmación "Antes / Ahora", y el rechazo en cualquier otro estado del ciclo de vida.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Editar Habitación | Specification Document | [documentos/SPEC/spec-editar-habitacion.md](../SPEC/spec-editar-habitacion.md) |
| Plan de Implementación de Editar Habitación | Plan Document | [documentos/PLAN/plan-editar-habitacion.md](../PLAN/plan-editar-habitacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot, frontend React y base de datos PostgreSQL activos. La ruta `/administracion/habitaciones/:roomId/editar` se encuentra implementada.

##### Dependencies & Assumptions
* La habitación debe encontrarse en estado `Available`. Habitaciones en `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive` no deben permitir edición.

##### Preconditions & Default Setup
* **Initial State** : Habitación 102 en estado `Available` con versión inicial `version = 0`.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Administrador (`admin`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Navegar a `/login` e ingresar credenciales de Administrador | Usuario: `admin` | Carga el Inventario en `/administracion/inventario` | [ ] | |
| 2 | Seleccionar habitación a editar | En `/administracion/inventario`, ubicar la habitación 102 (`Available`) y hacer clic en el botón "Editar" | Habitación 102 | Navega a `/administracion/habitaciones/a0000000-0000-0000-0000-000000000102/editar` con el formulario precargado | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Modificar atributos de la habitación | En el formulario de edición, cambiar:<br>- Tarifa base: de $140.000 a $220.000<br>- Capacidad máxima: de 2 a 3 | Nuevos valores | Los campos validan el formato y habilitan el botón "Revisar cambios" | [ ] | |
| 2 | Visualizar diálogo de confirmación Antes / Ahora | Hacer clic en "Revisar cambios" | Acción en pantalla | Se despliega el diálogo de confirmación `EditRoomConfirmation` contrastando los valores actuales ("Antes") vs nuevos ("Ahora") | [ ] | |
| 3 | Confirmar actualización | Hacer clic en "Confirmar cambios" en el diálogo | Confirmación modal | Se ejecuta la actualización vía `PATCH /api/rooms/{roomId}`, incrementa la versión y redirige a `/administracion/inventario` con la notificación: "Habitación 102 actualizada exitosamente." | [ ] | |
| 4 | Verificar reflejo inmediato | Consultar la fila de la habitación 102 en `/administracion/inventario` | Habitación 102 | Refleja de inmediato Tarifa: $220.000 y Capacidad: 3 | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Número de habitación vacío | Borrar el número de habitación en el formulario | Campo vacío | Mensaje de validación: "El número de habitación es obligatorio." | [ ] | |
| 2 | Número de más de 10 caracteres | Ingresar número de 11 caracteres | 11 caracteres | Mensaje de validación: "El número de habitación debe tener máximo 10 caracteres." | [ ] | |
| 3 | Piso menor o igual a 0 | Modificar piso a 0 o negativo | Piso 0 | Mensaje de validación: "El piso debe ser un número entero mayor a 0." | [ ] | |
| 4 | Tipo fuera de catálogo | Seleccionar valor no admitido | Tipo inválido | Mensaje de validación: "Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique." | [ ] | |
| 5 | Tarifa base inválida | Ingresar tarifa negativa o con 3 decimales | Tarifa inválida | Mensaje de validación: "La tarifa base debe ser mayor a 0 y tener máximo dos decimales." | [ ] | |
| 6 | Capacidad menor o igual a 0 | Ingresar capacidad 0 | Capacidad 0 | Mensaje de validación: "La capacidad máxima debe ser un número entero mayor a 0." | [ ] | |
| 7 | Habitación en estado no editable | Intentar abrir formulario de edición de una habitación en `Occupied` o `Reserved` | Habitación no `Available` | Mensaje de rechazo: "La habitación no está disponible para edición (estado actual: {estado})." | [ ] | |
| 8 | Conflicto por actualización concurrente | Enviar cambios habiendo sido modificada previamente por otro usuario | Versión desfasada | Mensaje de conflicto: "La información de la habitación ya fue actualizada por otro usuario. Recarga para ver los datos actuales." | [ ] | |
| 9 | Número duplicado con otra habitación | Cambiar número de habitación 102 a "101" | Número ya existente | Mensaje de conflicto: "Ya existe una habitación con el número 101." | [ ] | |
| 10 | Habitación inexistente | Navegar a ruta de edición con UUID inexistente | UUID no registrado | Mensaje de error: "No se encontró ninguna habitación con ese identificador." | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Edición exitosa de habitación | `PATCH {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102`<br>Headers: `X-User: admin`, `Content-Type: application/json`<br>Body: `{"version": 0, "baseRate": 220000.00, "maxCapacity": 3}` | Hab. 102 en `Available` | HTTP 200 OK con JSON `EditRoomResultDto` `{ "room": { "id": "...", "roomNumber": "102", "baseRate": 220000.00, "maxCapacity": 3, "version": 1 }, "changes": [...] }` | [ ] | |
| 2 | Error por falta de X-User | `PATCH {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102`<br>Headers: (sin X-User)<br>Body: `{"version": 0, "baseRate": 220000.00}` | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"` | [ ] | |
| 3 | Error por falta de versión obligatoria | `PATCH {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102`<br>Headers: `X-User: admin`<br>Body: `{"baseRate": 220000.00}` | Sin version | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"`, `details.field: "version"` | [ ] | |
| 4 | Error habitación inexistente | `PATCH {BASE_URL}/api/rooms/b0000000-0000-0000-0000-000000000999`<br>Headers: `X-User: admin`<br>Body: `{"version": 0, "baseRate": 220000.00}` | UUID inexistente | HTTP 404 Not Found, `errorCode: "ROOM_NOT_FOUND"`, mensaje: "No se encontró ninguna habitación con ese identificador." | [ ] | |
| 5 | Error habitación no en Available | `PATCH {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101`<br>Headers: `X-User: admin`<br>Body: `{"version": 0, "baseRate": 220000.00}` | Habitación en `Occupied` | HTTP 409 Conflict, `errorCode: "ROOM_INVALID_STATE"`, mensaje: "La habitación no está disponible para edición (estado actual: Occupied)." | [ ] | |
| 6 | Error por colisión de versión optimista | `PATCH {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102`<br>Headers: `X-User: admin`<br>Body: `{"version": 99, "baseRate": 220000.00}` | Versión obsoleta | HTTP 409 Conflict, `errorCode: "CONCURRENT_UPDATE"`, mensaje: "La información de la habitación ya fue actualizada por otro usuario. Recarga para ver los datos actuales." | [ ] | |
| 7 | Error por duplicidad de número | `PATCH {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000102`<br>Headers: `X-User: admin`<br>Body: `{"version": 0, "roomNumber": "101"}` | Número duplicado | HTTP 409 Conflict, `errorCode: "ROOM_NUMBER_ALREADY_EXISTS"`, mensaje: "Ya existe una habitación con el número 101." | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : Habitación cambia de estado a `Reserved` mientras el administrador tiene abierto el formulario de edición: al presionar confirmar cambios, el backend responde HTTP 409 `ROOM_INVALID_STATE` y la interfaz alerta que el estado cambió.
* **EC-002 (API)** : Edición parcial de atributos: al enviar únicamente `baseRate`, el resto de campos (piso, capacidad, tipo, número) se preservan intactos sin sobreescribirse con nulos.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
