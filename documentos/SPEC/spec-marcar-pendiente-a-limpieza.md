# Especificación del Caso de Uso: Marcar Pendiente a Limpieza

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Transición automática a PendingCleaning tras check-out o fin de reparación (Prioridad: P1)

Como sistema del módulo 1, quiero poder gestionar correctamente los eventos disparados por acciones críticas en el flujo operativo del hotel, como lo pueden ser la conclusión de reparaciones y la salida de huéspedes, para así formalizar la transición y notificación de responsabilidades a aquellas áreas funcionales del hotel que sea requerido y evitar posibles inconsistencias en otras operaciones del hotel.

**Por qué esta prioridad**: Toda habitación que ha sido ocupada o reparada necesita pasar por limpieza antes de poder ser reasignada. La transición al estado intermedio es automática porque forma parte del flujo de registro de check-out y finalización de reparación en habitaciones, y su documentación centralizada evita la duplicación de labores, asignaciones erróneas e inconsistencias en los registros del hotel.

**Prueba independiente**: Puede ser probada registrando el check-out de un huésped que ocupó una habitación durante un periodo de tiempo y se encuentra en estado `Occupied` o la finalización de reparaciones en una habitación en estado `DisabledForRepairs` o `TechnicalBlock`, verificando que el sistema la marca como `PendingCleaning` y registra la transición en `RoomStateHistory` con la fecha y hora del servidor.

**Escenarios de aceptación**:

1. **Escenario**: Transición automática exitosa `Occupied` a `PendingCleaning`
   - **Dado que** Existe una habitación "101" que se encuentra en estado `Occupied`
   - **Cuando** Se registra el check-out del huésped asociado a la habitación "101"
   - **Entonces** El sistema cambia automáticamente el estado de la habitación de `Occupied` a `PendingCleaning` como postcondición del check-out y registra la transición en `RoomStateHistory`

2. **Escenario**: Transición automática exitosa `DisabledForRepairs` o `TechnicalBlock` a `PendingCleaning`
   - **Dado que** Existe una habitación "101" que se encuentra en estado `DisabledForRepairs` o `TechnicalBlock`
   - **Cuando** Se registra la finalización de labores de reparación sobre la habitación "101"
   - **Entonces** El sistema cambia automáticamente el estado de la habitación de `DisabledForRepairs` o `TechnicalBlock` a `PendingCleaning` como postcondición de la finalización de reparaciones y registra la transición en `RoomStateHistory`

3. **Escenario**: Invocación desde un estado inválido
   - **Dado que** Existe una habitación "101" en estado `Available`
   - **Cuando** Se invoca este flujo sobre la habitación "101"
   - **Entonces** El sistema interrumpe la operación de forma controlada, no modifica el estado y devuelve al flujo invocador un error con el estado actual de la habitación; el flujo invocador es el responsable de mostrarlo al usuario

4. **Escenario**: Transición `InCleaning` a `PendingCleaning` por liberación de una tarea de limpieza
   - **Dado que** Existe una habitación "101" en estado `InCleaning` cuya tarea de limpieza pertenece al miembro autenticado
   - **Cuando** El miembro libera la tarea desde la vista de tarea activa (caso de uso *Confirmar Fin de Limpieza de Habitación*)
   - **Entonces** El sistema cambia el estado de la habitación de `InCleaning` a `PendingCleaning` dentro de la misma transacción de la liberación, de modo que cualquier miembro del personal de limpieza pueda retomarla desde el panel

---

### Casos Límite

1. **¿Qué ocurre si hay invocaciones concurrentes sobre la misma habitación (condición de carrera)?**
   Si ocurre un check-out y un intento de manipulación del mismo registro en el mismo milisegundo, el sistema utilizará bloqueo optimista (Optimistic Locking). La primera transacción mutará el estado a `PendingCleaning`. La segunda será abortada y se devolverá al flujo invocador el error con el estado actual de la habitación, para que este muestre el error y la sugerencia de reintentar la operación.

2. **¿Qué ocurre si el cambio de estado falla a nivel de base de datos?**
   El caso de uso debe ejecutarse dentro del mismo contexto transaccional (ACID) del flujo que lo invoca. Si "Marcar Pendiente a Limpieza" falla (ej. caída de base de datos), el flujo padre (ej. check-out o finalización de reparaciones) DEBE ejecutar un *rollback*, impidiendo la liberación de la habitación, ofreciendo retroalimentación visual del error y la sugerencia de reintentar la operación.

3. **¿Qué ocurre con la sincronización de la información en el panel de limpieza en tiempo real?**
   Al no poseer UI, este caso de uso es responsable de propagar el cambio. Debe emitir un evento de dominio o invalidar la caché correspondiente para asegurar que el panel operativo del personal de limpieza se actualice.

