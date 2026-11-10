### Test Case Specification: Panel de Recepción — Vista de Inicio de Jornada
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-panel-recepcion.md](../SPEC/spec-consultar-panel-recepcion.md) / [plan-consultar-panel-recepcion.md](../PLAN/plan-consultar-panel-recepcion.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-002-ConsultarPanelRecepcion
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P2 - High

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que el Panel de Recepción cargue y calcule correctamente los cuatro indicadores operativos (KPIs), despliegue y filtre en tiempo real los listados de llegadas y salidas del día sobre los datos locales de Módulo 1, y permita la navegación directa a los flujos de Check-In y Check-Out con los datos preseleccionados.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación del Panel de Recepción | Specification Document | [documentos/SPEC/spec-consultar-panel-recepcion.md](../SPEC/spec-consultar-panel-recepcion.md) |
| Plan de Implementación del Panel de Recepción | Plan Document | [documentos/PLAN/plan-consultar-panel-recepcion.md](../PLAN/plan-consultar-panel-recepcion.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere el sistema en marcha: frontend en ejecución y backend Spring Boot respondiendo en los endpoints de panel de recepción (`/api/reception/kpis`, `/api/reception/arrivals`, `/api/reception/departures`).

##### Dependencies & Assumptions
* TC-001 Consultar reservas debe haber inicializado la copia local de la lista del día.
* Para verificar la pestaña de salidas, debe existir al menos una estancia activa (`Stay`) en base de datos.

##### Preconditions & Default Setup
* **Initial State** : Base de datos con habitaciones de semilla V2. Lista del día cargada con al menos una reserva de llegada (ej. habitación 201) y al menos una estancia activa con fecha de salida hoy o anterior (ej. habitación 102 con salida vencida).
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario con área Recepción (ej. recepcionista "Ana Ramírez"); ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Visualización de KPIs, Llegadas, Salidas y Redirección)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar sesión y acceder al Panel de Recepción | Iniciar sesión con usuario de recepción y navegar a la ruta del panel | URL: `/panel` | Se despliega el hero con saludo "Bienvenida, [Nombre]" y subtítulo "Estancias · llegadas y salidas del día", las 4 tarjetas de KPIs ("Llegadas de hoy", "Salidas de hoy", "Salidas vencidas" y "Habitaciones ocupadas") y el contenedor de pestañas con "Llegadas" activa por defecto | [ ] | [ ] |
| 2 | Verificar tabla de Llegadas por defecto | Observar la tabla de la pestaña "Llegadas" | Pestaña activa: Llegadas | La tabla muestra columnas: Hab., Huésped titular (con documento debajo), Estadía (ej. "3 noches"), Personas, Tipo habitación, Fuente y el botón "Check-in" habilitado en cada fila de llegada | [ ] | [ ] |
| 3 | Filtrar llegadas en tiempo real | Escribir en la barra de búsqueda "Buscar por huésped, documento o referencia de reserva…" | Texto: `Pérez` | La tabla filtra en tiempo real mostrando únicamente las llegadas cuyo titular coincida con el texto ingresado | [ ] | [ ] |
| 4 | Navegar al flujo de Check-In desde una llegada | En una fila de llegada filtrada, hacer clic en el botón "Check-in" | Clic en botón "Check-in" de la fila de hab. 201 | El sistema redirige a `/check-in?reservationRef=...&roomId=...` iniciando directamente el proceso de admisión con la reserva y habitación precargadas | [ ] | [ ] |
| 5 | Cambiar a la pestaña Salidas | Regresar a `/panel` y hacer clic en la pestaña "Salidas" | Clic en pestaña "Salidas" | La URL se actualiza a `/panel?tab=salidas`, se activa la pestaña Salidas y se muestra la tabla con columnas: Hab., Huésped titular, Fecha entrada, Fecha salida, Personas, Tipo habitación, Fuente y botón "Check-out" | [ ] | [ ] |
| 6 | Verificar estancia con salida vencida | Observar las filas de estancias en la tabla de Salidas | Estancia con fecha de salida esperada anterior a hoy | La fecha de salida se muestra destacada en color de alerta y acompañada de la etiqueta "Vencida" | [ ] | [ ] |
| 7 | Filtrar salidas en tiempo real | Escribir un criterio de búsqueda (número de habitación, titular o documento) en la barra de búsqueda | Texto: número de habitación (ej. `102`) | La tabla filtra inmediatamente mostrando únicamente la estancia correspondiente | [ ] | [ ] |
| 8 | Navegar al flujo de Check-Out desde una salida | En la fila de la estancia, hacer clic en el botón "Check-out" | Clic en botón "Check-out" de la hab. 102 | El sistema redirige a `/check-out?reservationRef=...&roomId=...` cargando directamente el proceso de salida para esa estancia | [ ] | [ ] |

--------------------------------------------------------------------------------

##### Section 2: Estados Vacíos, Alertas Operativas y Contingencia
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda sin coincidencias en pestaña Llegadas | En la pestaña Llegadas, ingresar un texto sin coincidencias en el buscador | Texto: `XYZ9999` | La tabla muestra centrado el mensaje: "No hay reservas de llegada que mostrar." | [Pass] | Verificado por test automatizado (`ReceptionPanel.test.tsx`) |
| 2 | Búsqueda sin coincidencias en pestaña Salidas | En la pestaña Salidas, ingresar un texto sin coincidencias en el buscador | Texto: `XYZ9999` | La tabla muestra centrado el mensaje: "No hay salidas que mostrar." | [Pass] | Verificado por test automatizado (`ReceptionPanel.test.tsx`) |
| 3 | Visualización de llegada con habitación no disponible (mantenimiento o baja) | Observar una llegada cuya habitación física asignada se encuentra en DisabledForRepairs, TechnicalBlock o Inactive | Habitación en estado TechnicalBlock o Inactive | La fila de la habitación muestra la etiqueta de alerta: "No disponible: [Estado]" (ej. "No disponible: Bloqueo técnico") y el botón "Check-in" se presenta deshabilitado | [Pass] | Verificado por test automatizado (`ReceptionPanel.test.tsx`) |
| 4 | Visualización de llegada con habitación en limpieza o aún ocupada | Observar una llegada cuya habitación física asignada se encuentra en Occupied o PendingCleaning | Habitación en estado Occupied o PendingCleaning | La fila muestra el texto informativo: "[Estado]: se apartará al terminar la limpieza" (ej. "Ocupada: se apartará al terminar la limpieza") | [Pass] | Verificado por test automatizado (`ReceptionPanel.test.tsx`) |
| 5 | Visualización cuando la lista del día no ha sido recibida | Simular estado en que la lista diaria no ha llegado a Módulo 1 y acceder a la pestaña Llegadas | Copia local de reservas del día vacía / sin ingesta previa | La pestaña Llegadas muestra el mensaje: "Las llegadas de hoy no están disponibles en este momento. Inténtalo más tarde.", mientras que los KPIs de estancias y la pestaña Salidas continúan funcionando con normalidad | [Pass] | Verificado por test automatizado (`ReceptionPanel.test.tsx`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Concurrencia de recepcionistas: dos recepcionistas abren el panel simultáneamente y filtran llegadas; ambas obtienen lecturas locales coherentes e independientes en menos de 500 ms.
* **EC-002** : Espacios en blanco adicionales y mayúsculas en la búsqueda: ingresar `   ana   ` o `ANA` filtra correctamente la reserva de "Ana Pérez" sin generar errores.
* **EC-003** : Actualización de reservas durante la jornada: al recibirse una notificación `ADDED` o `REMOVED` de Módulo 2, el siguiente refresco del panel actualiza automáticamente la tabla de Llegadas y el indicador "Llegadas de hoy".

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
