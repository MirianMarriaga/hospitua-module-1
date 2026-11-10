### Test Case Specification: Consultar Panel de Limpieza
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-panel-limpieza.md](../SPEC/spec-consultar-panel-limpieza.md) / [plan-consultar-panel-limpieza.md](../PLAN/plan-consultar-panel-limpieza.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-012-ConsultarPanelLimpieza
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - High

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que el Panel de Limpieza (`GET /api/cleaning/panel`) despliegue exclusivamente las habitaciones en estados PendingCleaning (ordenadas primero) y Available con su tipo, estado en español, fecha de última limpieza y botones de acción contextuales, permitiendo la búsqueda por número exacto y redirigiendo a la vista de tarea activa a cualquier miembro que tenga una labor en curso.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Panel de Limpieza | Specification Document | [documentos/SPEC/spec-consultar-panel-limpieza.md](../SPEC/spec-consultar-panel-limpieza.md) |
| Plan de Implementación de Consultar Panel de Limpieza | Plan Document | [documentos/PLAN/plan-consultar-panel-limpieza.md](../PLAN/plan-consultar-panel-limpieza.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere frontend y backend Spring Boot en ejecución, y base de datos PostgreSQL con habitaciones en semilla V2.

##### Dependencies & Assumptions
* TC-009 Marcar pendiente a limpieza y TC-008 Marcar habitación como disponible alimentan los estados consultados por este panel.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en estado PendingCleaning. Habitación 102 en estado Available (sin limpieza previa registrada). Habitación 103 en Available (con limpieza completada previa). Habitación 105 en Occupied.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario con área Limpieza sin tarea activa (ej. `cstaff1`) y usuario con tarea activa abierta; ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Visualización, Contadores, Filtro y Acciones)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión y acceder al Panel de Limpieza | Iniciar sesión con un usuario de limpieza sin tareas activas y entrar al panel | Usuario: `cstaff1`, ruta: `/limpieza` | Se despliega el saludo "Bienvenido, [Nombre]", el subtítulo "Habitaciones por limpiar y disponibles", los contadores "Pendientes de limpieza" y "Disponibles", y la tabla de habitaciones ordenando primero las de estado "Pendiente de limpieza" y luego las "Disponible" | [ ] | [ ] |
| 2 | Verificar acciones y columnas por fila | Observar las filas de la tabla de habitaciones | Filas de habitación 101 y 102 | La habitación 101 (`PendingCleaning`) muestra el botón único "Iniciar limpieza". La habitación 102 (`Available`) muestra los botones "Reportar daño" e "Iniciar limpieza". Las habitaciones en `Occupied` o `InCleaning` no aparecen listadas | [ ] | [ ] |
| 3 | Verificar formato de última limpieza | Observar la columna "Última limpieza" en habitaciones con y sin historial | Habitación 102 y habitación 103 | Para la habitación 102 (sin aseo previo) muestra "Sin registro". Para la habitación 103 muestra la fecha y hora formateada `DD-MM-YYYY HH:MM` ignorando tareas con resultado liberado | [ ] | [ ] |
| 4 | Filtrar por número exacto de habitación | En el campo "Buscar habitación por número…", ingresar el número de habitación | Texto: `101` | La tabla filtra de inmediato presentando exclusivamente la fila de la habitación 101 | [ ] | [ ] |
| 5 | Limpiar filtro de búsqueda | Borrar el texto del buscador | Campo vacío | La tabla vuelve a desplegar la totalidad de las habitaciones pendientes y disponibles | [ ] | [ ] |

--------------------------------------------------------------------------------

##### Section 2: Estados Vacíos, Redirección y Contingencia
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda por número sin coincidencias | Ingresar un número de habitación no existente en el listado | Texto: `999` | La tabla muestra el mensaje centrado: "No se encontró la habitación 999" | [Pass] | Verificado por test automatizado (`CleaningPanelPage.test.tsx`) |
| 2 | Panel sin habitaciones para listar | Consultar el panel en un estado del hotel donde no haya habitaciones en PendingCleaning ni Available | 0 habitaciones en estados de limpieza | La tabla muestra el mensaje central: "No hay habitaciones pendientes de limpieza ni disponibles" y los contadores marcan 0 | [Pass] | Verificado por test automatizado (`CleaningPanelPage.test.tsx`) |
| 3 | Acceso al panel con tarea activa abierta | Iniciar sesión con un miembro del personal de limpieza que posee una tarea de limpieza sin finalizar | Usuario con `activeTask` abierta | El sistema detecta la tarea activa y redirige automáticamente al usuario a la vista de tarea activa `/limpieza/tarea`, impidiendo ver el panel general | [Pass] | Verificado por test automatizado (`CleaningPanelIntegrationTest` y `CleaningPanelPage.test.tsx`) |
| 4 | Habitación tomada concurrentemente por otro compañero | Un usuario hace clic en "Iniciar limpieza" sobre una habitación que otro miembro acaba de tomar | Habitación en transición a InCleaning | El sistema rechaza la acción informando que la habitación está En limpieza y recarga el panel excluyendo la habitación tomada | [Pass] | Verificado por test automatizado (`StartCleaningIntegrationTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Actualización periódica: el panel refresca su información automáticamente cada 60 segundos y cada vez que el miembro regresa de ejecutar una acción, manteniendo la lista al día sin recargar la página completa.
* **EC-002** : Tareas liberadas descartadas de última limpieza: si una habitación tuvo una tarea completada y posteriormente una tarea liberada (`Released`), la fecha mostrada corresponde a la completada.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
