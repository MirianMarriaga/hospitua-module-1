# Especificación del Caso de Uso: Marcar Habitación en Limpieza

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Marcar habitación en limpieza (Prioridad: P1)

Como miembro del personal de limpieza quiero poder indicar que voy a limpiar una habitación, independiente de la razón por la que requiera limpieza, es importante que pueda formalizar mi participación en las labores designadas a mi área y notificar esta acción al sistema de forma oportuna para evitar posibles inconsistencias en otras operaciones del hotel.

**Por qué esta prioridad**: Es la acción principal del personal de limpieza. Sin ella, las habitaciones no avanzan en su ciclo de vida y quedan estancadas, reduciendo la capacidad operativa del hotel y empeorando la experiencia del usuario.

**Prueba independiente**: Puede ser probada indicando que se va a iniciar la limpieza en una habitación en estado `PendingCleaning` o `Available`, verificando que el sistema la marca como `InCleaning`, asigna la labor al miembro que indicó el inicio de labores de limpieza, registra la fecha y hora de inicio y la excluye de la lista de habitaciones disponibles al público y del panel general de limpieza, haciéndola visible única y exclusivamente para el miembro del personal de limpieza vinculado a la labor.

**Escenarios de aceptación**:

1. **Escenario**: Inicio de limpieza post-check-out
   - **Dado que** El miembro del personal de limpieza está autenticado y existe una habitación "101" que acaba de ser liberada y se encuentra en estado `PendingCleaning`
   - **Cuando** El miembro del personal de limpieza indica que va a iniciar labores de limpieza en la habitación
   - **Entonces** El sistema registra que la habitación está en proceso de limpieza, transiciona su estado a `InCleaning`, vincula el miembro del personal de limpieza a la labor y registra el timestamp al recibir la confirmación

2. **Escenario**: Limpieza preventiva
   - **Dado que** El miembro del personal de limpieza está autenticado y existe una habitación "101" en estado `Available` que lleva tiempo sin ser asignada o usada
   - **Cuando** El miembro del personal de limpieza indica que va a iniciar labores en la habitación
   - **Entonces** El sistema realiza la misma transición a `InCleaning`, vinculación y registro de timestamp descritos en el escenario anterior, sin exigir que la habitación provenga de un check-out

3. **Escenario**: Habitación en estados distintos a `PendingCleaning` o `Available`
   - **Dado que** El miembro del personal de limpieza está autenticado y existe una habitación "101" que se encuentra en un estado distinto a los mencionados
   - **Cuando** El miembro del personal de limpieza intenta indicar que va a iniciar labores de limpieza en la habitación
   - **Entonces** El sistema rechaza la operación y muestra un error con el estado actual de la habitación indicando que es una operación no válida

4. **Escenario**: Miembro con una tarea activa
   - **Dado que** El miembro del personal de limpieza está autenticado y tiene una `CleaningTask` activa sobre la habitación "101"
   - **Cuando** Intenta iniciar labores de limpieza en la habitación "105"
   - **Entonces** El sistema rechaza la operación indicando que ya tiene una tarea activa, no modifica el estado de la habitación "105" y lo redirige a la vista de tarea activa

---

### Casos Límite

1. **¿Qué ocurre si dos miembros del personal intentan limpiar la misma habitación?**
   El sistema debe asignar la habitación al primero que la marque. El segundo recibe un error indicando el estado actual de la habitación `InCleaning`.

2. **¿Qué ocurre si se marca una habitación en limpieza y el personal no se autentica?**
   El sistema debe rechazar la operación y solicitar autenticación previo al inicio de labores de limpieza por parte de un miembro de su personal.

3. **¿Qué ocurre si la habitación fue liberada hace mucho tiempo y nadie la ha limpiado?**
   El sistema debe permitir la limpieza sin ninguna restricción de tiempo. La verificación de tiempos no es responsabilidad de este caso de uso.

4. **¿Qué ocurre si el reloj del dispositivo del cliente está desajustado o se intenta enviar una fecha propia?**
   El sistema ignora cualquier fecha enviada por el cliente y utiliza exclusivamente el timestamp del servidor, evitando registros con fechas sin sentido.

5. **¿Qué ocurre si el miembro del personal de limpieza no puede terminar una tarea activa?**
   El miembro vinculado puede liberarla desde la vista de tarea activa (caso de uso *Confirmar Fin de Limpieza de Habitación*): la habitación vuelve a `PendingCleaning` y cualquier miembro puede retomarla desde el panel. Si el miembro abandona la tarea sin liberarla, la habitación permanece en `InCleaning`; ese caso queda fuera del alcance del módulo.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al personal de limpieza autenticado marcar el inicio de labores de limpieza en una habitación y recibir retroalimentación visual de la acción que está realizando.
