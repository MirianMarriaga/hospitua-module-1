# Especificación del Caso de Uso: Marcar Habitación en Limpieza

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de limpieza
**Creado**: 2026-09-04

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Marcar habitación en limpieza (Prioridad: P1)

Como miembro del personal de limpieza quiero poder indicar que voy a limpiar una habitación, independiente de la razón por la que requiera limpieza, es importante que pueda formalizar mi participación en las labores designadas a mi área y notificar esta acción al sistema de forma oportuna para evitar posibles inconsistencias en otras operaciones del hotel.

**Por qué esta prioridad**: Es la acción principal del personal de limpieza. Sin ella, las habitaciones no avanzan en su ciclo de vida y quedan estancadas, reduciendo la capacidad operativa del hotel y empeorando la experiencia del usuario.

**Prueba independiente**: Puede ser probada indicando que se va a iniciar la limpieza en una habitación en estado `PendingCleaning` o `Available`, verificando que el sistema la marca como `InCleaning`, asigna la labor al miembro que indicó el inicio de labores de limpieza, registra la fecha y hora de inicio y lo lleva a su vista de tarea activa.

**Escenarios de aceptación**:

1. **Escenario**: Inicio de limpieza post-check-out
   - **Dado que** El miembro del personal de limpieza está autenticado, sin tarea activa, y existe una habitación "101" que acaba de ser liberada y se encuentra en estado `PendingCleaning`
   - **Cuando** El miembro del personal de limpieza indica que va a iniciar labores de limpieza en la habitación
   - **Entonces** El sistema transiciona la habitación a `InCleaning`, crea la `CleaningTask` del usuario con la fecha y hora de inicio y lo redirige a la vista de tarea activa

2. **Escenario**: Limpieza preventiva
   - **Dado que** El miembro del personal de limpieza está autenticado, sin tarea activa, y existe una habitación "101" en estado `Available` que lleva tiempo sin ser asignada o usada
   - **Cuando** El miembro del personal de limpieza indica que va a iniciar labores en la habitación
   - **Entonces** El sistema realiza la misma transición a `InCleaning` y crea la `CleaningTask`, sin exigir que la habitación provenga de un check-out

3. **Escenario**: Habitación en estados distintos a `PendingCleaning` o `Available`
   - **Dado que** El miembro del personal de limpieza está autenticado y existe una habitación "101" que se encuentra en un estado distinto a los mencionados
   - **Cuando** El miembro del personal de limpieza intenta indicar que va a iniciar labores de limpieza en la habitación
   - **Entonces** El sistema rechaza la operación y muestra el estado actual de la habitación (FR-002)

---

### Historia de Usuario 2 - Liberar una limpieza que no puedo terminar (Prioridad: P2)

Como miembro del personal de limpieza, quiero poder dejar una limpieza que no podré terminar (fin de turno u otra prioridad), para que un compañero pueda iniciarla sin que nadie se quede con mi tarea sin mi consentimiento.

**Por qué esta prioridad**: Solo el titular puede cerrar su tarea (FR-009). Sin esta opción, una tarea que el titular no puede terminar dejaría la habitación sin salida.

**Prueba independiente**: Con la habitación "105" en `InCleaning` y la tarea de "Usuario B", pulsar como "Usuario B" "Liberar tarea", confirmar y verificar que la tarea queda cerrada como `Released`, que la habitación vuelve a `PendingCleaning` y que aparece en el panel de limpieza con "Iniciar limpieza" para cualquier miembro.

**Escenarios de aceptación**:

1. **Escenario**: El titular libera su tarea
   - **Dado que** "Usuario B" tiene la tarea activa de la habitación "105", en `InCleaning`
   - **Cuando** pulsa "Liberar tarea" en su vista de tarea activa y acepta la confirmación (texto definido en `spec-consultar-panel-limpieza.md`)
   - **Entonces** el sistema cierra su `CleaningTask` con resultado `Released`, devuelve la habitación a `PendingCleaning` mediante *Marcar pendiente a limpieza* y lleva a "Usuario B" al listado del panel

