# Especificación del Caso de Uso: Consultar Panel de Mantenimiento

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del panel de mantenimiento (Prioridad: P1)

Como miembro del personal de mantenimiento quiero ver en un solo panel las habitaciones que necesitan intervención (inhabilitadas o en bloqueo técnico) y el resto de las habitaciones con su mantenimiento programado, para decidir qué intervenir, programar mantenimientos preventivos y reportar daños sin consultar otras pantallas.

**Por qué esta prioridad**: Es el punto de entrada del trabajo de mantenimiento. Desde el panel se inician las reparaciones (*Confirmar Fin de Reparación de Habitación*), se programan bloqueos técnicos (*Programar Bloqueo Técnico para Habitación*), se reportan daños (*Marcar Habitación Inhabilitada por Reparaciones*) y se consultan los informes. Sin él, una habitación inhabilitada o con un bloqueo vencido puede quedar fuera de servicio indefinidamente.

**Prueba independiente**: Puede ser probada autenticándose como un miembro del personal de mantenimiento sin tarea activa, verificando que el panel lista todas las habitaciones excepto las `Inactive` (primero las que requieren intervención), con número, tipo, estado, mantenimiento programado y las acciones de cada estado; buscando una habitación por su número; y verificando que un miembro con una tarea activa es redirigido a la vista de tarea activa.

**Escenarios de aceptación**:

1. **Escenario**: Panel con habitaciones que requieren intervención
   - **Dado que** El miembro del personal de mantenimiento está autenticado, no tiene una tarea activa y existen las habitaciones "201" en `DisabledForRepairs`, "202" en `TechnicalBlock`, "203" en `Occupied` y "204" en `Inactive`
   - **Cuando** El miembro ingresa al panel de mantenimiento
   - **Entonces** El sistema lista primero las habitaciones "201" y "202", luego la "203", cada una con su número, tipo, estado en español, mantenimiento programado y acciones, y no lista la habitación "204"

2. **Escenario**: Acciones según el estado de la habitación
   - **Dado que** El panel lista una habitación en `Available`, una en `DisabledForRepairs` sin tarea abierta, una en `TechnicalBlock` sin tarea abierta y una en `Occupied` con un mantenimiento programado
   - **Cuando** El miembro revisa las acciones de cada fila
   - **Entonces** El sistema ofrece **Programar mantenimiento** en todas; además **Reportar daño** en la `Available`, **Ver informe** (reporte de daño) e **Iniciar reparaciones** en la `DisabledForRepairs`, **Ver informe** (informe del bloqueo) e **Iniciar reparaciones** en la `TechnicalBlock`, y **Ver informe** (informe del bloqueo) en la `Occupied`

3. **Escenario**: Habitación que ya está siendo intervenida
   - **Dado que** La habitación "201" está en `DisabledForRepairs` y tiene una `ReparationTask` abierta de otro miembro
   - **Cuando** El miembro consulta el panel
   - **Entonces** El sistema muestra la habitación "201" con la etiqueta "En reparación" y sin la acción **Iniciar reparaciones**

4. **Escenario**: Bloqueo técnico vencido
   - **Dado que** La habitación "202" está en `TechnicalBlock` con un `TechnicalBlockReport` `Applied` cuya fecha estimada de fin fue "20-10-2026" y la fecha actual es "21-10-2026"
   - **Cuando** El miembro consulta el panel
   - **Entonces** El sistema muestra su estado como "Bloqueo técnico (vencido)" y la ubica antes que las demás habitaciones en `TechnicalBlock`

5. **Escenario**: Mantenimiento programado
   - **Dado que** La habitación "203" tiene dos `TechnicalBlockReport` en `Scheduled` (del "25-10-2026" al "26-10-2026" y del "10-11-2026" al "12-11-2026") y la habitación "205" tuvo uno que pasó a `Expired`
   - **Cuando** El miembro consulta el panel
   - **Entonces** El sistema muestra "25-10-2026 a 26-10-2026" como mantenimiento programado de la habitación "203" y "Sin programar" en la habitación "205"

6. **Escenario**: Búsqueda por número de habitación
   - **Dado que** El panel lista varias habitaciones
   - **Cuando** El miembro busca la habitación "202"
   - **Entonces** El sistema muestra solo la habitación "202", con el mismo formato del listado; si no existe o está `Inactive`, informa "No se encontró la habitación 202"

7. **Escenario**: Miembro con una tarea activa
   - **Dado que** El miembro tiene una `ReparationTask` abierta sobre la habitación "201"
   - **Cuando** Intenta ingresar al panel de mantenimiento
   - **Entonces** El sistema lo redirige a la vista de tarea activa, definida en *Confirmar Fin de Reparación de Habitación*

---

### Casos Límite

1. **¿Cómo se entera el personal de habitaciones inhabilitadas mientras tiene el panel abierto?**
   El panel se actualiza automáticamente cada 60 segundos y cada vez que el miembro vuelve a él después de una acción (FR-010). No se usan notificaciones en tiempo real.

2. **¿Qué ocurre si dos miembros pulsan Iniciar reparaciones sobre la misma habitación?**
   Solo el primero inicia la reparación (*Confirmar Fin de Reparación de Habitación*, FR-012); el segundo recibe el motivo del rechazo y el panel se recarga mostrando la habitación como "En reparación".

