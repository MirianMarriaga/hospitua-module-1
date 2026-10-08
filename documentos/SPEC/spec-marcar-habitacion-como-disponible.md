# Especificación de Funcionalidad: Marcar Habitación como Disponible

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Administrador / Personal de limpieza (vía "Confirmar fin de limpieza de habitación")
**Creado**: 2026-09-20
## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Revertir la baja de una habitación inactiva (Prioridad: P1)

Como Administrador (`Administrator`), quiero marcar una habitación dada de baja como "Disponible" para reintegrarla al inventario operativo del hotel, de modo que vuelva a ser elegible para reservas y asignaciones comerciales cuando se subsane el motivo de la baja o se desista del cierre temporal.

**Por qué esta prioridad**: Sin este mecanismo, una unidad dada de baja (`Inactive`) queda excluida permanentemente del inventario vendible o requeriría un nuevo registro manual, lo que fragmentaría la trazabilidad histórica. Es el mecanismo de reactivación directa del inventario del Módulo 1.

**Prueba Independiente**: Iniciar sesión como Administrador, seleccionar una habitación en estado `Inactive`, confirmar la reactivación y verificar que la habitación transicione a `Available` y reapareciendo en el catálogo de habitaciones disponibles.

**Escenarios de Aceptación**:

1. **Escenario**: Reactivación exitosa de habitación inactiva por el Administrador
   - **Dado** una habitación registrada que se encuentra en estado operativo `Inactive` 
   - **Cuando** el Administrador ejecuta la acción de marcar como disponible y confirma la operación
   - **Entonces** el sistema actualiza de forma atómica el estado a `Available`, la reintegra al catálogo comercial y registra la novedad en la bitácora de auditoría.

2. **Escenario**: Confirmación explícita antes de reactivar
   - **Dado** el Administrador ha seleccionado una habitación en estado `Inactive`
   - **Cuando** inicia la acción de marcar como disponible
   - **Entonces** el sistema solicita una confirmación explícita antes de aplicar el cambio, mostrando los datos de la habitación y la fecha y el motivo de su baja, dado el impacto de reintegrar una habitación al inventario operativo.

3. **Escenario**: Rechazo de reactivación directa sobre habitación en estado no permitido
   - **Dado** una habitación que se encuentra en cualquiera de los otros estados (`Available`, `Occupied`, `PendingCleaning`, `DisabledForRepairs` o `TechnicalBlock`)
   - **Cuando** el Administrador intenta ejecutar directamente la acción de marcar como disponible
   - **Entonces** el sistema bloquea la transacción, mantiene inalterado el estado actual y emite una alerta indicando que la habitación no se encuentra en estado `Inactive`.

---

### Historia de Usuario 2 - Reintegración operativa de la habitación tras fin de limpieza (Prioridad: P1)

Como Personal de limpieza (`CleaningStaff`), quiero que al confirmar la finalización del aseo de una habitación se invoque automáticamente este servicio para transicionar la unidad a "Disponible", permitiendo que quede habilitada para recepción sin requerir la intervención manual del Administrador.

**Por qué esta prioridad**: Es el flujo de mayor frecuencia operativa del hotel. Toda habitación que concluye su ciclo de desinfección (`InCleaning`) debe transicionar a `Available` para retornar al circuito vendible. Centralizar esta transición mediante inclusión garantiza la consistencia del inventario.

**Prueba Independiente**: Tomar una habitación en estado `InCleaning`, ejecutar la confirmación de fin de limpieza (que incluye este caso de uso vía `<<includes>>`) y validar que el estado resultante sea `Available`, quedando visible para asignaciones en el Módulo 2.

**Escenarios de Aceptación**:

4. **Escenario**: Reintegración exitosa a disponible invocada desde fin de limpieza
   - **Dado** una habitación que se encuentra en estado operativo `InCleaning`
   - **Cuando** el Personal de limpieza confirma la culminación del aseo a través de la inclusión del caso de uso (`<<includes>>`)
   - **Entonces** el sistema transiciona el estado de la habitación de `InCleaning` a `Available` y la hace elegible de inmediato para nuevas estancias o reservas.

5. **Escenario**: Habitación reintegrada aparece en disponibilidad para reservas
   - **Dado** la limpieza de una habitación ha sido confirmada como finalizada
   - **Cuando** se consulta el inventario de habitaciones disponibles
   - **Entonces** la habitación aparece con estado `Available` y es elegible para nuevas reservas o asignaciones.

6. **Escenario**: Rechazo de invocación desde limpieza en estado no válido
   - **Dado** una habitación en cualquier estado distinto a `InCleaning` (ej. `Occupied` o `DisabledForRepairs`)
   - **Cuando** se intenta invocar el servicio de marcar como disponible por la vía de limpieza
   - **Entonces** el sistema rechaza la ejecución e informa que la unidad no se encuentra en proceso de aseo activo.

---

### Casos Borde

