# Especificación de Funcionalidad: Marcar Habitación Inhabilitada por Reparaciones

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de mantenimiento
**Creado**: 2026-09-07

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Marcar una habitación con daño físico reportado (Prioridad: P1)

Como Personal de mantenimiento o Personal de limpieza, quiero marcar una habitación como "Inhabilitada por reparaciones" para indicar que presenta daño físico que requiere intervención, de modo que quede excluida del inventario operativo hasta que la reparación se confirme.

**Por qué esta prioridad**: Sin esta capacidad, una habitación con daño seguiría apareciendo como disponible o asignable, lo que podría resultar en la asignación a un huésped que se encuentre una habitación en mal estado. Esta acción protege la calidad de la experiencia del huésped y es el punto de entrada al flujo de mantenimiento correctivo.

**Prueba Independiente**: Puede probarse de forma independiente iniciando sesión como Personal de mantenimiento o Personal de limpieza, seleccionando una habitación con estado "Available", marcándola como "Inhabilitada por reparaciones" y verificando que la habitación transita a "DisabledForRepairs" y queda excluida de la disponibilidad.

**Escenarios de Aceptación**:

1. **Escenario**: Marcado exitoso de una habitación disponible con daño
   - **Dado** una habitación se encuentra en estado "Available" según documentos/SPEC/referencias/maquina-estados-habitacion.md
   - **Cuando** el Personal de mantenimiento o Personal de limpieza la marca como "Inhabilitada por reparaciones" indicando el daño reportado
   - **Entonces** el sistema cambia el estado de la habitación de "Available" a "DisabledForRepairs" y la excluye de los resultados de disponibilidad para reservas



2. **Escenario**: Habitación inhabilitada no aparece en disponibilidad
   - **Dado** una habitación ha sido marcada como "DisabledForRepairs"
   - **Cuando** se consulta el inventario de habitaciones disponibles para reservas
   - **Entonces** la habitación no aparece en los resultados de disponibilidad

---

### Historia de Usuario 2 - Intento de inhabilitar una habitación en estado no válido (Prioridad: P1)

Como Personal de mantenimiento, quiero ser informado cuando intento marcar una habitación como "Inhabilitada por reparaciones" que no se encuentra en un estado que lo permita, para evitar transiciones inválidas en la máquina de estados.

**Por qué esta prioridad**: Marcar como "DisabledForRepairs" una habitación que no está en "Available" generaría una transición de estado inválida que podría afectar procesos de limpieza, bloqueo técnico o baja de habitaciones.

**Prueba Independiente**: Puede probarse intentando marcar como "Inhabilitada por reparaciones" habitaciones en cada estado distinto de "Available" y "Occupied" y verificando que el sistema rechaza la operación en todos los casos.

**Escenarios de Aceptación**:

4. **Escenario**: Intento de inhabilitar una habitación en limpieza
   - **Dado** una habitación se encuentra en estado "PendingCleaning" o "InCleaning"
   - **Cuando** el Personal de mantenimiento intenta marcarla como "Inhabilitada por reparaciones"
   - **Entonces** el sistema rechaza la operación e informa que la habitación no se encuentra en un estado que permita esta transición 

5. **Escenario**: Intento de inhabilitar una habitación ya inhabilitada
   - **Dado** una habitación ya se encuentra en estado "DisabledForRepairs"
   - **Cuando** el Personal de mantenimiento intenta marcarla nuevamente como "Inhabilitada por reparaciones"
   - **Entonces** el sistema detecta que ya se encuentra en ese estado y no aplica una nueva transición, informando al Personal de mantenimiento

6. **Escenario**: Intento de inhabilitar una habitación en bloqueo técnico
   - **Dado** una habitación se encuentra en estado "TechnicalBlock"
   - **Cuando** el Personal de mantenimiento intenta marcarla como "Inhabilitada por reparaciones"
   - **Entonces** el sistema rechaza la operación e informa el estado actual de la habitación

---

### Casos Borde

- Habitación con daño menor que no requiere inhabilitación: el sistema debe permitir al Personal de mantenimiento o Personal de limpieza decidir si la habitación requiere inhabilitación o no.
- Dos miembros de mantenimiento o Personal de limpieza intentan inhabilitar la misma habitación simultáneamente: el primer intento exitoso cambia el estado a "DisabledForRepairs" y el segundo es rechazado.
- Pérdida de conexión o fallo del proceso: la transacción se cancela y la habitación permanece en su estado original.
- Habitación con reserva futura: el marcado como "DisabledForRepairs" no afecta las reservas futuras; la reserva es un dato independiente del estado físico.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al actor "Personal de mantenimiento" marcar manualmente una habitación como "DisabledForRepairs".
- **FR-002**: El sistema DEBE validar como precondición que la habitación se encuentre en estado "Available".
- **FR-003**: El sistema DEBE rechazar la operación si la habitación se encuentra en cualquier estado distinto de "Available", informando el estado actual.
- **FR-004**: El sistema DEBE impedir marcar como "DisabledForRepairs" una habitación que ya se encuentra en ese mismo estado.
- **FR-005**: El sistema DEBE cambiar el estado de la habitación a "DisabledForRepairs" de forma inmediata tras la confirmación.
- **FR-006**: El sistema DEBE excluir las habitaciones en estado "DisabledForRepairs" de los resultados de disponibilidad utilizados para nuevas reservas o asignaciones.
- **FR-007**: El sistema DEBE registrar el usuario responsable (Personal de mantenimiento) y la fecha/hora en que se marcó la habitación, para efectos de trazabilidad.
- **FR-008**: El sistema DEBE registrar la transición a "DisabledForRepairs" como una acción manual para efectos de trazabilidad.
- **FR-009**: El sistema DEBE permitir que la habitación transite fuera del estado "DisabledForRepairs" únicamente a través del caso de uso "Confirmar reparación finalizada".

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Para este caso de uso, transita del estado **"Available"** al estado **"DisabledForRepairs"**, dos de los 7 estados vigentes de su ciclo de vida.
- **MaintenanceStaff**: Actor responsable de reportar daños físicos en las habitaciones y gestionar su inhabilitación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las habitaciones marcadas como "DisabledForRepairs" quedan excluidas de los resultados de disponibilidad de forma inmediata.
- **SC-002**: El Personal de mantenimiento puede marcar una habitación como "DisabledForRepairs" en menos de 1 minuto.
- **SC-003**: El 100% de los intentos de inhabilitar una habitación que no esté en estado "Available" son rechazados por el sistema.
- **SC-004**: El 100% de los marcados exitosos quedan registrados con usuario responsable y fecha/hora.