- **FR-002**: El sistema DEBE aceptar la marcación únicamente desde habitaciones que se encuentren en los estados `PendingCleaning` o `Available`
- **FR-003**: El sistema DEBE cambiar el estado de la habitación a `InCleaning` al procesar la marcación.
- **FR-004**: El sistema DEBE registrar los datos del personal que solicita la tarea junto con la fecha y hora de inicio de labores de limpieza (timestamp).
- **FR-005**: El sistema DEBE rechazar la marcación si la habitación se encuentra en cualquier estado distinto de `PendingCleaning` o `Available`, informando el estado actual.
- **FR-006**: El sistema DEBE evitar que dos usuarios marquen el inicio de labores en la misma habitación de forma simultánea con éxito.
- **FR-007**: El sistema DEBE permitir a los miembros del personal de limpieza acceder a un panel de limpieza, donde se encontrarán listadas habitaciones en estados `PendingCleaning` o `Available`
   - Cada habitación que cumpla con las condiciones anteriormente descritas debe estar listada utilizando los siguientes datos y el formato descrito:
      - Número de habitación (ej. 1, 2, ...)
      - Tipo (Sencilla, Doble, Suite, Boutique)
      - Estado (Disponible, Pendiente de limpieza)
      - Última limpieza (`EndDateTime` de la última `CleaningTask` de la habitación con `Outcome` `Completed` o `DamageReported`; las tareas liberadas no cuentan) - Formato: DD-MM-YYYY HH:MM
      - Cinta de opciones - `PC`: Iniciar limpieza ; `AVB`: Reportar daño, Iniciar limpieza - Formato: Botón con el nombre de la acción
- **FR-008**: El sistema DEBE permitir realizar búsquedas en el listado de habitaciones utilizando el número de la habitación para agilizar las labores por solicitud específica
   - El tipo de búsqueda DEBE ser por coincidencia exacta
   - Una búsqueda exitosa debe retornar una habitación listada usando el mismo formato designado para presentar las habitaciones en el panel general de limpieza
- **FR-009**: El sistema DEBE redirigir a los miembros del personal de limpieza con una tarea activa a la vista exclusiva de tarea activa, cuyo comportamiento y cinta de opciones (`IC`: Confirmar fin, Liberar tarea) se definen en el caso de uso *Confirmar Fin de Limpieza de Habitación*.
- **FR-010**: El sistema DEBE generar el timestamp de inicio de labores (`StartDateTime`) exclusivamente en el servidor, sin aceptar fechas u horas enviadas por el cliente.
- **FR-011**: El sistema DEBE asignar `StartDateTime` en el mismo momento en que se procesa la transición a `InCleaning`, de modo que coincida con el inicio del periodo registrado en `RoomStateHistory`.
- **FR-012**: El sistema DEBE mostrar la fecha de la última limpieza cuando exista una `CleaningTask` cerrada con `Outcome` `Completed` o `DamageReported`; si no existe, DEBE mostrar el indicador "Sin registro".
- **FR-013**: El sistema DEBE rechazar la marcación si el miembro del personal de limpieza autenticado ya tiene una `CleaningTask` sin `EndDateTime`, aunque la solicitud no provenga del panel de limpieza, informando que ya tiene una tarea activa.
- **FR-014**: El sistema DEBE registrar la transición en `RoomStateHistory` dentro de la misma transacción: cerrar el periodo abierto de la habitación y abrir uno nuevo con `Status` = `InCleaning`, `PreviousStatus` = `PendingCleaning` o `Available`, `StartDateTime` = `StartDateTime` de la tarea, `ActorId` = miembro del personal de limpieza y `SourceFlow` = *Marcar Habitación en Limpieza*.
- **FR-015**: El sistema DEBE permitir liberar una `CleaningTask` activa únicamente a su miembro vinculado, desde la vista de tarea activa (caso de uso *Confirmar Fin de Limpieza de Habitación*); el sistema NO DEBE permitir transferir la tarea a un miembro específico ni liberar tareas de otros miembros.
- **FR-016**: El sistema DEBE ejecutar la marcación (transición, `CleaningTask` y registro en `RoomStateHistory`) dentro de una única transacción; si falla la persistencia o la conexión durante el procesamiento, el sistema DEBE ejecutar rollback completo, mantener la habitación en su estado previo, no crear ni modificar registros y mostrar retroalimentación visual del error con la sugerencia de reintentar la operación.
- **FR-017**: El sistema DEBE informar "No se encontró la habitación X" cuando la búsqueda del panel (FR-008) no retorne resultados, y "No hay habitaciones pendientes de limpieza ni disponibles" cuando el panel no tenga habitaciones para listar.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `PendingCleaning` o `Available` a `InCleaning`.
- **CleaningTask**: Entidad que representa la asignación temporal de las labores de limpieza en una habitación a un miembro del personal de limpieza, esta almacena:
   - **RoomId**: Identificador de la habitación
   - **CleaningStaffMemberId**: Identificador del miembro del personal de limpieza
   - **StartDateTime**: TimeStamp de inicio de labores de limpieza - Formato DD-MM-YYYY HH:MM
   - **EndDateTime**: TimeStamp de finalización de labores de limpieza - Formato DD-MM-YYYY HH:MM
   - **Outcome**: Resultado de la tarea, asignado al cerrarla - `Completed` (limpieza confirmada), `DamageReported` (limpieza confirmada con reporte de daño) o `Released` (tarea liberada sin terminar); nulo mientras la tarea está activa
- **RoomStateHistory**: Historial común de estados de la habitación (campos en documentos/SPEC/referencias/maquina-estados-habitacion.md). En este caso de uso se abre el periodo `InCleaning`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Personal de limpieza puede marcar una habitación en limpieza en menos de 15 segundos.
- **SC-002**: El 100% de las marcaciones registran correctamente el personal vinculado a la tarea junto con la fecha y hora de inicio de labores de limpieza.
- **SC-003**: El cambio de estado se refleja en el inventario en menos de 2 segundos.
- **SC-004**: El 100% de las marcaciones quedan registradas en `RoomStateHistory` con el mismo timestamp de servidor que `StartDateTime`, y ningún miembro tiene más de una `CleaningTask` activa.
