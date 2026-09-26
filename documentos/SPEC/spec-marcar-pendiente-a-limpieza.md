# Especificación de Funcionalidad: Marcar Pendiente a Limpieza

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Sistema (automático) / Personal de mantenimiento / Módulo 2 (Recepción)
**Creado**: 2026-09-07

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Transición automática Occupied → PendingCleaning tras check-out (Prioridad: P1)

Como sistema, quiero que al registrar un check-out de un huésped la habitación transite automáticamente al estado "PendingCleaning", para que la habitación entre en el ciclo de limpieza sin requerir una acción manual adicional.

**Por qué esta prioridad**: Esta es la transición de mayor frecuencia hacia "PendingCleaning". Toda habitación que ha sido ocupada necesita pasar por limpieza antes de poder ser reasignada. La transición es automática porque forma parte del flujo de check-out del Módulo 2, y su documentación centralizada evita duplicación.

**Prueba Independiente**: Puede probarse registrando un check-out para una habitación en estado "Occupied" y verificando que la habitación transita automáticamente a "PendingCleaning" sin intervención manual.

**Escenarios de Aceptación**:

1. **Escenario**: Transición automática exitosa Occupied → PendingCleaning
   - **Dado** una habitación se encuentra en estado "Occupied"
   - **Cuando** se registra el check-out del huésped asociado a la habitación
   - **Entonces** el sistema cambia automáticamente el estado de la habitación de "Occupied" a "PendingCleaning" como postcondición del check-out

2. **Escenario**: Habitación pendiente de limpieza aparece en la lista del Personal de limpieza
   - **Dado** una habitación ha transicionado a "PendingCleaning" tras un check-out
   - **Cuando** el Personal de limpieza consulta su lista de habitaciones pendientes
   - **Entonces** la habitación aparece con estado "PendingCleaning" y es elegible para ser marcada como "InCleaning"

3. **Escenario**: La habitación pendiente de limpieza no está disponible para reservas
   - **Dado** una habitación se encuentra en estado "PendingCleaning"
   - **Cuando** se consulta el inventario de habitaciones disponibles
   - **Entonces** la habitación no aparece en los resultados porque se encuentra pendiente de limpieza

---

### Historia de Usuario 2 - Transición desde confirmar reparación finalizada (Prioridad: P1)

Como Personal de mantenimiento, quiero que al confirmar la finalización de una reparación o mantenimiento preventivo la habitación transite al estado "PendingCleaning", para que la habitación pase por limpieza antes de volver a estar disponible.

**Por qué esta prioridad**: Toda intervención de mantenimiento —sea una reparación real o una tarea preventiva— ensucia la habitación, por lo que ninguna pasa a "Available" de forma directa. Esta lógica está centralizada en "Marcar pendiente a limpieza" para que "Confirmar reparación finalizada" tenga un único destino sin ramas según el estado de origen.

**Prueba Independiente**: Puede probarse confirmando la finalización de una reparación en una habitación con estado "DisabledForRepairs" y verificando que la habitación transita a "PendingCleaning" (no directamente a "Available").

**Escenarios de Aceptación**:

4. **Escenario**: Transición exitosa DisabledForRepairs → PendingCleaning
   - **Dado** una habitación se encuentra en estado "DisabledForRepairs"
   - **Cuando** el Personal de mantenimiento confirma que la reparación ha concluido
   - **Entonces** el sistema invoca `<<include>>` "Marcar pendiente a limpieza" y cambia el estado de la habitación de "DisabledForRepairs" a "PendingCleaning"

5. **Escenario**: Transición exitosa TechnicalBlock → PendingCleaning
   - **Dado** una habitación se encuentra en estado "TechnicalBlock"
   - **Cuando** el Personal de mantenimiento confirma que el mantenimiento preventivo ha concluido
   - **Entonces** el sistema invoca `<<include>>` "Marcar pendiente a limpieza" y cambia el estado de la habitación de "TechnicalBlock" a "PendingCleaning"

6. **Escenario**: La habitación reparada requiere limpieza antes de estar disponible
   - **Dado** se ha confirmado la finalización de una reparación
   - **Cuando** se consulta el inventario de habitaciones disponibles
   - **Entonces** la habitación no aparece en los resultados porque se encuentra en estado "PendingCleaning", requiriendo pasar por el proceso de limpieza

---

### Historia de Usuario 3 - Rechazo de transiciones inválidas hacia PendingCleaning (Prioridad: P2)

Como sistema, quiero que cada invocación de "Marcar pendiente a limpieza" valide que el estado de origen es uno de los permitidos, para evitar transiciones de estado inválidas que comprometan la integridad del inventario.

