# Test Case: Consultar Historial de Estados

**Created**: 2026-11-07  
**Spec/Plan Link**: [spec-consultar-historial-de-estados.md](../SPEC/spec-consultar-historial-de-estados.md) | [plan-consultar-historial-de-estados.md](../PLAN/plan-consultar-historial-de-estados.md)  
**Status**: Draft  

---

## Test Overview

| Field | Value |
| --- | --- |
| **Test Case ID** | TC-022-ConsultarHistorialDeEstados |
| **Product Name & Version** | Hospitua Módulo 1 v0.1 |
| **Author** | Equipo Módulo 1 |
| **Current Owner** | Por asignar |
| **Execution Type** | Manual |
| **Priority** | P2 |

---

## Test Objectives and Document References

### Objectives
Verificar la capacidad del Gerente para auditar y consultar la evolución histórica de estados y periodos de las habitaciones del hotel, validando los filtros combinables por habitación, tipo, estado y rango de fechas, comprobando la presentación de los campos de auditoría (inicio, fin o "En curso", responsable o "Sistema", y flujo de origen), y verificando el control de fechas inválidas y la paginación de resultados.

### Document References
| Document Type | Document Name & Link |
| --- | --- |
| Functional Specification | [spec-consultar-historial-de-estados.md](../SPEC/spec-consultar-historial-de-estados.md) |
| Implementation Plan | [plan-consultar-historial-de-estados.md](../PLAN/plan-consultar-historial-de-estados.md) |

---

## Prerequisites and Test Environment

### Assumptions and Limitations
- Requiere el sistema Hospitua Módulo 1 con sus servicios activos y la base de datos PostgreSQL inicializada con transiciones históricas registradas.
- La pantalla `/gerencia/historial` está implementada en React y conectada al backend REST `GET /api/rooms/state-history`.

### Dependencies
- TC-021-ConsultarInventarioHabitaciones debe estar operativo para la navegación contextual desde la acción "Ver historial".
- Casos de prueba de transiciones (check-in, check-out, limpieza, mantenimiento) deben haber generado periodos en `room_state_history`.

### Test Environment Requirements
- Navegador web moderno con acceso a la aplicación Hospitua.
- Cuentas de usuario configuradas con rol de Gerente.

### Test Data and Accounts
- Habitación "203" (Doble, con transiciones históricas previas).
- Cuenta con rol de Gerente (ver credenciales de pruebas del equipo).

---

## Test Execution Flow

### Section 1: Flujo Principal - Consulta de Historial y Navegación desde Inventario

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Iniciar sesión como Gerente y acceder al historial | Menú principal > Gerencia > Historial de Estados `/gerencia/historial` | Usuario Gerente | Se despliega la pantalla "Historial de estados" con el subtítulo "Consulta por cuánto tiempo estuvo cada habitación en cada estado.", el panel de filtros y la tabla con columnas: Hab., Tipo, Estado, Inicio, Fin, Responsable, Flujo de origen. | [ ] | |
| 2 | Navegar al historial desde el inventario de habitaciones | Gerencia > Inventario `/gerencia/inventario` > Fila habitación 203 > Clic en botón "Ver historial" | Habitación 203 | Se abre la pantalla `/gerencia/historial?roomId=...&roomNumber=203` con el filtro de habitación ya aplicado a "203" y mostrando la secuencia cronológica de sus periodos de estado. | [ ] | |
| 3 | Verificar periodo cerrado y periodo en curso | Tabla de historial > Inspeccionar filas | Historial habitación 203 | Los periodos finalizados muestran fecha/hora de inicio y de fin formateadas; el periodo actual muestra la etiqueta azul "En curso" en la columna Fin. | [ ] | |
| 4 | Verificar campos de trazabilidad y auditoría | Tabla de historial > Columnas Responsable y Flujo de origen | Periodos de la tabla | Se visualiza el nombre del usuario responsable (o "Sistema" si la transición fue automática) y el nombre del caso de uso de negocio en "Flujo de origen" (ej. "Registrar check-in", "Confirmar fin de limpieza", etc.). | [ ] | |
| 5 | Paginación de resultados | Barra inferior de paginación > Cambiar de página | Más de 15 registros | La tabla pagina los resultados de 15 en 15 mostrando "Página X de Y" y manteniendo los filtros activos. | [ ] | |

