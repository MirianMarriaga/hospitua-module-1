# Test Case: Consultar Inventario de Habitaciones

**Created**: 2026-11-07  
**Spec/Plan Link**: [spec-consultar-inventario-habitaciones.md](../SPEC/spec-consultar-inventario-habitaciones.md) | [plan-consultar-inventario-habitaciones.md](../PLAN/plan-consultar-inventario-habitaciones.md)  
**Status**: Draft  

---

## Test Overview

| Field | Value |
| --- | --- |
| **Test Case ID** | TC-021-ConsultarInventarioHabitaciones |
| **Product Name & Version** | Hospitua Módulo 1 v0.1 |
| **Author** | Equipo Módulo 1 |
| **Current Owner** | Por asignar |
| **Execution Type** | Manual |
| **Priority** | P2 |

---

## Test Objectives and Document References

### Objectives
Verificar la capacidad del Administrador y del Gerente para consultar el inventario completo de habitaciones del hotel de forma paginada y ordenada, aplicando filtros por número, tipo, estado y piso, asegurando que las habitaciones inactivas se incluyan por defecto en las vistas internas pero se excluyan en los servicios para Módulo 2, y comprobando la presencia de las acciones contextuales correspondientes a cada rol y estado.

### Document References
| Document Type | Document Name & Link |
| --- | --- |
| Functional Specification | [spec-consultar-inventario-habitaciones.md](../SPEC/spec-consultar-inventario-habitaciones.md) |
| Implementation Plan | [plan-consultar-inventario-habitaciones.md](../PLAN/plan-consultar-inventario-habitaciones.md) |

---

## Prerequisites and Test Environment

### Assumptions and Limitations
- Requiere el sistema Hospitua Módulo 1 con sus servicios activos y la base de datos PostgreSQL inicializada con la semilla de habitaciones.
- La pantalla `/administracion/inventario` está implementada en React y conectada al backend REST `GET /api/rooms/inventory`.

### Dependencies
- Habitaciones semilla cargadas en base de datos en diferentes pisos, categorías y estados.

### Test Environment Requirements
- Navegador web moderno con acceso a la aplicación Hospitua.
- Cuentas de usuario configuradas para roles de Administrador y Gerente.

### Test Data and Accounts
- Habitaciones semilla existentes: 101, 102, 103, 104 (Inactiva), 105, 201, 202, 203, 204 (Inactiva), 205, 301, 302, 303, 304.
- Rol Administrador y Rol Gerente (ver credenciales de pruebas del equipo).

---

## Test Execution Flow

### Section 1: Flujo Principal - Consulta Completa y Paginación

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Iniciar sesión como Administrador y acceder al inventario | Menú principal > Administración > Inventario de Habitaciones `/administracion/inventario` | Usuario Administrador | Se despliega la pantalla "Inventario de habitaciones" con el subtítulo "Registra, edita, da de baja o reactiva habitaciones del hotel.", el botón "Registrar habitación" y la tabla con las columnas: Hab., Piso, Tipo, Capacidad, Tarifa base, Estado, Acciones. | [ ] | |
| 2 | Verificar inclusión de habitaciones inactivas por defecto | Tabla de inventario sin filtros activos | Vista general | La tabla lista las habitaciones operativas e incluye también las habitaciones inactivas (ej. 104 y 204) con badge gris "Inactiva". | [ ] | |
| 3 | Verificar acciones contextuales del rol Administrador | Fila de habitación disponible (101) y fila de habitación inactiva (104) | Habitaciones 101 y 104 | En la habitación 101 se observan los botones "Editar" y "Dar de baja". En la habitación 104 se observa el botón "Marcar como disponible". En habitaciones ocupadas/reservadas se muestra "—". | [ ] | |
| 4 | Navegar entre páginas de la tabla | Barra de paginación inferior > Clic en botón "Siguiente" o página "2" | Total de 14 habitaciones (página de 10) | La tabla actualiza los elementos mostrando los registros 11 al 14, indicando "Página 2 de 2" y manteniendo la consistencia de columnas y acciones. | [ ] | |
| 5 | Acceder como rol Gerente | Iniciar sesión como Gerente > Acceder a Inventario `/gerencia/inventario` | Usuario Gerente | Se despliega el inventario con subtítulo "Consulta el inventario completo del hotel y el estado actual de cada habitación." sin botón "Registrar habitación", y en la columna de acciones cada fila presenta el botón "Ver historial". | [ ] | |