**Por qué esta prioridad**: Al ser un caso de uso compartido por múltiples flujos (check-out, confirmar reparación), la validación de estado precondición debe asegurar que solo se permitan las transiciones declaradas en la máquina de estados.

**Prueba Independiente**: Puede probarse intentando invocar la transición a "PendingCleaning" desde cada uno de los 7 estados posibles y verificando que solo se permiten las transiciones válidas.

**Escenarios de Aceptación**:

7. **Escenario**: Transiciones válidas hacia PendingCleaning según la máquina de estados
   - **Dado** que la máquina de estados define tres entradas al estado "PendingCleaning": "Occupied" (vía check-out, automática), "DisabledForRepairs" (vía confirmar reparación) y "TechnicalBlock" (vía confirmar reparación)
   - **Cuando** se invoca "Marcar pendiente a limpieza" desde cualquiera de los 7 estados
   - **Entonces** el sistema permite la transición únicamente si el estado de origen es "Occupied", "DisabledForRepairs" o "TechnicalBlock", rechazando cualquier otro caso

8. **Escenario**: Rechazo de transición desde Available
   - **Dado** una habitación se encuentra en estado "Available"
   - **Cuando** se intenta invocar "Marcar pendiente a limpieza"
   - **Entonces** el sistema rechaza la operación e informa que la habitación ya se encuentra disponible y no requiere limpieza

---

### Casos Borde

- Check-out que dispara la transición automática Occupied → PendingCleaning pero la habitación ya fue marcada como "InCleaning" por otro proceso: el sistema debe validar el estado antes de aplicar la transición automática.
- Confirmación de reparación que dispara la transición pero la habitación ya fue marcada como "DisabledForRepairs" por otro usuario: el sistema debe manejar la concurrencia adecuadamente.
- Pérdida de conexión o fallo del proceso tras la transición: la habitación debe permanecer en su estado original sin quedar en un estado intermedio inconsistente.
- Dos flujos intentan invocar la transición sobre la misma habitación simultáneamente: solo uno tiene éxito y el otro es rechazado porque la habitación ya cambió de estado.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: "Marcar pendiente a limpieza" es un caso de uso propio, centralizado y compartido, documentado una sola vez en vez de repetirse en cada flujo que la dispara (check-out del Módulo 2, y confirmar reparación finalizada).
- **FR-002**: El sistema DEBE validar como precondición que la habitación se encuentre en uno de los tres estados que permiten la transición a "PendingCleaning" : "Occupied" (para el flujo de check-out), "DisabledForRepairs" o "TechnicalBlock" (para el flujo de confirmar reparación).
- **FR-003**: El sistema DEBE rechazar la operación si la habitación se encuentra en cualquier estado distinto de "Occupied", "DisabledForRepairs" o "TechnicalBlock", informando el estado actual.
- **FR-004**: El sistema DEBE cambiar el estado de la habitación a "PendingCleaning" de forma inmediata tras la invocación, sin requerir pasos intermedios ni aprobaciones adicionales.
- **FR-005**: La habitación con estado "PendingCleaning" resultante DEBE aparecer de forma inmediata en la lista de habitaciones pendientes de limpieza para el Personal de limpieza.
- **FR-006**: La habitación en estado "PendingCleaning" NO DEBE aparecer en los resultados de disponibilidad para reservas.
- **FR-008**: El sistema DEBE registrar la transición a "PendingCleaning" como una acción documentada centralmente, evitando duplicación de lógica entre los flujos invocadores.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Para este caso de uso, la entidad transita al estado **"PendingCleaning"** desde "Occupied", "DisabledForRepairs" o "TechnicalBlock", cuatro de los 7 estados vigentes de su ciclo de vida según documentos/SPEC/referencias/maquina-estados-habitacion.md.
- **ReceptionStaff / MaintenanceStaff**: Actores que disparan indirectamente este caso de uso mediante sus respectivos flujos (check-out y confirmar reparación).
- **CleaningStaff**: Actor que recibe la habitación en estado "PendingCleaning" como insumo de su trabajo.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las transiciones Occupied → PendingCleaning se ejecutan de forma automática como postcondición del check-out, sin intervención manual.
- **SC-002**: El 100% de las transiciones desde "DisabledForRepairs" o "TechnicalBlock" a "PendingCleaning" se ejecutan de forma inmediata tras la confirmación de reparación.
- **SC-003**: Tras una transición exitosa, la habitación aparece como "PendingCleaning" en la lista visible para el Personal de limpieza sin demora perceptible.
- **SC-004**: El 100% de los intentos de transición desde estados no permitidos ("Available", "PendingCleaning", "InCleaning", "Inactive") son rechazados por el sistema.
- **SC-005**: El 100% de las transiciones ejecutadas quedan registradas con flujo de origen y fecha/hora.
