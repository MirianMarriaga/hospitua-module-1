# Especificación de Funcionalidad: Marcar Habitación como Reservada

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Módulo 2 (sistema externo)
**Creado**: 2026-09-21

---

## Use Case (Caso de Uso)

### Descripción del problema

En la operación integrada del hotel, el **Módulo 1** es el responsable exclusivo de digitalizar y custodiar el estado físico de las unidades habitacionales (`Room`), mientras que el **Módulo 2** administra el ciclo de vida de las reservas de los huéspedes. Cuando se concreta una reserva con asignación de unidad habitacional, el hotel necesita salvaguardar dicha habitación para evitar que sea asignada a otra estancia o intervenida por tareas preventivas no urgentes.

Para dar respuesta a esta necesidad sin acoplamientos innecesarios ni consultas continuas, la arquitectura del sistema incorpora el estado **`Reserved`** como uno de los 8 estados oficiales del ciclo de vida de `Room`, gobernado por el caso de uso **"Marcar habitación como reservada"**.

Este caso de uso posee una **naturaleza especial y 100% reactiva**:
1. **Disparador e integración**: No es iniciado por ningún actor humano (ni Recepcionista ni Administrador). Se invoca de forma autónoma por cada habitación de una reserva con `startDate` = hoy recibida desde el Módulo 2 por la cola de reservas diarias (caso de uso *Consultar reservas*): (a) al ingerir la lista diaria de las 00:00, (b) al recibir una actualización `ADDED` con `startDate` = hoy, o (c) cuando la habitación vuelve a `Available` al confirmarse el fin de su limpieza y su reserva del día sigue pendiente en la copia local (caso de uso *Confirmar fin de limpieza*, FR-011). En este documento, "evento de reserva" designa cada una de estas invocaciones.
2. **Sin polling**: El Módulo 1 **no realiza consultas activas periódicas (polling)** hacia el Módulo 2 para verificar si existen habitaciones por marcar; se limita a escuchar y reaccionar ante la llegada del evento.
3. **Transición permitida**: La única transición de estado autorizada en este caso de uso es estrictamente **`Available → Reserved`**.
4. **Idempotencia**: Dado que los entornos orientados a eventos pueden reenviar mensajes por políticas de reintento de la red, Módulo 1 debe procesar el evento de manera estrictamente **idempotente**: si llega el mismo evento para una habitación que ya se encuentra en `Reserved`, el sistema no debe fallar ni duplicar registros; debe reconocer el estado actual y confirmar la operación sin mutaciones redundantes.
5. **Manejo de conflictos de estado**: Si el evento de Módulo 2 arriba y la habitación no se encuentra en `Available` (por ejemplo, está físicamente `Occupied`, en mantenimiento `TechnicalBlock` / `DisabledForRepairs`, en limpieza `PendingCleaning` / `InCleaning`, o dada de baja `Inactive`), Módulo 1 **bajo ninguna circunstancia debe sobrescribir el estado físico real**. En su lugar, el sistema preserva el estado actual y registra formalmente una alerta de conflicto en la bitácora de auditoría para su análisis y trazabilidad, impidiendo el descarte silencioso del evento.
6. **Inmutabilidad manual**: El estado `Reserved` **nunca** se asigna manualmente desde ningún caso de uso de gestión directa de inventario (como "Registrar habitación", donde siempre nacen en `Available`, ni desde "Editar habitación").
7. **Compatibilidad con Check-In**: Conforme a la regla de admisión de Módulo 1, la habitación debe encontrarse en estado `Reserved` para iniciar y culminar el Check-In físico del huésped.

### Flujo de Integración de Alto Nivel

