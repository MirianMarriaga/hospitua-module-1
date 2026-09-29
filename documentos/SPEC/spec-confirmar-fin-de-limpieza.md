# Especificación del Caso de Uso: Confirmar Fin de Limpieza de Habitación

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Finalización de limpieza y disponibilidad de habitación (Prioridad: P1)

Como miembro del personal de limpieza quiero poder confirmar que he completado las labores de limpieza en una habitación, es importante que pueda formalizar mi participación en las labores designadas a mi área y notificar esta acción al sistema de forma oportuna para evitar posibles inconsistencias en otras operaciones del hotel.

**Por qué esta prioridad**: Es el cierre del ciclo de limpieza. Sin esta confirmación, la habitación queda bloqueada en medio del flujo de limpieza y disminuye la capacidad operativa del hotel.

**Prueba independiente**: Puede ser probada confirmando el fin de limpieza de una habitación en estado `InCleaning`, verificando que el sistema la marca como `Available`, desvincula al miembro previamente vinculado a la labor, registra la fecha y hora de finalización de labores de limpieza, reintegra la habitación al inventario operativo y redirige al miembro del personal de limpieza previamente vinculado a la labor al panel general de limpieza.

**Escenarios de aceptación**:

1. **Escenario**: Fin de limpieza exitoso
   - **Dado que** El miembro del personal de limpieza está autenticado y existe una habitación "101" que está en proceso de limpieza y asignada a él
   - **Cuando** El personal de limpieza confirma la finalización de labores de limpieza
   - **Entonces** El sistema registra la finalización de labores de limpieza y marca la habitación como `Available`

2. **Escenario**: Habitación no está en proceso de limpieza
   - **Dado que** El miembro del personal de limpieza está autenticado y existe una habitación "101" que acaba de ser liberada, pero nadie la ha tomado para limpiar
   - **Cuando** El miembro del personal de limpieza intenta confirmar el fin de limpieza en la habitación
   - **Entonces** El sistema rechaza la operación indicando el estado actual de la habitación y que es una operación no válida

3. **Escenario**: Intento de finalización de tarea ajena
   - **Dado que** El miembro del personal de limpieza "Usuario A" está autenticado y la habitación "105" está en estado `InCleaning` pero la tarea fue iniciada por el "Usuario B"
   - **Cuando** El "Usuario A" intenta confirmar el fin de limpieza en esa habitación
   - **Entonces** El sistema rechaza la operación e indica que la tarea pertenece a otro miembro del personal de limpieza

---

### Casos Límite

1. **¿Qué ocurre si el miembro del personal de limpieza confirma el fin, pero la habitación evidentemente no está limpia?**
   Este es un problema operativo que no puede ser validado técnicamente por el sistema. La responsabilidad recae en la supervisión del gerente, quien puede consultar el historial de estados para auditar tiempos de limpieza inusuales.

2. **¿Qué ocurre si se confirma el fin de limpieza y la habitación tenía un daño reportado?**
   El sistema debe permitir la confirmación. Si existe un daño, este puede ser reportado posteriormente a la marcación de la finalización de labores de limpieza.

3. **¿Qué ocurre si la marca de fin resulta anterior a la marca de inicio de la tarea (ej. por inconsistencia de reloj o dato corrupto)?**
   El sistema rechaza la operación, mantiene la habitación en `InCleaning` y muestra un error de inconsistencia temporal. Los timestamps siempre son generados por el servidor.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al personal de limpieza autenticado confirmar el fin de limpieza y recibir retroalimentación visual de la acción que está realizando.
- **FR-002**: El sistema DEBE aceptar la confirmación únicamente desde habitaciones que se encuentren en estado `InCleaning` y asignadas al miembro del personal de limpieza autenticado.
- **FR-003**: El sistema DEBE cambiar el estado de la habitación a `Available` al procesar la confirmación.
- **FR-004**: El sistema DEBE registrar la fecha y hora de finalización de labores de limpieza (timestamp).
- **FR-005**: El sistema DEBE rechazar la confirmación si la habitación se encuentra en cualquier estado distinto de `InCleaning` o si la tarea pertenece a otro miembro del personal, informando el estado actual o el conflicto de asignación según corresponda.
- **FR-006**: El sistema DEBE evitar que dos solicitudes simultáneas de confirmación de fin sobre la misma habitación se procesen con éxito; solo la primera transacción es válida y la segunda recibe un error con el estado actual.
- **FR-007**: El sistema DEBE permitir el acceso a una vista exclusiva a los miembros del personal de limpieza con una tarea activa
   - La vista de tarea activa debe mostrar la habitación vinculada a la tarea y al miembro del personal de limpieza autenticado, debe ser listada usando el mismo formato designado para presentar las habitaciones en el panel general de limpieza
   - La cinta de opciones debe estar limitada a la confirmación del fin de labores de limpieza - `IC`: Confirmar fin
   - Una vez confirmado el fin de la tarea listada, el usuario debe ser desvinculado de la misma y redirigido al panel de limpieza general
- **FR-008**: El sistema DEBE generar el timestamp de finalización (`EndDateTime`) exclusivamente en el servidor, sin aceptar fechas u horas enviadas por el cliente.
- **FR-009**: El sistema DEBE validar que `EndDateTime` no sea nulo, no sea anterior a `StartDateTime` de la misma tarea y no sea posterior a la fecha y hora actual del servidor; de lo contrario DEBE rechazar la operación, mantener la habitación en `InCleaning` y mostrar retroalimentación visual del error.
- **FR-010**: El sistema DEBE impedir que `EndDateTime` sea modificado una vez registrado.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `InCleaning` a `Available`, completando el ciclo de limpieza.
- **CleaningTask**: Entidad definida en el caso de uso *Marcar Habitación en Limpieza* (`RoomId`, `CleaningStaffMemberId`, `StartDateTime`, `EndDateTime`). En este caso de uso se completa el campo `EndDateTime`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El miembro del personal de limpieza puede confirmar el fin de limpieza en una habitación en menos de 15 segundos.
- **SC-002**: El 100% de las confirmaciones registran correctamente la fecha y hora de finalización de labores de limpieza.
- **SC-003**: El cambio de estado se refleja en el inventario en menos de 2 segundos.
- **SC-004**: Cero registros de `CleaningTask` con `EndDateTime` anterior a `StartDateTime` o posterior a la hora del servidor.
