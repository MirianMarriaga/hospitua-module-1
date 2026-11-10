# Test Case: Programar Bloqueo Técnico para Habitación

**Created**: 2026-11-07  
**Spec/Plan Link**: [spec-programar-bloqueo-tecnico-para-habitacion.md](../SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md) | [plan-programar-bloqueo-tecnico-para-habitacion.md](../PLAN/plan-programar-bloqueo-tecnico-para-habitacion.md)  
**Status**: Draft  

---

## Test Overview

| Field | Value |
| --- | --- |
| **Test Case ID** | TC-016-ProgramarBloqueoTecnicoParaHabitacion |
| **Product Name & Version** | Hospitua Módulo 1 v0.1 |
| **Author** | Equipo Módulo 1 |
| **Current Owner** | Por asignar |
| **Execution Type** | Manual |
| **Priority** | P1 |

---

## Test Objectives and Document References

### Objectives
Verificar la capacidad del personal de mantenimiento para programar bloqueos técnicos preventivos sobre habitaciones, validar los rangos de fechas y la justificación técnica, comprobar la inexistencia de conflictos con reservas y mantenimientos previos, aplicar el bloqueo de forma inmediata o autónoma según la fecha de inicio, y consultar el informe técnico correspondiente.

### Document References
| Document Type | Document Name & Link |
| --- | --- |
| Functional Specification | [spec-programar-bloqueo-tecnico-para-habitacion.md](../SPEC/spec-programar-bloqueo-tecnico-para-habitacion.md) |
| Implementation Plan | [plan-programar-bloqueo-tecnico-para-habitacion.md](../PLAN/plan-programar-bloqueo-tecnico-para-habitacion.md) |

---

## Prerequisites and Test Environment

### Assumptions and Limitations
- Requiere el sistema Hospitua Módulo 1 con sus servicios activos y la base de datos PostgreSQL inicializada con la semilla de habitaciones.
- La pantalla `/mantenimiento` y los diálogos modales (`ScheduleTechnicalBlockDialog`, `TechnicalBlockViewDialog`) están completamente implementados en React y conectados al backend REST.

### Dependencies
- TC-015-ConsultarPanelMantenimiento debe estar probado y operativo.
- El servicio de consulta de reservas de Módulo 2 debe responder adecuadamente o tener mocks disponibles para las pruebas de conflicto.

### Test Environment Requirements
- Navegador web moderno con acceso a la aplicación Hospitua.
- Base de datos con habitaciones en estado Disponible (101, 102), Ocupada (103 con huésped activo) y Fuera de Servicio/Inactiva (104).

### Test Data and Accounts
- Habitación "101" (Disponible, categoría Sencilla).
- Habitación "102" (Disponible, categoría Sencilla).
- Habitación "104" (Inactiva / Dada de baja).
- Cuenta de usuario del área de Mantenimiento (ver credenciales de pruebas del equipo).

---

## Test Execution Flow

### Section 1: Flujo Principal - Programación con Inicio Hoy y Consulta de Informe

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Iniciar sesión como personal de mantenimiento y acceder al panel | Pantalla de inicio de sesión > Acceder al Panel de Mantenimiento `/mantenimiento` | Usuario de mantenimiento | Se visualiza el panel de mantenimiento con el listado de habitaciones operativas y sus estados. | [ ] | |
| 2 | Abrir diálogo de programación para habitación disponible | Panel de Mantenimiento > Fila de la habitación 101 > Clic en botón "Programar mantenimiento" | Habitación 101 | Se abre la ventana modal "Programar bloqueo técnico" mostrando el número de habitación 101 y los campos: justificación técnica, fecha de inicio y fecha estimada de fin. | [ ] | |
| 3 | Diligenciar datos con inicio hoy y confirmar programación | Modal "Programar bloqueo técnico" > Ingresar justificación y fechas > Clic en "Confirmar programación" | Justificación: "Cambio preventivo de unidad de aire acondicionado", Fecha inicio: fecha actual de hoy (DD-MM-YYYY), Fecha fin estimada: fecha de mañana (DD-MM-YYYY) | El sistema valida que no existan conflictos de reservas ni de mantenimientos, registra el informe de bloqueo en estado `Applied`, transiciona la habitación 101 a "Bloqueo Técnico" (`TechnicalBlock`) y muestra mensaje de confirmación de programación exitosa. | [ ] | |
| 4 | Verificar actualización de estado en el panel de mantenimiento | Panel de Mantenimiento `/mantenimiento` | Habitación 101 | La habitación 101 figura con estado "Bloqueo Técnico" con badge rojo/ámbar y se habilitan los botones "Ver informe" e "Iniciar reparaciones". | [ ] | |
| 5 | Consultar el informe de bloqueo técnico recién creado | Panel de Mantenimiento > Fila habitación 101 > Clic en botón "Ver informe" | Habitación 101 | Se despliega el diálogo "Informe de Mantenimiento" mostrando la justificación "Cambio preventivo de unidad de aire acondicionado", el nombre del responsable, el rango de fechas programado y la marca de tiempo de registro. | [ ] | |

