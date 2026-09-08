# Especificación de Funcionalidad: Confirmar Reparación Finalizada

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de mantenimiento
**Creado**: 2026-09-07

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Confirmar finalización de reparación desde DisabledForRepairs (Prioridad: P1)

Como Personal de mantenimiento, quiero confirmar en el sistema que he finalizado la reparación de una habitación que estaba inhabilitada, para que el sistema actualice su estado y la habitación pueda reintegrarse al ciclo operativo mediante limpieza.

**Por qué esta prioridad**: Es la conclusión del flujo de mantenimiento correctivo. Sin esta confirmación, la habitación se quedaría atascada en estado "DisabledForRepairs", reduciendo el aforo disponible del hotel, toda intervención de mantenimiento ensucia la habitación, por lo que la transición es hacia "PendingCleaning" (no directamente a "Available").

**Prueba Independiente**: Puede probarse iniciando sesión como Personal de mantenimiento, buscando una habitación en estado "DisabledForRepairs", confirmando la finalización de la reparación y verificando que la habitación transita a "PendingCleaning" y aparece en la lista de pendientes de limpieza.

**Escenarios de Aceptación**:

1. **Escenario**: Confirmación exitosa de reparación desde DisabledForRepairs
   - **Dado** una habitación se encuentra en estado "DisabledForRepairs" 
   - **Cuando** el Personal de mantenimiento confirma que la reparación ha concluido
   - **Entonces** el sistema cambia el estado de la habitación de "DisabledForRepairs" a "PendingCleaning", invocando `<<include>>` "Marcar pendiente a limpieza"

2. **Escenario**: Habitación reparada aparece como pendiente de limpieza
   - **Dado** se ha confirmado la finalización de una reparación
   - **Cuando** el Personal de limpieza consulta su lista de habitaciones pendientes
   - **Entonces** la habitación aparece con estado "PendingCleaning" y es elegible para ser marcada como "InCleaning"

3. **Escenario**: La habitación reparada no está disponible directamente para reservas
   - **Dado** se ha confirmado la finalización de una reparación
   - **Cuando** se consulta el inventario de habitaciones disponibles
   - **Entonces** la habitación no aparece en los resultados porque se encuentra en estado "PendingCleaning", requiriendo pasar primero por el proceso de limpieza

---

### Historia de Usuario 2 - Confirmar finalización de reparación desde TechnicalBlock (Prioridad: P1)

Como Personal de mantenimiento, quiero confirmar en el sistema que he finalizado el mantenimiento preventivo de una habitación que estaba en bloqueo técnico, para que la habitación pueda reintegrarse al ciclo operativo.

**Por qué esta prioridad**: El bloqueo técnico reserva una habitación para mantenimiento preventivo. Sin la confirmación de finalización, la habitación permanecería bloqueada indefinidamente. Al igual que con las reparaciones correctivas, la transición es hacia "PendingCleaning" porque toda intervención de mantenimiento ensucia la habitación.

**Prueba Independiente**: Puede probarse iniciando sesión como Personal de mantenimiento, buscando una habitación en estado "TechnicalBlock", confirmando la finalización del mantenimiento y verificando que la habitación transita a "PendingCleaning"".

**Escenarios de Aceptación**:

4. **Escenario**: Confirmación exitosa de mantenimiento desde TechnicalBlock
   - **Dado** una habitación se encuentra en estado "TechnicalBlock" 
   - **Cuando** el Personal de mantenimiento confirma que el mantenimiento preventivo ha concluido
   - **Entonces** el sistema cambia el estado de la habitación de "TechnicalBlock" a "PendingCleaning", invocando `<<include>>` "Marcar pendiente a limpieza".

5. **Escenario**: Intento de confirmar reparación en habitación no inhabilitada ni en bloqueo técnico
   - **Dado** una habitación se encuentra en estado "Available", "Occupied", "InCleaning" o "Inactive"
   - **Cuando** el Personal de mantenimiento intenta confirmar la finalización de una reparación
   - **Entonces** el sistema rechaza la operación e informa que la habitación no se encuentra en estado "DisabledForRepairs" ni "TechnicalBlock".

---

### Casos Borde

- Obligatoriedad de observaciones e insumos utilizados en la reparación: El sistema exige de manera obligatoria el ingreso de un informe o detalle técnico de los arreglos realizados antes de permitir la confirmación; el registro de insumos o repuestos utilizados es un campo complementario opcional que queda registrado en la bitácora de auditoría sin impedir la transición de la habitación a "PendingCleaning".
- Dos miembros de mantenimiento intentan confirmar la reparación de la misma habitación simultáneamente: el primer intento exitoso cambia el estado a "PendingCleaning" y el segundo es rechazado.
- Pérdida de conexión o fallo del proceso tras confirmar: la transacción se cancela y la habitación permanece en su estado original ("DisabledForRepairs" o "TechnicalBlock").
- Habitación reparada con daño adicional descubierto durante la intervención: el Personal de mantenimiento puede registrar observaciones antes de confirmar, pero la transición sigue siendo hacia "PendingCleaning".


## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al actor "Personal de mantenimiento" ejecutar la confirmación de reparación finalizada.
- **FR-002**: El sistema DEBE validar como precondición que la habitación se encuentre en estado "DisabledForRepairs" o "TechnicalBlock" antes de permitir la confirmación.
- **FR-003**: El sistema DEBE rechazar la operación si la habitación no se encuentra en estado "DisabledForRepairs" ni "TechnicalBlock", informando el estado actual.
- **FR-004**: El sistema DEBE cambiar el estado de la habitación de "DisabledForRepairs" o "TechnicalBlock" a "PendingCleaning" de forma inmediata tras la confirmación, invocando `<<include>>` "Marcar pendiente a limpieza".
- **FR-005**: La habitación con estado "PendingCleaning" resultante DEBE aparecer de forma inmediata en la lista de habitaciones pendientes de limpieza para el Personal de limpieza.
- **FR-006**: La habitación en estado "PendingCleaning" NO DEBE aparecer en los resultados de disponibilidad para reservas, ya que requiere pasar por el proceso de limpieza antes de volver a estar "Available".
- **FR-007**: El sistema DEBE registrar el usuario responsable (Personal de mantenimiento) y la fecha/hora de la confirmación, para efectos de trazabilidad.
- **FR-008**: El sistema DEBE registrar la transición a "PendingCleaning" como una acción manual para efectos de trazabilidad.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Para este caso de uso, transita del estado **"DisabledForRepairs"** o **"TechnicalBlock"** al estado **"PendingCleaning"**, cuatro de los 7 estados vigentes de su ciclo de vida.
- **MaintenanceStaff**: Actor responsable de ejecutar y confirmar la finalización de reparaciones y mantenimientos preventivos.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las confirmaciones de reparación exitosas cambian el estado de la habitación a "PendingCleaning" de forma inmediata.
- **SC-002**: El Personal de mantenimiento puede registrar la finalización en menos de 2 minutos.
- **SC-003**: El 100% de los intentos de confirmar reparación sobre habitaciones que no estén en "DisabledForRepairs" ni "TechnicalBlock" son rechazados por el sistema.
- **SC-004**: Tras una confirmación exitosa, la habitación aparece como "PendingCleaning" en la lista visible para el Personal de limpieza sin demora perceptible.
- **SC-005**: El 100% de las confirmaciones quedan registradas con usuario responsable y fecha/hora.
