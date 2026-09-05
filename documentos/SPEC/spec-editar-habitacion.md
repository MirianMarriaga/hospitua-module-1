# Feature Specification: Editar Habitación

**Caso de uso**: UC-ADM-002  
**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario de Aforo  
**Actor principal**: Administrador del sistema  
**Created**: 2026-09-04

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Edición exitosa de una habitación disponible (Priority: P1)

Como administrador del sistema quiero poder actualizar los atributos de una habitación existente para mantener la información del inventario alineada con la realidad operativa del hotel y corregir errores que puedan afectar la experiencia de trabajadores y huespedes.

**Why this priority**: La edición de atributos base impacta directamente la lógica
comercial (tarifas) y operativa (capacidad) del hotel. Debe ejecutarse solo cuando
la habitación no tenga ocupación activa para evitar inconsistencias en reservas en curso.

**Independent Test**: Puede probarse tomando una habitación en estado "Disponible",
editando uno o más atributos, y verificando que los cambios se persisten correctamente
en el inventario.

**Acceptance Scenarios**:

1. **Scenario**: Edición de tarifa base en habitación disponible
   - **Given** existe una habitación con estado "Disponible"
   - **When** el administrador modifica su tarifa base y confirma los cambios
   - **Then** el sistema persiste la nueva tarifa base y confirma la actualización exitosa

2. **Scenario**: Edición de tipo de habitación en habitación disponible
   - **Given** existe una habitación con estado "Disponible" y tipo "Sencilla"
   - **When** el administrador cambia el tipo a "Doble" y confirma
   - **Then** el sistema actualiza el tipo y mantiene todos los demás atributos sin cambios

3. **Scenario**: Edición de capacidad máxima en habitación disponible
   - **Given** existe una habitación con estado "Disponible"
   - **When** el administrador actualiza la capacidad máxima a un valor válido y confirma
   - **Then** el sistema persiste la nueva capacidad y la habitación permanece en estado "Disponible"

---

### User Story 2 - Intento de edición sobre habitación no disponible (Priority: P1)

Como administrador del sistema quiero ser informado cuando una habitación no puede editarse por tener compromisos activos para evitar modificaciones que generen inconsistencias en la operación del hotel.

**Why this priority**: Editar una habitación ocupada o reservada podría generar
inconsistencias críticas en reservas activas y en la liquidación financiera.
Esta restricción es tan prioritaria como la edición misma.

**Independent Test**: Puede probarse intentando editar habitaciones en cada estado
no-disponible y verificando que el sistema bloquea la operación en todos los casos.

**Acceptance Scenarios**:

1. **Scenario**: Intento de edición sobre habitación reservada
   - **Given** existe una habitación con estado "Reservada"
   - **When** el administrador intenta editar sus atributos
   - **Then** el sistema rechaza la operación e informa que la habitación no está disponible para edición, indicando su estado actual

2. **Scenario**: Intento de edición sobre habitación ocupada
   - **Given** existe una habitación con estado "Ocupada"
   - **When** el administrador intenta editar sus atributos
   - **Then** el sistema rechaza la operación e informa que la habitación no está disponible para edición

3. **Scenario**: Intento de edición sobre habitación en mantenimiento
   - **Given** existe una habitación con estado "Mantenimiento"
   - **When** el administrador intenta editar sus atributos
   - **Then** el sistema rechaza la operación e informa que la habitación no está disponible para edición

---

### User Story 3 - Edición con datos inválidos (Priority: P2)

Como administrador del sistema quiero recibir retroalimentación clara cuando ingreso valores inválidos durante la edición para corregirlos sin alterar accidentalmente los datos originales de la habitación.

**Why this priority**: La validación de datos protege la integridad del inventario.
Es secundaria respecto a la edición exitosa pero necesaria antes de la entrega.

**Independent Test**: Puede probarse enviando actualizaciones con valores fuera de rango
y verificando que los datos originales de la habitación permanecen sin cambios.

**Acceptance Scenarios**:

1. **Scenario**: Intento de cambio de tipo a valor no permitido
   - **Given** existe una habitación con estado "Disponible"
   - **When** el administrador intenta asignar un tipo de habitación que no existe en el catálogo
   - **Then** el sistema rechaza el cambio e indica los tipos válidos permitidos

2. **Scenario**: Intento de actualizar tarifa base a valor inválido
   - **Given** existe una habitación con estado "Disponible"
   - **When** el administrador ingresa una tarifa base igual a cero o negativa
   - **Then** el sistema rechaza la actualización y la tarifa base original no se modifica

3. **Scenario**: Intento de actualizar capacidad máxima a valor inválido
   - **Given** existe una habitación con estado "Disponible"
   - **When** el administrador ingresa una capacidad máxima menor o igual a cero
   - **Then** el sistema rechaza la actualización y la capacidad original no se modifica

---

### Edge Cases

- ¿Puede el administrador editar el número de habitación? ¿O este campo es inmutable tras el registro?
- ¿Qué ocurre si el administrador intenta editar una habitación que no existe en el sistema?
- ¿Se deben registrar los cambios con historial de auditoría (valor anterior / valor nuevo / usuario / timestamp)?
- ¿Qué ocurre si el administrador intenta modificar la tarifa base de una habitación con una estancia activa (Ocupada)?
- ¿Qué ocurre si dos administradores intentan editar la misma habitación simultáneamente?
- ¿Puede editarse el piso/ala de una habitación? ¿Tiene restricciones adicionales?

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador editar los atributos de una habitación existente: piso/ala, tipo, capacidad máxima y tarifa base (en los casos donde sea válido dicha modificación).
- **FR-002**: El sistema DEBE verificar la disponibilidad de la habitación antes de permitir cualquier edición (`<<include>>` Verificar disponibilidad de habitación) para evitar ediciones sobre instancias activas.
- **FR-003**: El sistema DEBE rechazar la edición si la habitación se encuentra en estado:Ocupada, en Limpieza ,inhabilitada por reparaciones o en bloqueo Técnico.
- **FR-004**: El sistema DEBE informar al administrador el estado actual de la habitación cuando la edición sea rechazada por disponibilidad.
- **FR-005**: El sistema DEBE validar que los nuevos valores cumplan las mismas reglas de negocio que el registro inicial (tipo válido, capacidad > 0, tarifa > 0).
- **FR-006**: El sistema DEBE persistir únicamente los campos que el administrador modificó, sin alterar los demás atributos.
- **FR-007**: El sistema DEBE confirmar al administrador la actualización exitosa con los datos actualizados de la habitación.

### Key Entities

- **Habitación**: Unidad habitacional existente en el inventario. Los atributos editables son: piso/ala, tipo (Sencilla | Doble | Suite | Boutique), estado, capacidad máxima y tarifa base.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador puede completar la edición de una habitación disponible en menos de 2 minutos.
- **SC-002**: El 100% de los intentos de edición sobre habitaciones en estado no-disponible son rechazados antes de persistir.
- **SC-003**: El 100% de los intentos de edición con datos inválidos son rechazados sin modificar los datos originales de la habitación.
- **SC-004**: Los cambios exitosos se reflejan en el inventario de forma inmediata tras la confirmación.
- **SC-005**: El administrador recibe siempre retroalimentación clara (éxito o motivo de rechazo) tras cada intento de edición.
