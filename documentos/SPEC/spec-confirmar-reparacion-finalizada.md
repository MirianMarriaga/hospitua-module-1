# Especificación del Caso de Uso: Confirmar Fin de Reparación de Habitación

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Finalización de reparación y pase a limpieza (Prioridad: P1)

Como miembro del personal de mantenimiento quiero poder indicar que he finalizado las labores de reparación o revisión técnica en una habitación, para así poder formalizar mi participación en las labores definidas para mi área e indicar que la unidad se encuentra nuevamente disponible para ser asignada a labores de limpieza previo a su habilitación comercial.

**Por qué esta prioridad**: Es el cierre del ciclo de mantenimiento. Sin esta confirmación, la habitación queda bloqueada en el inventario por motivos técnicos, reduciendo la capacidad operativa del hotel y dejando la tarea del empleado abierta indefinidamente.

**Prueba independiente**: Puede ser probada indicando que se ha finalizado la reparación de una habitación en estado `DisabledForRepairs` o `TechnicalBlock` previamente asignada al usuario activo, verificando que el sistema la marca como `PendingCleaning`, registra la fecha y hora de finalización en la tarea del usuario, y retorna la vista del usuario al panel general de mantenimiento.

**Escenarios de aceptación**:

1. **Escenario**: Fin de reparación exitoso
   - **Dado que** El miembro del personal de mantenimiento tiene asignada activamente la habitación "101" cuyo estado es `DisabledForRepairs` (o `TechnicalBlock`)
   - **Cuando** El personal de mantenimiento confirma que ha finalizado las labores de reparación
   - **Entonces** El sistema transiciona la habitación a estado `PendingCleaning`, registra la hora de finalización y libera al usuario de la tarea activa.

2. **Escenario**: Tarea ya cerrada
   - **Dado que** La `ReparationTask` del miembro del personal de mantenimiento sobre la habitación "101" ya fue confirmada o liberada (por ejemplo, desde otra pestaña)
   - **Cuando** El miembro intenta confirmar el fin de labores de reparación o liberar esa tarea
   - **Entonces** El sistema rechaza la operación con un error de tarea ya cerrada (`TASK_ALREADY_CLOSED`) y no modifica la habitación ni la tarea.

3. **Escenario**: Intento de finalización de tarea ajena (Validación de propiedad)
   - **Dado que** El miembro del personal de mantenimiento es "Usuario A" y la habitación "105" se encuentra en estado `DisabledForRepairs` pero la orden de reparación fue iniciada por el "Usuario B"
   - **Cuando** El "Usuario A" intenta confirmar el fin de labores de reparación en esa habitación
   - **Entonces** El sistema rechaza la operación y muestra un error indicando que la tarea pertenece a otro miembro del personal de mantenimiento.

4. **Escenario**: Liberación de la tarea activa
   - **Dado que** El miembro del personal de mantenimiento tiene asignada activamente la habitación "101" en `DisabledForRepairs` (o `TechnicalBlock`)
   - **Cuando** El miembro pulsa **Liberar tarea** en la vista de tarea activa y confirma la acción
   - **Entonces** El sistema cierra la `ReparationTask` con `EndDateTime` generado por el servidor y `Outcome` = `Released`, mantiene el estado de la habitación y su informe sin cambios y redirige al miembro al panel de mantenimiento, donde la acción **Iniciar reparaciones** vuelve a estar disponible para cualquier miembro

5. **Escenario**: Aviso a Módulo 2 y cierre del informe al confirmar el fin de reparación
   - **Dado que** El miembro del personal de mantenimiento tiene asignada activamente la habitación "101" en `DisabledForRepairs` (o en `TechnicalBlock` con su informe `Applied`)
   - **Cuando** El personal de mantenimiento confirma que ha finalizado las labores de reparación
   - **Entonces** El sistema, dentro de la misma transacción, avisa a Módulo 2 que la habitación vuelve a estar disponible: la cancelación del mantenimiento del `DamageReport` si estaba en `DisabledForRepairs`, o, si estaba en `TechnicalBlock`, marca el `TechnicalBlockReport` como `Completed` y avisa el rango real (del inicio del informe a la fecha de cierre) cuando la fecha de cierre es distinta de la estimada (FR-016, FR-023)

