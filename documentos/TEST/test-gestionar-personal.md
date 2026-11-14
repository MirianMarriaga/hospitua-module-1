### Test Case Specification: Gestionar Personal
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-gestionar-personal.md](../SPEC/spec-gestionar-personal.md) / [plan-gestionar-personal.md](../PLAN/plan-gestionar-personal.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-023-GestionarPersonal
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la gestión integral de cuentas de usuario del personal hotelero (`/administracion/personal`), abarcando el registro de cuentas (`POST /api/staff`), consulta paginada (`GET /api/staff`), edición de perfil (`PATCH /api/staff/{username}`), desactivación lógica (`POST /api/staff/{username}/deactivation`), y el inicio de sesión (`POST /api/auth/login`) asegurando que una cuenta desactivada recibe exactamente el mismo código y mensaje de error que unas credenciales incorrectas (HTTP 401 `INVALID_CREDENTIALS`, "Usuario o contraseña incorrectos.").

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Gestionar Personal | Specification Document | [documentos/SPEC/spec-gestionar-personal.md](../SPEC/spec-gestionar-personal.md) |
| Plan de Implementación de Gestionar Personal | Plan Document | [documentos/PLAN/plan-gestionar-personal.md](../PLAN/plan-gestionar-personal.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot, frontend React y base de datos PostgreSQL activos. Las pantallas `/administracion/personal`, `/administracion/personal/nuevo` y `/administracion/personal/:username/editar` se encuentran implementadas.

##### Dependencies & Assumptions
* Requiere sesión activa con rol Administrador para mutaciones.

##### Preconditions & Default Setup
* **Initial State** : Cuenta administradora principal activa (`admin`).
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Administrador (`admin`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Navegar a `/login` e ingresar credenciales de Administrador | Usuario: `admin` | Redirige al inventario en `/administracion/inventario` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Navegar a gestión de personal | En el menú lateral o superior, hacer clic en "Personal" | Menú "Personal" | Carga el listado de colaboradores en `/administracion/personal` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Visualizar lista de colaboradores | En `/administracion/personal`, inspeccionar la tabla de cuentas | Cuentas existentes | Se despliegan filas con: Usuario, Nombre completo, Área (ADMINISTRACION, RECEPCION, LIMPIEZA, MANTENIMIENTO, GERENCIA), Estado (Activa/Desactivada) y Acciones | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Abrir formulario de nueva cuenta | En `/administracion/personal`, hacer clic en el botón "Nuevo colaborador" | Botón "Nuevo colaborador" | Navega a `/administracion/personal/nuevo` | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Registrar nuevo colaborador | Diligenciar formulario:<br>- Nombre completo: "Juan López"<br>- Usuario: "jlopez"<br>- Área: "LIMPIEZA"<br>- Contraseña: "Password123*"<br>Hacer clic en "Registrar cuenta" | Datos válidos | Se envía `POST /api/staff`, se crea la cuenta activa y redirige a `/administracion/personal` con mensaje: "Cuenta jlopez registrada exitosamente." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Editar cuenta existente | En la lista de personal, pulsar "Editar" en la fila de "jlopez" | Cuenta "jlopez" | Navega a `/administracion/personal/jlopez/editar` con datos precargados y campo de contraseña opcional | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 5 | Guardar cambios en colaborador | Modificar el Nombre completo a "Juan Carlos López" y pulsar "Guardar cambios" | Nuevo nombre | Se actualiza el perfil vía `PATCH /api/staff/jlopez` y redirige al listado con notificación: "Cuenta jlopez actualizada exitosamente." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 6 | Desactivar cuenta de personal | En la fila de "jlopez", hacer clic en "Desactivar" y confirmar el diálogo "¿Desactivar la cuenta de Juan Carlos López?" | Confirmación modal | Se envía `POST /api/staff/jlopez/deactivation`, la cuenta pasa a estado inactivo (badge "Desactivada") | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Campos obligatorios vacíos | Dejar campos en blanco al registrar personal | Formulario vacío | Mensaje de validación: "Completa los campos obligatorios: {campos}." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 2 | Formato de usuario inválido | Ingresar usuario con tildes o menos de 3 caracteres (ej. "pe") | Usuario "pe" | Mensaje de validación: "El usuario debe tener entre 3 y 30 caracteres: letras sin tilde, números, punto o guion bajo." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 3 | Contraseña con longitud menor a 8 caracteres | Ingresar contraseña "12345" | Contraseña corta | Mensaje de validación: "La contraseña debe tener al menos 8 caracteres." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 4 | Contraseña de más de 72 bytes | Ingresar contraseña de más de 72 caracteres | Contraseña excesiva | Mensaje de validación: "La contraseña admite máximo 72 caracteres." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 5 | Área no permitida | Seleccionar valor no admitido | Área inválida | Mensaje de validación: "Selecciona un área válida." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 6 | Nombre de usuario duplicado | Intentar registrar con usuario "admin" | Usuario existente | Mensaje de conflicto: "Ya existe una cuenta con el usuario admin." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 7 | Auto-desactivación del propio usuario | Intentar presionar "Desactivar" sobre la fila del usuario actualmente conectado (`admin`) | Propia cuenta | El botón se encuentra deshabilitado en pantalla o alerta: "No puedes desactivar tu propia cuenta." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 8 | Desactivación del único administrador | Intentar desactivar al único administrador activo del sistema | Último administrador | Mensaje de rechazo: "No se puede desactivar: es la única cuenta activa de Administración." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 9 | Cambio de área del único administrador | Intentar cambiar el área del único administrador a otra área | Cambio de área | Mensaje de rechazo: "No se puede cambiar el área: es la única cuenta activa de Administración." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 10 | Edición de cuenta desactivada | Intentar abrir formulario de edición para una cuenta inactiva | Cuenta desactivada | Mensaje de rechazo: "La cuenta está desactivada; no se puede editar." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |
| 11 | Concurrencia en edición de personal | Guardar cambios con versión desactualizada | Versión obsoleta | Mensaje de conflicto: "La información de la cuenta ya fue actualizada por otro usuario. Recarga para ver los datos actuales." | [x] Pass | 2026-11-13: Verificado con suites Vitest y navegación frontend |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Creación exitosa de cuenta | `POST {BASE_URL}/api/staff`<br>Headers: `X-User: admin`, `Content-Type: application/json`<br>Body: `{"username": "jlopez", "fullName": "Juan López", "area": "LIMPIEZA", "password": "Password123*"}` | Datos válidos | HTTP 201 Created con JSON conteniendo `username: "jlopez"`, `fullName`, `area: "LIMPIEZA"`, `active: true` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 2 | Consulta general de cuentas | `GET {BASE_URL}/api/staff?page=1&size=10`<br>Headers: (sin X-User obligatorio) | Paginación estándar | HTTP 200 OK con JSON `PagedResultDto<StaffAccountDto>` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 3 | Consulta individual con versión | `GET {BASE_URL}/api/staff/jlopez`<br>Headers: (sin X-User obligatorio) | Usuario existente | HTTP 200 OK con datos de la cuenta y atributo `version` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 4 | Actualización de perfil | `PATCH {BASE_URL}/api/staff/jlopez`<br>Headers: `X-User: admin`<br>Body: `{"version": 0, "fullName": "Juan Carlos López", "area": "LIMPIEZA"}` | Versión válida | HTTP 200 OK con cuenta actualizada y versión incrementada | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 5 | Desactivación lógica de cuenta | `POST {BASE_URL}/api/staff/jlopez/deactivation`<br>Headers: `X-User: admin` | Cuenta activa | HTTP 200 OK con `{ "username": "jlopez", "active": false }` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 6 | Intento de login con cuenta desactivada | `POST {BASE_URL}/api/auth/login`<br>Content-Type: application/json<br>Body: `{"username": "jlopez", "password": "Password123*"}` | Cuenta desactivada | HTTP 401 Unauthorized, `errorCode: "INVALID_CREDENTIALS"`, mensaje idéntico a contraseña incorrecta: "Usuario o contraseña incorrectos." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 7 | Error por falta de X-User en mutaciones | `POST {BASE_URL}/api/staff`<br>Headers: (sin X-User)<br>Body: `{"username": "test", ...}` | Sin usuario | HTTP 400 Bad Request, `errorCode: "CURRENT_USER_REQUIRED"` | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 8 | Error usuario duplicado | `POST {BASE_URL}/api/staff`<br>Headers: `X-User: admin`<br>Body: `{"username": "admin", ...}` | Usuario "admin" | HTTP 409 Conflict, `errorCode: "USERNAME_ALREADY_EXISTS"`, mensaje: "Ya existe una cuenta con el usuario admin." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 9 | Error auto-desactivación | `POST {BASE_URL}/api/staff/admin/deactivation`<br>Headers: `X-User: admin` | Propia cuenta | HTTP 409 Conflict, `errorCode: "CANNOT_DEACTIVATE_SELF"`, mensaje: "No puedes desactivar tu propia cuenta." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 10 | Error último administrador | `POST {BASE_URL}/api/staff/admin/deactivation`<br>Headers: `X-User: otro_admin` (si fuera el único) | Último admin | HTTP 409 Conflict, `errorCode: "LAST_ADMINISTRATOR"`, mensaje: "No se puede desactivar: es la única cuenta activa de Administración." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 11 | Error cuenta desactivada no editable | `PATCH {BASE_URL}/api/staff/jlopez`<br>Headers: `X-User: admin`<br>Body: `{"version": 1, "fullName": "Otro"}` | Cuenta ya inactiva | HTTP 409 Conflict, `errorCode: "ACCOUNT_INACTIVE"`, mensaje: "La cuenta está desactivada; no se puede editar." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |
| 12 | Error colisión concurrente en cuenta | `PATCH {BASE_URL}/api/staff/jlopez`<br>Headers: `X-User: admin`<br>Body: `{"version": 99, "fullName": "Otro"}` | Versión inválida | HTTP 409 Conflict, `errorCode: "CONCURRENT_UPDATE"`, mensaje: "La información de la cuenta ya fue actualizada por otro usuario. Recarga para ver los datos actuales." | [x] Pass | 2026-11-13: Verificado contra backend local Spring Boot |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (Interfaz)** : Deshabilitación defensiva en la fila del propio usuario: la interfaz comprueba la sesión activa y desactiva el botón "Desactivar" para el usuario que tiene la sesión abierta.
* **EC-002 (API)** : Intento de inicio de sesión con cuenta desactivada: verificar que tras desactivar una cuenta, la llamada a `POST /api/auth/login` con sus credenciales correctas rechaza el acceso con HTTP 401 Unauthorized (`INVALID_CREDENTIALS`) con el mensaje genérico `"Usuario o contraseña incorrectos."`.

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
| 31 | 31 | 0 | 0 | APROBADO |
