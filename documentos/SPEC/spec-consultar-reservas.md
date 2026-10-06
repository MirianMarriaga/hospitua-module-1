# Especificación de Funcionalidad: Consultar Reservas

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista / Personal de mantenimiento / Administrador
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Búsqueda de reservas y consulta de llegadas del día para Check-In en copia local (Prioridad: P1)

Como Recepcionista, quiero consultar el listado de reservas del día o buscar reservas por código de reserva, documento o nombre del titular directamente sobre la copia local de llegadas, para verificar de manera inmediata los datos contractuales y el estado de la unidad física antes de iniciar el Check-In, sin depender de llamadas de red a Módulo 2.

**Por qué esta prioridad**: Es la base operativa del mostrador de recepción en Check-In. Conforme al nuevo acuerdo oficial con Módulo 2 (sección A y B), las llegadas del día se transmiten a las 00:00 por la cola `m1.reservas.diarias.queue` (`reserva.lista-del-dia`) y se actualizan en tiempo real mediante `reserva.lista-del-dia.actualizacion`, almacenándose en la copia local de Módulo 1 (`daily_reservation` y `daily_reservation_room`). Ya no se utiliza `GET /api/reservations` ni consultas REST para las llegadas ni para los buscadores de Check-In, garantizando alta disponibilidad y respuesta inmediata en recepción.

