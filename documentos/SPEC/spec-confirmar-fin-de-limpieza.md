# Especificación del Caso de Uso: Confirmar Fin de Limpieza de Habitación

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Finalización de limpieza y disponibilidad de habitación (Prioridad: P1)

Como miembro del personal de limpieza quiero poder confirmar que he completado las labores de limpieza en una habitación, es importante que pueda formalizar mi participación en las labores designadas a mi área y notificar esta acción al sistema de forma oportuna para evitar posibles inconsistencias en otras operaciones del hotel.

**Por qué esta prioridad**: Es el cierre del ciclo de limpieza. Sin esta confirmación, la habitación queda bloqueada en medio del flujo de limpieza y disminuye la capacidad operativa del hotel.

**Prueba independiente**: Puede ser probada confirmando el fin de limpieza de una habitación en estado `InCleaning`, verificando que el sistema la marca como `Available` mediante la invocación del caso de uso *Marcar Habitación como Disponible* (o como `Reserved` si tiene una llegada pendiente hoy), desvincula al miembro previamente vinculado a la labor, registra la fecha y hora de finalización de labores de limpieza, reintegra la habitación al inventario operativo y redirige al miembro del personal de limpieza previamente vinculado a la labor al panel general de limpieza.

**Escenarios de aceptación**:

1. **Escenario**: Fin de limpieza exitoso
   - **Dado que** El miembro del personal de limpieza está autenticado y existe una habitación "101" que está en proceso de limpieza, asignada a él y sin llegada pendiente hoy
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

4. **Escenario**: Fin de limpieza con llegada pendiente hoy
   - **Dado que** El miembro del personal de limpieza está autenticado, la habitación "101" está en `InCleaning` asignada a él y la copia local de la lista del día contiene una reserva de hoy asignada a la habitación "101"
   - **Cuando** El personal de limpieza confirma la finalización de labores de limpieza
   - **Entonces** El sistema transiciona la habitación a `Available` y, en la misma transacción, la aparta automáticamente a `Reserved` vinculando la referencia de esa reserva, de modo que Recepción pueda registrar el check-in

5. **Escenario**: Fin de limpieza con reporte de daño
   - **Dado que** El miembro del personal de limpieza está autenticado y la habitación "101" está en `InCleaning` asignada a él
   - **Cuando** El personal de limpieza confirma la finalización de labores de limpieza diligenciando la descripción opcional de un daño encontrado
   - **Entonces** El sistema transiciona la habitación a `Available` y, en la misma transacción, la inhabilita a `DisabledForRepairs` registrando el reporte de daño; no la aparta a `Reserved` aunque tenga una llegada pendiente hoy (ver escenario 7)

6. **Escenario**: Liberación de la tarea activa
   - **Dado que** El miembro del personal de limpieza está autenticado y la habitación "101" está en `InCleaning` asignada a él
   - **Cuando** El miembro pulsa **Liberar tarea** en la vista de tarea activa y confirma la acción
   - **Entonces** El sistema cierra la `CleaningTask` con `EndDateTime` generado por el servidor y `Outcome` = `Released`, transiciona la habitación a `PendingCleaning` mediante el caso de uso *Marcar Pendiente a Limpieza* y redirige al miembro al panel de limpieza, donde cualquier miembro puede retomar la habitación

7. **Escenario**: Fin de limpieza con reporte de daño y llegada pendiente hoy
   - **Dado que** El miembro del personal de limpieza está autenticado, la habitación "101" está en `InCleaning` asignada a él y la copia local de la lista del día contiene la reserva "RES-101" de hoy asignada a la habitación "101"
   - **Cuando** El personal de limpieza confirma la finalización de labores de limpieza diligenciando la descripción de un daño encontrado
   - **Entonces** El sistema transiciona la habitación a `Available` y, en la misma transacción, la inhabilita a `DisabledForRepairs` sin apartarla a `Reserved`; la reserva "RES-101" queda sin habitación apartada y Recepción ve la alerta operativa de esa llegada en su panel (FR-019)

---

### Casos Límite

1. **¿Qué ocurre si el miembro del personal de limpieza confirma el fin, pero la habitación evidentemente no está limpia?**
   Este es un problema operativo que no puede ser validado técnicamente por el sistema. La responsabilidad recae en la supervisión del gerente, quien puede consultar el historial de estados para auditar tiempos de limpieza inusuales.

