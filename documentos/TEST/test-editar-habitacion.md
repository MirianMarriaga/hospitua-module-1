# Test Case: Editar Habitación

**Created**: 2026-11-07  
**Spec/Plan Link**: [spec-editar-habitacion.md](../SPEC/spec-editar-habitacion.md) | [plan-editar-habitacion.md](../PLAN/plan-editar-habitacion.md)  
**Status**: Draft  

---

## Test Overview

| Field | Value |
| --- | --- |
| **Test Case ID** | TC-019-EditarHabitacion |
| **Product Name & Version** | Hospitua Módulo 1 v0.1 |
| **Author** | Equipo Módulo 1 |
| **Current Owner** | Por asignar |
| **Execution Type** | Manual |
| **Priority** | P2 |

---

## Test Objectives and Document References

### Objectives
Verificar la capacidad del administrador para modificar los datos de identificación, categorización y tarifa base de una habitación existente únicamente cuando esta se encuentra en estado "Disponible" (`Available`), validando el bloqueo optimista frente a concurrencia, la unicidad del número modificado, el registro de la bitácora de cambios y la visualización de la confirmación con la tabla comparativa "Antes / Ahora".

### Document References
| Document Type | Document Name & Link |
| --- | --- |
| Functional Specification | [spec-editar-habitacion.md](../SPEC/spec-editar-habitacion.md) |
| Implementation Plan | [plan-editar-habitacion.md](../PLAN/plan-editar-habitacion.md) |

---

## Prerequisites and Test Environment

### Assumptions and Limitations
- Requiere el sistema Hospitua Módulo 1 con sus servicios activos y la base de datos PostgreSQL inicializada con la semilla de habitaciones.
- La pantalla `/administracion/habitaciones/:roomId/editar` está implementada en React y conectada al backend REST `PUT /api/rooms/{roomId}`.

### Dependencies
- TC-018-RegistrarHabitacion y TC-021-ConsultarInventarioHabitaciones deben estar operativos.

### Test Environment Requirements
- Navegador web moderno con acceso a la aplicación Hospitua.
- Base de datos con habitaciones en diferentes estados: Disponible (101), Ocupada (103), Inactiva (104).

### Test Data and Accounts
- Habitación "101" (Disponible, categoría Sencilla, Tarifa $120.000).
- Habitación "103" (Ocupada o Reservada).
- Habitación "104" (Inactiva).
- Cuenta con rol de Administrador (ver credenciales de pruebas del equipo).

---

## Test Execution Flow

### Section 1: Flujo Principal - Edición Exitosa con Cambios y Confirmación

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Iniciar sesión como Administrador y acceder al inventario | Menú principal > Administración > Inventario de Habitaciones `/administracion/inventario` | Usuario Administrador | Se visualiza la tabla de inventario con las habitaciones listadas y sus botones de acción. | [ ] | |
| 2 | Seleccionar acción de edición sobre habitación disponible | Fila de la habitación 101 > Clic en botón "Editar" | Habitación 101 (`Available`) | Se despliega la pantalla "Editar habitación 101" con el subtítulo "Modifica los datos que necesites; los demás se conservan.", badge de estado "Disponible" y los campos precargados con los valores actuales. | [ ] | |
| 3 | Modificar tarifa base y capacidad máxima | Pantalla de edición > Modificar campos Tarifa base y Capacidad máxima | Capacidad máxima: "2", Tarifa base: "150000" | Los campos reflejan los nuevos valores digitados. | [ ] | |
| 4 | Confirmar guardado de los cambios | Pantalla de edición > Clic en botón "Guardar cambios" | Cambios válidos sobre habitación 101 | El sistema valida que la habitación siga en Disponible, actualiza los campos, registra la bitácora con los cambios realizados y muestra la pantalla de confirmación. | [ ] | |
| 5 | Verificar pantalla de confirmación y tabla comparativa | Pantalla de éxito de edición | Habitación 101 actualizada | Se muestra el encabezado "Habitación 101 actualizada", el texto "Los cambios ya se reflejan en el inventario.", la tabla comparativa con columnas "Dato", "Antes" y "Ahora" (mostrando Capacidad máxima: 1 -> 2 personas, Tarifa base: $ 120.000 -> $ 150.000 tachado/nuevo) y la ficha resumen con los datos actuales. | [ ] | |
| 6 | Volver al inventario y comprobar persistencia | Pantalla de confirmación > Clic en "Volver al inventario" | - | Se redirige a `/administracion/inventario` y la habitación 101 refleja la nueva tarifa de $150.000 y capacidad 2. | [ ] | |

