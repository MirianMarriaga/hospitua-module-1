# Especificación de Funcionalidad: Marcar Habitación como Reservada

**Creado**: 2026-09-21  
**Módulo Propietario**: Módulo 1 — Gestión de Habitaciones e Inventario de Aforo, Check-In y Check-Out  
**Actor Principal**: Módulo 2 (Actor externo / Sistema)  

---

## Use Case (Caso de Uso)

### Descripción del problema

En la operación integrada del hotel, el **Módulo 1** es el responsable exclusivo de digitalizar y custodiar el estado físico de las unidades habitacionales (`Room`), mientras que el **Módulo 2** administra el ciclo de vida de las reservas de los huéspedes. Cuando se concreta una reserva con asignación de unidad habitacional, el hotel necesita salvaguardar dicha habitación para evitar que sea ofrecida a clientes sin reserva previa (walk-in) en recepción o intervenida por tareas preventivas no urgentes.

Para dar respuesta a esta necesidad sin acoplamientos innecesarios ni consultas continuas, la arquitectura del sistema incorpora el estado **`Reserved`** como el 8vo estado oficial del ciclo de vida de `Room`, gobernado por el caso de uso **"Marcar habitación como reservada"**.

Este caso de uso posee una **naturaleza especial y 100% reactiva**:
1. **Disparador e integración**: No es iniciado por ningún actor humano (ni Recepcionista ni Administrador). Su único disparador es un **evento asíncrono emitido por el Módulo 2** cuando corresponde marcar una habitación como reservada.
2. **Sin polling**: El Módulo 1 **no realiza consultas activas periódicas (polling)** hacia el Módulo 2 para verificar si existen habitaciones por marcar; se limita a escuchar y reaccionar ante la llegada del evento.
3. **Transición permitida**: La única transición de estado autorizada en este caso de uso es estrictamente **`Available → Reserved`**.
4. **Idempotencia**: Dado que los entornos orientados a eventos pueden reenviar mensajes por políticas de reintento de la red, Módulo 1 debe procesar el evento de manera estrictamente **idempotente**: si llega el mismo evento para una habitación que ya se encuentra en `Reserved`, el sistema no debe fallar ni duplicar registros; debe reconocer el estado actual y confirmar la operación sin mutaciones redundantes.
5. **Manejo de conflictos de estado**: Si el evento de Módulo 2 arriba y la habitación no se encuentra en `Available` (por ejemplo, está físicamente `Occupied`, en mantenimiento `TechnicalBlock` / `DisabledForRepairs`, en limpieza `PendingCleaning` / `InCleaning`, o dada de baja `Inactive`), Módulo 1 **bajo ninguna circunstancia debe sobrescribir el estado físico real**. En su lugar, el sistema preserva el estado actual y registra formalmente una alerta de conflicto en la bitácora de auditoría para su análisis y trazabilidad, impidiendo el descarte silencioso del evento.
6. **Inmutabilidad manual**: El estado `Reserved` **nunca** se asigna manualmente desde ningún caso de uso de gestión directa de inventario (como "Registrar habitación", donde siempre nacen en `Available`, ni desde "Editar habitación").
7. **Compatibilidad con Check-In**: Conforme a la regla de admisión de Módulo 1, la habitación debe encontrarse en estado `Reserved` para iniciar y culminar el Check-In físico del huésped.

### Flujo de Integración de Alto Nivel