### Section 2: Programación con Inicio Futuro y Aplicación Autónoma

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 6 | Abrir programación para fecha futura | Panel de Mantenimiento > Fila de la habitación 102 > Clic en "Programar mantenimiento" | Habitación 102 | Se abre el modal "Programar bloqueo técnico" para la habitación 102. | [ ] | |
| 7 | Registrar programación futura | Modal "Programar bloqueo técnico" > Ingresar justificación y fechas futuras > Clic en "Confirmar programación" | Justificación: "Pintura general y cambio de grifería", Fecha inicio: hoy + 5 días, Fecha fin estimada: hoy + 7 días | El sistema valida fechas y ausencia de conflictos, registra el informe en estado `Scheduled`, mantiene la habitación 102 en estado "Disponible", y muestra en el panel el rango programado (ej. "Mantenimiento: DD/MM - DD/MM"). | [ ] | |
| 8 | Verificar aviso y no alteración inmediata de estado | Panel de Mantenimiento `/mantenimiento` | Habitación 102 | La habitación permanece en estado "Disponible", no se crea registro de cambio de estado en el historial, y queda lista para ser bloqueada automáticamente al llegar la fecha de inicio. | [ ] | |

### Section 3: Validaciones, Rechazos y Manejo de Conflictos

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 9 | Intentar programar con justificación vacía | Modal "Programar bloqueo técnico" > Dejar justificación vacía > Intentar confirmar | Justificación: "" (vacía), Fechas válidas | El sistema bloquea el envío y muestra el mensaje de validación: "Escribe el motivo del mantenimiento (máximo 500 caracteres)." | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockDialog.test.tsx`) |
| 10 | Intentar programar con fecha de fin anterior a la de inicio | Modal "Programar bloqueo técnico" > Fechas invertidas > Clic en confirmar | Fecha inicio: hoy + 5 días, Fecha fin: hoy + 2 días | El sistema bloquea la confirmación y muestra el mensaje: "La fecha de fin no puede ser anterior a la fecha de inicio." | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockDialog.test.tsx` y `ScheduleTechnicalBlockIntegrationTest`) |
| 11 | Intentar programar con fecha de inicio en el pasado | Modal "Programar bloqueo técnico" > Fecha inicio pasada > Clic en confirmar | Fecha inicio: ayer, Fecha fin: hoy + 2 días | El sistema bloquea la operación y muestra el mensaje de regla temporal indicando que la fecha de inicio no puede ser anterior a hoy. | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockDialog.test.tsx` y `ScheduleTechnicalBlockIntegrationTest`) |
| 12 | Intentar programar con conflicto de reserva activa en Módulo 2 | Modal "Programar bloqueo técnico" > Habitación con reserva confirmada en las fechas elegidas > Clic en confirmar | Habitación y fechas con reserva existente (ej. RES-10502) | El sistema rechaza la programación y presenta el mensaje: "La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas." junto con el detalle de la reserva en conflicto. | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockIntegrationTest`) |
| 13 | Intentar programar con conflicto de mantenimiento existente | Modal "Programar bloqueo técnico" > Rango solapado con el mantenimiento de la habitación 102 | Rango solapado con hoy + 5 días a hoy + 7 días | El sistema rechaza la operación y muestra el mensaje: "Ya hay un mantenimiento programado o en curso del [inicio] al [fin]. Elige otras fechas." | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockIntegrationTest`) |
| 14 | Intentar programar mantenimiento en habitación dada de baja | Intentar programar sobre habitación 104 (Inactiva) | Habitación 104 (`Inactive`) | El sistema rechaza la solicitud indicando: "La habitación está dada de baja; no se puede programar mantenimiento." | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockIntegrationTest`) |

---

## Edge Cases & Boundary Conditions

| Case # | Description | Preconditions | Inputs / Actions | Expected Results | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| EC-001 | Rango de un solo día (inicio igual a fin) | Habitación disponible sin cruces | Fecha inicio: hoy + 3 días, Fecha fin: hoy + 3 días | El sistema acepta la programación correctamente (duración mínima permitida de un día). | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockIntegrationTest`) |
| EC-002 | Programación que excede el límite máximo de duración (más de 90 días) | Habitación disponible | Fecha inicio: hoy + 1 día, Fecha fin: hoy + 95 días | El sistema rechaza la operación indicando que el rango excede la duración máxima permitida. | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockIntegrationTest`) |
| EC-003 | Programación con antelación superior a 365 días | Habitación disponible | Fecha inicio: hoy + 400 días | El sistema rechaza la operación indicando que la fecha supera la antelación máxima permitida. | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockIntegrationTest`) |
| EC-004 | Caída o indisponibilidad de Módulo 2 al consultar reservas | Módulo 2 no responde o genera timeout | Intentar programar mantenimiento | El sistema no registra la programación, mantiene la habitación intacta y muestra: "Servicio de reservas temporalmente no disponible. No se programó el mantenimiento; inténtalo de nuevo." | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockIntegrationTest`) |
| EC-005 | Peticiones simultáneas con solapamiento sobre la misma habitación | Dos sesiones intentando programar en el mismo rango | Confirmación concurrente | Solo la primera solicitud se procesa; la segunda recibe conflicto de mantenimiento. | [Pass] | Verificado por test automatizado (`ScheduleTechnicalBlockIntegrationTest`) |

---

## Defect Severity Reference

| Severity | Description | Examples |
| --- | --- | --- |
| **Critical (P1)** | Impide la operación total de una función básica, causa pérdida o corrupción de datos sin solución alternativa inmediata. | No se bloquea la habitación, se permite solapar mantenimientos con reservas activas. |
| **Major (P2)** | Falla mayor en una funcionalidad relevante con solución temporal disponible pero no ideal. | El informe técnico no se despliega en modal aunque el registro en base de datos exista. |
| **Moderate (P3)** | Defecto que afecta un flujo secundario o una validación no crítica. | Mensaje de validación de fechas desalineado o formato visual de fecha confuso. |
| **Minor (P4)** | Inconsistencias estéticas menores o errores tipográficos en etiquetas que no impiden la comprensión. | Error tipográfico en el encabezado del diálogo de informe técnico. |