2. **Escenario**: Otro miembro continúa la limpieza
   - **Dado que** la habitación "105" volvió a `PendingCleaning` tras ser liberada
   - **Cuando** "Usuario A", sin tarea activa, pulsa "Iniciar limpieza" sobre ella
   - **Entonces** se aplica el flujo normal de la HU-1: la habitación pasa a `InCleaning` con una `CleaningTask` nueva para "Usuario A"

3. **Escenario**: Liberación por el Administrador (titular ausente)
   - **Dado que** "Usuario B" no está y su tarea en la habitación "105" sigue abierta
   - **Cuando** el Administrador pulsa "Liberar tarea" sobre la habitación "105" en *Consultar inventario de habitaciones* y confirma
   - **Entonces** se produce el mismo efecto que en el HU-2, escenario 1; si "Usuario B" vuelve a operar sobre esa tarea, el sistema le informa que fue liberada y lo devuelve al panel (FR-009)

---

### Casos Límite

1. **¿Qué ocurre si dos miembros del personal intentan iniciar la misma habitación a la vez?**
   Solo el primero procede; el segundo recibe un error con el estado actual (FR-011).

2. **¿Qué ocurre si se marca una habitación en limpieza y el personal no se autentica?**
   El sistema rechaza la operación y solicita autenticación (FR-007).

3. **¿Qué ocurre si la habitación fue liberada hace mucho tiempo y nadie la ha limpiado?**
   El sistema permite la limpieza sin ninguna restricción de tiempo.

4. **¿Qué ocurre si un miembro del personal que ya tiene una tarea activa intenta iniciar otra?**
   No es posible: mientras tenga una tarea activa solo ve la vista de tarea activa (FR-008).

5. **¿Puede un miembro tomar directamente la limpieza que otro tiene en curso?**
   No. Una habitación en `InCleaning` pertenece a su titular hasta que este la cierra o la libera (FR-005, FR-009); el panel no la ofrece a otros miembros.

6. **¿Qué pasa con los pendientes de la habitación al liberar la tarea?**
   Se conservan: la habitación vuelve a `PendingCleaning` con su reserva o bloqueo pendiente intactos.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al personal de limpieza autenticado marcar el inicio de labores de limpieza en una habitación y recibir retroalimentación visual de la acción que está realizando.