3. **¿Por qué se lista una habitación ocupada o en limpieza?**
   Porque sobre ella se puede programar un mantenimiento con fecha de inicio futura (*Programar Bloqueo Técnico para Habitación*, FR-001). Las habitaciones `Inactive` no se listan porque no admiten mantenimiento.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir el acceso al panel de mantenimiento únicamente a miembros del personal de mantenimiento autenticados (rol `MAINTENANCE_STAFF`). Si el miembro tiene una `ReparationTask` sin `EndDateTime`, el sistema DEBE redirigirlo a la vista de tarea activa (*Confirmar Fin de Reparación de Habitación*, FR-001) en lugar de mostrar el panel.
- **FR-002**: El sistema DEBE listar todas las habitaciones excepto las que están en estado `Inactive`.
- **FR-003**: Cada habitación listada DEBE mostrar: número de habitación, tipo (Sencilla, Doble, Suite o Boutique), estado con su nombre en español, mantenimiento programado y las acciones de su estado (FR-005). Una habitación en `TechnicalBlock` cuyo `TechnicalBlockReport` `Applied` tiene la fecha estimada de fin anterior a la fecha actual DEBE mostrarse como "Bloqueo técnico (vencido)", para indicar que debe confirmarse el fin de la reparación o programarse un nuevo mantenimiento.
- **FR-004**: El mantenimiento programado DEBE ser el rango del `TechnicalBlockReport` en estado `Scheduled` de fecha de inicio más temprana (formato DD-MM-YYYY a DD-MM-YYYY), o "Sin programar" si no tiene ninguno. Los informes `Expired` no se muestran.
- **FR-005**: El sistema DEBE ofrecer en cada habitación las acciones de su estado:
  - Todas las habitaciones listadas: **Programar mantenimiento** (*Programar Bloqueo Técnico para Habitación*).
  - `Available`: además **Reportar daño** (*Marcar Habitación Inhabilitada por Reparaciones*).
  - `DisabledForRepairs`: además **Ver informe** (reporte de daño, *Marcar Habitación Inhabilitada por Reparaciones* FR-007) e **Iniciar reparaciones** (*Confirmar Fin de Reparación de Habitación*, FR-011).
  - `TechnicalBlock`: además **Ver informe** (informe del bloqueo, *Programar Bloqueo Técnico para Habitación* FR-011) e **Iniciar reparaciones**.
  - Demás estados: **Ver informe** (informe del bloqueo) si la habitación tiene un mantenimiento programado.
  - **Iniciar reparaciones** solo aparece si la habitación no tiene una `ReparationTask` abierta; si la tiene, el sistema DEBE mostrar la etiqueta "En reparación".
  - Este caso de uso no reimplementa las reglas de las acciones que ejecuta.
- **FR-006**: El sistema DEBE ordenar el listado mostrando primero las habitaciones que requieren intervención (`DisabledForRepairs` y `TechnicalBlock`, con los bloqueos vencidos antes que los demás) y después el resto, cada grupo por número de habitación ascendente.
- **FR-007**: El sistema DEBE permitir buscar en el listado por número de habitación con coincidencia exacta, presentando el resultado con el mismo formato del listado. Al vaciar la búsqueda, el panel vuelve a mostrar el listado completo.
- **FR-008**: El sistema DEBE informar "No se encontró la habitación X" cuando la búsqueda no retorne resultados, y "No hay habitaciones para listar" cuando el panel no tenga habitaciones.
- **FR-009**: El sistema DEBE mostrar sobre el listado la cantidad de habitaciones inhabilitadas por reparaciones, la cantidad en bloqueo técnico (indicando cuántos están vencidos) y la cantidad con un mantenimiento programado.
- **FR-010**: El sistema DEBE actualizar el panel automáticamente cada 60 segundos y al volver a él después de ejecutar una acción. Si una acción falla porque la habitación cambió de estado o ya tiene una tarea abierta, el sistema DEBE informar el motivo y recargar el panel.
- **FR-011**: La consulta del panel DEBE ser de solo lectura: no cambia el estado de ninguna habitación ni crea o modifica tareas o informes.

### Entidades Clave

- **Room**: Habitación del hotel. El panel lista todas excepto las `Inactive`.
- **ReparationTask**: Entidad definida en *Confirmar Fin de Reparación de Habitación*. El panel la consulta para detectar la tarea activa del miembro y las habitaciones "En reparación".
- **TechnicalBlockReport**: Entidad definida en *Programar Bloqueo Técnico para Habitación*. El panel la consulta para el mantenimiento programado y el bloqueo vencido.
- **DamageReport**: Entidad definida en *Marcar Habitación Inhabilitada por Reparaciones*. Se consulta con la acción **Ver informe** de una habitación `DisabledForRepairs`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El panel carga en menos de 2 segundos.
- **SC-002**: El 100% de las habitaciones `Inactive` quedan fuera del panel y el 100% de las demás aparecen en él.
- **SC-003**: El 100% de las habitaciones en `DisabledForRepairs` o `TechnicalBlock` aparecen antes que las demás, y ninguna con una tarea abierta ofrece **Iniciar reparaciones**.
- **SC-004**: Una habitación que pasa a `DisabledForRepairs` aparece en el panel en menos de 60 segundos sin que el miembro recargue la página.