---

### Historia de Usuario 2 - Iniciar reparaciones desde el panel de mantenimiento (Prioridad: P1)

Como miembro del personal de mantenimiento quiero poder indicar que voy a intervenir una habitación inhabilitada o en bloqueo técnico, para así quedar vinculado formalmente a la labor y evitar que otro miembro del personal intervenga la misma habitación al mismo tiempo.

**Por qué esta prioridad**: Sin el inicio de la intervención no existe la tarea activa que exige la confirmación del fin de reparación (HU-1), por lo que la habitación quedaría bloqueada indefinidamente en `DisabledForRepairs` o `TechnicalBlock`.

**Prueba independiente**: Puede ser probada seleccionando en el panel de mantenimiento una habitación en estado `DisabledForRepairs` o `TechnicalBlock` sin tarea abierta, pulsando **Iniciar reparaciones** y verificando que el sistema crea la `ReparationTask` con la fecha y hora de inicio del servidor, mantiene el estado de la habitación y redirige al miembro del personal a la vista de tarea activa.

**Escenarios de aceptación**:

6. **Escenario**: Inicio de reparaciones exitoso
   - **Dado que** El miembro del personal de mantenimiento no tiene una tarea activa y la habitación "101" está en `DisabledForRepairs` (o `TechnicalBlock`) sin tarea abierta
   - **Cuando** El miembro del personal pulsa **Iniciar reparaciones** sobre la habitación "101"
   - **Entonces** El sistema crea la `ReparationTask` vinculando al miembro y a la habitación con `StartDateTime` generado por el servidor, mantiene el estado de la habitación y redirige al miembro a la vista de tarea activa

7. **Escenario**: Habitación con una tarea abierta por otro miembro
   - **Dado que** La habitación "101" está en `DisabledForRepairs` y tiene una `ReparationTask` abierta del "Usuario B"
   - **Cuando** El "Usuario A" intenta iniciar reparaciones sobre la habitación "101"
   - **Entonces** El sistema rechaza la operación indicando que la habitación ya está siendo intervenida por otro miembro del personal

8. **Escenario**: Miembro con una tarea activa
   - **Dado que** El miembro del personal de mantenimiento tiene una `ReparationTask` abierta sobre la habitación "101"
   - **Cuando** Intenta iniciar reparaciones sobre la habitación "105"
   - **Entonces** El sistema rechaza la operación indicando que ya tiene una tarea activa y lo redirige a la vista de tarea activa

9. **Escenario**: Habitación en un estado no válido
   - **Dado que** La habitación "101" está en un estado distinto de `DisabledForRepairs` o `TechnicalBlock`
   - **Cuando** El miembro del personal de mantenimiento intenta iniciar reparaciones sobre ella
   - **Entonces** El sistema rechaza la operación e informa el estado actual de la habitación

---

### Historia de Usuario 3 - Liberación de la tarea al desactivar la cuenta del miembro (Prioridad: P2)

Como Administrador quiero que, al desactivar la cuenta de un miembro del personal de mantenimiento, su tarea de reparación abierta quede liberada, para así que otro miembro pueda retomar la habitación sin que quede vinculada a una cuenta que ya no puede iniciar sesión.

**Por qué esta prioridad**: Sin la liberación, la habitación quedaría con una tarea abierta que nadie puede cerrar y ningún otro miembro podría iniciar reparaciones sobre ella (FR-012), dejándola fuera de servicio indefinidamente.

