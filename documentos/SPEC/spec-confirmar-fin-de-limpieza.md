# Especificación del Caso de Uso: Confirmar Fin de Limpieza de Habitación

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Finalización de limpieza y disponibilidad de habitación (Prioridad: P1)

Como miembro del personal de limpieza quiero poder confirmar que he completado las labores de limpieza en una habitación, es importante que pueda formalizar mi participación en las labores designadas a mi área y notificar esta acción al sistema de forma oportuna para evitar posibles inconsistencias en otras operaciones del hotel.

**Por qué esta prioridad**: Es el cierre del ciclo de limpieza. Sin esta confirmación, la habitación queda bloqueada en medio del flujo de limpieza y disminuye la capacidad operativa del hotel.

**Prueba independiente**: Puede ser probada confirmando el fin de limpieza de una habitación en estado `InCleaning`, verificando que el sistema la marca como `Available` mediante la invocación del caso de uso *Marcar Habitación como Disponible* (o como `Reserved` si tiene una llegada pendiente hoy), desvincula al miembro previamente vinculado a la labor, registra la fecha y hora de finalización de labores de limpieza, reintegra la habitación al inventario operativo y redirige al miembro del personal de limpieza previamente vinculado a la labor al panel general de limpieza.

**Escenarios de aceptación**:

1. **Escenario**: Fin de limpieza exitoso
   - **Dado que** Existe una habitación "101" que está en proceso de limpieza, asignada al miembro del personal de limpieza y sin llegada pendiente hoy
   - **Cuando** El personal de limpieza confirma la finalización de labores de limpieza
   - **Entonces** El sistema registra la finalización de labores de limpieza y marca la habitación como `Available`

2. **Escenario**: Intento de finalización de tarea ajena
   - **Dado que** El miembro del personal de limpieza es "Usuario A" y la habitación "105" está en estado `InCleaning` pero la tarea fue iniciada por el "Usuario B"
   - **Cuando** El "Usuario A" intenta confirmar el fin de limpieza en esa habitación
   - **Entonces** El sistema rechaza la operación e indica que la tarea pertenece a otro miembro del personal de limpieza

3. **Escenario**: Fin de limpieza con llegada pendiente hoy
   - **Dado que** La habitación "101" está en `InCleaning` asignada al miembro del personal de limpieza y la copia local de la lista del día contiene una llegada pendiente para la habitación "101" (ver *Llegada pendiente* en el diccionario)
   - **Cuando** El personal de limpieza confirma la finalización de labores de limpieza
   - **Entonces** El sistema transiciona la habitación a `Available` y, en la misma transacción, la aparta automáticamente a `Reserved` vinculando la referencia de esa reserva, de modo que Recepción pueda registrar el check-in

4. **Escenario**: Fin de limpieza con reporte de daño
   - **Dado que** La habitación "101" está en `InCleaning` asignada al miembro del personal de limpieza
   - **Cuando** El personal de limpieza confirma la finalización de labores de limpieza diligenciando la descripción opcional de un daño encontrado
   - **Entonces** El sistema transiciona la habitación a `Available` y, en la misma transacción, la inhabilita a `DisabledForRepairs` registrando el reporte de daño; no la aparta a `Reserved` aunque tenga una llegada pendiente hoy (ver escenario 6)

5. **Escenario**: Liberación de la tarea activa
   - **Dado que** La habitación "101" está en `InCleaning` asignada al miembro del personal de limpieza
   - **Cuando** El miembro pulsa **Liberar tarea** en la vista de tarea activa y confirma la acción
   - **Entonces** El sistema, en una sola transacción, cierra la `CleaningTask` con `EndDateTime` generado por el servidor y `Outcome` = `Released` y transiciona la habitación a `PendingCleaning` mediante el caso de uso *Marcar Pendiente a Limpieza*; después redirige al miembro al panel de limpieza, donde cualquier miembro puede retomar la habitación

6. **Escenario**: Fin de limpieza con reporte de daño y llegada pendiente hoy
   - **Dado que** La habitación "101" está en `InCleaning` asignada al miembro del personal de limpieza y la copia local de la lista del día contiene la reserva "RES-101" como llegada pendiente para la habitación "101" (ver *Llegada pendiente* en el diccionario)
   - **Cuando** El personal de limpieza confirma la finalización de labores de limpieza diligenciando la descripción de un daño encontrado
   - **Entonces** El sistema transiciona la habitación a `Available` y, en la misma transacción, la inhabilita a `DisabledForRepairs` sin apartarla a `Reserved`; la reserva "RES-101" queda sin habitación apartada y Recepción ve la alerta operativa de esa llegada en su panel (FR-019)

