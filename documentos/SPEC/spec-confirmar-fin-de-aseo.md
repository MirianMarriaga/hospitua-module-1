# Feature Specification: Confirmar Fin de Aseo de Habitación

**Caso de uso**: UC-LIM-002  
**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario de Aforo  
**Actor principal**: Personal de limpieza  
**Created**: 2026-09-04

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Confirmación exitosa de fin de aseo (Priority: P1)

Como personal de limpieza quiero confirmar que el aseo de una habitación ha concluido para reintegrarla al inventario disponible del hotel y habilitarla para nuevas reservas.

**Why this priority**: Es el cierre del ciclo de limpieza. Sin esta acción la habitación permanece bloqueada indefinidamente en estado "En Limpieza", reduciendo el inventario disponible y afectando la capacidad operativa del hotel.

**Independent Test**: Puede probarse tomando una habitación en estado "En Limpieza", confirmando el fin de aseo y verificando que su estado cambia a "Disponible" y aparece nuevamente en el inventario para reservas.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación exitosa de fin de aseo
   - **Given** existe una habitación con estado "En Limpieza"
   - **When** el personal de limpieza confirma que el aseo ha concluido
   - **Then** el sistema cambia el estado de la habitación a "Disponible" y confirma la operación

2. **Scenario**: Habitación confirmada como lista aparece en el inventario disponible
   - **Given** el aseo de una habitación ha sido confirmado como finalizado
   - **When** se consulta el inventario de habitaciones disponibles
   - **Then** la habitación aparece con estado "Disponible" y puede ser reservada

3. **Scenario**: El personal de limpieza puede identificar qué habitaciones están siendo aseadas
   - **Given** existen habitaciones con estado "En Limpieza"
   - **When** el personal de limpieza consulta su lista de habitacion  es en proceso
   - **Then** el sistema muestra únicamente las habitaciones en estado "En Limpieza" pendientes de confirmación

---

### User Story 2 - Intento de confirmar fin de aseo en habitación con estado no válido (Priority: P1)

Como personal de limpieza quiero ser informado cuando intento confirmar el fin de aseo de una habitación que no está en proceso de limpieza para evitar transiciones de estado incorrectas que afecten el inventario del hotel.

**Why this priority**: Confirmar el fin de aseo en una habitación que no está en estado "En Limpieza" generaría una transición de estado inválida que podría liberar habitaciones que no han sido acondicionadas, con impacto directo en la experiencia del huésped.

**Independent Test**: Puede probarse intentando confirmar el fin de aseo en habitaciones en cada estado distinto de "En Limpieza" y verificando que el sistema rechaza la operación en todos los casos.

**Acceptance Scenarios**:

1. **Scenario**: Intento de confirmar fin de aseo en habitación disponible
   - **Given** existe una habitación con estado "Disponible"
   - **When** el personal de limpieza intenta confirmar el fin de aseo
   - **Then** el sistema rechaza la operación e informa que la habitación no se encuentra en proceso de limpieza

2. **Scenario**: Intento de confirmar fin de aseo en habitación ocupada
   - **Given** existe una habitación con estado "Ocupada"
   - **When** el personal de limpieza intenta confirmar el fin de aseo
   - **Then** el sistema rechaza la operación e informa el estado actual de la habitación

3. **Scenario**: Intento de confirmar fin de aseo en habitación en mantenimiento
   - **Given** existe una habitación con estado "Mantenimiento" o "Bloqueo Técnico"
   - **When** el personal de limpieza intenta confirmar el fin de aseo
   - **Then** el sistema rechaza la operación e informa el estado actual de la habitación

---

### Edge Cases

- ¿Puede el personal de limpieza confirmar el fin de aseo de una habitación que otro miembro del personal marcó como "En Limpieza", o debe ser el mismo usuario?
- ¿El sistema debe registrar el tiempo total que duró el proceso de limpieza (desde el marcado inicial hasta la confirmación)?
- ¿Qué ocurre si el personal de limpieza confirma el fin de aseo por error? ¿Puede revertirse el estado de "Disponible" a "En Limpieza"?
- ¿Existe un tiempo máximo de limpieza tras el cual el sistema genera una alerta si la habitación no ha sido confirmada como lista?

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al personal de limpieza confirmar el fin de aseo de una habitación.
- **FR-002**: El sistema DEBE validar que la habitación se encuentre en estado "En Limpieza" antes de ejecutar la confirmación.
- **FR-003**: El sistema DEBE rechazar la operación si la habitación no está en estado "En Limpieza", informando el estado actual.
- **FR-004**: El sistema DEBE transicionar el estado de la habitación de "En limpieza" a "Disponible" al confirmar el fin de aseo (`<<include>>` Marcar habitación como disponible) en caso de completarse el proceso de forma normal.
- **FR-005**: La habitación con estado "Disponible" resultante DEBE aparecer de forma inmediata en el inventario disponible para reservas.
- **FR-006**: El sistema DEBE permitir al personal de limpieza consultar la lista de habitaciones en estado "En Limpieza" pendientes de confirmación.
- **FR-007**: El sistema DEBE confirmar al personal de limpieza la transición de estado exitosa.
- **FR-008**: El sistema DEBE registrar la hora de finalización del aseo y el usuario que realizó la confirmación. [NEEDS CLARIFICATION: ¿se requiere auditoría en esta fase?]

### Key Entities

- **Habitación**: La confirmación del fin de aseo transiciona su estado de "En Limpieza" a "Disponible", reintegrándola al inventario operativo.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las confirmaciones sobre habitaciones en estado distinto de "En Limpieza" son rechazadas sin modificar el estado de la habitación.
- **SC-002**: El 100% de las confirmaciones exitosas cambian el estado de la habitación a "Disponible" de forma inmediata.
- **SC-003**: Una habitación confirmada como lista aparece en el inventario disponible para reservas sin demora perceptible.
- **SC-004**: El personal de limpieza puede identificar en todo momento qué habitaciones están en proceso y cuáles han sido confirmadas, sin necesidad de consultar a otro actor del sistema.
- **SC-005**: El personal de limpieza puede completar la confirmación de fin de aseo en menos de 1 minuto.