**Prueba independiente**: Puede ser probada como Administrador desactivando en *Gestionar personal* la cuenta de un miembro del personal de mantenimiento con una `ReparationTask` abierta, verificando que la tarea queda cerrada con `Outcome` = `Released`, que la habitación sigue en su estado (`DisabledForRepairs` o `TechnicalBlock`) con su informe sin cambios y que la acción **Iniciar reparaciones** vuelve a estar disponible para cualquier miembro.

**Escenarios de aceptación**:

10. **Escenario**: Liberación de la tarea por desactivación de la cuenta del miembro
   - **Dado que** La habitación "101" está en `DisabledForRepairs` (o `TechnicalBlock`) con una `ReparationTask` abierta del "Usuario A"
   - **Cuando** El Administrador desactiva la cuenta del "Usuario A" en *Gestionar personal*
   - **Entonces** El sistema, en la misma transacción de la desactivación, cierra la `ReparationTask` con `EndDateTime` generado por el servidor y `Outcome` = `Released`, sin modificar el estado de la habitación ni su informe, sin generar registros en `RoomStateHistory` y sin avisar a Módulo 2 (FR-017, FR-019, FR-023; caso límite 5)

---

### Casos Límite

1. **¿Qué ocurre si el personal de mantenimiento confirma el fin, pero la habitación sigue presentando fallas físicas?**
   Este es un problema operativo de calidad que no puede ser validado técnicamente por el sistema en este punto. La responsabilidad recae en la supervisión de mantenimiento; el historial registrado permitirá auditar qué técnico cerró la tarea de forma deficiente.

2. **¿Qué ocurre si se confirma el fin de reparación pero se detecta una nueva avería distinta a la original?**
   El sistema debe permitir la confirmación de la reparación actual para cerrar el ciclo de la tarea activa. El reporte de la nueva avería representaría un flujo independiente que se podrá realizar posteriormente.

3. **¿Qué ocurre si la conexión a internet falla exactamente al momento de confirmar el fin de reparación?**
   La operación se ejecuta bajo control transaccional estricto (ACID), conforme a lo definido en el caso de uso *Marcar Pendiente a Limpieza*. Si ocurre un error de red, se ejecutará un *rollback* completo. La habitación permanecerá en `DisabledForRepairs` (o `TechnicalBlock`) y la tarea seguirá activa, y la retroalimentación visual sugerirá reintentar la operación notificando sobre el error de conexión ocurrido.

4. **¿Qué ocurre si la fecha y hora de finalización resultan anteriores al inicio de la reparación?**
   No puede ocurrir: `EndDateTime` es la marca del servidor de la transición de cierre y nunca es anterior a `StartDateTime` (invariante, FR-010).

5. **¿Qué ocurre si el miembro del personal de mantenimiento no puede terminar una tarea activa?**
   El miembro titular puede liberarla desde la vista de tarea activa (HU-1, escenario 4) para que otro miembro la retome con **Iniciar reparaciones**. Si el miembro abandona la tarea sin liberarla, la habitación permanece en su estado y la tarea abierta; ese caso queda fuera del alcance del módulo, salvo la liberación automática al desactivar la cuenta del miembro (*Gestionar personal*).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE proporcionar una vista alternativa al panel general de mantenimiento para restringir la acción de los miembros del personal con tareas activas vinculadas a la acción de **Confirmación de finalización de labores de reparación**.
