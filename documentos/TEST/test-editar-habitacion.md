### Test Case Specification: Editar Habitación
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-editar-habitacion.md](../SPEC/spec-editar-habitacion.md) / [plan-editar-habitacion.md](../PLAN/plan-editar-habitacion.md)  **Status** : Approved

#### Test Overview

**Test Case ID** : TC-019-EditarHabitacion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la edición de una habitación Disponible desde `/administracion/habitaciones/{roomId}/editar` (`PATCH /api/rooms/{roomId}`): el formulario precargado, la confirmación "Antes / Ahora" que aparece después de guardar, el aviso cuando no se cambió nada, las validaciones de campo, el número repetido, el rechazo de habitaciones que no están Disponibles, el control de versión ante una edición simultánea y la habitación inexistente.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Editar Habitación | Specification Document | [documentos/SPEC/spec-editar-habitacion.md](../SPEC/spec-editar-habitacion.md) |
| Plan de Implementación de Editar Habitación | Plan Document | [documentos/PLAN/plan-editar-habitacion.md](../PLAN/plan-editar-habitacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La edición cambia datos reales de la habitación de prueba; usar una habitación creada para pruebas (en la ejecución: la 407 de TC-018).

##### Dependencies & Assumptions
* TC-018 registró la 407 en Disponible. La 204 existe (número repetido) y la 101 no está Disponible.

##### Preconditions & Default Setup
* **Initial State** : 407 Disponible (piso 4, Boutique, 2 personas, $ 320.000); 101 en otro estado (en la ejecución: Pendiente de limpieza).
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : una cuenta de Administración (en la ejecución: `cabo`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Ir a `/login`, usuario y contraseña, "Ingresar" | Cuenta de Administración | Abre `/administracion/inventario` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Abrir la edición | En la fila de la 407 pulsar "Editar" | 407 | Abre `/administracion/habitaciones/{roomId}/editar` con "Modifica los datos que necesites; los demás se conservan.", el estado "Disponible" y los campos precargados; botones "Cancelar" y "Guardar cambios" | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Guardar cambios | Cambiar capacidad y tarifa y pulsar "Guardar cambios" (no hay diálogo previo) | Capacidad 3; tarifa 350000 | Se envía `PATCH /api/rooms/{roomId}` (200) y aparece "Habitación 407 actualizada" / "Los cambios ya se reflejan en el inventario." con la tabla Dato · Antes · Ahora (Capacidad máxima 2 personas → 3 personas; Tarifa base $ 320.000 → $ 350.000), los datos actuales y "Volver al inventario" | [x] Pass | 13-11-2026: comprobado |
| 2 | Guardar sin cambios | Abrir la edición y pulsar "Guardar cambios" sin tocar nada | N/A | "No has modificado ningún dato." y no se envía nada | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Valores inválidos | Vaciar el número; piso 0; capacidad 0; tarifa -5 | N/A | "El número de habitación es obligatorio.", "El piso debe ser un número entero mayor a 0.", "La capacidad máxima debe ser un número entero mayor a 0." y "La tarifa base debe ser mayor a 0 y tener máximo dos decimales." | [x] Pass | 13-11-2026: comprobado |
| 2 | Número de más de 10 caracteres | Número de 11 caracteres | `12345678901` | "El número de habitación debe tener máximo 10 caracteres." | [x] Pass | 13-11-2026: comprobado |
| 3 | Número repetido | Cambiar el número a uno existente | 204 | El backend responde 409 y el campo muestra "Ya existe una habitación con el número 204." | [x] Pass | 13-11-2026: comprobado |
| 4 | Edición simultánea | Abrir la edición en dos pestañas; guardar en la segunda y después en la primera | Tarifas distintas | La primera muestra "Otro usuario actualizó la información de esta habitación mientras la editabas. No se guardaron tus cambios." con el botón "Cargar los datos actuales" | [x] Pass | 13-11-2026: comprobado |
| 5 | Habitación que no está Disponible | Abrir a mano `/administracion/habitaciones/{id de la 101}/editar` | 101 | "No se puede editar la habitación 101" / "La habitación está en estado Pendiente de limpieza. Solo se pueden editar habitaciones disponibles." y "Volver al inventario" | [x] Pass | 13-11-2026: comprobado |
| 6 | Habitación inexistente | Abrir a mano la edición con un UUID que no existe | `b0000000-…-999` | "No existe ninguna habitación con ese identificador." y "Volver al inventario" | [x] Pass | 13-11-2026: comprobado |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Edición exitosa | `PATCH {BASE_URL}/api/rooms/{roomId}`, `X-User` de Administración, body `{"version": <actual>, "baseRate": 340000.00, "maxCapacity": 2}` | 407 | 200 con `changed` true, `changes` (campo, `previousValue`, `newValue`) y `room` con `version` + 1 | [x] Pass | 13-11-2026: comprobado |
| 2 | Sin cambios | Body solo con la `version` actual | 407 | 200 con `changed` false, `changes` vacío y la misma `version` | [x] Pass | 13-11-2026: comprobado |
| 3 | Sin usuario | El `PATCH` sin `X-User` | N/A | 400 `CURRENT_USER_REQUIRED` | [x] Pass | 13-11-2026: comprobado |
| 4 | Sin versión | Body `{"baseRate": 220000.00}` | N/A | 400 `VALIDATION_ERROR`, `details.field` `version` | [x] Pass | 13-11-2026: comprobado (mensaje: "La información de la habitación ya fue actualizada por otro usuario. Recarga para ver los datos actuales.") |
| 5 | Habitación inexistente | `PATCH {BASE_URL}/api/rooms/b0000000-0000-0000-0000-000000000999` | N/A | 404 `ROOM_NOT_FOUND`, "No se encontró ninguna habitación con ese identificador." | [x] Pass | 13-11-2026: comprobado |
| 6 | Habitación no Disponible | `PATCH` a la 101 con su `version` | 101 | 409 `ROOM_INVALID_STATE`, "La habitación no está disponible para edición (estado actual: Pendiente de limpieza).", `details.currentStatus` | [x] Pass | 13-11-2026: comprobado |
| 7 | Versión desactualizada | Body con `"version": 99` | N/A | 409 `CONCURRENT_UPDATE`, "La información de la habitación ya fue actualizada por otro usuario. Recarga para ver los datos actuales." | [x] Pass | 13-11-2026: comprobado |
| 8 | Número repetido | Body `{"version": <actual>, "roomNumber": "101"}` | 101 | 409 `ROOM_NUMBER_ALREADY_EXISTS`, "Ya existe una habitación con el número 101 (aunque esté inactiva)." | [x] Pass | 13-11-2026: comprobado. La interfaz muestra el texto corto, sin "(aunque esté inactiva)" |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Cambiar el número a uno de una habitación dada de baja también se rechaza con 409.
* **EC-002 (Interfaz)** : Tras una edición simultánea, "Cargar los datos actuales" trae la versión nueva y permite volver a editar.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |

--------------------------------------------------------------------------------

#### Hallazgos de la revisión (13-11-2026)

| ID | Tipo | Hallazgo | Corrección |
| :---: | ------ | ------ | ------ |
| H1 | Documento | La versión anterior esperaba un botón "Revisar cambios", un diálogo de confirmación "Antes / Ahora" con "Confirmar cambios", el regreso al inventario con "Habitación 102 actualizada exitosamente.", el tipo "fuera de catálogo" (el campo es una lista cerrada) y los textos "La habitación no está disponible para edición…" y "No se encontró ninguna habitación…" en pantalla. En la plataforma se guarda directo con "Guardar cambios" y el "Antes / Ahora" es la confirmación posterior | Pasos reescritos con lo que muestra la página |
| H2 | Documento | La versión anterior editaba la 102, que pertenece a los datos compartidos del equipo | Se usa la 407 creada en TC-018 |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 18 | 18 | 0 | 0 | APROBADO |
