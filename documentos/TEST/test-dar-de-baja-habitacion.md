# Test Case: Dar de Baja Habitación

**Created**: 2026-11-07  
**Spec/Plan Link**: [spec-dar-de-baja-habitacion.md](../SPEC/spec-dar-de-baja-habitacion.md) | [plan-dar-de-baja-habitacion.md](../PLAN/plan-dar-de-baja-habitacion.md)  
**Status**: Draft  

---

## Test Overview

| Field | Value |
| --- | --- |
| **Test Case ID** | TC-020-DarDeBajaHabitacion |
| **Product Name & Version** | Hospitua Módulo 1 v0.1 |
| **Author** | Equipo Módulo 1 |
| **Current Owner** | Por asignar |
| **Execution Type** | Manual |
| **Priority** | P1 |

---

## Test Objectives and Document References

### Objectives
Verificar la capacidad del administrador para deshabilitar operativamente una habitación del inventario pasándola al estado "Inactiva" (`Inactive`) mediante un flujo guiado de cuatro pasos, asegurando que la habitación esté en estado "Disponible", validando la justificación obligatoria, verificando con Módulo 2 la ausencia de reservas activas o futuras en los próximos 365 días, registrando el cambio en el historial de estados con su motivo y mostrando los resultados y advertencias correspondientes.

### Document References
| Document Type | Document Name & Link |
| --- | --- |
| Functional Specification | [spec-dar-de-baja-habitacion.md](../SPEC/spec-dar-de-baja-habitacion.md) |
| Implementation Plan | [plan-dar-de-baja-habitacion.md](../PLAN/plan-dar-de-baja-habitacion.md) |

---

## Prerequisites and Test Environment

### Assumptions and Limitations
- Requiere el sistema Hospitua Módulo 1 con sus servicios activos y la base de datos PostgreSQL inicializada con la semilla de habitaciones.
- La pantalla `/administracion/habitaciones/:roomId/baja` está implementada en React con su asistente de 4 pasos (Motivo, Confirmación, Verificación, Resultado) y conectada a `POST /api/rooms/{roomId}/decommission`.

### Dependencies
- TC-001-ConsultarReservas debe estar operativo para la comprobación de reservas vigentes.
- TC-021-ConsultarInventarioHabitaciones debe estar disponible para comprobar la exclusión de la habitación dada de baja del inventario activo.

### Test Environment Requirements
- Navegador web moderno con acceso a la aplicación Hospitua.
- Base de datos con habitaciones en diferentes estados: Disponible (101), Ocupada (103), Inactiva previa (104).

### Test Data and Accounts
- Habitación "101" (Disponible, sin reservas futuras).
- Habitación "102" (Disponible, con reservas simuladas en los próximos 365 días).
- Habitación "103" (Ocupada).
- Cuenta con rol de Administrador (ver credenciales de pruebas del equipo).

---

## Test Execution Flow

### Section 1: Flujo Principal - Baja Exitosa en 4 Pasos

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Iniciar sesión como Administrador y acceder al inventario | Menú principal > Administración > Inventario de Habitaciones `/administracion/inventario` | Usuario Administrador | Se visualiza el inventario con el listado de habitaciones operativas. | [ ] | |
| 2 | Seleccionar acción de baja sobre habitación disponible | Fila de la habitación 101 > Clic en botón "Dar de baja" | Habitación 101 (`Available`) | Se despliega la pantalla "Dar de baja habitación" con el stepper en el Paso 1: "1. Motivo de la baja · Habitación 101", la ficha técnica de la habitación y el formulario de motivo. | [ ] | |
| 3 | Seleccionar motivo predefinido y avanzar a confirmación | Paso 1 > Seleccionar motivo en el desplegable > Clic en botón "Continuar" | Motivo: "Mantenimiento prolongado", Detalle opcional: "Renovación integral de pisos y pintura" | Se avanza al Paso 2: "2. ¿Confirmas la baja de la habitación 101?", mostrando el resumen del motivo y la advertencia: "La habitación pasará a estado Inactiva y dejará de aparecer entre las habitaciones disponibles. Antes de aplicar la baja se comprobará que no tenga reservas vigentes en los próximos 365 días." | [ ] | |
| 4 | Confirmar la baja e iniciar comprobación | Paso 2 > Clic en botón "Confirmar baja" | - | Se visualiza el Paso 3: Verificación con spinner activo y los textos: "Comprobando reservas de la habitación 101… No cierres esta ventana." mientras el sistema consulta a Módulo 2. | [ ] | |
| 5 | Visualizar resultado exitoso | Paso 4: Resultado | Habitación 101 | Se presenta el encabezado "Habitación 101 dada de baja", el texto "Ya no aparece entre las habitaciones disponibles.", la ficha con estado "Inactiva", el detalle del motivo y el botón "Volver al inventario". En backend se cierra el periodo previo y se abre el nuevo en `RoomStateHistory` con `Status = Inactive`. | [ ] | |
| 6 | Volver al inventario y comprobar estado inactivo | Paso 4 > Clic en botón "Volver al inventario" | - | Se redirige a `/administracion/inventario`. La habitación 101 ya no figura en el filtro de disponibles y aparece como Inactiva al consultar todas o inactivas. | [ ] | |

