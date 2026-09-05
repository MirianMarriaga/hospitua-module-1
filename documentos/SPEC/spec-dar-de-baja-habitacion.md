# Feature Specification: Dar de Baja Habitación

**Created**: 2026-09-04

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Retirar una habitación del inventario activo (Priority: P1)

Como Administrador, quiero dar de baja una habitación existente en el inventario, para excluirla de futuras reservas cuando ya no está disponible para operar (por ejemplo, por remodelación permanente, cierre definitivo de un ala o decisión administrativa), sin perder el historial de reservas y consumos ya asociado a ella.

**Why this priority**: Es una operación esencial del ciclo de vida de la habitación dentro del Módulo 1 (Gestión de Habitaciones e Inventario). Sin ella, habitaciones que ya no deben operar seguirían apareciendo como reservables, generando reservas inválidas y afectando la integridad del inventario. Corresponde directamente al caso de uso "Dar de baja habitación" del diagrama oficial.

**Independent Test**: Puede probarse de forma independiente iniciando sesión como Administrador, seleccionando una habitación sin reservas activas ni huéspedes actuales, confirmando la baja, y verificando que la habitación deje de aparecer en los resultados de disponibilidad para nuevas reservas.

**Acceptance Scenarios**:

1. **Scenario**: Baja exitosa de una habitación sin actividad pendiente
   - **Given** una habitación existe en estado "Disponible" y no tiene reservas activas ni futuras confirmadas
   - **When** el Administrador selecciona la habitación y confirma la acción de dar de baja
   - **Then** el sistema cambia el estado de la habitación a "Inactiva", la excluye de los resultados de disponibilidad y conserva su historial

2. **Scenario**: Intento de baja sobre una habitación ocupada
   - **Given** una habitación se encuentra en estado "Ocupada" (huésped en sitio)
   - **When** el Administrador intenta darla de baja
   - **Then** el sistema, a través de la verificación de disponibilidad (`<<include>>` Verificar disponibilidad de habitación), rechaza la operación y muestra un mensaje indicando que la habitación tiene un huésped activo

3. **Scenario**: Intento de baja sobre una habitación con reserva confirmada a futuro
   - **Given** una habitación se encuentra en estado "Disponible" pero tiene asociada una reserva confirmada para una fecha próxima (una reserva es un dato independiente del estado físico de la habitación, no un estado en sí mismo)
   - **When** el Administrador intenta darla de baja
   - **Then** el sistema, a través de la verificación de disponibilidad (`<<include>>` Verificar disponibilidad de habitación), rechaza la operación y muestra un mensaje indicando que existe una reserva vigente asociada a la habitación

4. **Scenario**: Confirmación explícita antes de ejecutar la baja
   - **Given** el Administrador ha seleccionado una habitación elegible para ser dada de baja
   - **When** inicia la acción de dar de baja
   - **Then** el sistema solicita una confirmación explícita antes de aplicar el cambio, dado el impacto de la operación sobre el inventario

---

### Edge Cases

- ¿Qué sucede si el Administrador intenta dar de baja una habitación que ya está dada de baja?
- ¿Es posible reactivar una habitación previamente dada de baja, o eso corresponde a un caso de uso distinto?
- ¿Qué ocurre si se pierde la conexión o falla el proceso justo después de confirmar la baja? ¿La habitación queda en un estado intermedio inconsistente?
- ¿Debe quedar visible en algún listado (por ejemplo, "habitaciones inactivas") para consulta del Gerente, o desaparece por completo de las pantallas de inventario?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir únicamente al actor "Administrador" dar de baja habitaciones del inventario.
- **FR-002**: El sistema DEBE ejecutar obligatoriamente el caso de uso de soporte **"Verificar disponibilidad de habitación"** (`<<include>>`) antes de permitir la baja de la habitación.
- **FR-003**: El sistema DEBE impedir dar de baja una habitación que no se encuentre en estado "Disponible" (por ejemplo, "Ocupada", "En Limpieza", "Bloqueo Técnico" o "Inhabilitada por reparaciones"), independientemente de la causa que originó ese estado.
- **FR-004**: El sistema DEBE impedir dar de baja una habitación que tenga al menos una reserva vigente o futura confirmada, verificada como un dato independiente del estado físico de la habitación (una habitación puede figurar como "Disponible" y aun así tener reservas futuras que la bloqueen para esta operación).
- **FR-005**: El sistema DEBE solicitar una confirmación explícita del Administrador antes de ejecutar la baja.
- **FR-006**: El sistema DEBE cambiar el estado de la habitación al nuevo estado formal **"Inactiva"** tras la confirmación de la baja, sin eliminar físicamente el registro de la habitación. "Inactiva" es uno de los seis estados vigentes del ciclo de vida de la habitación (Disponible, Ocupada, En Limpieza, Bloqueo Técnico, Inhabilitada por reparaciones, Inactiva). Solo se permite la transición hacia "Inactiva" desde el estado "Disponible".

- **FR-007**: El sistema DEBE excluir las habitaciones dadas de baja de los resultados de disponibilidad usados para nuevas reservas.
- **FR-008**: El sistema DEBE conservar el historial de reservas y consumos previamente asociado a una habitación después de darla de baja.
- **FR-009**: El sistema DEBE registrar el usuario responsable y la fecha/hora en que se ejecutó la baja, para efectos de trazabilidad.
- **FR-010**: El sistema DEBE permitir registrar un motivo de baja mediante la selección obligatoria de una categoría predefinida (por ejemplo: remodelación permanente, cierre definitivo de ala/piso, decisión administrativa, otro), pudiendo complementarse opcionalmente con un campo de texto libre para detalles adicionales.

### Key Entities *(include if feature involves data)*

- **Habitación**: Unidad habitacional del hotel. Para este caso de uso, la entidad transita al estado **"Inactiva"**, uno de los seis estados vigentes de su ciclo de vida (Disponible, Ocupada, En Limpieza, Bloqueo Técnico, Inhabilitada por reparaciones, Inactiva), además de los atributos ya definidos (ID único, número, piso/ala, tipo, capacidad máxima, tarifa base).
- **Administrador**: Actor responsable de gestionar el inventario de habitaciones, incluyendo su baja.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El Administrador puede completar la baja de una habitación elegible en menos de 1 minuto.
- **SC-002**: El 100% de los intentos de baja sobre habitaciones que no estén en estado "Disponible", o que tengan reservas vigentes, son rechazados por el sistema.
- **SC-003**: El 0% de las habitaciones dadas de baja aparece en los resultados de disponibilidad para nuevas reservas.
- **SC-004**: El 100% de las bajas ejecutadas quedan registradas con usuario responsable y fecha/hora, sin pérdida del historial previo de la habitación.