1. El sistema externo **Módulo 2** publica en la cola `m1.reservas.diarias.queue` la lista diaria de reservas de las 00:00 y sus actualizaciones del día; Módulo 1 las persiste en la copia local (caso de uso *Consultar reservas*).
2. Por cada habitación (`room_id`) de una reserva (`reservation_ref`) con `startDate` = hoy, el Módulo 1 invoca este caso de uso y valida los datos requeridos.
3. El Módulo 1 consulta el estado actual de la habitación en su inventario físico:
   - **Camino Exitoso (`Room.status == Available`)**: Módulo 1 transiciona de forma síncrona y atómica el estado de la habitación de `Available` a **`Reserved`**, vincula la `reservationRef` y genera un registro de auditoría de cambio de estado.
   - **Camino Idempotente (`Room.status == Reserved` con la misma `reservationRef`)**: Si la habitación ya está en `Reserved` para la misma reserva, Módulo 1 confirma la recepción sin aplicar una nueva transición ni duplicar registros de historial.
   - **Camino de Conflicto por otra reserva (`Room.status == Reserved` con distinta `reservationRef`)**: Si la habitación ya está en `Reserved` para una reserva diferente, Módulo 1 **no modifica** el estado ni la reserva vinculada y persiste un registro de conflicto (`RESERVATION_STATE_CONFLICT`) en la bitácora de auditoría.
   - **Camino de Conflicto (`Room.status != Available` y `!= Reserved`)**: Si la habitación se encuentra en cualquier otro estado (`Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`), Módulo 1 **no modifica** el estado de la habitación y persiste inmediatamente un registro de conflicto (`RESERVATION_STATE_CONFLICT`) en la bitácora de auditoría con todos los detalles contextuales.

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 (Integración) - Marcado reactivo de habitación como Reserved ante evento de Módulo 2 (Prioridad: P1)

Como **Módulo 1**, quiero recibir y procesar el evento de reserva emitido por Módulo 2 transicionando la habitación disponible al estado "Reserved", para reflejar en el inventario físico que la unidad tiene una reserva asociada y salvaguardarla de otras asignaciones.

**Por qué esta prioridad**: Es la base del control de aforo e inventario entre reservas y cuartos físicos. Garantiza que las unidades asignadas queden identificadas como `Reserved` en tiempo real de forma reactiva y atómica, sin requerir intervención manual ni sobrecargar la arquitectura con consultas síncronas periódicas (polling).

**Prueba Independiente**: Puede probarse de forma aislada simulando la emisión de un evento desde Módulo 2 con el `roomId` de una habitación en estado `Available` y una referencia de reserva válida. Se comprueba que la habitación cambia atómicamente su estado a `Reserved`, se asocia la referencia de reserva y queda registrada en el inventario general con su trazabilidad de auditoría.

**Escenarios de Aceptación**:

*Escenarios de Éxito (Happy Path)*

1. **Escenario**: Transición exitosa de Available a Reserved ante evento de Módulo 2
   - **Dado** que existe una habitación registrada en Módulo 1 en estado físico `Available`
   - **Cuando** Módulo 1 ingiere desde la cola de reservas diarias de Módulo 2 una reserva con `startDate` = hoy que asigna esa habitación (`roomId`) con la referencia `reservationRef`
   - **Entonces** el sistema actualiza de forma atómica el estado de la habitación a `Reserved`, asocia la `reservationRef` para trazabilidad y registra el cambio de estado en la bitácora de auditoría

2. **Escenario**: Habitación en estado Reserved es apta para Check-In
   - **Dado** una habitación que se encuentra en estado físico `Reserved` vinculada a una reserva activa
   - **Cuando** el Recepcionista inicia el proceso de Check-In formal en Módulo 1
   - **Entonces** el sistema autoriza la admisión física reconociendo `Reserved` como un estado precondición válido para transicionar hacia `Occupied`

---

### Historia de Usuario 2 (Integración) - Procesamiento idempotente y gestión de conflictos de estado (Prioridad: P1)

Como **Módulo 1**, quiero procesar los eventos de forma idempotente ante reintentos de red y registrar los conflictos de estado sin sobrescribir la situación física real de la habitación, para garantizar la consistencia de la máquina de estados y permitir la trazabilidad operativa.

