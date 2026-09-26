# Especificación de Funcionalidad: Marcar Habitación en Bloqueo Técnico

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de mantenimiento
**Creado**: 2026-09-07

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Reservar una habitación para mantenimiento preventivo (Prioridad: P1)

Como Personal de mantenimiento, quiero marcar una habitación como "Bloqueo técnico" para reservarla para mantenimiento preventivo programado, de modo que quede excluida del inventario operativo durante la duración del mantenimiento sin que sea asignada a huéspedes.

**Por qué esta prioridad**: El bloqueo técnico es el mecanismo que permite realizar mantenimiento preventivo sin afectar la experiencia del huésped. Sin esta capacidad, las tareas de mantenimiento preventivo deberían coordinarse manualmente con recepción, con riesgo de errores.El bloqueo técnico es distinto de la ocupación por huésped y del convenio (concepto eliminado).

**Prueba Independiente**: Puede probarse de forma independiente iniciando sesión como Personal de mantenimiento, seleccionando una habitación con estado "Available", marcándola como "Bloqueo técnico" y verificando que la habitación transita a "TechnicalBlock" y queda excluida de la disponibilidad para reservas.

**Escenarios de Aceptación**:

1. **Escenario**: Marcado exitoso de una habitación disponible para bloqueo técnico
   - **Dado** una habitación se encuentra en estado "Available" 
   - **Cuando** el Personal de mantenimiento la marca como "Bloqueo técnico" indicando el motivo del mantenimiento preventivo
   - **Entonces** el sistema cambia el estado de la habitación de "Available" a "TechnicalBlock" y la excluye de los resultados de disponibilidad para reservas

2. **Escenario**: Habitación en bloqueo técnico no aparece en disponibilidad
   - **Dado** una habitación ha sido marcada como "TechnicalBlock"
   - **Cuando** se consulta el inventario de habitaciones disponibles para reservas
   - **Entonces** la habitación no aparece en los resultados de disponibilidad

3. **Escenario**: Personal de mantenimiento puede ver habitaciones en bloqueo técnico
   - **Dado** existen habitaciones con estado "TechnicalBlock"
   - **Cuando** el Personal de mantenimiento consulta la lista de habitaciones en bloqueo técnico
   - **Entonces** el sistema muestra las habitaciones marcadas como "TechnicalBlock" con el motivo y fecha del bloqueo

---

### Historia de Usuario 2 - Intento de bloquear una habitación en estado no válido (Prioridad: P1)

Como Personal de mantenimiento, quiero ser informado cuando intento marcar una habitación como "Bloqueo técnico" que no se encuentra en un estado que lo permita, para evitar transiciones inválidas en la máquina de estados.

**Por qué esta prioridad**: Marcar como "TechnicalBlock" una habitación que no está en "Available" generaría una transición de estado inválida que podría afectar habitaciones ocupadas, en limpieza o ya en mantenimiento.

**Prueba Independiente**: Puede probarse intentando marcar como "Bloqueo técnico" habitaciones en cada estado distinto de "Available" y verificando que el sistema rechaza la operación en todos los casos.

**Escenarios de Aceptación**:

4. **Escenario**: Intento de bloquear una habitación ocupada
   - **Dado** una habitación se encuentra en estado "Occupied"
   - **Cuando** el Personal de mantenimiento intenta marcarla como "Bloqueo técnico"
   - **Entonces** el sistema rechaza la operación e informa que la habitación no se encuentra en un estado que permita esta transición.

5. **Escenario**: Intento de bloquear una habitación en limpieza
   - **Dado** una habitación se encuentra en estado "PendingCleaning" o "InCleaning"
   - **Cuando** el Personal de mantenimiento intenta marcarla como "Bloqueo técnico"
   - **Entonces** el sistema rechaza la operación e informa el estado actual de la habitación

6. **Escenario**: Intento de bloquear una habitación ya en bloqueo técnico
   - **Dado** una habitación ya se encuentra en estado "TechnicalBlock"
   - **Cuando** el Personal de mantenimiento intenta marcarla nuevamente como "Bloqueo técnico"
   - **Entonces** el sistema detecta que ya se encuentra en ese estado y no aplica una nueva transición, informando al Personal de mantenimiento

---

### Casos Borde

- Habitación con reserva confirmada para el mismo día: el bloqueo técnico debe considerar la reserva existente y, de ser necesario, coordinar con recepción antes de proceder.
- Dos miembros de mantenimiento intentan bloquear la misma habitación simultáneamente: el primer intento exitoso cambia el estado a "TechnicalBlock" y el segundo es rechazado.
- Bloqueo técnico por tiempo indefinido: el sistema debe permitir registrar el motivo del bloqueo sin requerir obligatoriamente una fecha de finalización.
- Pérdida de conexión o fallo del proceso: la transacción se cancela y la habitación permanece en estado "Available".
- Habitación bloqueada técnicamente con daño adicional descubierto: el Personal de mantenimiento puede cambiar el estado de "TechnicalBlock" a "DisabledForRepairs" si se descubre un daño físico durante el mantenimiento preventivo.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al actor "Personal de mantenimiento" marcar manualmente una habitación como "TechnicalBlock".
- **FR-002**: El sistema DEBE validar como precondición que la habitación se encuentre en estado "Available" antes de permitir el bloqueo.
- **FR-003**: El sistema DEBE rechazar la operación si la habitación se encuentra en cualquier estado distinto de "Available", informando el estado actual.
- **FR-004**: El sistema DEBE impedir marcar como "TechnicalBlock" una habitación que ya se encuentra en ese mismo estado.
- **FR-005**: El sistema DEBE cambiar el estado de la habitación de "Available" a "TechnicalBlock" de forma inmediata tras la confirmación.
- **FR-006**: El sistema DEBE excluir las habitaciones en estado "TechnicalBlock" de los resultados de disponibilidad utilizados para nuevas reservas o asignaciones.
- **FR-007**: El sistema DEBE registrar el usuario responsable (Personal de mantenimiento) y la fecha/hora en que se marcó la habitación, para efectos de trazabilidad.
- **FR-008**: El sistema DEBE registrar la transición a "TechnicalBlock" como una acción manual para efectos de trazabilidad.
- **FR-009**: El sistema DEBE permitir que la habitación transite fuera del estado "TechnicalBlock" únicamente a través del caso de uso "Confirmar reparación finalizada".

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Para este caso de uso, transita del estado **"Available"** al estado **"TechnicalBlock"**, dos de los 7 estados vigentes de su ciclo de vida.
- **MaintenanceStaff**: Actor responsable de gestionar el mantenimiento preventivo de las habitaciones, incluyendo su bloqueo técnico.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las habitaciones marcadas como "TechnicalBlock" quedan excluidas de los resultados de disponibilidad de forma inmediata.
- **SC-002**: El Personal de mantenimiento puede marcar una habitación como "TechnicalBlock" en menos de 1 minuto.
- **SC-003**: El 100% de los intentos de bloquear una habitación que no esté en estado "Available" son rechazados por el sistema.
- **SC-004**: El 100% de los marcados exitosos quedan registrados con usuario responsable y fecha/hora.
