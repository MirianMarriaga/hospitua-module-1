# Test Case: Registrar Habitación

**Created**: 2026-11-07  
**Spec/Plan Link**: [spec-registrar-habitacion.md](../SPEC/spec-registrar-habitacion.md) | [plan-registrar-habitacion.md](../PLAN/plan-registrar-habitacion.md)  
**Status**: Draft  

---

## Test Overview

| Field | Value |
| --- | --- |
| **Test Case ID** | TC-018-RegistrarHabitacion |
| **Product Name & Version** | Hospitua Módulo 1 v0.1 |
| **Author** | Equipo Módulo 1 |
| **Current Owner** | Por asignar |
| **Execution Type** | Manual |
| **Priority** | P2 |

---

## Test Objectives and Document References

### Objectives
Verificar la capacidad del administrador para registrar nuevas habitaciones en el sistema, validando los campos obligatorios de identificación, categorización y tarifa base, asegurando la unicidad del número de habitación sin importar mayúsculas ni espacios, comprobando la asignación inicial del estado "Disponible" (`Available`), la apertura del periodo correspondiente en el historial de estados y la visualización de la confirmación con opciones de continuidad.

### Document References
| Document Type | Document Name & Link |
| --- | --- |
| Functional Specification | [spec-registrar-habitacion.md](../SPEC/spec-registrar-habitacion.md) |
| Implementation Plan | [plan-registrar-habitacion.md](../PLAN/plan-registrar-habitacion.md) |

---

## Prerequisites and Test Environment

### Assumptions and Limitations
- Requiere el sistema Hospitua Módulo 1 con sus servicios activos y la base de datos PostgreSQL inicializada con la semilla de habitaciones.
- La pantalla de registro `/administracion/registrar-habitacion` está implementada en React y conectada al backend REST `/api/rooms`.

### Dependencies
- El inventario de habitaciones debe estar operativo para comprobar la visualización inmediata de la nueva unidad.

### Test Environment Requirements
- Navegador web moderno con acceso a la aplicación Hospitua.
- Base de datos con las habitaciones semilla ya cargadas (incluyendo habitaciones 101 a 105, 201 a 205, etc.).

### Test Data and Accounts
- Habitación existente en semilla: "204" (Inactiva) y "101" (Disponible).
- Nueva habitación a registrar: "407", Piso 4, Tipo Doble, Capacidad 2, Tarifa $250.000.
- Cuenta con rol de Administrador (ver credenciales de pruebas del equipo).

---

## Test Execution Flow

### Section 1: Flujo Principal - Registro Exitoso y Confirmación

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Iniciar sesión como Administrador y navegar al formulario de registro | Menú principal > Administración > Inventario de Habitaciones `/administracion/inventario` > Clic en botón "Registrar habitación" | Usuario Administrador | Se despliega la pantalla "Registrar habitación" con el subtítulo "La habitación quedará disponible en el inventario apenas se registre." y el formulario vacío. | [ ] | |
| 2 | Completar datos obligatorios de identificación | Formulario de registro > Grupo "Identificación" > Diligenciar campos | Número de habitación: "407", Piso: "4" | Los campos aceptan los valores y se muestran formateados sin errores. | [ ] | |
| 3 | Completar categorización y tarifa base | Formulario de registro > Grupo "Categorización y tarifa" > Seleccionar y diligenciar | Tipo de habitación: "Doble", Capacidad máxima: "2", Tarifa base: "250000" | Se seleccionan las opciones válidas del selector y se ingresan valores enteros y decimales válidos. | [ ] | |
| 4 | Confirmar guardado de la habitación | Formulario de registro > Clic en botón "Registrar habitación" | Datos completos de la habitación 407 | El sistema persiste la habitación con estado inicial "Disponible" (`Available`), genera su identificador UUID, abre el historial de estados y muestra la tarjeta de confirmación de éxito. | [ ] | |
| 5 | Verificar pantalla de confirmación | Pantalla de confirmación de registro | Habitación 407 registrada | Se visualiza el encabezado "Habitación 407 registrada", el texto "Ya aparece en el inventario en estado Disponible.", la ficha con Estado: Disponible, Número: 407, Piso: Piso 4, Tipo: Doble, Capacidad máxima: 2 personas, Tarifa base: $ 250.000, y los botones "Registrar otra" y "Volver al inventario". | [ ] | |
| 6 | Volver al inventario y verificar la nueva habitación | Pantalla de confirmación > Clic en botón "Volver al inventario" | - | Se redirige a `/administracion/inventario` y la habitación 407 figura en la tabla en estado Disponible con su tipo Doble y tarifa $250.000. | [ ] | |

