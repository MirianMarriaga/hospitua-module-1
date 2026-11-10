# Test Case: Consultar Información de Mantenimientos

**Created**: 2026-11-07  
**Spec/Plan Link**: [spec-consultar-informacion-mantenimientos.md](../SPEC/spec-consultar-informacion-mantenimientos.md) | [plan-consultar-informacion-mantenimientos.md](../PLAN/plan-consultar-informacion-mantenimientos.md)  
**Status**: Draft  

---

## Test Overview

| Field | Value |
| --- | --- |
| **Test Case ID** | TC-017-ConsultarInformacionMantenimientos |
| **Product Name & Version** | Hospitua Módulo 1 v0.1 |
| **Author** | Equipo Módulo 1 |
| **Current Owner** | Por asignar |
| **Execution Type** | Manual |
| **Priority** | P2 |

---

## Test Objectives and Document References

### Objectives
Verificar la capacidad del servicio de consulta de información de mantenimientos para determinar si una habitación se encuentra disponible frente a mantenimientos preventivos o correctivos en un rango de fechas determinado, devolviendo los cruces correspondientes sin revelar justificaciones internas, y validando los rangos de fechas contra las reglas del negocio.

### Document References
| Document Type | Document Name & Link |
| --- | --- |
| Functional Specification | [spec-consultar-informacion-mantenimientos.md](../SPEC/spec-consultar-informacion-mantenimientos.md) |
| Implementation Plan | [plan-consultar-informacion-mantenimientos.md](../PLAN/plan-consultar-informacion-mantenimientos.md) |

---

## Prerequisites and Test Environment

### Assumptions and Limitations
- Requiere el sistema Hospitua Módulo 1 con sus servicios activos y la base de datos PostgreSQL inicializada con la semilla de habitaciones.
- La consulta es un servicio REST de solo lectura (`GET /api/rooms/{roomId}/maintenances`) consumido por el Módulo de Reservas (Módulo 2) y por los servicios internos del módulo.

### Dependencies
- TC-016-ProgramarBloqueoTecnicoParaHabitacion debe haber registrado mantenimientos de prueba en la base de datos.

### Test Environment Requirements
- Entorno de pruebas con backend activo y herramienta para pruebas de API o interfaz administrativa/pantalla de consulta si existiese.
- Base de datos con habitaciones en diferentes estados: Disponible (101), Con bloqueo programado futuro (102), y Fuera de Servicio por Daño (103).

### Test Data and Accounts
- Habitación "101" (Disponible, sin mantenimientos).
- Habitación "102" (Con mantenimiento programado del 24-10-2026 al 28-10-2026).
- Habitación "103" (Inhabilitada por reparaciones / `DisabledForRepairs` en la fecha actual).
- Credenciales de pruebas para invocación de servicios o usuario administrativo del sistema.

---

## Test Execution Flow

### Section 1: Flujo Principal - Consulta de Disponibilidad sin Cruces

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Consultar mantenimientos para habitación disponible sin eventos | Enviar solicitud de consulta de mantenimientos para la habitación 101 | Habitación 101, Rango: 22-10-2026 al 26-10-2026 | El sistema retorna respuesta exitosa indicando que la habitación está disponible (`available: true`) y con lista de conflictos vacía (`conflicts: []`). | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| 2 | Consultar estadía donde un mantenimiento finaliza antes de la fecha de llegada | Enviar consulta para habitación con mantenimiento previo que culmina antes | Habitación 101 con mantenimiento previo del 18-10 al 21-10; Rango consultado: 22-10-2026 al 26-10-2026 | El sistema retorna disponible (`available: true`) sin cruces, ya que el mantenimiento termina antes del inicio de la estadía. | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| 3 | Consultar estadía donde un mantenimiento inicia el mismo día de la salida | Enviar consulta con mantenimiento programado a partir del día de check-out | Habitación 101 con mantenimiento del 26-10 al 27-10; Rango consultado: 22-10-2026 al 26-10-2026 | El sistema retorna disponible (`available: true`) sin cruces, dado que el día de salida no se evalúa como ocupado para la estadía. | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |

