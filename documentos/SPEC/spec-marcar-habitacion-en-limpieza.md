# Feature Specification: Marcar Habitación en Limpieza

**Created**: 2026-09-04

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Marcar una habitación como en proceso de limpieza (Priority: P1)

Como Personal de limpieza, quiero marcar una habitación como "En Limpieza" para indicar que se está realizando el aseo, de modo que la habitación no pueda ser asignada a otro huésped ni aparezca como disponible mientras dure el proceso.

**Why this priority**: Es el mecanismo central que protege la calidad operativa del hotel: evita que recepción asigne o muestre como disponible una habitación que aún no ha sido aseada tras el check-out. Corresponde directamente al caso de uso "Marcar habitación en limpieza" del diagrama oficial, y es condición previa para que la habitación pueda eventualmente volver a estar "Disponible".

**Independent Test**: Puede probarse de forma independiente iniciando sesión como Personal de limpieza, seleccionando una habitación en estado "Pendiente de limpieza" y marcándola como "En Limpieza", verificando luego que la habitación quede excluida de los resultados de disponibilidad para nuevas reservas.

**Acceptance Scenarios**:

1. **Scenario**: Marcado manual exitoso de una habitación recién desocupada
   - **Given** una habitación se encuentra en estado "Pendiente de limpieza" tras la salida de un huésped
   - **When** el Personal de limpieza la marca como "En Limpieza"
   - **Then** el sistema cambia el estado de la habitación a "En Limpieza" y la excluye de los resultados de disponibilidad, verificando previamente como precondición directa que esté en "Pendiente de limpieza" según documentos/SPEC/referencias/maquina-estados-habitacion.md

2. **Scenario**: Intento de marcar una habitación que ya está en limpieza
   - **Given** una habitación ya se encuentra en estado "En Limpieza"
   - **When** el Personal de limpieza intenta marcarla nuevamente como "En Limpieza"
   - **Then** el sistema detecta que ya se encuentra en ese estado y no aplica una nueva transición, informando al Personal de limpieza

---

### Edge Cases

- ¿Qué sucede si el Personal de limpieza intenta marcar como "En Limpieza" una habitación que está en estado "Inhabilitada por reparaciones" o "Bloqueo Técnico"?
- ¿Qué sucede si el Personal de limpieza intenta marcar como "En Limpieza" una habitación con una reserva confirmada de llegada programada para el mismo día (independientemente de su estado físico)?
- ¿Cómo se gestiona si dos integrantes del Personal de limpieza intentan marcar simultáneamente la misma habitación?
- ¿Qué sucede si el proceso de check-out (Módulo 2) se completa pero la transición automática a "En Limpieza" falla por un error del sistema? ¿Queda la habitación en un estado inconsistente?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al actor "Personal de limpieza" marcar manualmente una habitación como "En Limpieza".
- **FR-002**: El sistema DEBE excluir las habitaciones en estado "En Limpieza" de los resultados de disponibilidad utilizados para nuevas reservas o asignaciones.
- **FR-003**: El sistema DEBE impedir marcar como "En Limpieza" una habitación que ya se encuentra en ese mismo estado.
- **FR-004**: El sistema DEBE validar como precondición directa que la habitación se encuentre en estado "Pendiente de limpieza" según documentos/SPEC/referencias/maquina-estados-habitacion.md antes de aplicar la transición al estado "En Limpieza".
- **FR-005**: El sistema DEBE permitir la transición a "En Limpieza" únicamente desde el estado "Pendiente de limpieza", rechazando la transición si la habitación se encuentra en cualquier otro estado.
- **FR-006**: El sistema DEBE registrar el usuario responsable (Personal de limpieza) y la fecha/hora en que se marcó la habitación como "En Limpieza".
- **FR-007**: El sistema DEBE registrar la transición a "En Limpieza" como una acción manual para efectos de trazabilidad.
- **FR-008**: El sistema DEBE permitir que la habitación transite fuera del estado "En Limpieza" únicamente a través del caso de uso "Confirmar fin de aseo de habitación".

### Key Entities *(include if feature involves data)*

- **Room**: Unidad habitacional del hotel. Para este caso de uso, transita al estado "En Limpieza" (ya definido en el ciclo de vida original del proyecto), quedando temporalmente excluida de la disponibilidad hasta que se confirme el fin del aseo.
- **CleaningStaff**: Actor responsable de ejecutar y confirmar el aseo de las habitaciones, incluyendo el marcado manual de inicio de limpieza.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las habitaciones marcadas como "En Limpieza" quedan excluidas de los resultados de disponibilidad de forma inmediata.
- **SC-002**: El Personal de limpieza puede marcar manualmente una habitación como "En Limpieza" en menos de 30 segundos.