### Section 2: Validaciones y Rechazos por Duplicidad o Datos Inválidos

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 7 | Intentar registrar con número de habitación duplicado existente | Formulario "Registrar habitación" > Ingresar número ya existente en semilla > Clic en "Registrar habitación" | Número de habitación: "204" (o "101"), Piso: 2, Tipo: Doble, Capacidad: 2, Tarifa: 200000 | El sistema rechaza el guardado y muestra el mensaje de error: "Ya existe una habitación con el número 204." marcando el campo en rojo. | [Pass] | Verificado por test automatizado (`RegisterRoomPage.test.tsx` y `RegisterRoomIntegrationTest`) |
| 8 | Intentar registrar número duplicado con espacios y minúsculas | Formulario "Registrar habitación" > Ingresar número existente con espacios | Número: " 101 ", Piso: 1, Tipo: Sencilla, Capacidad: 1, Tarifa: 120000 | El sistema normaliza a mayúsculas y sin espacios y rechaza indicando: "Ya existe una habitación con el número 101." | [Pass] | Verificado por test automatizado (`RegisterRoomIntegrationTest`) |
| 9 | Intentar registrar con campos obligatorios vacíos | Formulario "Registrar habitación" > Dejar campos vacíos > Clic en "Registrar habitación" | Formulario en blanco | El sistema bloquea el envío y muestra los mensajes de validación requeridos bajo cada campo obligatorio. | [Pass] | Verificado por test automatizado (`RegisterRoomPage.test.tsx`) |
| 10 | Intentar registrar número de habitación con más de 10 caracteres | Formulario "Registrar habitación" > Ingresar número largo | Número: "HABITACION101" (13 caracteres) | El sistema muestra el mensaje de validación: "El número de habitación debe tener máximo 10 caracteres." | [Pass] | Verificado por test automatizado (`RegisterRoomPage.test.tsx`) |
| 11 | Intentar registrar con piso cero o negativo | Formulario "Registrar habitación" > Ingresar piso 0 | Piso: "0" | El sistema muestra el mensaje de validación: "El piso debe ser un número entero mayor a 0." | [Pass] | Verificado por test automatizado (`RegisterRoomPage.test.tsx`) |
| 12 | Intentar registrar con capacidad máxima cero o negativa | Formulario "Registrar habitación" > Ingresar capacidad 0 | Capacidad máxima: "0" | El sistema muestra el mensaje de validación: "La capacidad máxima debe ser un número entero mayor a 0." | [Pass] | Verificado por test automatizado (`RegisterRoomPage.test.tsx`) |
| 13 | Intentar registrar con tarifa base cero o negativa | Formulario "Registrar habitación" > Ingresar tarifa 0 | Tarifa base: "0" | El sistema muestra el mensaje de validación: "La tarifa base debe ser mayor a 0 y tener máximo dos decimales." | [Pass] | Verificado por test automatizado (`RegisterRoomPage.test.tsx`) |

---

## Edge Cases & Boundary Conditions

| Case # | Description | Preconditions | Inputs / Actions | Expected Results | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| EC-001 | Registro de habitación alfanumérica válida | Formulario de registro | Número: "204A" (o "PH-1") | El sistema acepta el identificador alfanumérico dentro del límite de 10 caracteres y lo guarda en mayúsculas. | [Pass] | Verificado por test automatizado (`RegisterRoomIntegrationTest`) |
| EC-002 | Duplicado contra habitación en estado Inactiva (`Inactive`) | Habitación 104 está en estado `Inactive` | Intentar registrar nueva habitación con número "104" | El sistema rechaza el registro: la unicidad del número aplica sobre la totalidad del inventario, incluidas las bajas. | [Pass] | Verificado por test automatizado (`RegisterRoomIntegrationTest`) |
| EC-003 | Tarifa base con centavos (dos decimales) | Formulario de registro | Tarifa base: 185000.50 | El sistema acepta el valor numérico con dos decimales y lo almacena correctamente. | [Pass] | Verificado por test automatizado (`RegisterRoomIntegrationTest`) |
| EC-004 | Concurrencia al registrar la misma habitación | Dos administradores intentando registrar "501" simultáneamente | Enviar guardado al mismo tiempo | Una de las operaciones tiene éxito y la segunda recibe el error `ROOM_NUMBER_ALREADY_EXISTS`. | [Pass] | Verificado por test automatizado (`RegisterRoomIntegrationTest`) |

---

## Defect Severity Reference

| Severity | Description | Examples |
| --- | --- | --- |
| **Critical (P1)** | Impide la operación total de una función básica, causa pérdida o corrupción de datos sin solución alternativa inmediata. | No se genera el UUID de la habitación, no se crea el estado inicial `Available` o se permite duplicar números. |
| **Major (P2)** | Falla mayor en una funcionalidad relevante con solución temporal disponible pero no ideal. | Tras registrar la habitación, no aparece en el inventario hasta recargar la página manualmente. |
| **Moderate (P3)** | Defecto que afecta un flujo secundario o una validación no crítica. | Mensaje de validación de tarifa no aclara el límite de decimales permitidos. |
| **Minor (P4)** | Inconsistencias estéticas menores o errores tipográficos en etiquetas que no impiden la comprensión. | Texto de ayuda o espaciado desalineado en el formulario de registro. |