2. **¿Qué ocurre si durante la limpieza se encuentra un daño en la habitación?**
   El miembro del personal de limpieza lo reporta en la misma confirmación de fin de limpieza diligenciando la descripción opcional del daño (HU-1, escenario 5). Así el reporte no depende de que la habitación quede en `Available` después de la confirmación, lo que no ocurre si se aparta a `Reserved` por una llegada pendiente. Si la habitación tenía una llegada pendiente hoy, no se aparta y Recepción ve la alerta operativa de esa llegada (HU-1, escenario 7; FR-019).

3. **¿Qué ocurre si la marca de fin resulta anterior a la marca de inicio de la tarea (ej. por inconsistencia de reloj o dato corrupto)?**
   El sistema rechaza la operación, mantiene la habitación en `InCleaning` y muestra un error de inconsistencia temporal. Los timestamps siempre son generados por el servidor.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al personal de limpieza autenticado confirmar el fin de limpieza y recibir retroalimentación visual de la acción que está realizando.
- **FR-002**: El sistema DEBE aceptar la confirmación únicamente desde habitaciones que se encuentren en estado `InCleaning` y asignadas al miembro del personal de limpieza autenticado.
- **FR-003**: El sistema DEBE delegar la transición de `InCleaning` a `Available` en el caso de uso *Marcar Habitación como Disponible* (HU-2, FR-003), invocándolo dentro de la misma transacción, sin reimplementar su lógica.
- **FR-004**: El sistema DEBE registrar la fecha y hora de finalización de labores de limpieza (timestamp).
- **FR-005**: El sistema DEBE rechazar la confirmación si la habitación se encuentra en cualquier estado distinto de `InCleaning` o si la tarea pertenece a otro miembro del personal, informando el estado actual o el conflicto de asignación según corresponda.
- **FR-006**: El sistema DEBE evitar que dos solicitudes simultáneas de confirmación de fin sobre la misma habitación se procesen con éxito; solo la primera transacción es válida y la segunda recibe un error con el estado actual.
- **FR-007**: El sistema DEBE permitir el acceso a una vista exclusiva a los miembros del personal de limpieza con una tarea activa
  - La vista de tarea activa debe mostrar la habitación vinculada a la tarea y al miembro del personal de limpieza autenticado, debe ser listada usando el mismo formato designado para presentar las habitaciones en el panel general de limpieza
  - La cinta de opciones debe estar limitada a la confirmación del fin de labores de limpieza y a la liberación de la tarea - `IC`: Confirmar fin, Liberar tarea
  - Una vez confirmado el fin o liberada la tarea listada, el usuario debe ser desvinculado de la misma y redirigido al panel de limpieza general
