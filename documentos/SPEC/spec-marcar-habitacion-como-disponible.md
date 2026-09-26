# Especificación de Funcionalidad: Marcar Habitación como Disponible

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Administrador / Personal de limpieza (según flujo)
**Creado**: 2026-09-07

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Revertir la baja de una habitación inactiva (Prioridad: P1)

Como Administrador, quiero marcar una habitación dada de baja como "Disponible" para reintegrarla al inventario operativo del hotel, de modo que vuelva a ser elegible para reservas cuando la decisión de baja es revertida (por ejemplo, tras corregir un error administrativo o reactivar un cierre temporal que se desistió).

**Por qué esta prioridad**: Sin esta capacidad, una habitación dada de baja de forma errónea o cuya baja se desiste queda permanentemente excluida del inventario, requiriendo un nuevo registro manual que perdería el historial previo. Es la contraparte directa de "Dar de baja habitación" y una de las dos vías de invocación de este caso de uso centralizado.

**Prueba Independiente**: Puede probarse de forma independiente iniciando sesión como Administrador, seleccionando una habitación en estado "Inactive", ejecutando la acción de marcar como disponible, y verificando que la habitación reaparezca en el inventario con estado "Available" y conservando su historial previo.

**Escenarios de Aceptación**:

1. **Escenario**: Reactivación exitosa de una habitación inactiva
   - **Dado** una habitación se encuentra en estado "Inactive" tras haber sido dada de baja anteriormente 
   - **Cuando** el Administrador selecciona la habitación y ejecuta la acción de marcar como disponible
   - **Entonces** el sistema cambia el estado de la habitación de "Inactive" a "Available", la incluye de forma inmediata en los resultados de disponibilidad para nuevas reservas.

2. **Escenario**: Intento de reactivar una habitación que no está en estado Inactive
   - **Dado** una habitación se encuentra en estado "Available", "Occupied", "InCleaning", "DisabledForRepairs" o "TechnicalBlock"
   - **Cuando** el Administrador intenta ejecutar la acción de marcar como disponible
   - **Entonces** el sistema rechaza la operación e informa que la habitación no se encuentra en estado "Inactive" 

3. **Escenario**: Confirmación explícita antes de reactivar
   - **Dado** el Administrador ha seleccionado una habitación en estado "Inactive"
   - **Cuando** inicia la acción de marcar como disponible
   - **Entonces** el sistema solicita una confirmación explícita antes de aplicar el cambio, dado el impacto de reintegrar una habitación al inventario operativo


---

### Historia de Usuario 2 - Confirmar fin de limpieza reintegrando la habitación al inventario (Prioridad: P1)

Como Personal de limpieza, quiero que al confirmar el fin del limpieza de una habitación esta vuelva automáticamente al estado "Disponible", para que el sistema la incluya en el inventario de reservas sin requerir una acción adicional del Administrador.

**Por qué esta prioridad**: Este es el flujo de mayor frecuencia de invocación de "Marcar habitación como disponible". Toda habitación que transita por el ciclo de limpieza (PendingCleaning → InCleaning) necesita volver a Available para cerrar el ciclo operativo. La lógica de transición está centralizada en este caso de uso para evitar duplicación.

**Prueba Independiente**: Puede probarse tomando una habitación en estado "InCleaning", ejecutando la confirmación de fin de limpieza (que incluye `<<include>>` Marcar habitación como disponible) y verificando que el estado resultante es "Available" y que la habitación aparece en el inventario para reservas.

**Escenarios de Aceptación**:

5. **Escenario**: Transición exitosa InCleaning → Available vía confirmación de fin de limpieza
   - **Dado** una habitación se encuentra en estado "InCleaning"
   - **Cuando** el Personal de limpieza confirma que la limpieza ha concluido
   - **Entonces** el sistema ejecuta `<<include>>` Marcar habitación como disponible, transicionando la habitación de "InCleaning" a "Available"

6. **Escenario**: Habitación reintegrada aparece en disponibilidad para reservas
   - **Dado** el limpieza de una habitación ha sido confirmado como finalizado
   - **Cuando** se consulta el inventario de habitaciones disponibles
   - **Entonces** la habitación aparece con estado "Available"  y es elegible para nuevas reservas o asignaciones

7. **Escenario**: Intento de confirmar fin de limpieza en habitación con estado no válido
   - **Dado** una habitación se encuentra en estado "Available", "Occupied", "DisabledForRepairs", "TechnicalBlock" o "Inactive"
   - **Cuando** el Personal de limpieza intenta confirmar el fin de limpieza
   - **Entonces** el sistema rechaza la operación e informa que la habitación no se encuentra en estado "InCleaning" 
---

### Historia de Usuario 3 - Validación de estado precondición en todos los flujos de invocación (Prioridad: P2)

Como sistema, quiero que cada invocación de "Marcar habitación como disponible" valide rigurosamente el estado de origen esperado para ese flujo específico, para evitar transiciones de estado inválidas que comprometan la integridad del inventario.

**Por qué esta prioridad**: Al ser un caso de uso compartido por múltiples flujos, la validación de estado precondición debe ser contextual: cada flujo invocador espera un estado de origen distinto.

