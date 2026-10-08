# Especificación del Caso de Uso: Marcar Pendiente a Limpieza

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Módulo 1 (incluido desde *Registrar check-out*, *Confirmar reparación finalizada* y la liberación de una tarea en *Marcar habitación en limpieza*)
**Creado**: 2026-09-08

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Transición automática a PendingCleaning tras check-out o fin de reparación (Prioridad: P1)

Como sistema del módulo 1, quiero poder gestionar correctamente los eventos disparados por acciones críticas en el flujo operativo del hotel, como lo pueden ser la conclusión de reparaciones y la salida de huéspedes, para así formalizar la transición y notificación de responsabilidades a aquellas áreas funcionales del hotel que sea requerido y evitar posibles inconsistencias en otras operaciones del hotel.

**Por qué esta prioridad**: Toda habitación que ha sido ocupada o reparada necesita pasar por limpieza antes de poder ser reasignada. La transición al estado intermedio es automática porque forma parte del flujo de registro de check-out y finalización de reparación en habitaciones, y su documentación centralizada evita la duplicación de labores, asignaciones erróneas e inconsistencias en los registros del hotel.

**Prueba independiente**: Puede ser probada invocándolo desde un check-out (habitación en `Occupied`) o desde la confirmación de una reparación (habitación en `DisabledForRepairs` o `TechnicalBlock`), verificando que la habitación queda en `PendingCleaning`, que la transición se registra en el historial con el actor y el flujo de origen, y que la habitación aparece en el panel de limpieza. El cierre de la estancia y de la tarea de reparación los hacen los flujos invocadores.

**Escenarios de aceptación**:

1. **Escenario**: Transición automática exitosa `Occupied` a `PendingCleaning`
   - **Dado que** Existe una habitación "101" que se encuentra en estado `Occupied`
   - **Cuando** Se registra el check-out del huésped asociado a la habitación "101"
   - **Entonces** El sistema cambia automáticamente el estado de la habitación de `Occupied` a `PendingCleaning` como postcondición del check-out y registra la transición con la fecha y hora del servidor (la fecha de salida de la estancia la registra *Registrar check-out*)

2. **Escenario**: Transición automática exitosa `DisabledForRepairs` o `TechnicalBlock` a `PendingCleaning`
   - **Dado que** Existe una habitación "101" que se encuentra en estado `DisabledForRepairs` o `TechnicalBlock`
   - **Cuando** Se registra la finalización de labores de reparación sobre la habitación "101"
   - **Entonces** El sistema cambia automáticamente el estado de la habitación de `DisabledForRepairs` o `TechnicalBlock` a `PendingCleaning` como postcondición de la finalización de reparaciones y registra la transición

3. **Escenario**: Transición `InCleaning` a `PendingCleaning` al liberar una limpieza
   - **Dado que** La habitación "105" está en `InCleaning` y su titular (o el Administrador) libera la tarea
   - **Cuando** *Marcar habitación en limpieza* invoca este caso de uso (su FR-005)
   - **Entonces** El sistema devuelve la habitación a `PendingCleaning`, registra la transición con el actor que liberó la tarea y la habitación vuelve a aparecer en el panel de limpieza

4. **Escenario**: Invocación desde un estado inválido
   - **Dado que** Existe una habitación "101" en estado `Available`
   - **Cuando** Se invoca este flujo sobre la habitación "101"
   - **Entonces** El sistema no modifica el estado y devuelve el error con el estado actual al flujo invocador

---

### Casos Límite

1. **¿Qué ocurre si la transición falla?**
   El flujo que la invocó (check-out, confirmación de reparación o liberación de una limpieza) se revierte completo: la habitación conserva su estado y el usuario puede reintentar (FR-007).

2. **¿Cómo se entera el personal de limpieza?**
   La habitación aparece de inmediato en el panel de limpieza (`spec-consultar-panel-limpieza.md`).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: Este caso de uso NO tiene interfaz propia: se invoca únicamente por inclusión (`<<includes>>`) desde *Registrar check-out*, *Confirmar reparación finalizada* y la liberación de una tarea de limpieza (*Marcar habitación en limpieza*, FR-005), dentro de la misma operación del flujo invocador.
- **FR-002**: El sistema DEBE aceptar la invocación solo si la habitación está en `Occupied` (check-out), `DisabledForRepairs` o `TechnicalBlock` (fin de reparación) o `InCleaning` (liberación de una tarea de limpieza); en otro caso devuelve el error al flujo invocador con el estado actual.
- **FR-003**: El sistema DEBE cambiar la habitación a `PendingCleaning`, registrando en el historial el estado previo, el actor del flujo invocador (Recepcionista, técnico, miembro del personal de limpieza o Administrador) y el flujo de origen.
- **FR-004**: Tras la transición, la habitación DEBE salir de las consultas de disponibilidad y aparecer de inmediato en el panel de limpieza.
- **FR-005**: Los flujos invocadores NO DEBEN reimplementar esta transición.
- **FR-006**: La fecha y hora de la transición DEBE generarla el servidor en hora Colombia (UTC-5), ignorando cualquier hora enviada por el cliente.
- **FR-007**: La transición forma parte de la operación del flujo invocador: se confirma junto con ella o no se aplica nada. Si falla, el flujo invocador se revierte completo, la habitación conserva su estado y el usuario puede reintentar.
- **FR-008**: Este caso de uso no valida la sesión por su cuenta: usa el actor ya autorizado por el flujo invocador.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Transita de `Occupied`, `DisabledForRepairs`, `TechnicalBlock` o `InCleaning` a `PendingCleaning`. Sus pendientes (reserva o bloqueo técnico) se conservan sin cambios.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de los check-outs, confirmaciones de reparación y liberaciones de tareas de limpieza dejan la habitación en `PendingCleaning` sin pasos manuales.
- **SC-002**: La habitación aparece en el panel de limpieza en menos de 2 segundos.
- **SC-003**: El 100% de las invocaciones desde estados inválidos se rechazan sin modificar la habitación.
