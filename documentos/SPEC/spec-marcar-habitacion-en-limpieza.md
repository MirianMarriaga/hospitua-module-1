# Feature Specification: Marcar Habitación en Limpieza

**Created**: 2026-09-04

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Marcar una habitación como en proceso de limpieza (Priority: P1)

Como Personal de limpieza, quiero marcar una habitación como "En Limpieza" para indicar que se está realizando el aseo, de modo que la habitación no pueda ser asignada a otro huésped ni aparezca como disponible mientras dure el proceso.

**Why this priority**: Es el mecanismo central que protege la calidad operativa del hotel: evita que recepción asigne o muestre como disponible una habitación que aún no ha sido aseada tras el check-out. Corresponde directamente al caso de uso "Marcar habitación en limpieza" del diagrama oficial, y es condición previa para que la habitación pueda eventualmente volver a estar "Disponible".

**Independent Test**: Puede probarse de forma independiente iniciando sesión como Personal de limpieza, seleccionando una habitación recién desocupada (estado "Ocupada") y marcándola como "En Limpieza", verificando luego que la habitación quede excluida de los resultados de disponibilidad para nuevas reservas.

**Acceptance Scenarios**:

1. **Scenario**: Marcado manual exitoso de una habitación recién desocupada
   - **Given** una habitación se encuentra en estado "Ocupada" tras la salida de un huésped
   - **When** el Personal de limpieza la marca como "En Limpieza"
   - **Then** el sistema ejecuta la verificación de disponibilidad (`<<include>>` Verificar disponibilidad de habitación), cambia el estado de la habitación a "En Limpieza" y la excluye de los resultados de disponibilidad

2. **Scenario**: Transición automática tras el registro de check-out (Módulo 2)
   - **Given** se completa exitosamente el registro de check-out de un huésped (caso de uso "Registrar check-out" del Módulo 2)
   - **When** el check-out se confirma en el sistema
   - **Then** el sistema ejecuta la verificación de disponibilidad (`<<include>>` Verificar disponibilidad de habitación) y marca automáticamente la habitación como "En Limpieza", sin requerir una acción manual adicional del Personal de limpieza

3. **Scenario**: Intento de marcar una habitación que ya está en limpieza
   - **Given** una habitación ya se encuentra en estado "En Limpieza"
   - **When** el Personal de limpieza intenta marcarla nuevamente como "En Limpieza"
   - **Then** el sistema, a través de la verificación de disponibilidad (`<<include>>` Verificar disponibilidad de habitación), detecta que ya se encuentra en ese estado, no aplica una nueva transición e informa al Personal de limpieza

---

### Edge Cases

- ¿Qué sucede si el Personal de limpieza intenta marcar como "En Limpieza" una habitación que está en estado "Inhabilitada por reparaciones" o "Bloqueo Técnico"?
- ¿Qué sucede si el Personal de limpieza intenta marcar como "En Limpieza" una habitación con una reserva confirmada de llegada programada para el mismo día (independientemente de su estado físico)?
- ¿Cómo se gestiona si dos integrantes del Personal de limpieza intentan marcar simultáneamente la misma habitación?
- ¿Qué sucede si el proceso de check-out (Módulo 2) se completa pero la transición automática a "En Limpieza" falla por un error del sistema? ¿Queda la habitación en un estado inconsistente?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al actor "Personal de limpieza" marcar manualmente una habitación como "En Limpieza".
- **FR-002**: El sistema DEBE marcar automáticamente una habitación como "En Limpieza" al completarse exitosamente el registro de check-out (Módulo 2), sin requerir acción manual del Personal de limpieza.
- **FR-003**: El sistema DEBE excluir las habitaciones en estado "En Limpieza" de los resultados de disponibilidad utilizados para nuevas reservas o asignaciones.
- **FR-004**: El sistema DEBE impedir marcar como "En Limpieza" una habitación que ya se encuentra en ese mismo estado.
- **FR-005**: El sistema DEBE ejecutar obligatoriamente el caso de uso de soporte **"Verificar disponibilidad de habitación"** (`<<include>>`) antes de aplicar cualquier transición, manual o automática, al estado "En Limpieza".
- **FR-006**: El sistema DEBE permitir la transición a "En Limpieza" únicamente desde los estados "Disponible" u "Ocupada", rechazando la transición si la habitación se encuentra en cualquier otro estado (por ejemplo, "Inhabilitada por reparaciones" o "Bloqueo Técnico").
- **FR-007**: El sistema DEBE registrar el usuario responsable (Personal de limpieza) y la fecha/hora en que se marcó manualmente la habitación como "En Limpieza".
- **FR-008**: El sistema DEBE registrar si la transición a "En Limpieza" fue manual (Personal de limpieza) o automática (originada por check-out del Módulo 2), para efectos de trazabilidad.
- **FR-009**: El sistema DEBE permitir que la habitación transite fuera del estado "En Limpieza" únicamente a través del caso de uso "Confirmar fin de aseo de habitación".

### Key Entities *(include if feature involves data)*

- **Habitación**: Unidad habitacional del hotel. Para este caso de uso, transita al estado "En Limpieza" (ya definido en el ciclo de vida original del proyecto), quedando temporalmente excluida de la disponibilidad hasta que se confirme el fin del aseo.
- **Personal de limpieza**: Actor responsable de ejecutar y confirmar el aseo de las habitaciones, incluyendo el marcado manual de inicio de limpieza.
- **Check-out**: Evento del Módulo 2 (Reservas) que puede disparar automáticamente la transición de la habitación a "En Limpieza" al completarse.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las habitaciones marcadas como "En Limpieza" quedan excluidas de los resultados de disponibilidad de forma inmediata.
- **SC-002**: El 100% de los check-out completados exitosamente generan automáticamente la transición de la habitación a "En Limpieza", sin intervención manual.
- **SC-003**: El Personal de limpieza puede marcar manualmente una habitación como "En Limpieza" en menos de 30 segundos.
- **SC-004**: Cero habitaciones quedan sin una transición de estado registrada tras completarse un check-out.