- **FR-002**: En la vista de tarea activa (FR-001) únicamente DEBE estar presente la habitación cuyas labores activas estén vinculadas al usuario actual, con sus datos operativos (número de habitación, tipo, estado actual).
- **FR-003**: La cinta de opciones disponibles a realizar sobre la habitación en la vista de tarea activa (FR-001) DEBE limitarse a las acciones "Confirmar fin", que representa la intención inicial de confirmar la finalización de la reparación en la unidad habitacional, y "Liberar tarea" (FR-016).
- **FR-004**: El sistema DEBE permitir al personal de mantenimiento solicitar la finalización de sus labores y requerir confirmación explícita *(confirmación a través de un modal superpuesto indicando "¿Está seguro que desea finalizar las reparaciones de la habitación X?", advirtiendo que la acción trasladará la habitación a la cola de limpieza).*
- **FR-005**: El sistema DEBE validar estrictamente que el usuario solicitante sea el titular de la tarea activa vinculada a la habitación y que la tarea siga abierta; que la habitación esté en `DisabledForRepairs` (o `TechnicalBlock`) es una invariante de la tarea abierta. Si la petición incumple esta validación, el sistema DEBE rechazarla y proveer retroalimentación visual inmediata *(notificación de error en la vista actual que distingue si la tarea pertenece a otro miembro o ya fue cerrada)*.
- **FR-006**: El sistema DEBE delegar la transición de la habitación a `PendingCleaning` en el caso de uso *Marcar Pendiente a Limpieza*, invocándolo dentro del mismo contexto transaccional, sin reimplementar su lógica.
- **FR-007**: El sistema DEBE actualizar el registro de auditoría de la tarea (`ReparationTask`) que vincula al miembro del personal de mantenimiento asignado, la habitación, la fecha y hora de inicio y la fecha y hora exacta de finalización *(sin requerir input manual del usuario ni el diligenciamiento de informes adicionales)*.
- **FR-008**: El sistema DEBE actualizar la interfaz del usuario tras una confirmación exitosa del fin de labores de reparación en una habitación, cambiando la vista de "tarea activa" y redirigiendo al miembro del personal al panel general de mantenimiento.
- **FR-009**: El sistema DEBE generar `StartDateTime` y `EndDateTime` exclusivamente en el servidor, sin aceptar fechas u horas enviadas por el cliente, y DEBE impedir que se modifiquen una vez registrados.
- **FR-010**: El sistema DEBE garantizar como invariante que `EndDateTime` sea la marca del servidor de la transición de cierre y nunca sea anterior a `StartDateTime` de la misma tarea.
- **FR-011**: El sistema DEBE permitir al personal de mantenimiento iniciar reparaciones (acción **Iniciar reparaciones**) sobre una habitación en estado `DisabledForRepairs` o `TechnicalBlock`, creando una `ReparationTask` con `StartDateTime` generado exclusivamente por el servidor, sin modificar el estado de la habitación, y redirigiendo al miembro a la vista de tarea activa (FR-001).
- **FR-012**: El sistema DEBE rechazar el inicio de reparaciones si la habitación ya tiene una `ReparationTask` sin `EndDateTime`, si el miembro que realiza la acción ya tiene una `ReparationTask` sin `EndDateTime` o si la habitación no se encuentra en `DisabledForRepairs` o `TechnicalBlock`, informando el motivo; ante dos solicitudes simultáneas sobre la misma habitación, solo la primera DEBE procesarse con éxito.
- **FR-013**: Al confirmar el fin de reparación de una habitación en `TechnicalBlock`, el sistema DEBE marcar como `Completed`, dentro de la misma transacción, el `TechnicalBlockReport` en estado `Applied` de esa habitación.
- **FR-014**: El sistema DEBE permitir liberar una `ReparationTask` abierta únicamente a su miembro titular, desde la vista de tarea activa (FR-016), salvo la liberación automática al desactivar la cuenta del miembro (*Gestionar personal*); el sistema NO DEBE permitir transferir la tarea a un miembro específico ni liberar tareas de otros miembros.
- **FR-015**: El sistema DEBE registrar la transición a `PendingCleaning` en `RoomStateHistory` a través del caso de uso *Marcar Pendiente a Limpieza* (FR-004), indicando como `ActorId` al miembro del personal de mantenimiento titular de la tarea y como `SourceFlow` *Confirmar Fin de Reparación de Habitación*. El inicio de reparaciones (FR-011) no genera registros en `RoomStateHistory`, ya que no cambia el estado de la habitación.
- **FR-016**: El sistema DEBE permitir liberar una `ReparationTask` abierta (FR-014) desde la vista de tarea activa (acción **Liberar tarea**), previa confirmación explícita *(modal superpuesto indicando "¿Está seguro que desea liberar la reparación de la habitación X?", advirtiendo que otro miembro podrá retomarla)*. Al confirmar, el sistema DEBE cerrar la tarea con `EndDateTime` (con las reglas de FR-009 y FR-010) y `Outcome` = `Released`, sin modificar el estado de la habitación, sin generar registros en `RoomStateHistory` y manteniendo el `TechnicalBlockReport` en `Applied` cuando aplique, y DEBE redirigir al miembro al panel de mantenimiento.
- **FR-017**: El sistema DEBE asignar `Outcome` = `Completed` a la `ReparationTask` al confirmar el fin de reparación.
- **FR-018**: El sistema DEBE evitar que dos solicitudes simultáneas de confirmación de fin y/o liberación sobre la misma tarea se procesen con éxito; solo la primera transacción es válida y la segunda recibe un error de tarea ya cerrada (`TASK_ALREADY_CLOSED`).
- **FR-019**: Al confirmar el fin de reparación (FR-006), el sistema DEBE avisar a Módulo 2, dentro de la misma transacción, que la habitación vuelve a estar disponible: cancelación del mantenimiento del `DamageReport` si estaba en `DisabledForRepairs`, o cambio al rango real (inicio del informe a fecha de cierre) si estaba en `TechnicalBlock` y la fecha de cierre es distinta de la estimada; iniciar reparaciones y liberar la tarea no avisan.

