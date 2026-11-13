### Test Case Specification: Consultar Inventario de Habitaciones
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-inventario-habitaciones.md](../SPEC/spec-consultar-inventario-habitaciones.md) / [plan-consultar-inventario-habitaciones.md](../PLAN/plan-consultar-inventario-habitaciones.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-021-ConsultarInventarioHabitaciones
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - Major

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica la consulta paginada y filtrado del inventario general de habitaciones en pantalla (`GET /api/rooms/inventory`, rutas `/administracion/inventario` y `/gerencia/inventario`), el detalle individual (`GET /api/rooms/inventory/{roomId}`), y los contratos REST expuestos a Módulo 2 (`GET /api/rooms` y `GET /api/rooms/{roomId}`) garantizando que la consulta no exige X-User y excluye habitaciones Inactive para Módulo 2.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Inventario | Specification Document | [documentos/SPEC/spec-consultar-inventario-habitaciones.md](../SPEC/spec-consultar-inventario-habitaciones.md) |
| Plan de Implementación de Consultar Inventario | Plan Document | [documentos/PLAN/plan-consultar-inventario-habitaciones.md](../PLAN/plan-consultar-inventario-habitaciones.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere servicios backend Spring Boot y frontend React activos. Ambas rutas `/administracion/inventario` y `/gerencia/inventario` se encuentran implementadas.

##### Dependencies & Assumptions
* Requiere semilla de habitaciones cargada (migración V2 con 14 habitaciones iniciales: 12 Available, 2 Inactive: 104 y 204).

##### Preconditions & Default Setup
* **Initial State** : 14 habitaciones registradas en la base de datos de prueba.
* **Environment** : Sandbox / Pruebas; endpoints invocados contra `{BASE_URL}`.
* **Test Accounts** : Administrador (`admin`), Gerente (`gerente`).

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Sección A — Cómo llegar a la pantalla (interfaz)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión como Administrador | Navegar a `/login` e ingresar credenciales de Administrador | Usuario: `admin` | Redirige automáticamente a `/administracion/inventario` con rol de administrador | [ ] | |
| 2 | Iniciar sesión como Gerente | Navegar a `/login` e ingresar credenciales de Gerencia | Usuario: `gerente` | Redirige a `/gerencia/inventario` con rol de solo lectura/supervisión | [ ] | |

##### Sección B — Flujo principal en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Carga general de la tabla | Inspeccionar la tabla en `/administracion/inventario` | 14 habitaciones semilla | Se presentan habitaciones con columnas: Número, Tipo, Piso, Capacidad, Tarifa base, Estado actual (badges con colores) y Acciones | [ ] | |
| 2 | Filtrar por número de habitación | En la barra de búsqueda superior, escribir "101" | Búsqueda "101" | La tabla se filtra mostrando únicamente la fila de la habitación 101 | [ ] | |
| 3 | Filtrar por tipo y estado | Seleccionar Tipo: "Doble" y Estado: "Disponible" | Filtros combinados | La tabla actualiza sus resultados mostrando únicamente habitaciones dobles disponibles | [ ] | |
| 4 | Limpiar filtros aplicados | Hacer clic en "Limpiar filtros" | Acción de limpiar | La tabla restablece la lista completa de habitaciones del inventario | [ ] | |
| 5 | Paginación y navegación | Cambiar el selector de página o tamaño si el número de registros supera 10 | Página siguiente | Navegación fluida entre páginas conservando los filtros activos | [ ] | |

##### Sección C — Errores y validaciones en pantalla
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda sin coincidencias | Ingresar en el filtro de número "999" | Búsqueda inexistente | La tabla muestra el estado vacío: "No se encontraron habitaciones con los filtros aplicados." | [ ] | |
| 2 | Filtros con parámetros inválidos | Ingresar caracteres inválidos en campos numéricos | Parámetros inválidos | Mensaje de validación: "Revisa los filtros aplicados." | [ ] | |

##### Sección D — Pruebas de API (backend)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consulta de inventario administrativo | `GET {BASE_URL}/api/rooms/inventory?page=1&size=10`<br>Headers: (sin X-User obligatorio) | Paginación estándar | HTTP 200 OK con JSON `PagedResultDto<RoomDto>` `{ "items": [...], "total": 14, "page": 1, "totalPages": 2 }` | [ ] | |
| 2 | Consulta de detalle individual con versión | `GET {BASE_URL}/api/rooms/inventory/a0000000-0000-0000-0000-000000000101`<br>Headers: (sin X-User obligatorio) | `roomId` de hab. 101 | HTTP 200 OK con datos completos de la habitación incluyendo `version` | [ ] | |
| 3 | Consulta de habitaciones vendibles para Módulo 2 | `GET {BASE_URL}/api/rooms?categoryRoom=DOBLE`<br>Headers: (sin X-User) | Categoría "DOBLE" | HTTP 200 OK con lista JSON `List<Module2RoomDto>` de habitaciones vendibles excluyendo Inactive | [ ] | |
| 4 | Consulta de habitación individual para Módulo 2 | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000101`<br>Headers: (sin X-User) | `roomId` válido | HTTP 200 OK con `{ "id": "...", "roomNumber": "101", "categoryRoom": "SENCILLA", "maxCapacity": 1 }` | [ ] | |
| 5 | Consulta para M2 de habitación Inactive | `GET {BASE_URL}/api/rooms/a0000000-0000-0000-0000-000000000104` | Hab. 104 (`Inactive`) | HTTP 404 Not Found, `errorCode: "ROOM_NOT_FOUND"`, al estar dada de baja | [ ] | |
| 6 | Error filtros inválidos | `GET {BASE_URL}/api/rooms/inventory?floor=-1` | Piso negativo | HTTP 400 Bad Request, `errorCode: "VALIDATION_ERROR"` | [ ] | |
| 7 | Error habitación no encontrada en inventario | `GET {BASE_URL}/api/rooms/inventory/b0000000-0000-0000-0000-000000000999` | UUID inexistente | HTTP 404 Not Found, `errorCode: "ROOM_NOT_FOUND"` | [ ] | |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001 (API)** : Aislamiento de habitaciones inactivas para M2: comprobar que una habitación dada de baja responde HTTP 404 Not Found al ser consultada vía `GET /api/rooms/{roomId}` por Módulo 2, pero responde HTTP 200 OK en `GET /api/rooms/inventory/{roomId}` para el Administrador.
* **EC-002 (Interfaz)** : Vista diferenciada por rol: verificar que en `/gerencia/inventario` los botones de acción mutativa ("Editar", "Dar de baja", "Nueva habitación") no se muestran en pantalla.

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