### Section 2: Restricción por Estado No Disponible

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 7 | Intentar acceder a edición de habitación ocupada o reservada | Inventario > Fila habitación no disponible (ej. 103) o acceder directamente por URL a `/administracion/habitaciones/{id103}/editar` | Habitación 103 (`Occupied`) | La pantalla bloquea el formulario y muestra la alerta: "No se puede editar la habitación 103. La habitación está en estado Ocupada. Solo se pueden editar habitaciones disponibles." con botón "Volver al inventario". | [Pass] | Verificado por test automatizado (`EditRoomPage.test.tsx` y `UpdateRoomIntegrationTest`) |
| 8 | Intentar acceder a edición de habitación inactiva | Acceder a edición de habitación 104 (`Inactive`) | Habitación 104 (`Inactive`) | Se bloquea el formulario informando que la habitación está en estado Fuera de servicio (Inactiva) y solo se pueden editar habitaciones disponibles. | [Pass] | Verificado por test automatizado (`EditRoomPage.test.tsx` y `UpdateRoomIntegrationTest`) |

### Section 3: Validaciones, Rechazos y Control de Concurrencia

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 9 | Intentar guardar sin modificar ningún dato | Pantalla de edición > Sin alterar ningún campo > Clic en "Guardar cambios" | Mismos valores actuales | El sistema no envía petición al backend y muestra el mensaje informativo: "No has modificado ningún dato." | [Pass] | Verificado por test automatizado (`EditRoomPage.test.tsx`) |
| 10 | Intentar cambiar número por uno ya existente en otra habitación | Pantalla de edición habitación 101 > Cambiar número a "102" > Clic en "Guardar cambios" | Número: "102" | El sistema rechaza la edición indicando: "Ya existe una habitación con el número 102 en el inventario." | [Pass] | Verificado por test automatizado (`UpdateRoomIntegrationTest`) |
| 11 | Intentar ingresar tarifa base cero o negativa | Pantalla de edición > Tarifa base: "0" > Clic en "Guardar cambios" | Tarifa base: 0 | El sistema muestra el mensaje de error: "La tarifa base debe ser mayor a cero." | [Pass] | Verificado por test automatizado (`EditRoomPage.test.tsx`) |
| 12 | Intentar ingresar capacidad o piso cero o negativo | Pantalla de edición > Capacidad: "0" | Capacidad: 0 | El sistema muestra el mensaje de error: "La capacidad debe ser un número entero mayor a cero." | [Pass] | Verificado por test automatizado (`EditRoomPage.test.tsx`) |
| 13 | Detección de actualización concurrente (bloqueo optimista) | Dos sesiones de administración abren la edición de la habitación 101 simultáneamente. La Sesión A guarda un cambio primero. | Sesión B intenta guardar con la versión desactualizada | El sistema detecta el conflicto de versión (`CONCURRENT_UPDATE`) y muestra la advertencia: "Otro usuario actualizó la información de esta habitación mientras la editabas. No se guardaron tus cambios. Cargar los datos actuales". | [Pass] | Verificado por test automatizado (`UpdateRoomIntegrationTest`) |

---

## Edge Cases & Boundary Conditions

| Case # | Description | Preconditions | Inputs / Actions | Expected Results | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| EC-001 | Modificación de número de habitación que difiere solo en mayúsculas/espacios | Habitación 101 | Ingresar " 101 " | El sistema normaliza el valor, detecta que es el mismo número asignado a la propia habitación y no reporta error de duplicado. | [Pass] | Verificado por test automatizado (`UpdateRoomIntegrationTest`) |
| EC-002 | Modificación a un número que pertenece a una habitación inactiva (`Inactive`) | Habitación 104 está dada de baja con número 104 | Intentar renombrar la habitación 101 a "104" | El sistema rechaza la modificación por colisión con la habitación inactiva. | [Pass] | Verificado por test automatizado (`UpdateRoomIntegrationTest`) |
| EC-003 | Habitación cambia de estado mientras el formulario está abierto | La habitación pasa de Disponible a Reservada por una asignación en recepción | El administrador intenta guardar cambios | El backend rechaza la operación informando que la habitación no está disponible para edición (`ROOM_INVALID_STATE`). | [Pass] | Verificado por test automatizado (`UpdateRoomIntegrationTest`) |
| EC-004 | Acceso con identificador UUID inexistente | URL `/administracion/habitaciones/uuid-falso/editar` | Navegación directa | El sistema presenta la tarjeta "Habitación no encontrada. No existe ninguna habitación con ese identificador." con enlace para volver al inventario. | [Pass] | Verificado por test automatizado (`EditRoomPage.test.tsx`) |

---

## Defect Severity Reference

| Severity | Description | Examples |
| --- | --- | --- |
| **Critical (P1)** | Impide la operación total de una función básica, causa pérdida o corrupción de datos sin solución alternativa inmediata. | Se permite editar una habitación que está actualmente ocupada por un huésped. |
| **Major (P2)** | Falla mayor en una funcionalidad relevante con solución temporal disponible pero no ideal. | El conflicto de concurrencia sobrescribe silenciosamente los cambios del otro usuario sin avisar. |
| **Moderate (P3)** | Defecto que afecta un flujo secundario o una validación no crítica. | La tabla comparativa "Antes / Ahora" no muestra el valor tachado del dato modificado. |
| **Minor (P4)** | Inconsistencias estéticas menores o errores tipográficos en etiquetas que no impiden la comprensión. | Etiqueta de moneda con espaciado irregular en la tarjeta de confirmación. |

