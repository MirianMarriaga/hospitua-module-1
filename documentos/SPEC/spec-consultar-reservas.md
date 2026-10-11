# Especificación de Funcionalidad: Consultar Reservas

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Sistema Módulo 1 (Autónomo) / Recepcionista / Personal de mantenimiento / Administrador
**Creado**: 2026-09-25
**Actualizado**: 2026-10-09

---

## Use Case (Caso de Uso)

### Descripción del problema

El Módulo 1 requiere consultar reservas para dos propósitos operativos claramente diferenciados:

1. **Recepción (Check-In y Panel de Llegadas)**: Conocer las reservas del día actual. Para garantizar alta disponibilidad e independencia operativa en mostrador, el Módulo 1 **no realiza consultas REST para llegadas ni para Check-In**. En su lugar, recibe las reservas del día de forma asíncrona mediante mensajería y las mantiene en una **copia local**.
2. **Mantenimiento y Administración (Bloqueo técnico y Baja de habitación)**: Consultar al Módulo 2 si un rango de fechas se cruza con alguna reserva futura asignada a una habitación específica antes de intervenirla físicamente. Para esto se realiza una **consulta REST directa al Módulo 2**, siendo esta la única consulta REST a reservas del sistema.

Existen por lo tanto dos modos de consulta:

- **Modo Local**: La Recepcionista consulta y busca en la copia local que llega cada día por cola.
- **Modo REST**: El Personal de mantenimiento y el Administrador consultan directamente al Módulo 2 por habitación y rango de fechas.

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Recepción automática de la lista diaria y actualizaciones asíncronas (Prioridad: P1)

Como **Sistema autónomo de Módulo 1**, quiero recibir la lista diaria de reservas a las 00:00 y sus actualizaciones continuas de forma asíncrona para mantener sincronizada la copia local de llegadas del día sin intervención humana.

**Por qué esta prioridad**: Alimenta la copia local que sustenta la operación de recepción y Check-In, eliminando llamadas síncronas por red durante la atención al huésped.

**Prueba Independiente**: Procesar la recepción de la lista diaria a las 00:00, verificar la purga de registros del día anterior, la persistencia en `daily_reservation` y `daily_reservation_room`, el descarte de duplicados por identificador de mensaje y la aplicación ordenada por número de secuencia de las actualizaciones posteriores.

**Escenarios de Aceptación**:

1. **Escenario**: Ingesta de lista diaria de las 00:00 y purga de la copia anterior
   - **Dado** que son las 00:00 (hora de Colombia, UTC-5)
   - **Cuando** arriba la notificación de lista diaria inicial (con `sequenceNumber = 1`) conteniendo únicamente reservas en estado `ACTIVE` con `startDate = hoy`
   - **Entonces** el sistema purga completamente los datos de la copia local del día anterior (`daily_reservation` y `daily_reservation_room`), reinicia el contador de `sequenceNumber`, persiste las nuevas reservas y registra el `messageId` en `daily_reservation_message_log`.

2. **Escenario**: Procesamiento ordenado de actualizaciones del día
   - **Dado** que la copia local del día ya fue inicializada
   - **Cuando** se recibe una actualización del día con `sequenceNumber` creciente:
     - Si es `ADDED`: agrega la reserva y sus habitaciones a la copia local.
     - Si es `UPDATED`: actualiza los datos de la reserva y habitaciones solo si el `updatedAt` del mensaje es estrictamente más reciente que el registrado localmente.
     - Si es `REMOVED` (por motivo `CANCELLED`, `DATE_CHANGED` o `NO_SHOW`): elimina la reserva y sus habitaciones de la copia local.
   - **Entonces** el sistema aplica los cambios en la copia local y persiste el `messageId` en el log de mensajes.

3. **Escenario**: Descarte estricto de mensajes duplicados por `messageId`
   - **Dado** un mensaje de lista o actualización cuyo `messageId` ya fue registrado en `daily_reservation_message_log`
   - **Cuando** el sistema recibe nuevamente dicho mensaje
   - **Entonces** descarta el mensaje inmediatamente sin alterar la copia local ni generar inconsistencias.

---

### Historia de Usuario 2 - Consulta y búsqueda en copia local por la Recepcionista (Prioridad: P1)

Como **Recepcionista**, quiero consultar el listado de llegadas del día y buscar reservas por código, número de documento o nombre del titular directamente sobre la copia local, para verificar la información de la reserva y sus habitaciones de manera instantánea y autónoma.

**Por qué esta prioridad**: Permite agilidad total en recepción sin depender del estado de la red ni de la disponibilidad del Módulo 2.

