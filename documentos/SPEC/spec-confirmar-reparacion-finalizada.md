# Especificación del Caso de Uso: Confirmar Fin de Reparación de Habitación

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Finalización de reparación y pase a limpieza (Prioridad: P1)

Como miembro del personal de mantenimiento quiero poder indicar que he finalizado las labores de reparación o revisión técnica en una habitación, para así poder formalizar mi participación en las labores definidas para mi área e indicar que la unidad se encuentra nuevamente disponible para ser asignada a labores de limpieza previo a su habilitación comercial.

**Por qué esta prioridad**: Es el cierre del ciclo de mantenimiento. Sin esta confirmación, la habitación queda bloqueada en el inventario por motivos técnicos, reduciendo la capacidad operativa del hotel y dejando la tarea del empleado abierta indefinidamente.

**Prueba independiente**: Puede ser probada indicando que se ha finalizado la reparación de una habitación en estado `DisabledForRepairs` o `TechnicalBlock` previamente asignada al usuario activo, verificando que el sistema la marca como `PendingCleaning`, registra la fecha y hora de finalización en la tarea del usuario, y retorna la vista del usuario al panel general de mantenimiento.

**Escenarios de aceptación**:

1. **Escenario**: Fin de reparación exitoso
   - **Dado que** El miembro del personal de mantenimiento está autenticado y tiene asignada activamente la habitación "101" cuyo estado es `DisabledForRepairs` (o `TechnicalBlock`)
   - **Cuando** El personal de mantenimiento confirma que ha finalizado las labores de reparación
   - **Entonces** El sistema transiciona la habitación a estado `PendingCleaning`, registra la hora de finalización y libera al usuario de la tarea activa.

2. **Escenario**: Habitación no está en proceso de reparación
   - **Dado que** El miembro del personal de mantenimiento está autenticado y la habitación "101" se encuentra en estado `Available`, `PendingCleaning` u `Occupied`
   - **Cuando** El miembro del personal de mantenimiento intenta ejecutar la acción de marcar el fin de labores de reparación sobre ella
   - **Entonces** El sistema rechaza la operación, indicando a través de un error que el estado actual no permite esta transición.

3. **Escenario**: Intento de finalización de tarea ajena (Validación de propiedad)
   - **Dado que** El miembro del personal de mantenimiento identificado como "Usuario A" está autenticado y la habitación "105" se encuentra en estado `DisabledForRepairs` pero la orden de reparación fue iniciada por el "Usuario B"
   - **Cuando** El "Usuario A" intenta confirmar el fin de labores de reparación en esa habitación
   - **Entonces** El sistema rechaza la operación por falta de autorización y muestra un error indicando que la tarea pertenece a otro miembro del personal de mantenimiento.

---

### Casos Límite

1. **¿Qué ocurre si el personal de mantenimiento confirma el fin, pero la habitación sigue presentando fallas físicas?**
   Este es un problema operativo de calidad que no puede ser validado técnicamente por el sistema en este punto. La responsabilidad recae en la supervisión de mantenimiento; el historial registrado permitirá auditar qué técnico cerró la tarea de forma deficiente.

2. **¿Qué ocurre si se confirma el fin de reparación pero se detecta una nueva avería distinta a la original?**
   El sistema debe permitir la confirmación de la reparación actual para cerrar el ciclo de la tarea activa. El reporte de la nueva avería representaría un flujo independiente que se podrá realizar posteriormente.

3. **¿Qué ocurre si la conexión a internet falla exactamente al momento de confirmar el fin de reparación?**
   La operación se ejecuta bajo control transaccional estricto (ACID), conforme a lo definido en el caso de uso *Marcar Pendiente a Limpieza*. Si ocurre un error de red, se ejecutará un *rollback* completo. La habitación permanecerá en `DisabledForRepairs` (o `TechnicalBlock`) y la tarea seguirá activa, y la retroalimentación visual sugerirá reintentar la operación notificando sobre el error de conexión ocurrido.

