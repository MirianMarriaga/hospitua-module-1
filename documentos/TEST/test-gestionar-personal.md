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
Este caso de prueba verifica la gestión de las cuentas del personal desde `/administracion/personal` (`/api/staff`): la lista con búsqueda y filtros, el registro, la edición (el usuario no se puede cambiar), la desactivación con su diálogo, los rechazos (usuario repetido, cuenta propia, cuenta desactivada, edición simultánea) y que una cuenta desactivada ya no pueda iniciar sesión ni actuar en la API.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Gestionar Personal | Specification Document | [documentos/SPEC/spec-gestionar-personal.md](../SPEC/spec-gestionar-personal.md) |
| Plan de Implementación de Gestionar Personal | Plan Document | [documentos/PLAN/plan-gestionar-personal.md](../PLAN/plan-gestionar-personal.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Las cuentas no se borran: las de prueba quedan desactivadas (en la ejecución: `tc.personal` por pantalla y `tc.personal2` por API).
* La regla "única cuenta activa de Administración" no se puede provocar en la plataforma sin desactivar a los administradores reales del equipo (hay 3 activos).

##### Dependencies & Assumptions
* La cuenta `admin` existe (usuario repetido). `jlopez` ya existe en la plataforma: no usarlo como cuenta nueva.

##### Preconditions & Default Setup
* **Initial State** : los usuarios `tc.personal` y `tc.personal2` no existen.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : una cuenta de Administración (en la ejecución: `cabo`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Ir a `/login`, usuario y contraseña, "Ingresar" | Cuenta de Administración | Abre `/administracion/inventario`; el menú lateral muestra "Inventario" y "Personal" | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Abrir Personal | Pulsar "Personal" | N/A | Abre `/administracion/personal` con "Registra, edita o desactiva las cuentas con las que el personal inicia sesión.", el botón "Registrar personal", los filtros Buscar, Área y Estado y las columnas Usuario, Nombre, Área, Estado y Acciones | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Buscar y filtrar | Escribir en "Buscar"; revisar las opciones de Área y Estado | `tc.` | La dirección pasa a `?query=tc.` y solo quedan las cuentas que coinciden; Área: Todas, Recepción, Administración, Gerencia, Limpieza, Mantenimiento; Estado: Todos, Activa, Desactivada | [x] Pass | 13-11-2026: comprobado |
| 2 | Registrar una cuenta | "Registrar personal", llenar y pulsar "Registrar personal" | Usuario `tc.personal`; contraseña de 8+ caracteres; "Prueba Personal"; Limpieza | Se envía `POST /api/staff` (201) y aparece "Cuenta de Prueba Personal registrada" / "Ya puede iniciar sesión con su usuario y contraseña." con Usuario, Nombre, Área, Estado "Activa" y los botones "Registrar otra persona" y "Volver al personal" | [x] Pass | 13-11-2026: comprobado |
| 3 | Abrir la edición | En la fila de `tc.personal` pulsar "Editar" | N/A | Abre `/administracion/personal/tc.personal/editar` con "El usuario no se puede cambiar, porque identifica a la persona en el historial.", el Usuario no editable, "Contraseña nueva" opcional, Nombre completo y Área precargados | [x] Pass | 13-11-2026: comprobado |
| 4 | Guardar cambios | Cambiar el nombre y pulsar "Guardar cambios" | "Prueba Personal Editada" | `PATCH /api/staff/tc.personal` (200) y "Cuenta de Prueba Personal Editada actualizada" / "Los cambios ya están guardados." con los datos y "Volver al personal" | [x] Pass | 13-11-2026: comprobado |
| 5 | Desactivar una cuenta | En la fila de `tc.personal` pulsar "Desactivar" y luego "Desactivar cuenta" | N/A | El diálogo dice "¿Desactivar la cuenta de {nombre}?", "Revisa los datos de la cuenta antes de confirmar.", "La persona no podrá iniciar sesión. La cuenta no se borra: el historial seguirá mostrando su nombre." con los datos y "Cancelar" / "Desactivar cuenta". Al confirmar, la fila queda "Desactivada" y sin acciones ("—") | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Formulario vacío | Pulsar "Registrar personal" sin llenar nada | N/A | "Ingresa el usuario.", "Ingresa la contraseña.", "Ingresa el nombre completo." y "Selecciona el área." | [x] Pass | 13-11-2026: comprobado |
| 2 | Usuario y contraseña inválidos | Usuario con tilde; contraseña corta | `pé`; `12345` | "El usuario debe tener entre 3 y 30 caracteres: letras sin tilde, números, punto o guion bajo." y "La contraseña debe tener al menos 8 caracteres." | [x] Pass | 13-11-2026: comprobado |
| 3 | Contraseña de más de 72 bytes | 37 letras "ñ" (74 bytes) | N/A | "La contraseña admite máximo 72 caracteres." | [x] Pass | 13-11-2026: comprobado |
| 4 | Usuario repetido | Registrar con el usuario `admin` | `admin` | El backend responde 409 y el campo muestra "Ya existe una cuenta con el usuario admin." | [x] Pass | 13-11-2026: comprobado |
| 5 | Desactivar la cuenta propia | En la fila propia pulsar "Desactivar" y confirmar | Cuenta en sesión | El botón está habilitado; al confirmar, el diálogo muestra "No puedes desactivar tu propia cuenta." y ofrece "Cerrar" | [x] Pass | 13-11-2026: comprobado (409 `CANNOT_DEACTIVATE_SELF`) |
| 6 | Única cuenta de Administración | Desactivar o cambiar de área a la única cuenta activa de Administración | Último administrador | "No se puede desactivar: es la única cuenta activa de Administración." | N/C | No comprobable en la plataforma sin desactivar a los administradores del equipo |
| 7 | Editar una cuenta desactivada | Abrir a mano `/administracion/personal/tc.personal/editar` | Cuenta desactivada | "No se puede editar la cuenta tc.personal" / "La cuenta está desactivada; no se puede editar." y "Volver al personal" | [x] Pass | 13-11-2026: comprobado |
| 8 | Edición simultánea | Abrir la edición en dos pestañas; guardar en la segunda y luego en la primera | Nombres distintos | "La información de la cuenta ya fue actualizada por otro usuario. Recarga para ver los datos actuales." con "Cargar los datos actuales" | [x] Pass | 13-11-2026: comprobado |
| 9 | Cuenta inexistente | Abrir a mano `/administracion/personal/nadie.tc/editar` | N/A | "Cuenta no encontrada" / "No existe ninguna cuenta con ese usuario." | [x] Pass | 13-11-2026: comprobado |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Crear una cuenta | `POST {BASE_URL}/api/staff`, `X-User` de Administración, body `{"username":"tc.personal2","fullName":"Prueba Personal API","area":"LIMPIEZA","password":"…"}` | N/A | 201 con `username`, `fullName`, `area` y `active` true (sin contraseña) | [x] Pass | 13-11-2026: comprobado |
| 2 | Listar y consultar | `GET {BASE_URL}/api/staff?page=1&size=10`; `GET {BASE_URL}/api/staff/tc.personal2` | N/A | 200; la consulta individual trae además `version` | [x] Pass | 13-11-2026: comprobado |
| 3 | Editar | `PATCH {BASE_URL}/api/staff/tc.personal2` con `{"version":0,"fullName":"…","area":"LIMPIEZA"}` | N/A | 200 con los datos nuevos y `version` 1 | [x] Pass | 13-11-2026: comprobado |
| 4 | Sin usuario | `POST {BASE_URL}/api/staff` sin `X-User` | N/A | 400 `CURRENT_USER_REQUIRED` | [x] Pass | 13-11-2026: comprobado |
| 5 | Usuario repetido | `POST` con `"username":"admin"` | N/A | 409 `USERNAME_ALREADY_EXISTS`, "Ya existe una cuenta con el usuario admin." | [x] Pass | 13-11-2026: comprobado |
| 6 | Área inexistente | `POST` con `"area":"COCINA"` | N/A | 400 `VALIDATION_ERROR`, "Selecciona un área válida.", `details.field` `area` | [x] Pass | 13-11-2026: comprobado |
| 7 | Versión desactualizada | `PATCH` con `"version":99` | N/A | 409 `CONCURRENT_UPDATE`, "La información de la cuenta ya fue actualizada por otro usuario. Recarga para ver los datos actuales." | [x] Pass | 13-11-2026: comprobado |
| 8 | Desactivar la cuenta propia | `POST {BASE_URL}/api/staff/{propio}/deactivation` con el mismo `X-User` | N/A | 409 `CANNOT_DEACTIVATE_SELF`, "No puedes desactivar tu propia cuenta." | [x] Pass | 13-11-2026: comprobado |
| 9 | Desactivar | `POST {BASE_URL}/api/staff/tc.personal2/deactivation` | N/A | 200 con `active` false; repetirlo da 409 `ACCOUNT_INACTIVE`, "La cuenta ya está desactivada." | [x] Pass | 13-11-2026: comprobado |
| 10 | Editar una desactivada | `PATCH {BASE_URL}/api/staff/tc.personal2` | N/A | 409 `ACCOUNT_INACTIVE`, "La cuenta está desactivada; no se puede editar." | [x] Pass | 13-11-2026: comprobado |
| 11 | Iniciar sesión desactivada | `POST {BASE_URL}/api/auth/login` con la cuenta desactivada y su contraseña | N/A | 401 `INVALID_CREDENTIALS`, "Usuario o contraseña incorrectos." (el mismo de una contraseña errada) | [x] Pass | 13-11-2026: comprobado |
| 12 | Actuar con una cuenta desactivada | `POST {BASE_URL}/api/rooms` con `X-User: tc.personal2` | N/A | 400 `CURRENT_USER_REQUIRED` | [x] Pass | 13-11-2026: comprobado |
| 13 | Cuenta inexistente | `GET {BASE_URL}/api/staff/nadie.tc` | N/A | 404 `STAFF_NOT_FOUND`, "No se encontró la cuenta." | [x] Pass | 13-11-2026: comprobado |
| 14 | Último administrador | Desactivar la única cuenta activa de Administración | N/A | 409 `LAST_ADMINISTRATOR` | N/C | No comprobable en la plataforma (hay 3 administradores activos) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : La contraseña nunca viaja en las respuestas.
* **EC-002 (Interfaz)** : "Guardar cambios" sin modificar nada igual envía el `PATCH` y muestra "Cuenta de … actualizada" (la edición de habitaciones, en cambio, avisa "No has modificado ningún dato.").

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
| 30 | 28 | 0 | 2 | APROBADO PARCIAL (pasos no comprobables en la plataforma) |