4. **¿Qué ocurre si la conexión a internet falla exactamente al momento de confirmar el check-out o el fin de reparación?**
   La operación se ejecuta bajo control transaccional estricto (ACID). Si ocurre un error de red al intentar cambiar el estado a `PendingCleaning`, se ejecutará un *rollback* completo. La habitación permanecerá en su estado de origen (`Occupied`, `DisabledForRepairs`, `TechnicalBlock` o `InCleaning`) para evitar inconsistencias en el sistema, ofreciendo retroalimentación visual del error y la sugerencia de reintentar la operación.


---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE interceptar toda invocación a este flujo y validar que el estado operativo de la habitación sobre la que se desea realizar la operación sea única y exclusivamente: `Occupied`, `DisabledForRepairs`, o `TechnicalBlock`; o `InCleaning` únicamente cuando el flujo invocador sea la liberación de una tarea de limpieza (*Confirmar Fin de Limpieza de Habitación*).
- **FR-002**: El sistema DEBE sobrescribir el estado de la entidad a `PendingCleaning` ejecutando la mutación dentro de un contexto transaccional seguro, sin requerir pasos intermedios ni aprobaciones humanas.
- **FR-003**: El sistema DEBE garantizar que, tras la transición exitosa, la habitación sea excluida instantáneamente de las consultas de disponibilidad comercial (reservas/check-in) y sea agregada a las consultas del panel de labores del personal de limpieza.
- **FR-004**: El sistema DEBE registrar la transición en `RoomStateHistory` dentro de la misma transacción: cerrar el periodo abierto de la habitación (`EndDateTime`) y abrir uno nuevo con `Status` = `PendingCleaning`, `PreviousStatus` = estado de origen, `StartDateTime` = `TransitionDateTime`, `ActorId` = usuario del flujo invocador y `SourceFlow` = nombre del flujo invocador (*Registrar check-out*, *Confirmar Fin de Reparación de Habitación* o *Liberar tarea de limpieza*, definida en *Confirmar Fin de Limpieza de Habitación*).
- **FR-005**: El sistema DEBE registrar la transición a `PendingCleaning` como una acción documentada centralmente, evitando duplicación de lógica entre los flujos invocadores (check-out, *Confirmar Fin de Reparación de Habitación* y la liberación de tareas de limpieza de *Confirmar Fin de Limpieza de Habitación*), los cuales DEBEN invocar este caso de uso en lugar de reimplementarlo.
- **FR-006**: El sistema DEBE generar `TransitionDateTime` exclusivamente en el servidor, sin aceptar fechas u horas enviadas por el cliente o por el flujo invocador.
- **FR-007**: El sistema DEBE devolver al flujo invocador un error con el estado actual de la habitación cuando rechace la invocación (FR-001), sin modificar el estado; este caso de uso no posee interfaz propia.
- **FR-008**: El sistema DEBE impedir la modificación o eliminación de los registros de `RoomStateHistory` creados por este caso de uso, salvo el cierre del periodo (`EndDateTime`) que realice la siguiente transición de la habitación.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `Occupied`, `DisabledForRepairs`, `TechnicalBlock` o `InCleaning` a `PendingCleaning`, completando el flujo de check-out en lo que respecta al módulo 1, el flujo de reparaciones o la liberación de una tarea de limpieza.
- **RoomStateHistory**: Historial común de estados de la habitación (campos en documentos/SPEC/referencias/maquina-estados-habitacion.md). En este caso de uso se cierra el periodo del estado de origen y se abre el periodo `PendingCleaning`, cuyo `StartDateTime` es el `TransitionDateTime` generado por el servidor.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las invocaciones provenientes de un check-out o de un cierre de reparación transicionan la habitación a `PendingCleaning` de manera totalmente automatizada (Zero-Touch).
- **SC-002**: El sistema alcanza un tiempo de procesamiento interno para la transición, actualización de estado e inserción en el log de auditoría inferior a 300 milisegundos.
- **SC-003**: El total de las peticiones ejecutadas desde estados inválidos (ej. `Available`) resultan en una interrupción controlada con un error devuelto al flujo invocador y sin corromper la consistencia de los datos del inventario.
- **SC-004**: La propagación del nuevo estado a las consultas de disponibilidad comercial y paneles de limpieza refleja el dato actualizado en tiempo real, evitando dobles asignaciones o cuellos de botella operativos.
- **SC-005**: El 100% de las transiciones a `PendingCleaning` quedan registradas en `RoomStateHistory` con fecha y hora generadas por el servidor.