1. El sistema externo **Módulo 2** emite una orden de estado (`RoomStateRequest`) hacia Módulo 1 con los campos: `requestId`, `roomId`, `requestedStatus` (`Reserved`), `previousStatus` (`Available`), `originEvent`, `reservationRef`, `sequenceNumber` (creciente único por habitación), `requestedAt` y `requestedBy` (acuerdo B7).
2. El Módulo 1 recibe la orden y valida la estructura de los datos requeridos.
3. El Módulo 1 consulta el estado actual de la habitación en su inventario físico:
   - **Camino Exitoso (`Room.status == Available`)**: Módulo 1 transiciona de forma síncrona y atómica el estado de la habitación de `Available` a **`Reserved`**, vincula la `reservationRef` y genera un registro de auditoría de cambio de estado.
   - **Camino Idempotente (`Room.status == Reserved`)**: Si la habitación ya está en `Reserved` para la misma reserva, Módulo 1 confirma la recepción sin aplicar una nueva transición ni duplicar registros de historial.
   - **Camino de Conflicto (`Room.status != Available` y `!= Reserved`)**: Si la habitación se encuentra en cualquier otro estado (`Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`), Módulo 1 **no modifica** el estado de la habitación y persiste inmediatamente un registro de conflicto (`RESERVATION_STATE_CONFLICT`) en la bitácora de auditoría con todos los detalles contextuales.

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 (Integración) - Marcado reactivo de habitación como Reserved ante evento de Módulo 2 (Prioridad: P1)

Como **Módulo 1**, quiero recibir y procesar el evento de reserva emitido por Módulo 2 transicionando la habitación disponible al estado "Reserved", para reflejar en el inventario físico que la unidad tiene una reserva asociada y salvaguardarla de otras asignaciones.

**Por qué esta prioridad**: Es la base del control de aforo e inventario entre reservas y cuartos físicos. Garantiza que las unidades asignadas queden identificadas como `Reserved` en tiempo real de forma reactiva y atómica, sin requerir intervención manual ni sobrecargar la arquitectura con consultas síncronas periódicas (polling).

**Prueba Independiente**: Puede probarse de forma aislada simulando la emisión de un evento desde Módulo 2 con el `habitationId` de una habitación en estado `Available` y una referencia de reserva válida. Se comprueba que la habitación cambia atómicamente su estado a `Reserved`, se asocia la referencia de reserva y queda registrada en el inventario general con su trazabilidad de auditoría.

**Escenarios de Aceptación**:

*Escenarios de Éxito (Happy Path)*

1. **Escenario**: Transición exitosa de Available a Reserved ante evento de Módulo 2
   - **Dado** que existe una habitación registrada en Módulo 1 en estado físico `Available`
   - **Cuando** el Módulo 2 emite y entrega a Módulo 1 el evento de asignación de reserva con el `habitationId` y la referencia `reservationRef`
   - **Entonces** el sistema actualiza de forma atómica el estado de la habitación a `Reserved`, asocia la `reservationRef` para trazabilidad y registra el cambio de estado en la bitácora de auditoría

2. **Escenario**: Reflejo inmediato del estado Reserved en la consulta de inventario
   - **Dado** que una habitación ha transicionado a `Reserved` tras procesar el evento de Módulo 2
   - **Cuando** el Administrador o el Recepcionista consulta el inventario operativo de habitaciones
   - **Entonces** la habitación refleja de inmediato el estado `Reserved`, indicando la referencia de la reserva asociada

3. **Escenario**: Habitación en estado Reserved es apta para Check-In
   - **Dado** una habitación que se encuentra en estado físico `Reserved` vinculada a una reserva activa
   - **Cuando** el Recepcionista inicia el proceso de Check-In formal en Módulo 1
   - **Entonces** el sistema autoriza la admisión física reconociendo `Reserved` como un estado precondición válido para transicionar hacia `Occupied`

---

### Historia de Usuario 2 (Integración) - Procesamiento idempotente y gestión de conflictos de estado (Prioridad: P1)

Como **Módulo 1**, quiero procesar los eventos de forma idempotente ante reintentos de red y registrar los conflictos de estado sin sobrescribir la situación física real de la habitación, para garantizar la consistencia de la máquina de estados y permitir la trazabilidad operativa.

**Por qué esta prioridad**: En arquitecturas distribuidas y orientadas a eventos, las entregas duplicadas son habituales y los descalces de tiempo pueden generar colisiones (por ejemplo, una habitación ocupada por un walk-in antes de procesar el evento). Garantizar la idempotencia evita corrupciones en el historial, y rechazar la sobreescritura protege la verdad física de lo que ocurre en el hotel.

