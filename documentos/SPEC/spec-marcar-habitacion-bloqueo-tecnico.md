# Feature Specification: Marcar Habitación en Bloqueo Técnico

**Created**: 2026-09-04

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Marcar habitación en bloqueo técnico (Priority: P1)

Como Personal de mantenimiento, quiero marcar una habitación con el estado "Bloqueo Técnico", para inhabilitarla temporalmente y evitar que sea reservada por huéspedes (por ejemplo, por requerimientos operativos internos).

**Why this priority**: Es necesario poder bloquear habitaciones temporalmente por motivos internos u operativos, asegurando que no se asignen a huéspedes cuando no están disponibles para venta.

**Independent Test**: Puede probarse iniciando sesión como personal de mantenimiento, seleccionando una habitación, aplicando la acción de bloqueo técnico y verificando que su estado cambie a "Bloqueo Técnico" y ya no aparezca como disponible para nuevas reservas.

**Acceptance Scenarios**:

1. **Scenario**: Bloqueo técnico exitoso de una habitación
   - **Given** una habitación que se encuentra en un estado elegible
   - **When** el personal de mantenimiento ejecuta la acción de "Marcar en bloqueo técnico"
   - **Then** el sistema ejecuta la verificación de disponibilidad (`<<include>>` Verificar disponibilidad de habitación), cambia el estado de la habitación a "Bloqueo Técnico" y la excluye de la disponibilidad comercial.

2. **Scenario**: Intento de aplicar bloqueo técnico a una habitación ocupada
   - **Given** una habitación que se encuentra en estado "Ocupada"
   - **When** el personal de mantenimiento intenta ejecutar la acción de "Marcar en bloqueo técnico"
   - **Then** el sistema, a través de la verificación de disponibilidad (`<<include>>` Verificar disponibilidad de habitación), detecta el conflicto y rechaza la operación, mostrando un mensaje de error.

---

### Edge Cases

- ¿Se debe registrar un motivo o justificación obligatoria para este bloqueo?
- ¿Qué ocurre si la habitación está en estado "En Limpieza" y se requiere pasar a "Bloqueo Técnico" inmediatamente?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir únicamente al "personal de mantenimiento" marcar una habitación en estado de "Bloqueo Técnico".
- **FR-002**: El sistema DEBE ejecutar obligatoriamente el caso de uso de soporte **"Verificar disponibilidad de habitación" (`<<include>>`)** antes de permitir el cambio de estado.
- **FR-003**: El sistema DEBE rechazar el bloqueo técnico si la habitación se encuentra "Ocupada".
- **FR-004**: El sistema DEBE actualizar el estado de la habitación a "Bloqueo Técnico" tras la confirmación exitosa.
- **FR-005**: El sistema DEBE registrar la fecha, hora y el usuario (Personal de Mantenimiento) que marco la habitación en estado de "Bloqueo Técnico.

### Key Entities *(include if feature involves data)*

- **Habitación**: Entidad que sufre la alteración de su estado a "Bloqueo Técnico".
- **personal de mantenimiento**: Actor que ejecuta la acción.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las habitaciones marcadas en bloqueo técnico quedan fuera de la disponibilidad de nuevas reservas.
- **SC-002**: El sistema previene el 100% de los intentos de bloqueo sobre habitaciones con huéspedes actuales.
