# Feature Specification: Confirmar Reparación Finalizada

**Created**: 2026-09-04

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Confirmar finalización de reparación (Priority: P1)

Como Personal de Mantenimiento, quiero confirmar en el sistema que he finalizado la reparación o mantenimiento de una habitación, para que el sistema actualice su estado automáticamente y vuelva a estar disponible para asignación o reservas.

**Why this priority**: Es la conclusión natural del flujo de mantenimiento. Sin esta confirmación, la habitación se quedaría atascada en un estado inoperativo, reduciendo el aforo disponible del hotel, impactando directamente en los ingresos (Módulo 3).

**Independent Test**: Puede probarse iniciando sesión como Personal de Mantenimiento, buscando una habitación que se encuentre en estado "Inhabilitada por reparaciones", aplicando la confirmación de finalización y verificando que el sistema actualice el estado automáticamente.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación exitosa de reparación
   - **Given** una habitación que se encuentra en estado "Inhabilitada por reparaciones"
   - **When** el Personal de Mantenimiento ejecuta la acción "Confirmar reparación finalizada"
   - **Then** el sistema ejecuta obligatoriamente el caso de uso (`<<include>>` Marcar habitación como disponible) para retornar la habitación al inventario activo.

2. **Scenario**: Intento de confirmar reparación en habitación no averiada
   - **Given** una habitación que se encuentra en un estado distinto a "Inhabilitada por reparaciones" (ej. "Ocupada" o "Disponible")
   - **When** el Personal de Mantenimiento intenta ejecutar la acción "Confirmar reparación finalizada"
   - **Then** el sistema rechaza la operación informando que la habitación no está registrada en mantenimiento.

---

### Edge Cases

- ¿Debe el Personal de Mantenimiento ingresar observaciones, notas de la reparación o insumos gastados antes de confirmar?
- ¿La habitación pasa directamente a "Disponible" o requiere pasar primero por "En Limpieza" tras una reparación invasiva? (Actualmente el diagrama indica que incluye directamente el paso a Disponible).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al rol "Personal de Mantenimiento" ejecutar la confirmación de reparación finalizada.
- **FR-002**: El sistema DEBE validar que el estado actual de la habitación sea "Inhabilitada por reparaciones" antes de permitir la confirmación.
- **FR-003**: El sistema DEBE ejecutar automáticamente el caso de uso **"Marcar habitación como disponible" (`<<include>>`)** una vez confirmada la reparación.
- **FR-004**: El sistema DEBE registrar la fecha, hora y el usuario (Personal de Mantenimiento) que confirmó la reparación.

### Key Entities *(include if feature involves data)*

- **Habitación**: Entidad sobre la cual recae la confirmación y cuyo estado será actualizado indirectamente.
- **Personal de Mantenimiento**: Actor responsable de ejecutar la acción.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las reparaciones confirmadas desencadenan el flujo para marcar la habitación como disponible de forma automatizada.
- **SC-002**: El Personal de Mantenimiento puede registrar la finalización en menos de 3 clics.