**Prueba Independiente**: Consultar el panel de llegadas y realizar búsquedas por código de reserva, documento o nombre del titular en la base de datos local, comprobando que se retornan los datos y las habitaciones asociadas (de 1 a 10) sin invocar servicios REST externos.

**Escenarios de Aceptación**:

1. **Escenario**: Listado completo de llegadas del día desde la copia local
   - **Dado** que la copia local contiene reservas para el día de hoy
   - **Cuando** la Recepcionista consulta las llegadas en el Panel de Recepción
   - **Entonces** el sistema retorna las reservas agrupadas, presentando cada habitación asignada (1 a 10 habitaciones por reserva), titular, fechas de estadía, noches calculadas, cantidad de huéspedes y la fuente (`source`: `DIRECTA` o nombre de la OTA).

2. **Escenario**: Búsqueda por código de reserva, documento o nombre del titular
   - **Dado** una reserva almacenada en la copia local
   - **Cuando** la Recepcionista busca por `reservationRef`, número de documento o nombre del titular
   - **Entonces** el sistema localiza y muestra los datos de la reserva y sus habitaciones de forma inmediata sin peticiones de red externas.

3. **Escenario**: Búsqueda con múltiples coincidencias
   - **Dado** un criterio de búsqueda que coincide con dos o más reservas en la copia local
   - **Cuando** la Recepcionista ejecuta la búsqueda
   - **Entonces** el sistema presenta el listado de reservas coincidentes con su titular, habitaciones y fechas, requiriendo la selección puntual de una reserva para avanzar.

4. **Escenario**: Búsqueda sin coincidencias
   - **Dado** un término de búsqueda inexistente en la copia local del día
   - **Cuando** la Recepcionista realiza la consulta
   - **Entonces** el sistema informa que no se encontraron llegadas para el criterio ingresado.

---

### Historia de Usuario 3 - Consulta REST a Módulo 2 para bloqueo técnico y baja de habitación (Prioridad: P2)

Como **Personal de mantenimiento o Administrador**, quiero consultar al Módulo 2 mediante REST si un rango de fechas se cruza con alguna reserva de una habitación específica, para saber si es seguro programar un bloqueo técnico o dar de baja la unidad.

**Por qué esta prioridad**: Previene que se inhabiliten o den de baja habitaciones que ya tienen compromisos comerciales futuros en el Módulo 2.

**Prueba Independiente**: Enviar la consulta REST a Módulo 2 con la credencial de servicio de Módulo 1 y los parámetros `dateFrom`, `dateTo` y `roomId`. Validar que se consulta una única habitación por petición y que se detecta si existe o no algún cruce con reservas.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta para programar bloqueo técnico sin cruce de reservas
   - **Dado** una habitación para la que se proyecta mantenimiento desde una fecha de inicio hasta una fecha estimada de finalización
   - **Cuando** el Personal de mantenimiento consulta al Módulo 2 enviando `roomId`, `dateFrom` (inicio) y `dateTo` (fin)
   - **Entonces** Módulo 2 responde indicando que no existen reservas que se crucen en ese rango, permitiendo proceder con la programación del bloqueo.

2. **Escenario**: Consulta para dar de baja habitación (sin límite superior)
   - **Dado** una habitación que el Administrador requiere dar de baja
   - **Cuando** se ejecuta la consulta al Módulo 2 enviando `roomId`, `dateFrom = hoy` y `dateTo = 9999-12-31`
   - **Entonces** el sistema obtiene todas las reservas vigentes de la habitación desde hoy para que *Dar de baja habitación* rechace la operación si existe al menos una.

3. **Escenario**: Detección de cruce con reservas futuras
    - **Dado** que la habitación posee al menos una reserva que se solapa con el rango (`startDate` a `endDate`) consultado
    - **Cuando** se ejecuta la consulta REST
    - **Entonces** el sistema recibe la indicación de cruce para que el flujo consumidor aplique la regla correspondiente (impedir la baja o advertir al usuario).

4. **Escenario**: Indisponibilidad o falla controlada de Módulo 2
    - **Dado** una falla de conexión o tiempo de espera agotado al consultar a Módulo 2
    - **Cuando** se intenta verificar la existencia de reservas para la habitación
    - **Entonces** el sistema maneja la contingencia de forma controlada sin fallos no capturados, informando al usuario la imposibilidad de verificar reservas en ese momento.

---

### Casos Borde