**Prueba Independiente**: Se prueba enviando dos veces consecutivas el mismo evento de reserva para verificar idempotencia sin errores, y enviando eventos de reserva hacia habitaciones en cada uno de los otros estados operativos (`Occupied`, `InCleaning`, `TechnicalBlock`, etc.), verificando que el estado físico no se modifique y que se genere un registro explícito de conflicto en auditoría.

**Escenarios de Aceptación**:

*Escenarios de Idempotencia y Manejo de Conflictos*

4. **Escenario**: Procesamiento idempotente ante evento duplicado de Módulo 2
   - **Dado** una habitación que ya se encuentra en estado `Reserved` vinculada a la reserva "RES-101"
   - **Cuando** Módulo 2 reenvía el mismo evento de reserva para la misma habitación y reserva "RES-101"
   - **Entonces** el sistema detecta que la habitación ya se encuentra en estado `Reserved` para esa reserva, no aplica una nueva transición, no altera el inventario y confirma el procesamiento satisfactorio sin duplicar registros en la bitácora

5. **Escenario**: Conflicto de estado por habitación físicamente ocupada
   - **Dado** una habitación que se encuentra en estado físico `Occupied`
   - **Cuando** Módulo 1 recibe un evento de Módulo 2 solicitando marcar dicha habitación como reservada
   - **Entonces** el sistema **no sobrescribe** el estado `Occupied`, rechaza la transición y persiste un registro de conflicto de tipo `RESERVATION_STATE_CONFLICT` en la bitácora de auditoría detallando el `habitationId`, el estado real `Occupied` y la `reservationRef` entrante

6. **Escenario**: Conflicto de estado por habitación en ciclo de aseo o mantenimiento
   - **Dado** una habitación en estado operativo `PendingCleaning`, `InCleaning`, `DisabledForRepairs` o `TechnicalBlock`
   - **Cuando** Módulo 1 recibe un evento de Módulo 2 para marcarla como reservada
   - **Entonces** el sistema preserva el estado físico operativo actual, no altera la habitación a `Reserved` y registra el conflicto en la auditoría para análisis operativo

7. **Escenario**: Rechazo de evento con identificador de habitación inexistente
   - **Dado** un evento emitido por Módulo 2 cuyo `habitationId` no corresponde a ninguna habitación registrada en el catálogo de Módulo 1
   - **Cuando** Módulo 1 intenta procesar el evento
   - **Entonces** el sistema rechaza la operación, no altera ninguna entidad y registra una advertencia de recurso no encontrado en el registro del sistema

---

### Casos Borde