### Section 2: Restricción por Estado No Disponible

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 7 | Intentar dar de baja una habitación ocupada | Inventario > Seleccionar dar de baja en habitación 103 (o navegar por URL a su ruta de baja) | Habitación 103 (`Occupied`) | La pantalla bloquea el inicio del flujo y muestra: "No se puede dar de baja la habitación 103. La habitación está en estado Ocupada y tiene un huésped alojado. Solo se pueden dar de baja habitaciones disponibles." con botón "Volver al inventario". | [Pass] | Verificado por test automatizado (`DecommissionRoomPage.test.tsx`) |
| 8 | Intentar dar de baja una habitación en limpieza o bloqueo | Intentar baja sobre habitación en `InCleaning` o `TechnicalBlock` | Habitación no disponible | El sistema bloquea el flujo mostrando la alerta de restricción de estado indicando que solo se pueden dar de baja habitaciones disponibles. | [Pass] | Verificado por test automatizado (`DecommissionRoomPage.test.tsx` y `DecommissionRoomIntegrationTest`) |

### Section 3: Conflictos con Reservas y Caídas de Servicios Externos

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 9 | Intentar dar de baja habitación con reservas vigentes en Módulo 2 | Completar Pasos 1 y 2 para habitación 102 con reservas activas > Confirmar baja | Habitación 102 con reserva RES-10502 en los próximos 365 días | En el Paso 4 el sistema muestra tarjeta de error: "No se puede dar de baja la habitación 102. Tiene 1 reserva(s) vigente(s) en los próximos 365 días." con la tabla detallada de reservas (código, fechas de llegada/salida y estado) y el botón "Volver al inventario". La habitación permanece en estado Disponible. | [Pass] | Verificado por test automatizado (`DecommissionRoomIntegrationTest`) |
| 10 | Manejo de caída o timeout de Módulo 2 durante la comprobación | Forzar desconexión de red o caída de Módulo 2 > Confirmar baja en Paso 2 | Falla de conexión a Módulo 2 | En el Paso 4 el sistema muestra: "No se pudo comprobar si la habitación tiene reservas. Servicio de reservas temporalmente no disponible. No se aplicó la baja; inténtalo de nuevo." ofreciendo los botones "Reintentar comprobación" y "Volver al inventario". | [Pass] | Verificado por test automatizado (`DecommissionRoomIntegrationTest`) |
| 11 | Validación de motivo obligatorio "Otro" sin texto de detalle | Paso 1 > Seleccionar categoría "Otro" > Dejar detalle vacío > Clic en "Continuar" | Motivo: "Otro", Detalle: "" | El sistema bloquea el avance y muestra el mensaje de error: "Escribe el motivo de la baja." bajo el campo de texto. | [Pass] | Verificado por test automatizado (`DecommissionRoomPage.test.tsx`) |

---

## Edge Cases & Boundary Conditions

| Case # | Description | Preconditions | Inputs / Actions | Expected Results | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| EC-001 | Detalle de motivo de longitud máxima permitida (500 caracteres) | Paso 1 del flujo de baja | Ingresar texto de exactamente 500 caracteres | El sistema acepta el texto completo y lo almacena sin truncar. | [Pass] | Verificado por test automatizado (`DecommissionRoomIntegrationTest`) |
| EC-002 | Intento de ingresar detalle mayor a 500 caracteres | Paso 1 del flujo de baja | Ingresar texto de más de 500 caracteres | El campo restringe o valida con el mensaje: "El detalle admite máximo 500 caracteres." | [Pass] | Verificado por test automatizado (`DecommissionRoomPage.test.tsx`) |
| EC-003 | Habitación reservada en el mismo instante de confirmación | Habitación Disponible al abrir el asistente, pero recibe reserva antes de confirmar | Confirmar baja en Paso 2 | La consulta previa detecta la nueva reserva y rechaza la baja con conflicto de reservas. | [Pass] | Verificado por test automatizado (`DecommissionRoomIntegrationTest`) |
| EC-004 | Navegación hacia atrás entre pasos | Usuario en Paso 2 de confirmación | Clic en botón "Atrás" | El sistema regresa al Paso 1 conservando el motivo y detalle previamente seleccionados. | [Pass] | Verificado por test automatizado (`DecommissionRoomPage.test.tsx`) |

---

## Defect Severity Reference

| Severity | Description | Examples |
| --- | --- | --- |
| **Critical (P1)** | Impide la operación total de una función básica, causa pérdida o corrupción de datos sin solución alternativa inmediata. | Se da de baja una habitación con reservas vigentes o con huésped alojado. |
| **Major (P2)** | Falla mayor en una funcionalidad relevante con solución temporal disponible pero no ideal. | El spinner de verificación en el Paso 3 queda bloqueado indefinidamente ante caídas de red. |
| **Moderate (P3)** | Defecto que afecta un flujo secundario o una validación no crítica. | La tabla de reservas en conflicto no formatea adecuadamente las fechas de llegada y salida. |
| **Minor (P4)** | Inconsistencias estéticas menores o errores tipográficos en etiquetas que no impiden la comprensión. | Alineación del indicador de paso activo en la barra de progreso del asistente. |

