# Especificación del Caso de Uso: Consultar Panel de Limpieza

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del panel de limpieza (Prioridad: P1)

Como miembro del personal de limpieza quiero ver en un solo panel las habitaciones que necesitan limpieza y las que están disponibles, con su última limpieza y las acciones que puedo ejecutar sobre cada una, para decidir qué habitación atender sin depender de instrucciones verbales y sin tomar una habitación que ya está siendo limpiada por otro miembro.

**Por qué esta prioridad**: Es el punto de entrada de todo el trabajo de limpieza. Desde el panel se inician las limpiezas (*Marcar Habitación en Limpieza*) y se reportan daños en habitaciones disponibles (*Marcar Habitación Inhabilitada por Reparaciones*). Sin él, el personal no sabe qué habitaciones quedaron pendientes después de un check-out, una reparación o una tarea liberada.

**Prueba independiente**: Puede ser probada autenticándose como un miembro del personal de limpieza sin tarea activa, verificando que el panel lista únicamente las habitaciones en `PendingCleaning` y `Available` (primero las pendientes), con número, tipo, estado, última limpieza y las acciones de cada estado; buscando una habitación por su número; y verificando que un miembro con una tarea activa es redirigido a la vista de tarea activa.

**Escenarios de aceptación**:

1. **Escenario**: Panel con habitaciones pendientes y disponibles
   - **Dado que** El miembro del personal de limpieza está autenticado, no tiene una tarea activa y existen las habitaciones "101" en `PendingCleaning`, "102" en `Available` y "103" en `InCleaning`
   - **Cuando** El miembro ingresa al panel de limpieza
   - **Entonces** El sistema lista la habitación "101" (primero, por estar pendiente) y la "102" con su número, tipo, estado en español, última limpieza y acciones, y no lista la habitación "103"

2. **Escenario**: Acciones según el estado de la habitación
   - **Dado que** El panel lista la habitación "101" en `PendingCleaning` y la "102" en `Available`
   - **Cuando** El miembro revisa las acciones de cada fila
   - **Entonces** El sistema ofrece **Iniciar limpieza** en la habitación "101", y **Reportar daño** e **Iniciar limpieza** en la habitación "102"

3. **Escenario**: Última limpieza de una habitación
   - **Dado que** La habitación "102" tiene una `CleaningTask` cerrada con `Outcome` = `Completed` el "07-10-2026 16:40" y una posterior con `Outcome` = `Released`, y la habitación "104" nunca ha sido limpiada
   - **Cuando** El miembro consulta el panel
   - **Entonces** El sistema muestra "07-10-2026 16:40" como última limpieza de la habitación "102" (las tareas liberadas no cuentan) y "Sin registro" en la habitación "104"

4. **Escenario**: Búsqueda por número de habitación
   - **Dado que** El panel lista varias habitaciones
   - **Cuando** El miembro busca la habitación "101"
   - **Entonces** El sistema muestra solo la habitación "101", con el mismo formato del listado

5. **Escenario**: Búsqueda sin resultados o panel vacío
   - **Dado que** No existe ninguna habitación "999" en `PendingCleaning` o `Available`, o no hay habitaciones en esos estados
   - **Cuando** El miembro busca la habitación "999" o ingresa al panel
   - **Entonces** El sistema informa "No se encontró la habitación 999" o "No hay habitaciones pendientes de limpieza ni disponibles", según el caso

6. **Escenario**: Miembro con una tarea activa
   - **Dado que** El miembro tiene una `CleaningTask` activa sobre la habitación "105"
   - **Cuando** Intenta ingresar al panel de limpieza
   - **Entonces** El sistema lo redirige a la vista de tarea activa, definida en *Confirmar Fin de Limpieza de Habitación*

7. **Escenario**: Habitación tomada por otro miembro mientras se consulta el panel
   - **Dado que** El panel del "Usuario A" lista la habitación "101" en `PendingCleaning`
   - **Cuando** El "Usuario B" inicia la limpieza de la habitación "101" y luego el "Usuario A" pulsa **Iniciar limpieza** sobre ella
   - **Entonces** El sistema rechaza la acción del "Usuario A" informando el estado actual de la habitación (*Marcar Habitación en Limpieza*, FR-006) y recarga el panel, en el que la habitación "101" ya no aparece