7. **Escenario**: Fin de limpieza con un mantenimiento programado que inicia o está en curso hoy
   - **Dado que** La habitación "101" está en `InCleaning` asignada al miembro del personal de limpieza, sin llegada pendiente hoy, y tiene un `TechnicalBlockReport` en `Scheduled` cuyo rango contiene la fecha actual (por ejemplo, porque a las 00:00 seguía ocupada por un huésped que salió ese día)
   - **Cuando** El personal de limpieza confirma la finalización de labores de limpieza sin describir un daño
   - **Entonces** El sistema transiciona la habitación a `Available` y, en la misma transacción, le aplica el bloqueo técnico: la habitación pasa a `TechnicalBlock` y el informe a `Applied`, de modo que el Personal de mantenimiento puede iniciar las reparaciones ese mismo día (FR-020)

8. **Escenario**: Liberación de la tarea por desactivación de la cuenta del miembro
   - **Dado que** La habitación "101" está en `InCleaning` con una tarea abierta del "Usuario A"
   - **Cuando** El Administrador desactiva la cuenta del "Usuario A" en *Gestionar personal*
   - **Entonces** El sistema, en la misma transacción de la desactivación, cierra la `CleaningTask` con `Outcome` = `Released` y transiciona la habitación a `PendingCleaning` mediante el caso de uso *Marcar Pendiente a Limpieza*, con el Administrador como actor (FR-014)

---

### Casos Límite

1. **¿Qué ocurre si el miembro del personal de limpieza confirma el fin, pero la habitación evidentemente no está limpia?**
   Este es un problema operativo que no puede ser validado técnicamente por el sistema. La responsabilidad recae en la supervisión del gerente, quien puede consultar el historial de estados para auditar tiempos de limpieza inusuales.

2. **¿Qué ocurre si durante la limpieza se encuentra un daño en la habitación?**
   El miembro del personal de limpieza lo reporta en la misma confirmación de fin de limpieza diligenciando la descripción opcional del daño (HU-1, escenario 4). Así el reporte no depende de que la habitación quede en `Available` después de la confirmación, lo que no ocurre si se aparta a `Reserved` por una llegada pendiente. Si la habitación tenía una llegada pendiente hoy, no se aparta y Recepción ve la alerta operativa de esa llegada (HU-1, escenario 6; FR-019).

3. **¿Qué ocurre si la marca de fin resulta anterior a la marca de inicio de la tarea (ej. por inconsistencia de reloj o dato corrupto)?**
   No puede ocurrir: `EndDateTime` es la marca del servidor de la transición de cierre y nunca es anterior a `StartDateTime` (invariante, FR-008).

4. **¿Qué ocurre si el miembro del personal de limpieza no puede terminar una tarea activa?**
   El miembro vinculado puede liberarla desde la vista de tarea activa (FR-013): la habitación vuelve a `PendingCleaning` y cualquier miembro puede retomarla desde el panel. Si el miembro abandona la tarea sin liberarla, la habitación permanece en `InCleaning`; ese caso queda fuera del alcance del módulo, salvo la liberación automática al desactivar la cuenta del miembro (*Gestionar personal*; HU-1 esc. 8).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al personal de limpieza confirmar el fin de limpieza y recibir retroalimentación visual de la acción que está realizando.
- **FR-002**: El sistema DEBE aceptar la confirmación únicamente sobre la tarea abierta del miembro del personal de limpieza que realiza la acción.
- **FR-003**: El sistema DEBE delegar la transición de `InCleaning` a `Available` en el caso de uso *Marcar Habitación como Disponible* (HU-2, FR-003), invocándolo dentro de la misma transacción, sin reimplementar su lógica.
- **FR-004**: El sistema DEBE registrar la fecha y hora de finalización de labores de limpieza (timestamp).
- **FR-005**: El sistema DEBE rechazar la confirmación si la tarea pertenece a otro miembro del personal, informando el conflicto de asignación.
- **FR-006**: El sistema DEBE permitir el acceso a una vista exclusiva a los miembros del personal de limpieza con una tarea activa
  - La vista de tarea activa debe mostrar la habitación vinculada a la tarea y al miembro del personal de limpieza que la consulta, debe ser listada usando el mismo formato designado para presentar las habitaciones en el panel general de limpieza
  - La cinta de opciones debe estar limitada a las acciones Confirmar fin y Liberar tarea
  - Una vez confirmado el fin o liberada la tarea listada, el usuario debe ser desvinculado de la misma y redirigido al panel de limpieza general