4. **¿Qué ocurre si la fecha y hora de finalización resultan anteriores al inicio de la reparación?**
   El sistema rechaza la operación, mantiene la habitación y la tarea sin cambios y muestra un error de inconsistencia temporal. Los timestamps siempre son generados por el servidor.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE proporcionar una vista alternativa al panel general de mantenimiento para restringir la acción de los miembros del personal con tareas activas vinculadas a la acción de **Confirmación de finalización de labores de reparación**.
   - **FR-001.1**: En la vista alternativa o vista de tarea activa únicamente debe estar presente la habitación cuyas labores activas estén vinculadas al usuario autenticado en dicha sesión, con sus datos operativos (número de habitación, tipo, estado actual).
   - **FR-001.2**: La cinta de opciones disponibles a realizar sobre la habitación debe ser limitada a la acción "Confirmar fin", que representa la intención inicial de confirmar la finalización de la reparación en la unidad habitacional.
- **FR-002**: El sistema DEBE permitir al personal de mantenimiento autenticado solicitar la finalización de sus labores y requerir confirmación explícita *(confirmación a través de un modal superpuesto indicando "¿Está seguro que desea finalizar las reparaciones de la habitación X?", advirtiendo que la acción trasladará la habitación a la cola de limpieza).*
- **FR-003**: El sistema DEBE validar estrictamente que la habitación objetivo se encuentre en estado `DisabledForRepairs` (o `TechnicalBlock`) Y que el usuario solicitante sea el titular de la tarea activa vinculada a dicha habitación.
- **FR-004**: El sistema DEBE delegar la transición de la habitación a `PendingCleaning` en el caso de uso *Marcar Pendiente a Limpieza*, invocándolo dentro del mismo contexto transaccional, sin reimplementar su lógica.
- **FR-005**: El sistema DEBE actualizar el registro de auditoría de la tarea (`ReparationTask`) que vincula al miembro del personal de mantenimiento asignado, la habitación, la fecha y hora de inicio y la fecha y hora exacta de finalización *(sin requerir input manual del usuario ni el diligenciamiento de informes adicionales)*.
- **FR-006**: El sistema DEBE rechazar cualquier petición que incumpla el **FR-003** y proveer retroalimentación visual inmediata *(notificación de error en la vista actual explicando si el fallo se debe al estado de la habitación o a un conflicto de asignación de la orden de trabajo).*
- **FR-007**: El sistema DEBE actualizar la interfaz del usuario tras una confirmación exitosa del fin de labores de reparación en una habitación, cambiando la vista de "tarea activa" y redirigiendo al miembro del personal al panel general de mantenimiento.
- **FR-008**: El sistema DEBE generar `EndDateTime` exclusivamente en el servidor, sin aceptar fechas u horas enviadas por el cliente.
- **FR-009**: El sistema DEBE validar que `EndDateTime` no sea nulo, no sea anterior a `StartDateTime` de la misma tarea y no sea posterior a la fecha y hora actual del servidor; de lo contrario DEBE ejecutar rollback, mantener la habitación en su estado actual y mostrar retroalimentación visual del error.
- **FR-010**: El sistema DEBE impedir que `StartDateTime` y `EndDateTime` sean modificados manualmente una vez registrados.

### Entidades Clave

- **Room**: Entidad que representa la habitación del hotel. Transita de `DisabledForRepairs` (o `TechnicalBlock`) a `PendingCleaning`.
- **ReparationTask**: Entidad que representa la vinculación del miembro del personal técnico con la labor que realizó, esta almacena:
   - **MaintenanceStaffMemberId**: Identificador del miembro del personal de mantenimiento
   - **RoomId**: Identificador de la habitación sobre la que se realizaron las labores
   - **StartDateTime**: TimeStamp del inicio de reparaciones - Formato DD-MM-YYYY HH:MM
   - **EndDateTime**: TimeStamp de la finalización de las reparaciones - Formato DD-MM-YYYY HH:MM

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El personal de mantenimiento puede confirmar el fin del proceso técnico desde su vista de tarea activa en menos de 3 clics, sin necesidad de redactar justificaciones.
- **SC-002**: La habitación es delegada a la cola operativa de limpieza (`PendingCleaning`) en menos de 2 segundos tras la confirmación.
- **SC-003**: El 100% de las confirmaciones cierran la entidad `ReparationTask` correspondiente con una marca de tiempo válida asociada al técnico responsable.
- **SC-004**: Cero registros de `ReparationTask` con `EndDateTime` anterior a `StartDateTime` o posterior a la hora del servidor.