- **Mensaje con estampa desactualizada (`updatedAt`)**: Si llega un mensaje `UPDATED` cuyo `updatedAt` no es más reciente que el registrado en la copia local, se descarta para evitar sobrescrituras.
- **Reserva multi-habitación (1 a 10 habitaciones)**: La copia local modela y almacena cada habitación de la reserva en `daily_reservation_room`, permitiendo su gestión individual por habitación en llegadas.
- **Campo `source`**: El valor de `source` se persiste y muestra exactamente como se recibe (`DIRECTA` o el nombre de la OTA: `BOOKING`, `EXPEDIA`, etc.).
- **Ausencia de campo `version`**: El orden y la coherencia se garantizan exclusivamente mediante `sequenceNumber` dentro del día y `updatedAt`.
- **Contrato REST de Módulo 2**: lo define *Consultar reservas* FR-023 de Módulo 2: `GET /api/reservations?dateFrom={d1}&dateTo={d2}&roomId={id}`, autenticado con la credencial de servicio de Módulo 1. Sin coincidencias responde una lista vacía (no hay 404).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE mantener una copia local de las reservas del día (`daily_reservation`, `daily_reservation_room`, `daily_reservation_message_log`) alimentada a través de mensajería asíncrona proveniente de Módulo 2.
- **FR-002**: El sistema DEBE procesar dos modalidades de notificación asíncrona de reservas:
  - Notificación de lista del día: recibida a las 00:00 (hora Colombia), con secuencia inicial (`sequenceNumber = 1`) y conteniendo únicamente reservas en estado `ACTIVE` con `startDate = hoy`.
  - Notificación de actualización del día: recibida en tiempo real durante el día con acciones `ADDED`, `UPDATED` o `REMOVED` y `sequenceNumber` estrictamente incremental.
- **FR-003**: Control de idempotencia y secuencia:
  - Todo mensaje DEBE incluir un `messageId` único. Si el `messageId` ya existe en `daily_reservation_message_log`, el mensaje DEBE ser descartado.
  - Los mensajes DEBEN aplicarse en estricto orden de `sequenceNumber` dentro del mismo `operationalDate` (día operativo que Módulo 2 indica en cada mensaje). Un mensaje cuyo `operationalDate` sea anterior al día de la copia local (por ejemplo, un `REMOVED` por no-show del día anterior que llega atrasado después de la lista de las 00:00) DEBE descartarse sin aplicarse, registrando su `messageId`; las habitaciones de esas reservas ya se liberan a las 00:00 al no figurar en la nueva lista (FR-009).
  - Si llega un `sequenceNumber` mayor que el siguiente esperado (falta un mensaje intermedio), el sistema DEBE aplicarlo igual y registrar el hueco en el log (`SEQUENCE_GAP`, con el número esperado y el recibido), sin detener la ingesta. Es el mismo criterio de Módulo 2, que publica en orden y solo deja un hueco si agotó sus reintentos (y entonces genera una alerta de su lado).
  - Un mensaje ilegible o sin `messageId`, `sequenceNumber`, `operationalDate` o `reservationRef` (en las actualizaciones) DEBE enviarse a la cola de mensajes fallidos sin aplicarse. Un fallo temporal al procesar (por ejemplo, base de datos caída) DEBE reintentarse con espera creciente y, agotados los reintentos, enviarse también a la cola de mensajes fallidos.
  - Un mensaje `UPDATED` solo DEBE aplicarse si su `updatedAt` es más reciente que el almacenado localmente.
  - Al recibir la lista de las 00:00, el sistema DEBE reiniciar el contador de `sequenceNumber` y purgar la copia local del día anterior.
- **FR-004**: Estructura de la copia local:
  - `daily_reservation`: `reservation_ref` (PK), `guest_first_name`, `guest_last_name`, `guest_document_type`, `guest_document_number`, `guest_nationality`, `source`, `start_date`, `end_date`, `guest_count`, `updated_at`.
  - `daily_reservation_room`: `reservation_ref` + `room_id` (PK compuesta), `room_number`, `category_room`, `guest_count` (cantidad de personas por habitación; confirmado por Módulo 2: `rooms[]` de la lista del día incluye `guestCount` por habitación; respaldo: `maxCapacity` de `Room`).
  - `daily_reservation_message_log`: `message_id` (PK), `sequence_number`, `message_type`, `received_at`.
  - El sistema NO DEBE persistir `status` en la copia local (se asume `ACTIVE`; un `REMOVED` elimina la reserva de la copia), ni `notes`, `createdAt`, `guestRef`, `externalConfirmationCode`, `contactPhone`, `contactEmail`, `nights` ni `lateArrivalNotice`. Las noches se calculan en tiempo de ejecución. No existe campo `version` en los mensajes de reserva.