---

### Casos Límite

1. **¿Cómo se entera el personal de habitaciones que quedan pendientes mientras tiene el panel abierto?**
   El panel se actualiza automáticamente cada 60 segundos y cada vez que el miembro vuelve a él después de una acción (FR-009). No se usan notificaciones en tiempo real.

2. **¿Qué ocurre si el miembro no está autenticado o no tiene el rol de limpieza?**
   El sistema rechaza el acceso y solicita autenticación, o informa que no tiene permiso.

3. **¿Qué ocurre con las habitaciones en otros estados?**
   No se listan. Las habitaciones en `InCleaning` las ve solo el miembro dueño de la tarea, desde su vista de tarea activa; las demás no requieren acciones del personal de limpieza.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir el acceso al panel de limpieza únicamente a miembros del personal de limpieza autenticados (rol `CLEANING_STAFF`). Si el miembro tiene una `CleaningTask` sin `EndDateTime`, el sistema DEBE redirigirlo a la vista de tarea activa (*Confirmar Fin de Limpieza de Habitación*, FR-007) en lugar de mostrar el panel.
- **FR-002**: El sistema DEBE listar únicamente las habitaciones en estado `PendingCleaning` o `Available`.
- **FR-003**: Cada habitación listada DEBE mostrar: número de habitación, tipo (Sencilla, Doble, Suite o Boutique), estado con su nombre en español (Pendiente de limpieza o Disponible), última limpieza (formato DD-MM-YYYY HH:MM, o "Sin registro") y las acciones de su estado (FR-005).
- **FR-004**: La última limpieza DEBE ser el `EndDateTime` de la `CleaningTask` cerrada más reciente de la habitación con `Outcome` `Completed` o `DamageReported`; las tareas con `Outcome` `Released` no cuentan. Si no existe, el sistema DEBE mostrar "Sin registro".
- **FR-005**: El sistema DEBE ofrecer en cada habitación las acciones de su estado: en `PendingCleaning`, **Iniciar limpieza**; en `Available`, **Reportar daño** e **Iniciar limpieza**. **Iniciar limpieza** ejecuta *Marcar Habitación en Limpieza* y **Reportar daño** ejecuta *Marcar Habitación Inhabilitada por Reparaciones*; este caso de uso no reimplementa sus reglas.
- **FR-006**: El sistema DEBE ordenar el listado mostrando primero las habitaciones en `PendingCleaning` y después las `Available`, cada grupo por número de habitación ascendente.
- **FR-007**: El sistema DEBE permitir buscar en el listado por número de habitación con coincidencia exacta, presentando el resultado con el mismo formato del listado. Al vaciar la búsqueda, el panel vuelve a mostrar el listado completo.
- **FR-008**: El sistema DEBE informar "No se encontró la habitación X" cuando la búsqueda no retorne resultados, y "No hay habitaciones pendientes de limpieza ni disponibles" cuando el panel no tenga habitaciones para listar.
- **FR-009**: El sistema DEBE actualizar el panel automáticamente cada 60 segundos y al volver a él después de ejecutar una acción. Si una acción falla porque la habitación cambió de estado, el sistema DEBE informar el estado actual y recargar el panel.
- **FR-010**: El sistema DEBE mostrar sobre el listado la cantidad de habitaciones pendientes de limpieza y la cantidad de habitaciones disponibles.
- **FR-011**: La consulta del panel DEBE ser de solo lectura: no cambia el estado de ninguna habitación ni crea o modifica tareas.

### Entidades Clave

- **Room**: Habitación del hotel. El panel lista las que están en `PendingCleaning` o `Available`.
- **CleaningTask**: Entidad definida en *Marcar Habitación en Limpieza*. El panel la consulta para calcular la última limpieza y para detectar la tarea activa del miembro.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El panel carga en menos de 2 segundos.
- **SC-002**: El 100% de las habitaciones listadas están en `PendingCleaning` o `Available` al momento de la consulta, y ninguna habitación en otro estado aparece en el panel.
- **SC-003**: Un miembro con una tarea activa nunca ve el panel: el 100% de sus accesos lo llevan a la vista de tarea activa.
- **SC-004**: Una habitación que pasa a `PendingCleaning` aparece en el panel en menos de 60 segundos sin que el miembro recargue la página.