### Entidades Clave

- **Room**: Entidad que representa la habitación del hotel. Transita de `DisabledForRepairs` (o `TechnicalBlock`) a `PendingCleaning`.
- **ReparationTask**: Entidad que representa la vinculación del miembro del personal técnico con la labor que realizó, esta almacena:
  - **MaintenanceStaffMemberId**: Identificador del miembro del personal de mantenimiento
  - **RoomId**: Identificador de la habitación sobre la que se realizaron las labores
  - **StartDateTime**: TimeStamp del inicio de reparaciones - Formato DD-MM-YYYY HH:MM
  - **EndDateTime**: TimeStamp de la finalización de las reparaciones o de la liberación de la tarea - Formato DD-MM-YYYY HH:MM
  - **Outcome**: Resultado de la tarea, asignado al cerrarla - `Completed` (reparación confirmada) o `Released` (tarea liberada sin terminar); nulo mientras la tarea está abierta
- **TechnicalBlockReport**: Entidad definida en el caso de uso *Programar Bloqueo Técnico para Habitación*. En este caso de uso se consulta para la acción **Ver informe**, y pasa a `Completed` al confirmar el fin de reparación de una habitación en `TechnicalBlock`.
- **DamageReport**: Entidad definida en el caso de uso *Marcar Habitación Inhabilitada por Reparaciones*. En este caso de uso se consulta mediante la acción **Ver informe**.
- **RoomStateHistory**: Historial común de estados de la habitación. El periodo `PendingCleaning` se abre a través de *Marcar Pendiente a Limpieza*.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El personal de mantenimiento puede confirmar el fin del proceso técnico desde su vista de tarea activa en menos de 3 clics, sin necesidad de redactar justificaciones (verificación manual, UAT).
- **SC-002**: La habitación es delegada a la cola operativa de limpieza (`PendingCleaning`) en menos de 2 segundos tras la confirmación.
- **SC-003**: El 100% de las confirmaciones cierran la entidad `ReparationTask` correspondiente con una marca de tiempo válida asociada al técnico responsable.
- **SC-004**: Cero registros de `ReparationTask` con `EndDateTime` anterior a `StartDateTime` (invariante de FR-010).
