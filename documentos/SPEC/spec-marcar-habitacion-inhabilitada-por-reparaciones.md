# Feature Specification: Marcar Habitación Inhabilitada por Reparaciones

**Caso de uso**: UC-MNT-001  
**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario de Aforo  
**Actores principales**: Personal de mantenimiento, Personal de limpieza  
**Created**: 2026-09-04

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Inhabilitación exitosa de habitación por reparaciones (Priority: P1)

Como personal de mantenimiento o personal de limpieza quiero marcar una habitación como inhabilitada por reparaciones para excluirla del inventario disponible mientras se realizan trabajos correctivos y evitar que sea asignada a un huésped durante ese período.

**Why this priority**: Es el flujo principal de ambos actores operativos. El personal de limpieza puede identificar daños físicos durante el aseo de una habitación, y el personal de mantenimiento durante inspecciones o intervenciones correctivas. Sin esta acción, el sistema no puede reflejar que una habitación tiene daños físicos o requiere intervención, exponiendo al hotel a alojar huéspedes en condiciones inadecuadas.

**Independent Test**: Puede probarse tomando una habitación en estado "Disponible", marcándola como inhabilitada por reparaciones —ya sea desde el rol de personal de mantenimiento o desde el rol de personal de limpieza— y verificando que su estado cambia a "Mantenimiento" y queda excluida del inventario disponible para reservas.

**Acceptance Scenarios**:

1. **Scenario**: Inhabilitación exitosa con motivo registrado (actor: Personal de mantenimiento)
   - **Given** existe una habitación con estado "Disponible" [NEEDS CLARIFICATION -> Desde qué estados se puede transicionar al estado "Mantenimiento"]
   - **When** el personal de mantenimiento registra el motivo de la reparación y confirma la inhabilitación
   - **Then** el sistema cambia el estado de la habitación a "Mantenimiento", asocia el motivo registrado y confirma la operación

2. **Scenario**: Inhabilitación exitosa con motivo registrado (actor: Personal de limpieza)
   - **Given** existe una habitación con estado "En limpieza"
   - **When** el personal de limpieza registra el motivo de la reparación y confirma la inhabilitación
   - **Then** el sistema cambia el estado de la habitación a "Mantenimiento", asocia el motivo registrado y confirma la operación

3. **Scenario**: Habitación inhabilitada no aparece disponible para reservas
   - **Given** una habitación tiene estado "Mantenimiento", independientemente del actor que realizó la inhabilitación
   - **When** se consulta el inventario de habitaciones disponibles para reserva
   - **Then** la habitación no aparece como disponible en ningún resultado

4. **Scenario**: Cada actor puede consultar el motivo de inhabilitación registrado
   - **Given** una habitación tiene estado "Mantenimiento"
   - **When** el personal de mantenimiento o el personal de limpieza consulta el detalle de la habitación
   - **Then** el sistema muestra el motivo de reparación registrado al momento de la inhabilitación

---

### User Story 2 - Intento de inhabilitación sobre habitación en estado no válido (Priority: P1)

Como personal de mantenimiento o personal de limpieza quiero ser informado cuando intento inhabilitar una habitación que no está en un estado válido para recibir intervención correctiva para evitar alterar el ciclo de vida de habitaciones con compromisos activos.

**Why this priority**: Inhabilitar una habitación reservada u ocupada generaría conflictos operativos directos con huéspedes y reservas activas. Esta restricción aplica por igual a ambos actores y es tan prioritaria como la inhabilitación misma.

**Independent Test**: Puede probarse (con cada uno de los dos actores) intentando inhabilitar habitaciones en cada estado no válido y verificando que el sistema rechaza la operación y preserva el estado original en todos los casos.

**Acceptance Scenarios**:

1. **Scenario**: Intento de inhabilitación sobre habitación reservada
   - **Given** existe una habitación con estado "Reservada"
   - **When** el personal de mantenimiento o el personal de limpieza intenta marcarla como inhabilitada por reparaciones
   - **Then** el sistema rechaza la operación e informa el estado actual de la habitación

2. **Scenario**: Intento de inhabilitación sobre habitación ocupada
   - **Given** existe una habitación con estado "Ocupada"
   - **When** el personal de mantenimiento o el personal de limpieza intenta marcarla como inhabilitada por reparaciones
   - **Then** el sistema rechaza la operación e informa el estado actual de la habitación

3. **Scenario**: Intento de inhabilitación sobre habitación ya en mantenimiento
   - **Given** existe una habitación con estado "Mantenimiento"
   - **When** el personal de mantenimiento o el personal de limpieza intenta marcarla nuevamente como inhabilitada
   - **Then** el sistema rechaza la operación e informa que la habitación ya se encuentra en mantenimiento

---

### User Story 3 - Consulta de habitaciones que requieren intervención (Priority: P2)

Como personal de mantenimiento o personal de limpieza quiero consultar las habitaciones que requieren o están bajo intervención correctiva para organizar y priorizar el trabajo sin depender de información verbal o externa al sistema.