### Section 2: Aplicación de Filtros y Búsquedas

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 6 | Filtrar por número de habitación | Panel de filtros > Campo "Número" > Ingresar "101" | Número: "101" | La tabla se actualiza automáticamente mostrando únicamente la fila correspondiente a la habitación 101. | [Pass] | Verificado por test automatizado (`RoomInventoryPage.test.tsx`) |
| 7 | Filtrar por categoría / tipo de habitación | Selector "Tipo" > Elegir "Suite" | Tipo: SUITE | La tabla lista exclusivamente las habitaciones de tipo Suite (ej. 105 y 204). | [Pass] | Verificado por test automatizado (`RoomInventoryPage.test.tsx`) |
| 8 | Filtrar por estado | Selector "Estado" > Elegir "Disponible" | Estado: Disponible | La tabla excluye las habitaciones inactivas, ocupadas o en mantenimiento, mostrando únicamente aquellas con badge verde "Disponible". | [Pass] | Verificado por test automatizado (`RoomInventoryPage.test.tsx`) |
| 9 | Filtrar por piso | Campo "Piso" > Ingresar "2" | Piso: 2 | La tabla muestra únicamente las habitaciones pertenecientes al piso 2 (201 a 205). | [Pass] | Verificado por test automatizado (`RoomInventoryPage.test.tsx`) |
| 10 | Búsqueda sin coincidencias y limpieza de filtros | Campo "Número" > Ingresar "999" | Número: "999" | La tabla muestra el mensaje: "Ninguna habitación cumple los filtros seleccionados." junto con el enlace interactivo "Limpiar filtros". | [Pass] | Verificado por test automatizado (`RoomInventoryPage.test.tsx`) |
| 11 | Restablecer filtros | Clic en enlace "Limpiar filtros" | - | Se restablecen todos los campos de búsqueda y la tabla vuelve a cargar el listado completo desde la página 1. | [Pass] | Verificado por test automatizado (`RoomInventoryPage.test.tsx`) |

---

## Edge Cases & Boundary Conditions

| Case # | Description | Preconditions | Inputs / Actions | Expected Results | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| EC-001 | Búsqueda de habitación con espacios en blanco | Inventario con filtros | Ingresar " 101 " en el campo número | El sistema normaliza la búsqueda por coincidencia exacta sin espacios y retorna la habitación 101. | [Pass] | Verificado por test automatizado (`RoomInventoryPage.test.tsx`) |
| EC-002 | Consulta de inventario por parte de Módulo 2 (`GET /api/rooms`) | Invocación de API de integración | Consulta para venta externa | El sistema responde lista de habitaciones vendibles y NUNCA incluye habitaciones en estado `Inactive`. | [Pass] | Verificado por test automatizado (`RoomControllerTest`) |
| EC-003 | Cambio de filtro mientras se está en página avanzada | Usuario posicionado en página 2 | Aplicar filtro por tipo "Suite" | El sistema restablece automáticamente la paginación a la página 1 (`page=1`) con los resultados filtrados. | [Pass] | Verificado por test automatizado (`RoomInventoryPage.test.tsx`) |
| EC-004 | Inventario sin ninguna habitación registrada | Base de datos vacía | Cargar pantalla de inventario | La tabla muestra el estado vacío: "Todavía no hay habitaciones registradas." conservando las cabeceras intactas. | [Pass] | Verificado por test automatizado (`RoomInventoryPage.test.tsx`) |

---

## Defect Severity Reference

| Severity | Description | Examples |
| --- | --- | --- |
| **Critical (P1)** | Impide la operación total de una función básica, causa pérdida o corrupción de datos sin solución alternativa inmediata. | El endpoint expone habitaciones dadas de baja (`Inactive`) a los canales de venta externa de Módulo 2. |
| **Major (P2)** | Falla mayor en una funcionalidad relevante con solución temporal disponible pero no ideal. | Al filtrar por estado la paginación no se restablece a la página 1, mostrando pantallas vacías. |
| **Moderate (P3)** | Defecto que afecta un flujo secundario o una validación no crítica. | Botón "Limpiar filtros" no limpia inmediatamente el texto en los inputs debounced. |
| **Minor (P4)** | Inconsistencias estéticas menores o errores tipográficos en etiquetas que no impiden la comprensión. | Alineación del texto "Mostrando X–Y de Z" en la barra de paginación. |

