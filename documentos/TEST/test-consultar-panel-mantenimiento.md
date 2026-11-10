### Test Case Specification: Consultar Panel de Mantenimiento
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-panel-mantenimiento.md](../SPEC/spec-consultar-panel-mantenimiento.md) / [plan-consultar-panel-mantenimiento.md](../PLAN/plan-consultar-panel-mantenimiento.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-015-ConsultarPanelMantenimiento
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - High

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que el Panel de Mantenimiento (`GET /api/maintenance/panel`) despliegue todas las habitaciones del hotel salvo las Inactivas, ordenando prioritariamente los bloqueos técnicos vencidos y las unidades inhabilitadas o en bloqueo, con sus contadores operativos, rangos de mantenimiento, etiquetas contextuales ("En reparación", "Bloqueo técnico (vencido)"), acciones correspondientes y redirección ante tareas activas.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Panel de Mantenimiento | Specification Document | [documentos/SPEC/spec-consultar-panel-mantenimiento.md](../SPEC/spec-consultar-panel-mantenimiento.md) |
| Plan de Implementación de Consultar Panel de Mantenimiento | Plan Document | [documentos/PLAN/plan-consultar-panel-mantenimiento.md](../PLAN/plan-consultar-panel-mantenimiento.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere backend Spring Boot y frontend React en marcha, y base de datos PostgreSQL con migraciones V1 a V3. La pantalla `/mantenimiento` está completamente implementada y conectada al backend REST `/api/maintenance/panel`.

##### Dependencies & Assumptions
* El panel consulta el estado de las habitaciones y los informes de `damage_report` y `technical_block_report`.

##### Preconditions & Default Setup
* **Initial State** : Habitación 101 en DisabledForRepairs. Habitación 103 en TechnicalBlock con bloqueo vencido (fecha estimada fin anterior a hoy). Habitación 105 en Occupied con mantenimiento programado a futuro. Habitación 104 en Inactive.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario con área Mantenimiento autenticado (ej. `mtorres`); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Visualización, Ordenamiento, Contadores y Acciones)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Acceder al Panel de Mantenimiento | Iniciar sesión con usuario de mantenimiento sin tarea activa y navegar a `/mantenimiento` | Usuario: `mtorres`, ruta: `/mantenimiento` | Se despliega el hero "Bienvenido, [Nombre]" con subtítulo "Reparaciones y mantenimientos programados", los contadores resumen y la tabla de habitaciones | [ ] | [ ] |
| 2 | Verificar orden de grupos en la tabla | Observar el orden de las filas presentadas | Habitaciones 103, 101, 105 y 104 | Se listan primero los bloqueos vencidos (hab. 103 como "Bloqueo técnico (vencido)"), luego las que requieren intervención (hab. 101 como "Inhabilitada por reparaciones"), y al final las demás (hab. 105 como "Ocupada"). La habitación 104 (`Inactive`) no aparece listada | [ ] | [ ] |
| 3 | Verificar acciones contextuales por fila | Observar los botones de acción en cada fila | Filas de habitaciones disponibles, inhabilitadas y ocupadas | Todas las filas ofrecen el botón "Programar mantenimiento". La habitación disponible ofrece además "Reportar daño". Las habitaciones inhabilitadas o en bloqueo ofrecen "Iniciar reparaciones" y "Ver informe" | [ ] | [ ] |
| 4 | Visualización de habitación con reparación en curso | Consultar una habitación inhabilitada que ya tiene una tarea activa tomada por otro técnico | Habitación con `ReparationTask` abierta | La fila muestra la etiqueta morada "En reparación" y el botón "Iniciar reparaciones" se encuentra oculto | [ ] | [ ] |
| 5 | Filtrar por número exacto de habitación | En el campo "Buscar habitación por número…", ingresar el número de habitación | Texto: `103` | La tabla filtra en tiempo real mostrando únicamente la fila de la habitación 103 | [ ] | [ ] |

--------------------------------------------------------------------------------

##### Section 2: Estados Vacíos, Redirección y Contingencia
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda por número sin coincidencias o habitación inactiva | Ingresar en el buscador el número de una habitación inactiva o inexistente | Texto: `104` (Inactive) o `999` | La tabla muestra el mensaje centrado: "No se encontró la habitación 104" | [Pass] | Verificado por test automatizado (`MaintenancePanelPage.test.tsx`) |
| 2 | Panel sin habitaciones | Consultar el panel en un entorno donde no existan habitaciones registradas | 0 habitaciones activas | La tabla presenta el mensaje central: "No hay habitaciones para listar" y los contadores marcan 0 | [Pass] | Verificado por test automatizado (`MaintenancePanelPage.test.tsx`) |
| 3 | Acceso al panel con tarea activa de reparación abierta | Iniciar sesión con un técnico que posee una `ReparationTask` sin finalizar | Usuario con `activeTask` abierta | El sistema detecta la tarea y redirige automáticamente al usuario a la vista de tarea activa de mantenimiento `/mantenimiento/tarea`, impidiendo ver el panel general | [Pass] | Verificado por test automatizado (`MaintenancePanelIntegrationTest` y `MaintenancePanelPage.test.tsx`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Actualización periódica: el panel refresca automáticamente su contenido cada 60 segundos y cada vez que el miembro regresa de ejecutar una acción, incorporando de inmediato nuevas habitaciones inhabilitadas.
* **EC-002** : Consulta de informe de bloqueo o daño desde "Ver informe": al hacer clic en "Ver informe", el sistema abre el diálogo modal correspondiente mostrando el reporte de daño (en `DisabledForRepairs`) o el informe técnico (en `TechnicalBlock` o mantenimientos programados).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