**Por qué esta prioridad**: En arquitecturas distribuidas y orientadas a eventos, las entregas duplicadas son habituales y los descalces de tiempo pueden generar colisiones (por ejemplo, una habitación que sigue ocupada por una estancia anterior cuando llega el evento). Garantizar la idempotencia evita corrupciones en el historial, y rechazar la sobreescritura protege la verdad física de lo que ocurre en el hotel.

**Prueba Independiente**: Se prueba enviando dos veces consecutivas el mismo evento de reserva para verificar idempotencia sin errores, y enviando eventos de reserva hacia habitaciones en cada uno de los otros estados operativos (`Occupied`, `InCleaning`, `TechnicalBlock`, etc.), verificando que el estado físico no se modifique y que se genere un registro explícito de conflicto en auditoría.

**Escenarios de Aceptación**:

*Escenarios de Idempotencia y Manejo de Conflictos*

3. **Escenario**: Procesamiento idempotente ante evento duplicado de Módulo 2
   - **Dado** una habitación que ya se encuentra en estado `Reserved` vinculada a la reserva "RES-101"
   - **Cuando** Módulo 2 reenvía el mismo evento de reserva para la misma habitación y reserva "RES-101"
   - **Entonces** el sistema detecta que la habitación ya se encuentra en estado `Reserved` para esa reserva, no aplica una nueva transición, no altera el inventario y confirma el procesamiento satisfactorio sin duplicar registros en la bitácora

4. **Escenario**: Conflicto de estado por habitación físicamente ocupada
   - **Dado** una habitación que se encuentra en estado físico `Occupied`
   - **Cuando** Módulo 1 recibe un evento de Módulo 2 solicitando marcar dicha habitación como reservada
   - **Entonces** el sistema **no sobrescribe** el estado `Occupied`, rechaza la transición y persiste un registro de conflicto de tipo `RESERVATION_STATE_CONFLICT` en la bitácora de auditoría detallando el `roomId`, el estado real `Occupied` y la `reservationRef` entrante

5. **Escenario**: Conflicto de estado por habitación en ciclo de aseo, mantenimiento o dada de baja
   - **Dado** una habitación en estado operativo `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`
   - **Cuando** Módulo 1 recibe un evento de Módulo 2 para marcarla como reservada
   - **Entonces** el sistema preserva el estado físico operativo actual, no altera la habitación a `Reserved` y registra el conflicto en la auditoría para análisis operativo

6. **Escenario**: Conflicto por habitación ya reservada para otra reserva
   - **Dado** una habitación que ya se encuentra en estado `Reserved` vinculada a la reserva "RES-101"
   - **Cuando** Módulo 1 recibe un evento de Módulo 2 para marcarla como reservada con la reserva "RES-202"
   - **Entonces** el sistema no cambia el estado ni la reserva vinculada ("RES-101") y registra un conflicto `RESERVATION_STATE_CONFLICT` en la bitácora de auditoría con ambas referencias

7. **Escenario**: Rechazo de evento con identificador de habitación inexistente
   - **Dado** un evento emitido por Módulo 2 cuyo `roomId` no corresponde a ninguna habitación registrada en el catálogo de Módulo 1
   - **Cuando** Módulo 1 intenta procesar el evento
   - **Entonces** el sistema rechaza la operación, no altera ninguna entidad y registra una advertencia de recurso no encontrado en el registro del sistema

---

### Casos Borde