- **FR-007**: El sistema DEBE generar el timestamp de finalización (`EndDateTime`) exclusivamente en el servidor, sin aceptar fechas u horas enviadas por el cliente.
- **FR-008**: El sistema DEBE garantizar como invariante que `EndDateTime` sea la marca del servidor de la transición de cierre y nunca sea anterior a `StartDateTime` de la misma tarea.
- **FR-009**: El sistema DEBE impedir que `EndDateTime` sea modificado una vez registrado.
- **FR-010**: Tras la transición a `Available`, y solo si no se reportó un daño (FR-011), el sistema DEBE verificar en la copia local de la lista del día (`daily_reservation_room`) si la habitación tiene una llegada pendiente hoy (ver *Llegada pendiente* en el diccionario); si es así, DEBE invocar dentro de la misma transacción el caso de uso *Marcar habitación como reservada*, que transiciona la habitación a `Reserved` asignando `reservedByReservationRef`. Esta transición es autónoma del Módulo 1 y no es seleccionable por el usuario.
- **FR-011**: El sistema DEBE permitir, en la confirmación del fin de limpieza, diligenciar de forma opcional una descripción del daño encontrado, con las mismas reglas de obligatoriedad y longitud máxima (500 caracteres) del caso de uso *Marcar Habitación Inhabilitada por Reparaciones*. Si se diligencia, el sistema DEBE invocar ese caso de uso inmediatamente después de la transición a `Available` y dentro de la misma transacción, dejando la habitación en `DisabledForRepairs`; si la invocación falla, DEBE ejecutar rollback completo y mantener la habitación en `InCleaning`.
- **FR-012**: El sistema DEBE registrar en `RoomStateHistory`, dentro de la misma transacción, cada transición de este flujo:
  - `InCleaning` → `Available` y `Available` → `DisabledForRepairs`, con `ActorId` = miembro del personal de limpieza y `SourceFlow` = *Confirmar Fin de Limpieza de Habitación*.
  - `Available` → `Reserved`, la registra *Marcar habitación como reservada*.
  - `Available` → `TechnicalBlock`, la registra *Programar bloqueo técnico* (`TECHNICAL_BLOCK`, actor nulo).
  - `InCleaning` → `PendingCleaning` de la liberación (FR-013), la registra *Marcar Pendiente a Limpieza*.