- **FR-005**: El valor de `source` DEBE almacenarse y mostrarse tal como llega (`DIRECTA` o el nombre de la OTA: `BOOKING`, `EXPEDIA`, etc.).
- **FR-006**: Consultas para Recepción: Las consultas y búsquedas de llegadas en mostrador y Panel de Recepción DEBEN ejecutarse exclusivamente contra la copia local, sin realizar peticiones HTTP/REST hacia el Módulo 2.
- **FR-007**: Consulta REST de Mantenimiento y Baja:
  - El sistema DEBE consultar al Módulo 2 con `GET /api/reservations` (*Consultar reservas* FR-023 de Módulo 2), autenticado con la credencial de servicio de Módulo 1, únicamente desde *Programar Bloqueo Técnico para Habitación* (Personal de mantenimiento) y *Dar de baja habitación* (Administrador).
  - La petición DEBE enviar `dateFrom` y `dateTo` (obligatorios, formato `AAAA-MM-DD`, ambos inclusivos) y siempre el `roomId` de la habitación; solo se consulta una habitación por petición.
  - Para programar bloqueo técnico: `dateFrom` es el inicio del mantenimiento y `dateTo` su fecha estimada de finalización.
  - Para dar de baja una habitación: `dateFrom` es la fecha actual y `dateTo` es `9999-12-31`, que representa "sin límite superior": cualquier reserva vigente desde hoy cuenta.
  - Módulo 2 devuelve en `items` solo las reservas vigentes (`PENDING`, `ACTIVE` o `IN_PROGRESS`) cuya estadía se cruza con el rango (`startDate` ≤ `dateTo` y `endDate` > `dateFrom`: el día de salida no se considera ocupado), con `reservationRef`, `status`, `startDate`, `endDate` y sus habitaciones. El sistema DEBE tratar cada reserva devuelta como un conflicto, sin volver a evaluar estados ni fechas; una lista vacía significa que no hay conflictos.
- **FR-008**: Manejo de fallos en consulta REST: Ante indisponibilidad, timeout o una respuesta de error de Módulo 2 (por ejemplo, 400 con `INVALID_DATE`, `INCOMPLETE_DATE_RANGE` o `INVALID_ROOM_ID`) en la consulta de mantenimiento o baja, el sistema DEBE cancelar la operación sin cambiar la habitación, capturar la contingencia de manera controlada e informar al usuario sin provocar errores no manejados.
- **FR-009**: Efecto de la ingesta sobre el estado de las habitaciones: al persistir en la copia local la lista diaria de las 00:00 o una actualización `ADDED` con `startDate` = hoy, el sistema DEBE invocar el caso de uso *Marcar habitación como reservada* por cada habitación de la reserva. Al procesar un `REMOVED`, o al no figurar en la nueva lista de las 00:00 una reserva que mantenía una habitación en `Reserved`, el sistema DEBE liberar esa habitación a `Available` mediante el caso de uso *Marcar habitación como disponible*. En la ingesta de las 00:00 las liberaciones DEBEN ejecutarse antes de apartar las habitaciones de la nueva lista. Un `UPDATED` que cambia la habitación asignada DEBE tratarse como la liberación de la habitación anterior y el apartado de la nueva.

---

## Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **`DailyReservation`**: Cabecera de la reserva en la copia local. Atributos: `reservation_ref` (PK), `guest_first_name`, `guest_last_name`, `guest_document_type`, `guest_document_number`, `guest_nationality`, `source`, `start_date`, `end_date`, `guest_count`, `updated_at`.
- **`DailyReservationRoom`**: Detalle de habitaciones asociadas a la reserva (de 1 a 10 por reserva). Atributos: `reservation_ref`, `room_id`, `room_number`, `category_room`, `guest_count`.
- **`DailyReservationMessageLog`**: Bitácora de control de mensajería asíncrona. Atributos: `message_id` (PK), `sequence_number`, `message_type`, `received_at`.
- **`Room`**: Entidad física en Módulo 1. En consultas de recepción se presenta su estado operativo actual (`Reserved`). En consultas REST de mantenimiento se envía su `id` (`roomId`).

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las consultas y búsquedas de llegadas para Check-In se resuelven en la copia local en menos de 100 ms, sin invocar peticiones HTTP síncronas a sistemas externos.
- **SC-002**: El 100% de los mensajes duplicados recibidos por la cola son descartados mediante la verificación de `messageId`.
- **SC-003**: A las 00:00 se purga el 100% de los registros del día previo y se reinicia la secuencia de mensajes.
- **SC-004**: La consulta REST hacia Módulo 2 envía con exactitud los parámetros requeridos (`roomId`, `dateFrom`, `dateTo`) y gestiona de forma controlada el 100% de las posibles demoras o caídas de red.
