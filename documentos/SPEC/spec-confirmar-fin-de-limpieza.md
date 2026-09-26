# Especificación de Funcionalidad: Confirmar Fin de limpieza de Habitación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de limpieza
**Creado**: 2026-09-07

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Confirmación exitosa de fin de limpieza (Prioridad: P1)

Como Personal de limpieza, quiero confirmar que el limpieza de una habitación ha concluido para reintegrarla al inventario disponible del hotel y habilitarla para nuevas reservas.

**Por qué esta prioridad**: Es el cierre del ciclo de limpieza. Sin esta acción la habitación permanece bloqueada indefinidamente en estado "InCleaning", reduciendo el inventario disponible y afectando la capacidad operativa del hotel. Esta confirmación invoca `<<include>>` "Marcar habitación como disponible" según , que centraliza la transición hacia el estado "Available".

**Prueba Independiente**: Puede probarse tomando una habitación en estado "InCleaning", confirmando el fin de limpieza y verificando que su estado cambia a "Available" y aparece nuevamente en el inventario para reservas.

**Escenarios de Aceptación**:

1. **Escenario**: Confirmación exitosa de fin de limpieza
   - **Dado** una habitación se encuentra en estado "InCleaning" según 
   - **Cuando** el Personal de limpieza confirma que la limpieza ha concluido
   - **Entonces** el sistema ejecuta `<<include>>` "Marcar habitación como disponible", transicionando la habitación de "InCleaning" a "Available" y confirmando la operación

2. **Escenario**: Habitación confirmada como lista aparece en el inventario disponible
   - **Dado** el limpieza de una habitación ha sido confirmado como finalizado
   - **Cuando** se consulta el inventario de habitaciones disponibles
   - **Entonces** la habitación aparece con estado "Available" según  y puede ser reservada

3. **Escenario**: Personal de limpieza puede identificar qué habitaciones están siendo aseadas
   - **Dado** existen habitaciones con estado "InCleaning"
   - **Cuando** el Personal de limpieza consulta su lista de habitaciones en proceso
   - **Entonces** el sistema muestra únicamente las habitaciones en estado "InCleaning" pendientes de confirmación

---

### Historia de Usuario 2 - Intento de confirmar fin de limpieza en habitación con estado no válido (Prioridad: P1)

Como Personal de limpieza, quiero ser informado cuando intento confirmar el fin de limpieza de una habitación que no está en proceso de limpieza, para evitar transiciones de estado incorrectas que afecten el inventario del hotel.

**Por qué esta prioridad**: Confirmar el fin de limpieza en una habitación que no está en estado "InCleaning" generaría una transición de estado inválida que podría liberar habitaciones que no han sido acondicionadas, con impacto directo en la experiencia del huésped.

**Prueba Independiente**: Puede probarse intentando confirmar el fin de limpieza en habitaciones en cada estado distinto de "InCleaning" y verificando que el sistema rechaza la operación en todos los casos.

**Escenarios de Aceptación**:

1. **Escenario**: Intento de confirmar fin de limpieza en habitación disponible
   - **Dado** una habitación se encuentra en estado "Available"
   - **Cuando** el Personal de limpieza intenta confirmar el fin de limpieza
   - **Entonces** el sistema rechaza la operación e informa que la habitación no se encuentra en proceso de limpieza

2. **Escenario**: Intento de confirmar fin de limpieza en habitación ocupada
   - **Dado** una habitación se encuentra en estado "Occupied"
   - **Cuando** el Personal de limpieza intenta confirmar el fin de limpieza
   - **Entonces** el sistema rechaza la operación e informa el estado actual de la habitación

3. **Escenario**: Intento de confirmar fin de limpieza en habitación en mantenimiento
   - **Dado** una habitación se encuentra en estado "DisabledForRepairs", "TechnicalBlock" o "Inactive"
   - **Cuando** el Personal de limpieza intenta confirmar el fin de limpieza
   - **Entonces** el sistema rechaza la operación e informa el estado actual de la habitación

---

### Casos Borde

- ¿Puede el Personal de limpieza confirmar el fin de limpieza de una habitación que otro miembro del personal marcó como "InCleaning", o debe ser el mismo usuario? :Inicialmente si,solo la persona asignada a dicho evento puede confirmar el fin de limpieza en esa habitación, para próximas iteraciones se ha propuesto como backup que el administrador tenga acceso a dicho caso de uso para situaciones excepcionales (bugs,sesiones con errores que no permitan al miembro del personal de limpieza asignado confirmar el fin de limpieza en la habitación).
- ¿El sistema debe registrar el tiempo total que duró el proceso de limpieza (desde el marcado inicial hasta la confirmación)? :No,se ha pensado registrar el timestamp de inicio (cuando pasa al estado "En limpieza" y luego cuando se confirma el fin de limpieza en habitación),de esta forma la duración o tiempo total del proceso es la diferencia entre la hora en ambos (independiente de si se almacena en minutos,segundos u horas)
- ¿Qué ocurre si el Personal de limpieza confirma el fin de limpieza por error? ¿Puede revertirse el estado de "Available" a "InCleaning"? :Relacionado a primer caso borde,se ha pensado incluir al administrador la factultad para acceder al caso de uso "Confirmar fin de limpieza en habitación" como backup para situaciones excepcionales.
- Pérdida de conexión o fallo del proceso tras confirmar: la transacción se cancela y la habitación permanece en estado "InCleaning".

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al actor "Personal de limpieza" confirmar el fin de limpieza de una habitación.
- **FR-002**: El sistema DEBE validar como precondición que la habitación se encuentre en estado "InCleaning" antes de ejecutar la confirmación.
- **FR-003**: El sistema DEBE rechazar la operación si la habitación no está en estado "InCleaning", informando el estado actual.
- **FR-004**: El sistema DEBE ejecutar `<<include>>` "Marcar habitación como disponible" para transicionar la habitación de "InCleaning" a "Available" al confirmar el fin de limpieza.
- **FR-005**: La habitación con estado "Available" resultante DEBE aparecer de forma inmediata en el inventario disponible para reservas.
- **FR-006**: El sistema DEBE permitir al Personal de limpieza consultar la lista de habitaciones en estado "PendingCleaning" para contribuir a la distribucion de la carga de la labor de limpieza en el hotel.
- **FR-007**: El sistema DEBE confirmar al Personal de limpieza la transición de estado exitosa.
- **FR-008**: El sistema DEBE registrar la fecha, hora y el usuario (Personal de limpieza) que realizó la confirmación, para efectos de trazabilidad.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. La confirmación del fin de limpieza transiciona su estado de **"InCleaning"** a **"Available"**, reintegrándola al inventario operativo. Son dos de los 7 estados vigentes de su ciclo de vida.
- **CleaningStaff**: Actor responsable de ejecutar y confirmar el limpieza de las habitaciones.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las confirmaciones sobre habitaciones en estado distinto de "InCleaning" son rechazadas sin modificar el estado de la habitación.
- **SC-002**: El 100% de las confirmaciones exitosas cambian el estado de la habitación a "Available" de forma inmediata.
- **SC-003**: Una habitación confirmada como lista aparece en el inventario disponible para reservas sin demora perceptible.
- **SC-004**: El Personal de limpieza puede identificar en todo momento qué habitaciones están en proceso y cuáles han sido confirmadas, sin necesidad de consultar a otro actor del sistema.
- **SC-005**: El Personal de limpieza puede completar la confirmación de fin de limpieza en menos de 1 minuto.