### Section 2: Detección de Cruces y Conflictos de Mantenimiento

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 4 | Consultar habitación con mantenimiento programado solapado | Enviar solicitud de consulta para habitación 102 con mantenimiento registrado | Habitación 102 (con bloqueo programado del 24-10 al 28-10); Rango consultado: 22-10-2026 al 26-10-2026 | El sistema responde que la habitación no está disponible (`available: false`) y lista el cruce con inicio "2026-10-24" y fin "2026-10-28", sin incluir textos de justificación ni datos privados. | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| 5 | Consultar habitación inhabilitada por daño en el día de hoy con llegada hoy | Enviar consulta para habitación 103 (`DisabledForRepairs`) con fecha de llegada hoy | Habitación 103; Rango consultado: Fecha de hoy a hoy + 2 días | El sistema responde que la habitación no está disponible (`available: false`) con un cruce cuyo inicio y fin corresponden a la fecha de hoy. | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| 6 | Consultar bloqueo técnico en curso con fecha estimada vencida | Enviar consulta sobre habitación con bloqueo `Applied` cuya fecha de fin ya expiró | Habitación con bloqueo vencido; Rango consultado: Fechas futuras (hoy + 2 a hoy + 5) | El sistema retorna disponible (`available: true`) para fechas futuras, ya que el bloqueo vencido no bloquea reservas posteriores a su fin estimado. | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |

### Section 3: Validaciones de Fechas y Parámetros

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 7 | Consultar con fecha de fin anterior a fecha de inicio | Enviar consulta con rango de fechas invertido | Fecha inicio: 26-10-2026, Fecha fin: 22-10-2026 | El sistema rechaza la consulta sin evaluar la tabla de mantenimientos y muestra el mensaje: "La fecha de fin no puede ser anterior a la fecha de inicio." | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| 8 | Consultar con rango que termina en el pasado | Enviar consulta con rango anterior a la fecha actual | Fecha inicio: 01-01-2026, Fecha fin: 05-01-2026 | El sistema rechaza la consulta con mensaje de error de validación: "El rango consultado termina antes de hoy." | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| 9 | Consultar habitación con identificador inexistente | Enviar consulta con identificador de habitación no registrado | Habitación con identificador no existente en el sistema | El sistema retorna error controlado indicando: "No se encontró la habitación." | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| 10 | Consultar con formato de fecha inválido o inexistente | Enviar consulta con fecha inválida (ej. 31 de febrero) | Fecha inicio: "2026-02-31", Fecha fin: "2026-03-05" | El sistema rechaza la consulta con mensaje: "La habitación y las fechas deben tener un formato válido (fechas AAAA-MM-DD)." | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |

---

## Edge Cases & Boundary Conditions

| Case # | Description | Preconditions | Inputs / Actions | Expected Results | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| EC-001 | Consulta con llegada y salida en el mismo día | Habitación sin cruces ese día | Fecha inicio: 22-10-2026, Fecha fin: 22-10-2026 | El sistema acepta la consulta y evalúa únicamente ese día, retornando disponible si no hay cruces. | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| EC-002 | Rango que inicia en el pasado pero termina en el futuro | Estadía en curso que se desea verificar | Fecha inicio: ayer, Fecha fin: hoy + 3 días | El sistema ajusta la evaluación a partir de la fecha de hoy hasta la fecha de fin, evaluando los mantenimientos de forma válida. | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| EC-003 | Mantenimiento finalizado (`Completed`) o caducado (`Expired`) | Habitación con registros históricos de mantenimiento | Consultar en las mismas fechas del mantenimiento finalizado | El sistema no genera solapamiento ni cruces, respondiendo `available: true`. | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |
| EC-004 | Consulta de disponibilidad para recálculo masivo (90 días) | Módulo 2 recalcula disponibilidad en canales | Rango de hoy a hoy + 90 días | El sistema procesa la consulta en menos de 200 ms sin aplicar restricciones de antelación máxima. | [Pass] | Verificado por test automatizado (`MaintenanceAvailabilityIntegrationTest`) |

---

## Defect Severity Reference

| Severity | Description | Examples |
| --- | --- | --- |
| **Critical (P1)** | Impide la operación total de una función básica, causa pérdida o corrupción de datos sin solución alternativa inmediata. | La consulta reporta erróneamente disponible una habitación con mantenimiento activo, provocando sobreventa. |
| **Major (P2)** | Falla mayor en una funcionalidad relevante con solución temporal disponible pero no ideal. | La consulta no ajusta los rangos que inician en el pasado y los rechaza incorrectamente. |
| **Moderate (P3)** | Defecto que afecta un flujo secundario o una validación no crítica. | Mensaje de error de fecha invertida no especifica la regla exacta incumplida. |
| **Minor (P4)** | Inconsistencias estéticas menores o errores tipográficos en etiquetas que no impiden la comprensión. | Formato de fecha devuelto con diferencias en separadores secundarios. |