- **Consulta de habitación inexistente**: Si la solicitud de marcado a disponible se envía con un UUID que no existe en la base de datos, el sistema retorna un error controlado indicando que el recurso no fue encontrado.
- **Peticiones simultáneas o redundantes (idempotencia)**: Ante llamadas concurrentes para marcar la misma unidad como disponible, el sistema procesa la primera transacción de forma atómica y resuelve las subsecuentes de manera idempotente sin generar fallos ni duplicar registros de auditoría.
- **Pérdida de conexión o fallo de persistencia**: La transacción se ejecuta bajo control ACID; ante una caída de red o error en la base de datos, se ejecuta rollback completo y la habitación permanece en su estado original (`Inactive`, `Reserved` o `InCleaning`, según el flujo).
- **Fallo en la confirmación de fin de aseo**: Si durante el flujo de limpieza se confirma el fin de tareas pero la invocación a marcar como disponible falla, la unidad permanece en `InCleaning` para evitar que quede en un estado inconsistente o huérfano.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE estructurar "Marcar habitación como disponible" como un servicio transaccional centralizado y reutilizable en el Módulo 1.
- **FR-002**: El sistema DEBE permitir la invocación directa de este caso de uso exclusivamente al actor "Administrador" (`Administrator`) para reactivar unidades desde el estado `Inactive`.
- **FR-003**: El sistema DEBE permitir la invocación de este caso de uso mediante inclusión (`<<includes>>`) desde el caso de uso "Confirmar fin de limpieza de habitación", ejecutado por el actor "Personal de limpieza" (`CleaningStaff`).
- **FR-004**: Precondición: la entidad `Room` DEBE encontrarse en estado `Inactive` (para reactivación directa administrativa), en estado `InCleaning` (para el flujo proveniente de fin de limpieza) o en estado `Reserved` (para liberar una habitación cuya reserva fue cancelada o liberada en Módulo 2, invocado por *Consultar reservas*, FR-009), según documentos/SPEC/referencias/maquina-estados-habitacion.md.
- **FR-005**: Transición de estado: El sistema DEBE actualizar de forma atómica el estado de la entidad `Room` a `Available` tras completarse la validación del flujo correspondiente.
- **FR-006**: El sistema DEBE rechazar la operación si la habitación se encuentra en cualquiera de los otros estados operativos (`Available`, `Occupied`, `PendingCleaning`, `DisabledForRepairs`, `TechnicalBlock`).
- **FR-007**: La habitación en estado `Available` DEBE reflejarse de forma inmediata en las consultas de inventario y quedar habilitada para asignaciones comerciales del Módulo 2.
- **FR-008**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, el identificador del usuario responsable (`Administrator` o `CleaningStaff`), el flujo de invocación utilizado y la marca de tiempo (timestamp) de la transición.
- **FR-009**: En la confirmación de la reactivación directa, el sistema DEBE mostrar al Administrador los datos de la habitación (número, piso, tipo y estado) junto con la fecha y el motivo (y el detalle, si existe) con que fue dada de baja.
- **FR-010**: Tras una reactivación directa, el sistema DEBE mostrar un resultado con los datos de la habitación (número, piso, tipo y estado) e indicar que ya puede recibir reservas y estancias. Si la habitación ya había sido reactivada (caso de idempotencia), DEBE indicar que no fue necesario hacer cambios.
- **FR-011**: En la reactivación directa (`Inactive`) y en la liberación de una habitación `Reserved`, el sistema DEBE registrar la transición en `RoomStateHistory` (campos en documentos/SPEC/referencias/maquina-estados-habitacion.md) dentro de la misma transacción del cambio de estado: cerrar el periodo abierto de la habitación (`EndDateTime`) y abrir uno nuevo con `Status` = `Available`, `PreviousStatus` = estado de origen, `StartDateTime` = fecha y hora del servidor, `ActorId` = usuario responsable (nulo cuando la liberación es autónoma) y `SourceFlow` = *Marcar habitación como disponible*. Cuando se invoca desde *Confirmar fin de limpieza de habitación*, el registro lo realiza ese caso de uso (su FR-013) y este caso de uso NO DEBE duplicarlo.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **`Room`**: Unidad habitacional física del hotel. Su atributo `status` transiciona hacia `Available` desde `Inactive`, `Reserved` o `InCleaning` (cuatro de los 8 estados canónicos de su ciclo de vida). Atributos involucrados: ID único (UUID), número de habitación, piso, tarifa base y estado operativo.
- **`Administrator`**: Actor autorizado para ejecutar la reactivación directa de inventario inactivo.
- **`CleaningStaff`**: Actor operativo cuya confirmación de aseo dispara la inclusión de este servicio para liberar la habitación.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las invocaciones válidas transicionan la habitación al estado `Available` en menos de 1 segundo.
- **SC-002**: El sistema intercepta y rechaza el 100% de los intentos de ejecución sobre unidades cuyo estado de origen no sea estrictamente `Inactive`, `Reserved` o `InCleaning`.
- **SC-003**: Tras la transición exitosa, la habitación reaparece en el inventario vendible y en las consultas de disponibilidad sin retraso perceptible.
- **SC-004**: El 100% de las transiciones quedan registradas en auditoría con su usuario responsable, marca de tiempo y flujo de origen trazable.