- **Idempotencia ante reenvío de eventos por fallas de transporte**: Si la capa de mensajería o Módulo 2 reintenta la entrega de un evento debido a un retraso de confirmación previa, Módulo 1 valida si la habitación ya posee el estado `Reserved` con la misma `reservationRef`. En caso afirmativo, la operación se da por completada con éxito sin realizar escrituras redundantes ni disparar nuevos eventos en el historial de estados.
- **Colisión de estado con ocupación previa, mantenimiento o baja**: Si la habitación asignada en Módulo 2 se encuentra en `Occupied`, `TechnicalBlock`, `DisabledForRepairs` o `Inactive`, Módulo 1 respeta la realidad física del hotel y no permite que un evento lógico de reserva altere dicho estado. El conflicto se registra de forma obligatoria en la bitácora de auditoría (`RESERVATION_STATE_CONFLICT`) con fecha, hora, estado actual y referencia de reserva, impidiendo el descarte silencioso del incidente.
- **Llegada de eventos concurrentes para la misma habitación**: Si ingresan simultáneamente dos eventos intentando reservar la misma habitación, el bloqueo transaccional a nivel de persistencia garantiza que solo el primero que valide la precondición `Available` ejecutará la transición a `Reserved`; el segundo detectará el nuevo estado y será tratado bajo las reglas de idempotencia o conflicto según corresponda.
- **Momento de la invocación**: La habitación se aparta a las 00:00 del día de llegada, al ingerirse la lista diaria, o en el momento en que se recibe una actualización `ADDED` con `startDate` = hoy. Hasta entonces permanece en `Available` y puede pasar por limpieza, mantenimiento o bloqueo técnico. Si a esa hora la habitación no está en `Available`, se aplican las reglas de conflicto; si está en limpieza, se reintenta al confirmarse el fin de la limpieza (Disparador, literal c).
- **Interrupción de conectividad o falla de persistencia**: Si ocurre una falla en el motor de base de datos durante la actualización, la transacción se cancela atómicamente (rollback), garantizando que la habitación permanezca en su estado original sin inconsistencias intermedias.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: Naturaleza reactiva y actor: El caso de uso DEBE ser ejecutado de forma autónoma por Módulo 1 únicamente en las invocaciones descritas en el Disparador (lista diaria de las 00:00 y actualizaciones `ADDED` con `startDate` = hoy recibidas desde **Módulo 2** por la cola de reservas diarias, y reintento al confirmarse el fin de limpieza). Ningún actor humano (Recepcionista, Personal de mantenimiento o Administrador) podrá invocar este caso de uso de manera directa o manual.
- **FR-002**: Ausencia de polling: El sistema **NO DEBE** implementar mecanismos de sondeo continuo o periódico (polling) hacia el Módulo 2 para consultar habitaciones por reservar; el flujo DEBE ser 100% dirigido por los mensajes entrantes de la cola de reservas diarias.
- **FR-003**: Datos requeridos: El sistema DEBE validar que cada invocación cuente obligatoriamente con el identificador de la habitación (`roomId`, tomado de `room_id` de la copia local) y la referencia única de la reserva (`reservationRef`, tomada de `reservation_ref`); la marca de tiempo de la transición la genera el servidor.
- **FR-004**: Precondición física obligatoria: El sistema DEBE validar que la habitación se encuentre estrictamente en estado **`Available`** dentro del catálogo de 8 estados de Módulo 1 antes de autorizar la transición.
- **FR-005**: Transición atómica de estado: Al cumplirse la precondición, el sistema DEBE actualizar de forma atómica y síncrona el atributo `Room.status` de `Available` a **`Reserved`**, asociando la `reservationRef` para efectos de trazabilidad.
- **FR-006**: Comportamiento idempotente: Si el sistema recibe un evento para una habitación que ya se encuentra en estado `Reserved` asociada a la misma `reservationRef`, DEBE procesar la solicitud como exitosa sin modificar el estado de la habitación, sin provocar errores y sin generar registros duplicados en el historial de transiciones.
- **FR-007**: Rechazo ante estados no aptos: Si la habitación se encuentra en cualquiera de los otros 6 estados operativos (`Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`), o en estado `Reserved` asociada a una `reservationRef` distinta a la del evento, el sistema **NO DEBE** sobrescribir ni modificar el estado físico actual de la habitación ni la reserva vinculada.
- **FR-008**: Registro obligatorio de conflicto: Ante el rechazo previsto en FR-007, el sistema DEBE generar y persistir de inmediato un registro de auditoría de tipo `RESERVATION_STATE_CONFLICT` con el `roomId`, el estado operativo actual de la habitación, la `reservationRef` entrante, la `reservationRef` ya vinculada (cuando la habitación esté en `Reserved`) y la marca de tiempo, garantizando que el conflicto no sea descartado en silencio.
- **FR-009**: Prohibición de asignación manual: El estado `Reserved` **NO DEBE** ser seleccionable ni asignable manualmente desde los casos de uso "Registrar habitación", "Editar habitación", "Marcar habitación como disponible" ni "Confirmar fin de limpieza". La invocación autónoma que realiza "Confirmar fin de limpieza" (Disparador, literal c) no constituye una asignación manual.
- **FR-010**: Habilitación para Check-In: Toda habitación que se encuentre en estado `Reserved` DEBE ser reconocida por el caso de uso "Registrar Check-In" como un estado de origen válido para completar la admisión física y transicionar hacia `Occupied`.
- **FR-011**: Auditoría de transición: El sistema DEBE registrar en `RoomStateHistory` cada transición exitosa a `Reserved`, incluyendo el `roomId`, el estado previo (`Available`), el estado resultante (`Reserved`), la `reservationRef` y la marca de tiempo exacta.

