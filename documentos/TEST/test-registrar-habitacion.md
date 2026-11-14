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
Este caso de prueba verifica el registro de una habitación nueva desde `/administracion/habitaciones/nueva` (`POST /api/rooms`): que quede en estado Disponible, las validaciones de cada campo (en pantalla y en el backend), el rechazo de un número repetido (409 `ROOM_NUMBER_ALREADY_EXISTS`) y el cierre de sesión cuando el usuario de la sesión ya no es válido (400 `CURRENT_USER_REQUIRED`).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Registrar Habitación | Specification Document | [documentos/SPEC/spec-registrar-habitacion.md](../SPEC/spec-registrar-habitacion.md) |
| Plan de Implementación de Registrar Habitación | Plan Document | [documentos/PLAN/plan-registrar-habitacion.md](../PLAN/plan-registrar-habitacion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Cada registro exitoso deja una habitación nueva en la plataforma; usar números de prueba que no existan (en la ejecución: 407 por pantalla y 408 por API).

##### Dependencies & Assumptions
* La habitación 101 existe (para probar el número repetido).

##### Preconditions & Default Setup
* **Initial State** : los números 407 y 408 no existen.
* **Environment** : https://gestionophospitua.tech (`{BASE_URL}` = `https://gestionophospitua.tech`).
* **Test Accounts** : una cuenta de Administración (en la ejecución: `cabo`; en la API, `X-User: cabo`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Ir a `/login`, usuario y contraseña, "Ingresar" | Cuenta de Administración | Abre `/administracion/inventario` | [x] Pass | 13-11-2026: comprobado en gestionophospitua.tech |
| 2 | Abrir el formulario | Pulsar "Registrar habitación" arriba del inventario | N/A | Abre `/administracion/habitaciones/nueva` con "La habitación quedará disponible en el inventario apenas se registre.", los grupos "Identificación" y "Categorización y tarifa" y los campos Número de habitación, Piso, Tipo (Sencilla, Doble, Suite, Boutique), Capacidad máxima (personas) y Tarifa base por noche (COP) | [x] Pass | 13-11-2026: comprobado |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar una habitación | Llenar el formulario y pulsar "Registrar habitación" | 407; piso 4; Boutique; capacidad 2; tarifa 320000 | Se envía `POST /api/rooms` (201) y aparece la confirmación "Habitación 407 registrada" / "Ya aparece en el inventario en estado Disponible." con ID, Estado "Disponible", Número, Piso "Piso 4", Tipo "Boutique", Capacidad "2 personas", Tarifa "$ 320.000" y los botones "Registrar otra" y "Volver al inventario" | [x] Pass | 13-11-2026: comprobado |
| 2 | Verificar en el inventario | Filtrar el inventario por "407" | 407 | La fila muestra 407, Piso 4, Boutique, 2 personas, $ 320.000, Disponible, con "Editar" y "Dar de baja" | [x] Pass | 13-11-2026: comprobado |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Formulario vacío | Pulsar "Registrar habitación" sin llenar nada | N/A | Bajo cada campo: "Ingresa el número de habitación.", "Ingresa el piso.", "Selecciona el tipo de habitación.", "Ingresa la capacidad máxima." e "Ingresa la tarifa base."; no se envía nada | [x] Pass | 13-11-2026: comprobado |
| 2 | Valores fuera de rango | Número de 11 caracteres, piso 0, capacidad 0, tarifa con 3 decimales | `12345678901`; 0; 0; `150000.555` | "El número de habitación debe tener máximo 10 caracteres.", "El piso debe ser un número entero mayor a 0.", "La capacidad máxima debe ser un número entero mayor a 0." y "La tarifa base debe ser mayor a 0 y tener máximo dos decimales." | [x] Pass | 13-11-2026: comprobado |
| 3 | Número repetido | Registrar con un número que ya existe | 101 | El backend responde 409 y el formulario muestra "Ya existe una habitación con el número 101." | [x] Pass | 13-11-2026: comprobado |
| 4 | Sesión no válida | Con la sesión de un usuario que ya no existe o está desactivado, enviar el formulario | Usuario de sesión inválido | El backend responde 400 `CURRENT_USER_REQUIRED`; la página vuelve a `/login` con "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 13-11-2026: comprobado cambiando el usuario guardado en la sesión del navegador |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registro exitoso | `POST {BASE_URL}/api/rooms`, `X-User` de Administración, body `{"roomNumber":"408","floor":4,"categoryRoom":"BOUTIQUE","maxCapacity":2,"baseRate":320000.00}` | 408 | 201 con `roomId`, `status` `Available`, los datos enviados y `version` 0 | [x] Pass | 13-11-2026: comprobado |
| 2 | Sin usuario o usuario inexistente | El mismo `POST` sin `X-User`, y con `X-User: nadie.tc` | N/A | 400 `CURRENT_USER_REQUIRED`, "Tu sesión terminó. Inicia sesión de nuevo." | [x] Pass | 13-11-2026: comprobado |
| 3 | Campos faltantes | Body `{"floor":4}` | N/A | 400 `VALIDATION_ERROR`, "Completa los campos obligatorios.", `details.fields` = `roomNumber`, `maxCapacity`, `categoryRoom`, `baseRate` | [x] Pass | 13-11-2026: comprobado |
| 4 | Número de más de 10 caracteres | `"roomNumber":"HABITACION407"` | 13 caracteres | 400 `VALIDATION_ERROR`, `details.field` `roomNumber` | [x] Pass | 13-11-2026: comprobado |
| 5 | Piso 0 | `"floor":0` | N/A | 400 `VALIDATION_ERROR`, `details.field` `floor` | [x] Pass | 13-11-2026: comprobado |
| 6 | Tarifa negativa o con 3 decimales | `"baseRate":-50.00`, luego `150000.555` | Uno por consulta | 400 `VALIDATION_ERROR`, `details.field` `baseRate` | [x] Pass | 13-11-2026: comprobado |
| 7 | Tipo inexistente | `"categoryRoom":"PENTHOUSE"` | N/A | 400 `VALIDATION_ERROR`, "Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique.", `details.field` `categoryRoom` | [x] Pass | 13-11-2026: comprobado |
| 8 | Número repetido | `"roomNumber":"101"` | 101 | 409 `ROOM_NUMBER_ALREADY_EXISTS`, "Ya existe una habitación con el número 101." | [x] Pass | 13-11-2026: comprobado |
| 9 | Usuario de otra área | El `POST` del paso 1 con `X-User` de Recepción | Número nuevo | Por la regla 14 del plan base no hay permisos por rol en esta etapa: el backend acepta (201). El control de área lo hace solo la interfaz (Recepción no ve el inventario de Administración) | [x] Pass | 13-11-2026: comprobado; se registró la 409 con `tc.recepcion` (hallazgo H2) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Un número de exactamente 10 caracteres se acepta; con 11 se rechaza.
* **EC-002 (API)** : El número se guarda como texto (por ejemplo "405A"); la comparación de repetidos es exacta.

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
| H1 | Documento | La versión anterior esperaba el botón "Nueva habitación", el botón "Guardar habitación", el aviso "Habitación 407 registrada exitosamente." con regreso al inventario y el mensaje "Completa los campos obligatorios: {campos}.". En la plataforma el botón es "Registrar habitación", tras registrar se muestra una confirmación en la misma ruta, y los mensajes de campo vacío son uno por campo | Pasos reescritos con lo que muestra la página |
| H2 | Limitación conocida | Sin permisos por rol (regla 14 del plan base): cualquier usuario válido en `X-User` puede registrar habitaciones por la API, y la sesión del navegador se puede editar para actuar como otro usuario. Importante porque la plataforma está publicada en internet | Ninguna en esta etapa; queda registrada para la identidad compartida entre módulos. La habitación 409 queda como dato de prueba |

#### Resumen de Ejecución

| Total Pasos | Pasos Exitosos (Pass) | Pasos Fallidos (Fail) | Pasos No Verificados | Estado General |
| :---: | :---: | :---: | :---: | :---: |
| 17 | 17 | 0 | 0 | APROBADO |