**Prueba Independiente**: Puede probarse intentando invocar la transición a "Available" desde cada uno de los 7 estados posibles y verificando que solo se permiten las transiciones válidas según la máquina de estados.

**Escenarios de Aceptación**:

8. **Escenario**: Transiciones válidas hacia Available según la máquina de estados
   - **Dado** que la máquina de estados define únicamente dos entradas al estado "Available" desde estados no operativos: "Inactive" (vía reactivación directa del Administrador) e "InCleaning" (vía confirmación de fin de limpieza)
   - **Cuando** se invoca "Marcar habitación como disponible" desde cualquiera de los 7 estados
   - **Entonces** el sistema permite la transición únicamente si el estado de origen es "Inactive" o "InCleaning", rechazando cualquier otro caso

9. **Escenario**: Rechazo de transición desde estados Blocked u Occupied
   - **Dado** una habitación se encuentra en estado "Occupied", "PendingCleaning", "DisabledForRepairs" o "TechnicalBlock"
   - **Cuando** se intenta invocar "Marcar habitación como disponible" (por cualquier vía)
   - **Entonces** el sistema rechaza la operación e informa que la habitación no se encuentra en un estado que permita la transición a "Available"

---

### Casos Borde

- Habitación que no existe en el sistema: el sistema rechaza la operación e informa que la habitación no fue encontrada.
- Pérdida de conexión o fallo del proceso tras confirmar: la transacción se cancela y la habitación permanece en su estado original ("Inactive" o "InCleaning" según el flujo).
- Concurrencia: dos actores intentan invocar la transición sobre la misma habitación simultáneamente; el primer intento cambia el estado a "Available" y el segundo es rechazado porque la habitación ya no se encuentra en un estado origen válido.
- Flujo de limpieza interrumpido: si el Personal de limpieza confirma fin de limpieza pero la transición a "Available" falla, la habitación debe permanecer en "InCleaning" sin quedar en un estado intermedio inconsistente.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: "Marcar habitación como disponible" es un caso de uso propio, centralizado y compartido, no una extensión (`<<extend>>`) de ningún otro caso de uso. Es invocado por "Confirmar fin de limpieza" mediante `<<include>>` y disparado directamente por el Administrador para revertir bajas.
- **FR-002**: El sistema DEBE permitir la invocación directa de este caso de uso únicamente al actor "Administrador" (flujo de reactivación desde "Inactive").
- **FR-003**: El sistema DEBE permitir la invocación indirecta de este caso de uso mediante `<<include>>` desde "Confirmar fin de limpieza", ejecutada por el actor "Personal de limpieza".
- **FR-004**: El sistema DEBE validar como precondición que la habitación se encuentre en uno de los dos estados que permiten la transición a "Available" : "Inactive" (para el flujo de reactivación directa) o "InCleaning" (para el flujo de confirmación de fin de limpieza).
- **FR-005**: El sistema DEBE rechazar la operación si la habitación se encuentra en cualquier estado distinto de "Inactive" o "InCleaning", informando el estado actual de la habitación.
- **FR-006**: El sistema DEBE cambiar el estado de la habitación a "Available" de forma inmediata tras la confirmación, sin requerir pasos intermedios ni aprobaciones adicionales.
- **FR-007**: La habitación con estado "Available" resultante DEBE aparecer de forma inmediata en los resultados de disponibilidad para nuevas reservas o asignaciones del Módulo 2.
- **FR-008**: El sistema DEBE registrar el usuario responsable (Administrador o Personal de limpieza, según el flujo) y la fecha/hora en que se ejecutó la transición, para efectos de auditoría.
- **FR-009**: El sistema DEBE registrar la transición a "Available" como una acción manual para efectos de trazabilidad, independientemente del flujo que la haya disparado.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Para este caso de uso, la entidad transita al estado **"Available"** desde "Inactive" o "InCleaning", dos de los 7 estados vigentes de su ciclo de vida. La transición no modifica atributos de la habitación; solo actualiza su estado y la integra (o reintegra) al inventario operativo.
- **Administrator**: Actor que invoca directamente este caso de uso para revertir bajas desde el estado "Inactive".
- **CleaningStaff**: Actor que invoca indirectamente este caso de uso mediante `<<include>>` desde "Confirmar fin de limpieza", transicionando desde "InCleaning".

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Administrador puede completar la reactivación de una habitación inactiva en menos de 1 minuto.
- **SC-002**: El Personal de limpieza puede completar la confirmación de fin de limpieza (que incluye esta transición) en menos de 1 minuto.
- **SC-003**: El 100% de las invocaciones sobre habitaciones en estado distinto de "Inactive" o "InCleaning" son rechazadas por el sistema sin modificar el estado.
- **SC-004**: El 100% de las transiciones exitosas cambian el estado de la habitación a "Available" de forma inmediata.
- **SC-005**: El 100% de las habitaciones que transitan a "Available" quedan visibles en el inventario de disponibilidad para reservas sin demora perceptible.
- **SC-006**: El 100% de las transiciones ejecutadas quedan registradas con usuario responsable, flujo de origen y fecha/hora, sin pérdida del historial previo de la habitación.