### Section 2: Filtros por Tipo, Estado y Rango Temporal

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 6 | Filtrar por estado específico | Panel de filtros > Selector "Estado" > Elegir "En limpieza" | Estado: `InCleaning` | La tabla actualiza sus resultados mostrando únicamente los periodos en los cuales las habitaciones estuvieron en estado En limpieza. | [Pass] | Verificado por test automatizado (`RoomStateHistoryPage.test.tsx`) |
| 7 | Filtrar por categoría de habitación | Selector "Tipo" > Elegir "Doble" | Tipo: DOBLE | La tabla lista únicamente los periodos pertenecientes a habitaciones de categoría Doble. | [Pass] | Verificado por test automatizado (`RoomStateHistoryPage.test.tsx`) |
| 8 | Filtrar por rango de fechas válido | Campos "Desde" y "Hasta" > Ingresar fechas válidas | Desde: "2026-01-01", Hasta: "2026-01-31" | Se muestran los periodos cuyo rango temporal se solapa o está comprendido en el mes de enero de 2026. | [Pass] | Verificado por test automatizado (`RoomStateHistoryPage.test.tsx`) |
| 9 | Intentar filtrar con fecha Desde posterior a Hasta | Campos de fecha > Ingresar Desde mayor a Hasta | Desde: "2026-02-15", Hasta: "2026-02-10" | El sistema detecta el rango inválido, bloquea la consulta y muestra el mensaje de error: 'La fecha "Desde" no puede ser posterior a "Hasta".' | [Pass] | Verificado por test automatizado (`RoomStateHistoryPage.test.tsx`) |
| 10 | Búsqueda sin coincidencias | Aplicar combinación de filtros sin registros coincidentes | Habitación: "999" | La tabla presenta el mensaje de estado vacío: "No se encontraron resultados" centrado en el cuerpo de la tabla. | [Pass] | Verificado por test automatizado (`RoomStateHistoryPage.test.tsx`) |

---

## Edge Cases & Boundary Conditions

| Case # | Description | Preconditions | Inputs / Actions | Expected Results | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| EC-001 | Habitación recién creada sin cambios de estado posteriores | Habitación registrada solo en Disponible | Consultar historial de esa habitación | La tabla muestra una única fila con estado "Disponible", fecha de inicio correspondiente a su creación y columna Fin en "En curso". | [Pass] | Verificado por test automatizado (`RoomStateHistoryIntegrationTest`) |
| EC-002 | Consulta simultánea con transiciones en ejecución | Módulo de recepción ejecutando check-in al mismo tiempo | Recargar historial | La consulta es de solo lectura sobre snapshot consistente, no genera bloqueos de base de datos ni afecta las transiciones activas. | [Pass] | Verificado por test automatizado (`RoomStateHistoryIntegrationTest`) |
| EC-003 | Historial extenso con alto volumen de registros | Base de datos con más de 10.000 periodos registrados | Cargar vista de historial sin filtros | La página carga y responde de forma paginada en menos de 5 segundos. | [Pass] | Verificado por test automatizado (`RoomStateHistoryIntegrationTest`) |
| EC-004 | Cambio de número de habitación manual en filtro | Usuario venía de "Ver historial" con `roomId` predeterminado | Modificar el input de texto a otro número | El sistema elimina el parámetro `roomId` de la URL y filtra por el nuevo número digitado. | [Pass] | Verificado por test automatizado (`RoomStateHistoryPage.test.tsx`) |

---

## Defect Severity Reference

| Severity | Description | Examples |
| --- | --- | --- |
| **Critical (P1)** | Impide la operación total de una función básica, causa pérdida o corrupción de datos sin solución alternativa inmediata. | La consulta ejecuta bloqueos pesados de tabla impidiendo el funcionamiento de recepción o limpieza. |
| **Major (P2)** | Falla mayor en una funcionalidad relevante con solución temporal disponible pero no ideal. | La columna "Fin" no muestra el badge "En curso" cuando el periodo está abierto y muestra error o fecha nula. |
| **Moderate (P3)** | Defecto que afecta un flujo secundario o una validación no crítica. | El mensaje de fechas invertidas no desaparece inmediatamente al corregir la fecha Hasta. |
| **Minor (P4)** | Inconsistencias estéticas menores o errores tipográficos en etiquetas que no impiden la comprensión. | Formato de hora en 24h sin ceros a la izquierda en los minutos. |