- **Idempotencia ante reenvío de órdenes por fallas de transporte** (acuerdo B7): Si Módulo 2 reintenta la entrega de una `RoomStateRequest` con el mismo `requestId`, Módulo 1 valida si ya fue procesada exitosamente y retorna el mismo resultado sin aplicar la transición nuevamente ni duplicar registros en el historial de estados.
- **Colisión de estado con ocupación previa (Walk-in o mantenimiento)**: Si la habitación asignada en Módulo 2 se encuentra en `Occupied`, `TechnicalBlock` o `DisabledForRepairs`, Módulo 1 respeta la realidad física del hotel y no permite que una orden lógica de reserva altere dicho estado. Módulo 1 rechaza la orden con motivo `ROOM_OCCUPIED` o el estado real y registra el conflicto de forma obligatoria en la bitácora de auditoría (`RESERVATION_STATE_CONFLICT`) con fecha, hora, estado actual y referencia de reserva, impidiendo el descarte silencioso del incidente.
- **Órdenes en secuencia** (acuerdo B7): Módulo 2 envía las órdenes de una misma habitación de una en una, en orden, sin enviar la N+1 hasta que la N esté `COMPLETED` o `REJECTED`. Si llega una orden con `sequenceNumber` menor o igual al último aplicado, Módulo 1 la descarta con motivo `OBSOLETE`.
- **Consulta del resultado de una orden por `requestId`** (acuerdo B7): Módulo 2 puede consultar el resultado de una `RoomStateRequest` por su `requestId` para resolver casos donde el resultado quedó ambiguo (timeout, falla de red). Módulo 1 DEBE exponer este mecanismo de consulta.
- **Momento exacto del disparo de la orden por Módulo 2** (resuelto B1/B14): Módulo 2 envía la orden `Reserved` en dos momentos: (a) inmediatamente al crear una reserva cuya fecha de llegada es el día operativo actual, o (b) a las 00:00 del día de llegada (hora Colombia, UTC-5) para las reservas cuyo `startDate` sea ese día. Módulo 1 procesa la orden en el momento exacto en que es recibida.
- **Llegada de órdenes concurrentes para la misma habitación**: Si ingresan simultáneamente dos órdenes intentando reservar la misma habitación, el bloqueo transaccional a nivel de persistencia garantiza que solo la primera que valide la precondición `Available` ejecutará la transición a `Reserved`; la segunda detectará el nuevo estado y será tratada bajo las reglas de idempotencia o conflicto según corresponda.
- **Interrupción de conectividad o falla de persistencia**: Si ocurre una falla en el motor de base de datos durante la actualización, la transacción se cancela atómicamente (rollback), garantizando que la habitación permanezca en su estado original sin inconsistencias intermedias.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: Naturaleza reactiva y actor: El caso de uso DEBE ser iniciado y ejecutado exclusivamente ante la recepción de un evento asíncrono emitido por el actor **Módulo 2 (actor externo / sistema)**. Ningún actor humano (Recepcionista, Personal de mantenimiento o Administrador) podrá invocar este caso de uso de manera directa o manual.
- **FR-002**: Ausencia de polling: El sistema **NO DEBE** implementar mecanismos de sondeo continuo o periódico (polling) hacia el Módulo 2 para consultar habitaciones por reservar; el flujo DEBE ser 100% dirigido por eventos entrantes.
- **FR-003**: Datos requeridos de la orden: El sistema DEBE validar que la `RoomStateRequest` recibida desde Módulo 2 contenga obligatoriamente los campos acordados en B7: `requestId` (UUID único de la orden), `roomId` (identificador de la habitación), `requestedStatus` (`Reserved`), `previousStatus` (`Available`), `originEvent`, `reservationRef`, `sequenceNumber` (creciente único por habitación), `requestedAt` y `requestedBy`.
- **FR-004**: Precondición física obligatoria: El sistema DEBE validar que la habitación se encuentre estrictamente en estado **`Available`** dentro del catálogo de 8 estados de Módulo 1 antes de autorizar la transición.
- **FR-005**: Transición atómica de estado: Al cumplirse la precondición, el sistema DEBE actualizar de forma atómica y síncrona el atributo `Room.stateHabitation` de `Available` a **`Reserved`**, asociando la `reservationRef` para efectos de trazabilidad.
- **FR-006**: Comportamiento idempotente por `requestId`: Si el sistema recibe una `RoomStateRequest` con un `requestId` que ya fue procesado exitosamente, DEBE retornar el mismo resultado sin modificar el estado de la habitación, sin provocar errores y sin generar registros duplicados en el historial de transiciones.
- **FR-006b**: Comportamiento idempotente por estado: Si la habitación ya se encuentra en `Reserved` asociada a la misma `reservationRef`, DEBE procesar la solicitud como exitosa sin modificar el estado.
- **FR-007**: Rechazo ante estados no aptos: Si la habitación se encuentra en cualquiera de los otros 6 estados operativos (`Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`), el sistema **NO DEBE** sobrescribir ni modificar el estado físico actual de la habitación.
- **FR-008**: Registro obligatorio de conflicto: Ante el rechazo previsto en FR-007, el sistema DEBE generar y persistir de inmediato un registro de auditoría de tipo `RESERVATION_STATE_CONFLICT` con el `habitationId`, el estado operativo actual de la habitación, la `reservationRef` involucrada y la marca de tiempo, garantizando que el conflicto no sea descartado en silencio.
- **FR-009**: Prohibición de asignación manual: El estado `Reserved` **NO DEBE** ser seleccionable ni asignable manualmente desde los casos de uso "Registrar habitación", "Editar habitación", "Marcar habitación como disponible" ni "Confirmar fin de limpieza".
- **FR-010**: Habilitación para Check-In: Toda habitación que se encuentre en estado `Reserved` DEBE ser reconocida por el caso de uso "Registrar Check-In" como un estado de origen válido para completar la admisión física y transicionar hacia `Occupied`.
- **FR-011**: Auditoría de transición: El sistema DEBE registrar en la bitácora de auditoría cada transición exitosa a `Reserved`, incluyendo el `roomId`, el estado previo (`Available`), el estado resultante (`Reserved`), la `reservationRef`, el `requestId` y la marca de tiempo exacta.
- **FR-012**: Consulta de resultado por `requestId` (acuerdo B7): El sistema DEBE exponer un mecanismo para que Módulo 2 consulte el resultado de una `RoomStateRequest` por su `requestId`, devolviendo el estado actual (`COMPLETED`, `REJECTED`, `PENDING`) para resolver ambigüedades causadas por timeout o fallas de red.