**Prueba Independiente**: Se prueba consultando el listado de llegadas de hoy y ejecutando búsquedas por código de reserva (`reservationRef`), número de documento o nombre del titular contra la copia local, verificando que el sistema retorna los datos contractuales sin invocar REST a Módulo 2: código de reserva, titular (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`), cantidad total de huéspedes (`guestCount`), habitaciones asociadas (de 1 a 10 habitaciones con su `roomId`, `roomType` y `guestCount` asignado), fechas de estadía (solo fechas: `startDate`, `endDate` y noches calculadas), canal de origen (`source`: "DIRECTA" o identificador de OTA), estado de la reserva (`status = ACTIVE`), y el estado físico actual de cada habitación en Módulo 1 (`Reserved`).

**Escenarios de Aceptación**:

1. **Escenario**: Listado de llegadas del día visualizado desde la copia local (Happy Path Check-In)
   - **Dado** que a las 00:00 Módulo 1 recibió la `DailyReservationList` por la cola `m1.reservas.diarias.queue` (`reserva.lista-del-dia`) y persistió las reservas en su copia local (`daily_reservation`, `daily_reservation_room`) con `status = ACTIVE` y `startDate = hoy`
   - **Cuando** el Recepcionista accede a la pestaña Llegadas del Panel de Recepción
   - **Entonces** el sistema lista las reservas de la copia local mostrando una fila por habitación asignada (número, tipo y estado actual "Reserved"), titular (`firstName`, `lastName`, tipo y documento), estadía (fechas y noches calculadas), `guestCount` de la habitación y de la reserva, canal (`source`) y estado "Activa" (`ACTIVE`), habilitando el inicio de Check-In.

2. **Escenario**: Búsqueda para Check-In por número de identificación del titular en copia local
   - **Dado** una reserva en la copia local en estado `ACTIVE` con llegada hoy asociada a un titular con documento "10203040"
   - **Cuando** el Recepcionista realiza la búsqueda en el buscador de Llegadas ingresando "10203040"
   - **Entonces** el sistema localiza instantáneamente el registro en la copia local sin realizar peticiones de red a Módulo 2 y presenta los datos de la reserva y sus habitaciones para continuar el Check-In.

3. **Escenario**: Búsqueda para Check-In por nombre del titular en copia local
   - **Dado** una reserva en la copia local con llegada hoy a nombre de "Carlos Gómez"
   - **Cuando** el Recepcionista busca en la barra de Llegadas por el texto "Carlos Gómez"
   - **Entonces** el sistema consulta la copia local y presenta los datos contractuales del titular, habitaciones y estadía.

4. **Escenario**: Selección obligatoria ante múltiples coincidencias en la copia local
   - **Dado** que una búsqueda por documento o apellido arroja dos o más reservas coincidentes en la copia local
   - **Cuando** el Recepcionista ejecuta la búsqueda
   - **Entonces** el sistema lista todas las coincidencias exhibiendo código (`reservationRef`), titular, habitaciones y fechas, bloqueando el avance hasta que el usuario seleccione explícitamente una reserva.

5. **Escenario**: Búsqueda sin coincidencias en la copia local
   - **Dado** un criterio de búsqueda que no coincide con ninguna reserva activa del día en la copia local
   - **Cuando** el Recepcionista ejecuta la búsqueda
   - **Entonces** el sistema notifica que no se encontraron reservas para el día de hoy con ese criterio, permitiendo rectificar la búsqueda.

---

### Historia de Usuario 2 - Consulta de reservas futuras para mantenimiento y bloqueos técnicos vía REST GET a Módulo 2 (Prioridad: P2)

Como Personal de mantenimiento o Administrador, quiero consultar las reservas futuras asociadas a una habitación o categoría dentro de un rango de fechas mediante consulta REST GET a Módulo 2, para verificar si existen compromisos de hospedaje antes de autorizar un bloqueo técnico (`TechnicalBlock`) o dar de baja temporal una unidad (`DisabledForRepairs` / `Inactive`).

**Por qué esta prioridad**: Conforme al nuevo acuerdo oficial con Módulo 2 (sección B), esta es la **única** consulta REST GET a reservas que realiza Módulo 1. Permite prevenir que Mantenimiento bloquee habitaciones que ya han sido vendidas o apartadas para fechas futuras.

**Prueba Independiente**: Se prueba ejecutando una consulta REST GET a Módulo 2 ingresando un rango de fechas (`startDate`, `endDate`) y una habitación física (`roomId`) o categoría, comprobando que Módulo 2 retorna el listado de reservas futuras programadas y que Módulo 1 procesa la respuesta para alertar sobre conflictos de ocupación antes de aplicar un bloqueo técnico.

**Escenarios de Aceptación**:

1. **Escenario**: Habitación sin reservas futuras en el rango consultado
   - **Dado** una solicitud de bloqueo técnico o mantenimiento para una habitación en un rango de fechas
   - **Cuando** el Personal de mantenimiento consulta las reservas futuras a Módulo 2 mediante REST GET
   - **Entonces** Módulo 2 retorna una lista vacía de reservas y Módulo 1 confirma que la unidad está despejada para aplicar el bloqueo técnico.

2. **Escenario**: Habitación con reservas futuras en conflicto
   - **Dado** que existen reservas en Módulo 2 programadas dentro del rango solicitado para la habitación
   - **Cuando** se ejecuta la consulta REST GET a Módulo 2
   - **Entonces** el sistema recibe las reservas en conflicto exhibiendo código, fechas contractuales y titular, alertando al Personal de mantenimiento para reprogramar la intervención técnica o reubicar las reservas en Módulo 2 antes de bloquear.

3. **Escenario**: Manejo controlado ante indisponibilidad de Módulo 2 en consulta de mantenimiento
   - **Dado** una interrupción de conectividad con Módulo 2 al consultar reservas para mantenimiento
   - **Cuando** el Personal de mantenimiento intenta verificar el calendario de reservas futuras
   - **Entonces** el sistema captura la contingencia e informa de forma controlada la indisponibilidad de Módulo 2, bloqueando preventivamente la inactivación de la habitación hasta confirmar que no hay reservas comprometidas.

---

### Casos Borde

- **Espacios en blanco y formato en la consulta**: El sistema elimina espacios accidentales iniciales y finales (trim) y tolera variaciones de mayúsculas/minúsculas en el código de reserva (ej. "res-101" es equivalente a "RES-101").
- **Coincidencias con homónimos en búsqueda local**: Si existen dos titulares con nombres similares en la lista del día, el sistema lista ambas reservas detallando el número de documento y habitación para permitir una selección inequívoca.
- **Reserva con múltiples habitaciones (1 a 10)**: La copia local soporta reservas grupales o familiares con hasta 10 habitaciones asociadas. En el listado de llegadas se presenta una fila por habitación, referenciando la misma reserva principal.
- **Actualización concurrente en copia local**: Si mientras se consulta el listado se recibe un mensaje `DailyReservationUpdate` (`UPDATED` o `REMOVED`) con `updatedAt` superior, la copia local se refresca reactivamente.
- **Contingencia por falta de lista a las 00:00**: Si la cola no ha entregado la lista del día a las 00:00 o la copia local está vacía, el sistema advierte en pantalla el estado desactualizado de llegadas y permite reintentar la sincronización.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE proveer la consulta y búsqueda de reservas para Check-In y para la pestaña Llegadas del Panel de Recepción (`spec-consultar-panel-recepcion.md`) operando **exclusivamente sobre la copia local** (`daily_reservation` y `daily_reservation_room`), alimentada por la cola asíncrona `m1.reservas.diarias.queue`. El proceso de Check-Out NO invoca consultas de reservas externas ni locales de llegadas, ya que se alimenta de la información de ocupación física en Módulo 1 (`Stay` + `Room` + `RoomGuest`).
- **FR-002**: Las llegadas del día y las búsquedas por código de reserva (`reservationRef`), documento o nombre del titular para Check-In DEBEN ejecutarse contra la copia local sin invocar peticiones REST (`GET /api/reservations`) a Módulo 2.
- **FR-003**: La copia local DEBE estructurar cada reserva conforme al contrato acordado en la sección 6 del Módulo 2:
  - Datos de la reserva (`daily_reservation`): `reservationRef` (ID único de reserva), titular (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`), cantidad total de huéspedes (`guestCount`), fechas (`startDate`, `endDate`), noches calculadas localmente (`endDate - startDate`), canal de origen (`source`: "DIRECTA" o identificador de la OTA), estado contractual (`status = ACTIVE`) y marca temporal de actualización (`updatedAt`). No se almacenan `guestRef`, `externalConfirmationCode`, `createdAt` ni `notes`.
  - Habitaciones asociadas (`daily_reservation_room`): de 1 a 10 habitaciones por reserva, cada una con su `roomId`, `roomType` y la cantidad de huéspedes asignada a la habitación (`guestCount` por habitación, pendiente de definición formal por Módulo 2; si no llega, se valida contra la capacidad máxima `maxCapacity` de la habitación).