### Requisitos No Funcionales

- **NFR-001**: El procesamiento interno del evento y la actualización atómica del estado a `Reserved` deben completarse en un tiempo inferior a 200 milisegundos a partir de la recepción del mensaje en Módulo 1.
- **NFR-002**: El componente de ingesta de eventos debe operar de forma no bloqueante y garantizar la idempotencia lógica de extremo a extremo sin degradar el rendimiento del inventario de habitaciones.
- **NFR-003**: Toda excepción por datos mal estructurados o colisiones debe capturarse y registrarse como error controlado en la bitácora, evitando fallos no controlados del servicio de base de datos.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Entidad que representa la unidad física de hospedaje gestionada centralmente por el Módulo 1.
  - *Atributos clave*: `roomId` (UUID), número de habitación, piso, tipo, capacidad máxima (`maxCapacity`), tarifa base, `status` y `currentReservationRef` (referencia externa de reserva asociada).
  - *Catálogo oficial (8 estados vigentes)*: `Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`.
  - *Comportamiento en este caso de uso*: Transiciona exclusivamente de `Available` a `Reserved`.
- **Module2 (Actor Externo / Sistema)**: Módulo de Reservas y Cumplimiento Legal que publica la lista diaria de reservas y sus actualizaciones, origen de la asignación de habitación.
- **Reservation (Referencia Externa de Módulo 2)**: Entidad de reserva externa consumida solo por referencia (`reservationRef`) para vincular el compromiso comercial a la unidad física.
- **RoomStateHistory**: Historial común de transiciones de estado definido en documentos/SPEC/referencias/maquina-estados-habitacion.md. Registra cada transición exitosa `Available → Reserved`.
- **Bitácora de auditoría**: Registra los eventos de discrepancia operativa (`RESERVATION_STATE_CONFLICT`), que no generan transición.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de los eventos válidos de Módulo 2 recibidos para habitaciones en estado `Available` transicionan la habitación a `Reserved` en menos de 200 milisegundos.
- **SC-002**: El 100% de los eventos duplicados recibidos para habitaciones ya en estado `Reserved` son procesados de forma idempotente sin arrojar errores ni duplicar transiciones.
- **SC-003**: Cero sobreescrituras indebidas de habitaciones en estados físicos `Occupied`, `InCleaning`, `TechnicalBlock`, `DisabledForRepairs`, `PendingCleaning` o `Inactive` ante eventos de reserva.
- **SC-004**: El 100% de los conflictos de estado, incluidos los eventos para habitaciones ya reservadas con otra reserva, quedan persistidos en la bitácora de auditoría con identificación de habitación, estado físico actual y referencia de reserva.
- **SC-005**: Cero habitaciones registradas o editadas manualmente en estado `Reserved` mediante formularios de usuario.