### Requisitos No Funcionales

- **NFR-001**: El procesamiento interno del evento y la actualización atómica del estado a `Reserved` deben completarse en un tiempo inferior a 200 milisegundos a partir de la recepción del mensaje en Módulo 1.
- **NFR-002**: El componente de ingesta de eventos debe operar de forma no bloqueante y garantizar la idempotencia lógica de extremo a extremo sin degradar el rendimiento del inventario de habitaciones.
- **NFR-003**: Toda excepción por datos mal estructurados o colisiones debe capturarse y registrarse como error controlado en la bitácora, evitando fallos no controlados del servicio de base de datos.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room / Habitation**: Entidad que representa la unidad física de hospedaje gestionada centralmente por el Módulo 1.
  - *Atributos clave*: `habitationId` (UUID), `numberHabitation`, `floorWing`, `categoryHabitation`, `maxCapacity`, `baseRate`, `stateHabitation`, y `currentReservationRef` (referencia externa de reserva asociada).
  - *Catálogo oficial (8 estados vigentes)*: `Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`.
  - *Comportamiento en este caso de uso*: Transiciona exclusivamente de `Available` a `Reserved`.
- **Module2 (Actor Externo / Sistema)**: Módulo de Reservas y Cumplimiento Legal que actúa como emisor asíncrono del evento de asignación de habitación.
- **Reservation (Referencia Externa de Módulo 2)**: Entidad de reserva externa consumida solo por referencia (`reservationRef`) para vincular el compromiso comercial a la unidad física.
- **RoomStateAudit**: Bitácora de auditoría del inventario. Registra las transiciones exitosas a `Reserved` y los eventos de discrepancia operativa (`RESERVATION_STATE_CONFLICT`).

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de los eventos válidos de Módulo 2 recibidos para habitaciones en estado `Available` transicionan la habitación a `Reserved` en menos de 200 milisegundos.
- **SC-002**: El 100% de los eventos duplicados recibidos para habitaciones ya en estado `Reserved` son procesados de forma idempotente sin arrojar errores ni duplicar transiciones.
- **SC-003**: Cero sobreescrituras indebidas de habitaciones en estados físicos `Occupied`, `InCleaning`, `TechnicalBlock`, `DisabledForRepairs`, `PendingCleaning` o `Inactive` ante eventos de reserva.
- **SC-004**: El 100% de los conflictos de estado quedan persistidos en la bitácora de auditoría con identificación de habitación, estado físico actual y referencia de reserva.
- **SC-005**: Cero habitaciones registradas o editadas manualmente en estado `Reserved` mediante formularios de usuario.