- **FR-004**: El sistema DEBE proveer al Personal de mantenimiento y Administrador un mecanismo de consulta REST GET hacia Módulo 2 exclusivamente para verificar reservas futuras ante bloqueos técnicos (`TechnicalBlock`) o bajas de habitación (`DisabledForRepairs` / `Inactive`), por rango de fechas (`startDate`, `endDate`) y habitación (`roomId`) o categoría.
  *(Nota técnica: La ruta del endpoint, parámetros exactos de consulta y estructura detallada de respuesta están `[PENDIENTE DE DEFINICIÓN POR EL MÓDULO 2]`).*
- **FR-005**: Al localizar una reserva en la copia local para Check-In, el sistema DEBE retornar:
  - Identificador de la reserva (`reservationRef`).
  - Datos del titular (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`).
  - Habitación o habitaciones asignadas (1 a 10), con su tipo y su estado físico actual en Módulo 1 (`Reserved`, etc.).
  - Noches calculadas y rango de fechas de estadía.
  - Cantidad de huéspedes (`guestCount` total y por habitación).
  - Canal de origen (`source`).
  - Estado contractual (`ACTIVE`).
- **FR-006**: Si la búsqueda local por documento o nombre retorna más de una reserva, el sistema DEBE listar todas las coincidencias exhibiendo su código, titular, habitaciones y fechas, exigiendo la selección explícita del Recepcionista antes de continuar.
- **FR-007**: El sistema DEBE capturar cualquier falla de red, lentitud o caída de Módulo 2 durante la consulta REST de mantenimiento (FR-004), informando de manera controlada la indisponibilidad e impidiendo bloqueos técnicos que puedan superponerse con reservas no verificadas.
- **FR-008**: Este caso de uso NO DEBE validar si la habitación está lista para entrega física ni ejecutar transiciones de estado de habitaciones; dichas acciones son responsabilidad exclusiva de "Registrar Check-In" o de los flujos de mantenimiento.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **DailyReservation**: Entidad de la copia local en Módulo 1 que almacena la cabecera de la reserva diaria. Atributos: `reservationRef`, `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `guestCount`, `startDate`, `endDate`, `source` ("DIRECTA" o identificador de OTA), `status` (`ACTIVE`), `updatedAt`.
- **DailyReservationRoom**: Entidad de la copia local que vincula la reserva con cada una de sus unidades físicas asignadas (de 1 a 10 habitaciones). Atributos: `id`, `reservationRef`, `roomId`, `roomType`, `guestCount` (huéspedes asignados a esta habitación).
- **DailyReservationMessageLog**: Registro de auditoría local de mensajes procesados para garantizar idempotencia por `messageId` y control de secuencia por `sequenceNumber`.
- **MaintenanceReservationQuery**: Consulta conceptual REST GET remitida a Módulo 2 por Personal de mantenimiento para verificar reservas futuras (`roomId`, categoría, `startDate`, `endDate`). *[Ruta y esquema detallado pendientes de definición por el Módulo 2]*.
- **Room**: Unidad habitacional en Módulo 1. Atributos clave: ID único (UUID), número de habitación, tipo, capacidad máxima (`maxCapacity`), tarifa base y estado actual del ciclo de vida (`Reserved`, `Occupied`, `Available`, etc.) con su atributo `reservedByReservationRef`.
- **Module2 (Operación de Reservas)**: Sistema externo responsable del ciclo de vida contractual de las reservas, emisor de la lista diaria y sus actualizaciones por cola, y receptor de la consulta REST de mantenimiento.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las búsquedas de llegadas en mostrador operan sobre la copia local de Módulo 1 y entregan resultados en menos de 500 milisegundos, eliminando la dependencia de red con Módulo 2 en el flujo crítico de Check-In.
- **SC-002**: El 100% de las búsquedas con múltiples coincidencias en la copia local obligan a la selección manual del Recepcionista sin asumir registros por defecto.
- **SC-003**: La consulta REST GET a Módulo 2 para Personal de mantenimiento previene el 100% de bloqueos técnicos o bajas no autorizadas sobre habitaciones con reservas futuras confirmadas en el rango solicitado.
- **SC-004**: Cero caídas del sistema ante demoras o indisponibilidad de Módulo 2 en la consulta REST de mantenimiento; el 100% de los fallos se gestionan mediante mensajes controlados.