- **FR-013**: El sistema DEBE permitir al miembro del personal de limpieza vinculado a una `CleaningTask` activa liberarla desde la vista de tarea activa (acción **Liberar tarea**), previa confirmación explícita *(modal superpuesto indicando "¿Está seguro que desea liberar la limpieza de la habitación X?", advirtiendo que la habitación volverá a la cola de limpieza)*. Al confirmar, el sistema DEBE ejecutar en una sola transacción el cierre de la tarea con `EndDateTime` (con las reglas de FR-007 y FR-008) y `Outcome` = `Released`, y la transición de `InCleaning` a `PendingCleaning` delegada en el caso de uso *Marcar Pendiente a Limpieza*. La liberación NO DEBE ejecutar la verificación de llegadas (FR-010) ni el reporte de daño (FR-011), y DEBE rechazarse si la tarea pertenece a otro miembro.
- **FR-014**: El sistema DEBE permitir liberar una `CleaningTask` activa únicamente a su miembro vinculado, desde la vista de tarea activa, salvo la liberación automática al desactivar la cuenta del miembro (*Gestionar personal*; HU-1 esc. 8); el sistema NO DEBE permitir transferir la tarea a un miembro específico ni liberar tareas de otros miembros.
- **FR-015**: El sistema DEBE asignar el `Outcome` de la `CleaningTask` al confirmar el fin de limpieza: `Completed` cuando no se reporta daño y `DamageReported` cuando se usa FR-012.
- **FR-016**: El sistema DEBE evitar que dos solicitudes simultáneas de confirmación de fin y/o liberación sobre la misma tarea se procesen con éxito; solo la primera transacción es válida y la segunda recibe un error de tarea ya cerrada (`TASK_ALREADY_CLOSED`).
- **FR-017**: El sistema DEBE ejecutar la confirmación de fin o la liberación de la tarea (cierre de la `CleaningTask`, transiciones y registros en `RoomStateHistory`) dentro de una única transacción; si falla la persistencia o la conexión durante el procesamiento, el sistema DEBE ejecutar rollback completo, mantener la habitación en su estado previo (`InCleaning`) con la tarea activa, no crear ni modificar registros y mostrar retroalimentación visual del error con la sugerencia de reintentar la operación.
- **FR-018**: El sistema DEBE requerir confirmación explícita antes de procesar el fin de limpieza *(modal superpuesto indicando "¿Está seguro que desea finalizar la limpieza de la habitación X?", con un campo de texto opcional "Describir daño encontrado" de máximo 500 caracteres)*. Si el campo se deja vacío o solo con espacios, no hay daño y el sistema DEBE procesar el fin de limpieza sin reporte de daño (FR-003, FR-010); si se diligencia, DEBE aplicar FR-011 y advertir en el mismo modal que la habitación quedará inhabilitada por reparaciones. Cancelar el modal NO DEBE modificar la tarea ni la habitación.
- **FR-019**: Cuando se reporta un daño (FR-011) y la copia local de la lista del día (`daily_reservation_room`) contiene una llegada pendiente hoy para la habitación (FR-010), el sistema NO DEBE apartarla a `Reserved`: la habitación queda en `DisabledForRepairs` y la reserva queda sin habitación apartada. Recepción lo ve con la alerta operativa de su panel ("No disponible: Inhabilitada por reparaciones"), que el panel calcula a partir del estado de la habitación (*Consultar panel de recepción* FR-005); este caso de uso no envía mensajes a Recepción; el aviso a Módulo 2 lo publica *Marcar Habitación Inhabilitada por Reparaciones* (FR-013).
- **FR-020**: Tras la transición a `Available`, solo si no se reportó un daño (FR-011) y la habitación no tiene una llegada pendiente hoy (FR-010), el sistema DEBE verificar si la habitación tiene un `TechnicalBlockReport` en `Scheduled` cuyo rango contiene la fecha actual; si es así, DEBE aplicarlo dentro de la misma transacción mediante el caso de uso *Programar Bloqueo Técnico para Habitación* (FR-002), que transiciona la habitación a `TechnicalBlock` y marca el informe como `Applied`. Cuando varias transiciones compiten en esta confirmación, la prioridad es según la regla 6 de la máquina de estados. Esta transición es autónoma y no es seleccionable por el usuario.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `InCleaning` a `Available`, completando el ciclo de limpieza, y según el caso a `Reserved`, `DisabledForRepairs` o `TechnicalBlock`; o de `InCleaning` a `PendingCleaning` cuando se libera la tarea. Invariante: mientras una `CleaningTask` está abierta, su habitación está en `InCleaning` (solo se entra en `InCleaning` creando la tarea y solo se sale cerrándola).
- **CleaningTask**: Entidad definida en el caso de uso *Marcar Habitación en Limpieza*. En este caso de uso se completan los campos `EndDateTime` y `Outcome`.
- **DamageReport**: Entidad definida en el caso de uso *Marcar Habitación Inhabilitada por Reparaciones*. Se crea solo cuando se diligencia la descripción opcional del daño (FR-011).
- **TechnicalBlockReport**: Entidad definida en el caso de uso *Programar Bloqueo Técnico para Habitación*. Se consulta tras la transición a `Available` y, si un informe `Scheduled` contiene la fecha actual, pasa a `Applied` (FR-020).
- **RoomStateHistory**: Historial común de estados de la habitación. En este caso de uso se cierra el periodo `InCleaning` y se abren los periodos de las transiciones ejecutadas.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las confirmaciones registran correctamente la fecha y hora de finalización de labores de limpieza.
- **SC-002**: Cero registros de `CleaningTask` con `EndDateTime` anterior a `StartDateTime` (invariante de FR-008).