- **FR-008**: El sistema DEBE generar el timestamp de finalización (`EndDateTime`) exclusivamente en el servidor, sin aceptar fechas u horas enviadas por el cliente.
- **FR-009**: El sistema DEBE validar que `EndDateTime` no sea nulo, no sea anterior a `StartDateTime` de la misma tarea y no sea posterior a la fecha y hora actual del servidor; de lo contrario DEBE rechazar la operación, mantener la habitación en `InCleaning` y mostrar retroalimentación visual del error.
- **FR-010**: El sistema DEBE impedir que `EndDateTime` sea modificado una vez registrado.
- **FR-011**: Tras la transición a `Available`, y solo si no se reportó un daño (FR-012), el sistema DEBE verificar en la copia local de la lista del día (`daily_reservation_room`) si contiene una reserva asignada a la habitación; si es así, DEBE invocar dentro de la misma transacción el caso de uso *Marcar habitación como reservada*, que transiciona la habitación a `Reserved` asignando `reservedByReservationRef`. Esta transición es autónoma del Módulo 1 y no es seleccionable por el usuario.
- **FR-012**: El sistema DEBE permitir, en la confirmación del fin de limpieza, diligenciar de forma opcional una descripción del daño encontrado, con las mismas reglas de obligatoriedad y longitud máxima (500 caracteres) del caso de uso *Marcar Habitación Inhabilitada por Reparaciones*. Si se diligencia, el sistema DEBE invocar ese caso de uso inmediatamente después de la transición a `Available` y dentro de la misma transacción, dejando la habitación en `DisabledForRepairs`; si la invocación falla, DEBE ejecutar rollback completo y mantener la habitación en `InCleaning`.
- **FR-013**: El sistema DEBE registrar en `RoomStateHistory`, dentro de la misma transacción, cada transición ejecutada en este flujo (`InCleaning` → `Available` y, cuando aplique, `Available` → `DisabledForRepairs`), cerrando el periodo anterior y abriendo el nuevo con `ActorId` = miembro del personal de limpieza y `SourceFlow` = *Confirmar Fin de Limpieza de Habitación*. La transición `Available` → `Reserved` se registra a través del caso de uso *Marcar habitación como reservada*. La transición `InCleaning` → `PendingCleaning` de la liberación (FR-014) se registra a través del caso de uso *Marcar Pendiente a Limpieza*.
- **FR-014**: El sistema DEBE permitir al miembro del personal de limpieza vinculado a una `CleaningTask` activa liberarla desde la vista de tarea activa (acción **Liberar tarea**), previa confirmación explícita *(modal superpuesto indicando "¿Está seguro que desea liberar la limpieza de la habitación X?", advirtiendo que la habitación volverá a la cola de limpieza)*. Al confirmar, y dentro de una sola transacción, el sistema DEBE cerrar la tarea con `EndDateTime` (con las reglas de FR-008 y FR-009) y `Outcome` = `Released`, y delegar la transición de `InCleaning` a `PendingCleaning` en el caso de uso *Marcar Pendiente a Limpieza*. La liberación NO DEBE ejecutar la verificación de llegadas (FR-011) ni el reporte de daño (FR-012), y DEBE rechazarse si la tarea pertenece a otro miembro.
- **FR-015**: El sistema DEBE asignar el `Outcome` de la `CleaningTask` al confirmar el fin de limpieza: `Completed` cuando no se reporta daño y `DamageReported` cuando se usa FR-012.
- **FR-016**: El sistema DEBE evitar que dos solicitudes simultáneas de confirmación de fin y/o liberación sobre la misma tarea se procesen con éxito; solo la primera transacción es válida y la segunda recibe un error con el estado actual de la habitación.
- **FR-017**: El sistema DEBE ejecutar la confirmación de fin o la liberación de la tarea (cierre de la `CleaningTask`, transiciones y registros en `RoomStateHistory`) dentro de una única transacción; si falla la persistencia o la conexión durante el procesamiento, el sistema DEBE ejecutar rollback completo, mantener la habitación en su estado previo (`InCleaning`) con la tarea activa, no crear ni modificar registros y mostrar retroalimentación visual del error con la sugerencia de reintentar la operación.
- **FR-018**: El sistema DEBE requerir confirmación explícita antes de procesar el fin de limpieza *(modal superpuesto indicando "¿Está seguro que desea finalizar la limpieza de la habitación X?", con un campo de texto opcional "Describir daño encontrado" de máximo 500 caracteres)*. Si el campo se deja vacío, el sistema DEBE procesar el fin de limpieza sin reporte de daño (FR-003, FR-011); si se diligencia, DEBE aplicar FR-012 y advertir en el mismo modal que la habitación quedará inhabilitada por reparaciones. Cancelar el modal NO DEBE modificar la tarea ni la habitación.
- **FR-019**: Cuando se reporta un daño (FR-012) y la copia local de la lista del día (`daily_reservation_room`) contiene una reserva asignada a la habitación, el sistema NO DEBE apartarla a `Reserved`: la habitación queda en `DisabledForRepairs` y la reserva queda sin habitación apartada. Recepción lo ve con la alerta operativa de su panel ("No disponible: Inhabilitada por reparaciones"), que el panel calcula a partir del estado de la habitación (*Consultar panel de recepción* FR-005); este caso de uso no envía mensajes a Recepción ni a Módulo 2 (regla 3 de la máquina de estados).

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `InCleaning` a `Available`, completando el ciclo de limpieza, o de `InCleaning` a `PendingCleaning` cuando se libera la tarea.
- **CleaningTask**: Entidad definida en el caso de uso *Marcar Habitación en Limpieza* (`RoomId`, `CleaningStaffMemberId`, `StartDateTime`, `EndDateTime`, `Outcome`). En este caso de uso se completan los campos `EndDateTime` y `Outcome`.
- **DamageReport**: Entidad definida en el caso de uso *Marcar Habitación Inhabilitada por Reparaciones*. Se crea solo cuando se diligencia la descripción opcional del daño (FR-012).
- **RoomStateHistory**: Historial común de estados de la habitación. En este caso de uso se cierra el periodo `InCleaning` y se abren los periodos de las transiciones ejecutadas.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El miembro del personal de limpieza puede confirmar el fin de limpieza en una habitación en menos de 15 segundos.
- **SC-002**: El 100% de las confirmaciones registran correctamente la fecha y hora de finalización de labores de limpieza.
- **SC-003**: El cambio de estado se refleja en el inventario en menos de 2 segundos.
- **SC-004**: Cero registros de `CleaningTask` con `EndDateTime` anterior a `StartDateTime` o posterior a la hora del servidor.
- **SC-005**: El 100% de las confirmaciones con daño reportado sobre habitaciones con llegada pendiente hoy dejan la habitación en `DisabledForRepairs` sin apartarla y generan la alerta operativa a Recepción.