- **FR-002**: El sistema DEBE aceptar la marcación únicamente desde habitaciones que se encuentren en los estados `PendingCleaning` o `Available`; en cualquier otro estado la rechaza informando el estado actual de la habitación.
- **FR-003**: El sistema DEBE cambiar el estado de la habitación a `InCleaning` y crear la `CleaningTask` vinculada al miembro del personal autenticado con su fecha y hora de inicio.
- **FR-004**: El punto de entrada de este caso de uso es el **panel de limpieza**, definido en `spec-consultar-panel-limpieza.md` (qué habitaciones lista, columnas, orden, búsqueda, vista de tarea activa y textos). Desde él se inicia **Iniciar limpieza** (habitaciones en `PendingCleaning` o `Available`); desde la vista de tarea activa, **Liberar tarea**.
- **FR-005**: **Liberar tarea** permite al titular dejar una limpieza que no podrá terminar (fin de turno u otra prioridad). DEBE pedir confirmación y, al confirmarse, cerrar la `CleaningTask` con resultado `Released`, registrar quién la liberó y delegar la transición `InCleaning → PendingCleaning` en *Marcar pendiente a limpieza* (`<<includes>>`), en la misma operación. La habitación vuelve a aparecer en el panel con "Iniciar limpieza" para cualquier miembro y conserva sus pendientes. Solo puede ejecutarla el titular de la tarea o, si el titular está ausente, el Administrador desde *Consultar inventario de habitaciones*.
- **FR-006**: Tras iniciar una limpieza, y mientras su `CleaningTask` siga abierta, el miembro del personal solo accede a la vista de tarea activa (FR-008), definida en `spec-consultar-panel-limpieza.md`. Sus acciones son Confirmar fin (*Confirmar fin de limpieza de habitación*), Reportar daño (*Marcar habitación inhabilitada por reparaciones*) y Liberar tarea (FR-005).
- **FR-007**: Solo el Personal de limpieza autenticado DEBE poder iniciar una limpieza o liberar su propia tarea; el Administrador solo puede liberar tareas (FR-005). Si la sesión expiró o el rol no corresponde, el sistema NO DEBE ejecutar nada: pide iniciar sesión o informa que la acción no está permitida.
- **FR-008**: Una habitación DEBE tener como máximo una `CleaningTask` abierta y cada miembro del personal como máximo una tarea activa. El sistema DEBE rechazar "Iniciar limpieza" si el miembro ya tiene una tarea abierta o si la habitación ya tiene una.
- **FR-009**: Solo el titular DEBE poder cerrar su `CleaningTask` (confirmar el fin, reportar un daño o liberarla); ningún otro miembro puede quedarse con ella. La única excepción es la liberación por el Administrador (FR-005). Si el Administrador liberó la tarea y el titular intenta operar sobre ella, el sistema DEBE informarle "Tu tarea en la habitación [número] fue liberada por la administración." y devolverlo al panel.
- **FR-010**: `StartDateTime` y `EndDateTime` DEBEN generarse en el servidor en hora Colombia (UTC-5), ignorando cualquier hora enviada por el cliente. La hora de fin nunca es anterior a la de inicio, y una hora registrada no se modifica.
- **FR-011**: Si dos operaciones compiten sobre la misma habitación o la misma tarea (dos miembros inician la misma habitación, o el titular y el Administrador liberan la misma tarea), solo la primera DEBE proceder; la segunda se rechaza informando el estado actual de la habitación.
- **FR-012**: Iniciar (transición, `CleaningTask` e historial) y liberar (cierre de la tarea, *Marcar pendiente a limpieza* e historial) DEBEN confirmarse completos o no aplicar nada. Ante un fallo, la habitación y la tarea conservan su estado, se informa el error y el usuario puede reintentar.
- **FR-013**: Cada transición de la habitación DEBE registrarse en `RoomStateHistory` (estado previo y nuevo, fecha y hora, actor y flujo de origen) dentro de la misma operación.
- **FR-014**: El sistema NO DEBE incluir asignación ni reasignación de limpiezas por un supervisor: cada miembro inicia sus tareas desde el panel, y el Administrador solo puede liberarlas (FR-005).

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `PendingCleaning` o `Available` a `InCleaning` y, al liberar la tarea, de `InCleaning` a `PendingCleaning`.
- **CleaningTask**: Asignación de la limpieza de una habitación a un miembro del personal de limpieza. Almacena:
  - **RoomId**: Identificador de la habitación
  - **CleaningStaffMemberId**: Identificador del miembro del personal de limpieza (titular)
  - **StartDateTime**: Fecha y hora de inicio de labores de limpieza
  - **EndDateTime**: Fecha y hora de cierre de la tarea
  - **Outcome**: Resultado del cierre: `Completed` (fin confirmado), `DamageReported` (se reportó un daño) o `Released` (liberada por el titular o por el Administrador); vacío mientras la tarea está abierta
  - **ReleasedBy**: Solo si `Outcome = Released`: quién la liberó (el titular o el Administrador)

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Personal de limpieza puede iniciar o liberar una limpieza en menos de 15 segundos.
- **SC-002**: El 100% de las marcaciones crean la `CleaningTask` con el personal vinculado y su fecha y hora de inicio.
- **SC-003**: El cambio de estado se refleja en el inventario y en los paneles en menos de 2 segundos.
- **SC-004**: Ninguna habitación queda en `InCleaning` sin posibilidad de cierre: toda tarea abierta la cierra o libera su titular, o la libera el Administrador.
- **SC-005**: Cero tareas de limpieza cambian de titular sin haber sido liberadas antes.

> Los criterios de presentación del panel (orden, búsqueda, mensajes) se miden en `spec-consultar-panel-limpieza.md`.
