 # Especificación de Funcionalidad: Editar Habitación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Administrador
**Creado**: 2026-09-07

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Edición exitosa de una habitación disponible (Prioridad: P1)

Como Administrador, quiero poder actualizar los atributos de una habitación existente para mantener la información del inventario alineada con la realidad operativa del hotel y corregir errores que puedan afectar la experiencia de trabajadores y huéspedes.

**Por qué esta prioridad**: La edición de atributos base impacta directamente la lógica comercial (tarifas) y operativa (capacidad) del hotel. Debe ejecutarse solo cuando la habitación no tenga ocupación activa para evitar inconsistencias en reservas en curso.

**Prueba Independiente**: Puede probarse tomando una habitación en estado "Available", editando uno o más atributos, y verificando que los cambios se persisten correctamente en el inventario.

**Escenarios de Aceptación**:

1. **Escenario**: Edición de tarifa base en habitación disponible
   - **Dado** existe una habitación con estado "Available" 
   - **Cuando** el Administrador modifica su tarifa base y confirma los cambios
   - **Entonces** el sistema persiste la nueva tarifa base y confirma la actualización exitosa

2. **Escenario**: Edición de tipo de habitación en habitación disponible
   - **Dado** existe una habitación con estado "Available" y tipo "Sencilla"
   - **Cuando** el Administrador cambia el tipo a "Doble" y confirma
   - **Entonces** el sistema actualiza el tipo y mantiene todos los demás atributos sin cambios

3. **Escenario**: Edición de capacidad máxima en habitación disponible
   - **Dado** existe una habitación con estado "Available"
   - **Cuando** el Administrador actualiza la capacidad máxima a un valor válido y confirma
   - **Entonces** el sistema persiste la nueva capacidad y la habitación permanece en estado "Available"

---

### Historia de Usuario 2 - Intento de edición sobre habitación no disponible (Prioridad: P1)

Como Administrador, quiero ser informado cuando una habitación no puede editarse por estar comprometida, para evitar modificaciones que generen inconsistencias en la operación del hotel.

**Por qué esta prioridad**: Editar una habitación en cualquier estado que no sea "Available" podría generar inconsistencias críticas en reservas activas, en procesos de limpieza o en intervenciones de mantenimiento. Esta restricción es tan prioritaria como la edición misma.

**Prueba Independiente**: Puede probarse intentando editar habitaciones en cada estado distinto de "Available" y verificando que el sistema bloquea la operación en todos los casos.

**Escenarios de Aceptación**:

4. **Escenario**: Intento de edición sobre habitación ocupada
   - **Dado** existe una habitación con estado "Occupied"
   - **Cuando** el Administrador intenta editar sus atributos
   - **Entonces** el sistema rechaza la operación e informa que la habitación no está disponible para edición, indicando su estado actual

5. **Escenario**: Intento de edición sobre habitación en limpieza
   - **Dado** existe una habitación con estado "PendingCleaning" o "InCleaning"
   - **Cuando** el Administrador intenta editar sus atributos
   - **Entonces** el sistema rechaza la operación e informa que la habitación no está disponible para edición

6. **Escenario**: Intento de edición sobre habitación en mantenimiento o inhabilitada
   - **Dado** existe una habitación con estado "DisabledForRepairs", "TechnicalBlock" o "Inactive"
   - **Cuando** el Administrador intenta editar sus atributos
   - **Entonces** el sistema rechaza la operación e informa que la habitación no está disponible para edición

---

### Historia de Usuario 3 - Edición con datos inválidos (Prioridad: P2)

Como Administrador, quiero recibir retroalimentación clara cuando ingreso valores inválidos durante la edición para corregirlos sin alterar accidentalmente los datos originales de la habitación.

**Por qué esta prioridad**: La validación de datos protege la integridad del inventario. Es secundaria respecto a la edición exitosa pero necesaria antes de la entrega.

**Prueba Independiente**: Puede probarse enviando actualizaciones con valores fuera de rango y verificando que los datos originales de la habitación permanecen sin cambios.

**Escenarios de Aceptación**:

7. **Escenario**: Intento de cambio de tipo a valor no permitido
   - **Dado** existe una habitación con estado "Available"
   - **Cuando** el Administrador intenta asignar un tipo de habitación que no existe en el catálogo
   - **Entonces** el sistema rechaza el cambio e indica los tipos válidos permitidos

8. **Escenario**: Intento de actualizar tarifa base a valor inválido
   - **Dado** existe una habitación con estado "Available"
   - **Cuando** el Administrador ingresa una tarifa base igual a cero o negativa
   - **Entonces** el sistema rechaza la actualización y la tarifa base original no se modifica

9. **Escenario**: Intento de actualizar capacidad máxima a valor inválido
   - **Dado** existe una habitación con estado "Available"
   - **Cuando** el Administrador ingresa una capacidad máxima menor o igual a cero
   - **Entonces** el sistema rechaza la actualización y la capacidad original no se modifica

---

### Casos Borde

- ¿Puede el Administrador editar el número de habitación? ¿O este campo es inmutable tras el registro?
- ¿Qué ocurre si el Administrador intenta editar una habitación que no existe en el sistema?
- ¿Se deben registrar los cambios con historial de auditoría (valor anterior / valor nuevo / usuario / timestamp)?
- ¿Qué ocurre si dos Administradores intentan editar la misma habitación simultáneamente?
- ¿Puede editarse el piso/ala de una habitación? ¿Tiene restricciones adicionales?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al Administrador editar los atributos de una habitación existente: piso/ala, tipo, capacidad máxima y tarifa base.
- **FR-002**: El sistema DEBE verificar que la habitación se encuentre en estado "Available"  antes de permitir cualquier edición.
- **FR-003**: El sistema DEBE rechazar la edición si la habitación se encuentra en cualquier estado distinto de "Available" ("Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"), informando el estado actual.
- **FR-004**: El sistema DEBE informar al Administrador el estado actual de la habitación cuando la edición sea rechazada por disponibilidad.
- **FR-005**: El sistema DEBE validar que los nuevos valores cumplan las mismas reglas de negocio que el registro inicial (tipo válido, capacidad > 0, tarifa > 0).
- **FR-006**: El sistema DEBE persistir únicamente los campos que el Administrador modificó, sin alterar los demás atributos.
- **FR-007**: El sistema DEBE confirmar al Administrador la actualización exitosa con los datos actualizados de la habitación.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional existente en el inventario. Los atributos editables son: piso/ala, tipo (Sencilla | Doble | Suite | Boutique), capacidad máxima y tarifa base. Solo puede editarse cuando se encuentra en estado "Available" .
- **Administrator**: Actor responsable de gestionar el inventario de habitaciones, incluyendo su edición.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Administrador puede completar la edición de una habitación disponible en menos de 2 minutos.
- **SC-002**: El 100% de los intentos de edición sobre habitaciones en estado distinto de "Available" son rechazados antes de persistir.
- **SC-003**: El 100% de los intentos de edición con datos inválidos son rechazados sin modificar los datos originales de la habitación.
- **SC-004**: Los cambios exitosos se reflejan en el inventario de forma inmediata tras la confirmación.
- **SC-005**: El Administrador recibe siempre retroalimentación clara (éxito o motivo de rechazo) tras cada intento de edición.