**Why this priority**: La visibilidad del estado de las habitaciones bajo mantenimiento permite a ambos actores planificar su trabajo de forma autónoma y eficiente. El personal de limpieza puede usarla para evitar iniciar el aseo de una habitación que ya fue reportada con daños; el personal de mantenimiento, para priorizar y organizar las intervenciones pendientes. Es complementaria al flujo de inhabilitación.

**Independent Test**: Puede probarse (con cada uno de los dos actores) verificando que el sistema lista correctamente todas las habitaciones en estado "Mantenimiento" y que la lista se actualiza de forma inmediata tras cada inhabilitación, independientemente de qué actor la haya realizado.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de habitaciones en mantenimiento
   - **Given** existen habitaciones con estado "Mantenimiento"
   - **When** el personal de mantenimiento o el personal de limpieza consulta la lista de habitaciones bajo intervención
   - **Then** el sistema muestra únicamente las habitaciones en estado "Mantenimiento" con su motivo registrado

2. **Scenario**: La lista se actualiza tras una nueva inhabilitación
   - **Given** cualquiera de los dos actores tiene abierta la lista de habitaciones en mantenimiento
   - **When** se inhabilita una nueva habitación por reparaciones (por cualquiera de los dos actores)
   - **Then** la habitación recién inhabilitada aparece en la lista de forma inmediata

---

### Edge Cases

- ¿El campo de motivo de reparación es obligatorio o puede omitirse?
- ¿Existe un catálogo predefinido de tipos de reparación o el motivo es texto libre?
- ¿Debe registrarse una duración estimada de la reparación al momento de la inhabilitación?
- ¿El sistema debe notificar al administrador del sistema o al gerente cuando se inhabilita una habitación, y debe indicar qué actor realizó la acción?
- ¿Puede revertirse la inhabilitación sin completar la reparación, y tienen ambos actores autorización para hacerlo o esta acción es exclusiva de uno de ellos?
- ¿La inhabilitación realizada por el personal de limpieza requiere confirmación o validación posterior del personal de mantenimiento?

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al personal de mantenimiento y al personal de limpieza marcar una habitación como inhabilitada por reparaciones.
- **FR-002**: El sistema DEBE validar que la habitación se encuentre en estado "Disponible" antes de ejecutar la inhabilitación, independientemente del actor que la solicite.
- **FR-003**: El sistema DEBE rechazar la inhabilitación —para cualquiera de los dos actores— si la habitación se encuentra en estado: Reservada, Ocupada, En Limpieza, Mantenimiento o Bloqueo Técnico, informando el estado actual.
- **FR-004**: El sistema DEBE requerir el registro de un motivo de reparación al momento de la inhabilitación. [NEEDS CLARIFICATION: ¿texto libre o catálogo predefinido?]
- **FR-005**: El sistema DEBE cambiar el estado de la habitación a "Mantenimiento" al confirmar la inhabilitación, independientemente del actor que la ejecute.
- **FR-006**: El sistema DEBE asociar el motivo de reparación registrado a la habitación inhabilitada.
- **FR-007**: Una habitación con estado "Mantenimiento" NO DEBE aparecer como disponible para reservas ni para ninguna operación que requiera disponibilidad.
- **FR-008**: El sistema DEBE permitir al personal de mantenimiento y al personal de limpieza consultar la lista de habitaciones en estado "Mantenimiento" con sus motivos registrados.
- **FR-009**: El sistema DEBE confirmar al actor correspondiente la inhabilitación exitosa.
- **FR-010**: El sistema DEBE registrar la hora de inicio de la intervención y el usuario que realizó la inhabilitación, identificando el rol del actor (personal de mantenimiento o personal de limpieza). [NEEDS CLARIFICATION: ¿se requiere auditoría en esta fase?]

### Key Entities

- **Habitación**: La inhabilitación transiciona su estado a "Mantenimiento", excluyéndola del inventario disponible. Solo puede inhabilitarse desde el estado "Disponible" o "En limpieza". La transición puede ser iniciada por el personal de mantenimiento o por el personal de limpieza.
- **Reparación**: Registro del motivo que justifica la inhabilitación. Atributos mínimos: descripción del motivo, fecha y hora de inicio, rol del actor que realizó la inhabilitación. [NEEDS CLARIFICATION: ¿campos adicionales requeridos como duración estimada o tipo de reparación?]

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos de inhabilitación sobre habitaciones en estado no válido son rechazados sin modificar el estado de la habitación, independientemente del actor que lo intente.
- **SC-002**: El 100% de las inhabilitaciones exitosas cambian el estado de la habitación a "Mantenimiento" de forma inmediata, tanto si las realiza el personal de mantenimiento como el personal de limpieza.
- **SC-003**: Una habitación en estado "Mantenimiento" no aparece en ninguna consulta de disponibilidad para reservas.
- **SC-004**: Tanto el personal de mantenimiento como el personal de limpieza pueden consultar en todo momento las habitaciones bajo intervención y sus motivos registrados, sin depender de otro actor del sistema.
- **SC-005**: Cualquiera de los dos actores puede completar la inhabilitación de una habitación en menos de 2 minuto.
