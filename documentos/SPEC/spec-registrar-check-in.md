# Especificación de Funcionalidad: Registrar Check-In

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-19
**Actualizado**: 2026-10-09

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Admisión física de huéspedes y ocupación en inventario (Prioridad: P1)

Como Recepcionista, quiero registrar el Check-In presencial guiado por el flujo de 4 pasos de la interfaz (Validar reserva → Datos de huéspedes → Confirmación → Check-In), validando la reserva activa contra la copia local de la lista del día (`daily_reservation`, `daily_reservation_room`), la ausencia de un `Stay` previo para ese par (`reservationRef`, `roomId`) y capturando la identidad de todos los ocupantes (abriendo el panel de datos migratorios para cada extranjero con procedencia y destino en formato texto libre asistido por catálogo), para que se cree formalmente la estancia física persistiendo la fuente (`source`), la habitación transicione de forma síncrona a "Occupied" en el inventario y se notifique de forma asíncrona a Módulo 2 la admisión física con todos los huéspedes (nacionales y extranjeros), permitiendo la entrega ágil de la habitación.

**Por qué esta prioridad**: Es el flujo misional principal de admisión presencial en el hotel. La notificación de check-in remitida a Módulo 2 consolida a todos los huéspedes (nacionales y extranjeros) en un único aviso con el movimiento de entrada y la fecha de llegada. No existe notificación separada para extranjeros. El tipo de movimiento (entrada) y la fecha de movimiento son asignados automáticamente por el sistema, no por el Recepcionista.

**Prueba Independiente**: Iniciar desde el Panel de Recepción con una habitación y reserva seleccionadas desde la copia local. Ejecutar: (1) validación local contra `daily_reservation`/`daily_reservation_room` — reserva activa, habitación apta para Check-In (en estado `Reserved` y apartada para esa reserva), sin Stay previo; (2) captura de huéspedes con `firstName`/`lastName` separados, titular precargado de solo lectura para consulta (no cuenta como ocupante predeterminado; solo se cuenta si se aloja físicamente), `birthDate` (pasada) para todos los huéspedes, incluido el titular si se aloja, y apertura del panel migratorio ante nacionalidad ≠ Colombia solicitando `originPlace` y `destinationPlace`; (3) resumen de confirmación; (4) confirmación final verificando Stay creado, transición a Occupied, y registro de la notificación saliente para Módulo 2 con el movimiento de entrada, fecha de check-in y la nómina completa de todos los huéspedes admitidos.

**Escenarios de Aceptación**:

1. **Escenario**: Flujo completo de Check-In exitoso para huéspedes nacionales
   - **Dado** una reserva en la copia local con `startDate = hoy`, habitación en estado `Reserved` apartada para esa reserva, sin `Stay` previo para el par (`reservationRef`, `roomId`), `guestCount = 2` para la habitación
   - **Cuando** el Recepcionista valida la reserva en el paso 1 (local), registra 2 ocupantes colombianos en el paso 2 y confirma en el paso 3
   - **Entonces** el sistema crea la `Estancia` (Stay) con `source`, `checkInDate`, `expectedCheckinTime`, `expectedCheckoutTime`, `titularFirstName`, `titularLastName`, `titularDocumentNumber`; registra los `RoomGuest` con `firstName`, `lastName`, `isReservationGuest`; transiciona la habitación a `Occupied`; emite la notificación asíncrona hacia Módulo 2 con la reserva, habitación, movimiento de entrada, fecha de ingreso y la lista de todos los huéspedes (sin datos migratorios para nacionales); y muestra la pantalla de éxito

2. **Escenario**: Check-In con huésped extranjero y panel migratorio
   - **Dado** una reserva activa en la copia local con habitación en estado `Reserved` apartada para esa reserva
   - **Cuando** en el paso 2 el Recepcionista registra un ocupante con `nationality ≠ Colombia`
   - **Entonces** el sistema abre el panel migratorio para ese ocupante, solicitando obligatoriamente `originPlace` y `destinationPlace` (texto libre con asistencia de catálogo, formato "Ciudad, País"); su `birthDate` se captura en el formulario principal, como para todos los huéspedes (FR-006). El sistema asigna el tipo de movimiento de entrada y la fecha de ingreso automáticamente. Al confirmar, la notificación a Módulo 2 incluye al extranjero con sus datos de identificación y sus datos migratorios completos (`birthDate`, `originPlace`, `destinationPlace`). El Check-In nunca se bloquea esperando confirmación externa

3. **Escenario**: Resumen de confirmación (paso 3)
   - **Dado** que los pasos 1 y 2 están validados
   - **Cuando** el Recepcionista avanza al paso 3
   - **Entonces** el sistema muestra: cantidad de huéspedes de la habitación, habitación asignada (número y tipo), estadía (noches calculadas y rango de fechas), y cuadro informativo indicando que al confirmar la habitación pasará a "Occupied" y la reserva pasará a "En curso" cuando todas sus habitaciones completen Check-In. No se muestran indicadores técnicos de colas ni módulos al Recepcionista

4. **Escenario**: Titular de la reserva y regla de conteo de ocupantes en el paso 2
   - **Dado** una reserva obtenida en el paso 1 desde la copia local
   - **Cuando** el Recepcionista accede al paso 2
   - **Entonces** el sistema presenta los datos del titular (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`) en solo lectura para verificación. El titular de la reserva NO cuenta como ocupante predeterminado; solo se cuenta y se incluye si también se aloja físicamente en la habitación (marcado con `isReservationGuest = true`). El conteo de `guests[]` parte desde cero y se incrementa por cada huésped que realmente ingresa, incluyendo al titular si se aloja. Los datos del titular precargados (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`) se presentan en solo lectura

---

### Historia de Usuario 2 - Bloqueo de admisiones inválidas (Prioridad: P2)

Como Recepcionista, quiero que el sistema rechace Check-Ins inválidos con alertas claras.

**Escenarios de Aceptación**:

1. **Escenario**: Rechazo por discrepancia con `guestCount` de la habitación
   - **Dado** `guestCount = 2` para la habitación
   - **Cuando** se intenta confirmar con 1 o 3 ocupantes
   - **Entonces** el sistema bloquea el avance e informa la discrepancia

2. **Escenario**: Rechazo por habitación en estado operativo no apto
   - **Dado** habitación en `Available`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`
   - **Cuando** se intenta el Check-In
   - **Entonces** el sistema bloquea en el paso 1 informando el estado real (solo una habitación en `Reserved` apartada para esa reserva es apta para Check-In)

3. **Escenario**: Rechazo por reserva no presente en la copia local del día
   - **Dado** reserva no encontrada en `daily_reservation` (cancelada, vencida o con `REMOVED`)
   - **Cuando** se intenta validar en el paso 1
   - **Entonces** el sistema rechaza indicando que la reserva no figura en la lista del día

4. **Escenario**: Rechazo por superar la capacidad máxima (`maxCapacity`)
   - **Dado** `maxCapacity = 2`
   - **Cuando** se intentan registrar 3 o más ocupantes
   - **Entonces** el sistema deshabilita el botón de agregar huéspedes e informa que se alcanzó la capacidad máxima

5. **Escenario**: Rechazo por datos migratorios incompletos o `birthDate` no válida
   - **Dado** un ocupante (nacional o extranjero) sin `birthDate` o con `birthDate` futura/hoy, o un ocupante extranjero con el panel migratorio incompleto
   - **Cuando** se intenta avanzar al paso 3
   - **Entonces** el sistema bloquea el avance, resalta los campos faltantes o inválidos

---

### Casos Borde

- **Falla de comunicación al notificar a Módulo 2**: El Check-In físico se consolida localmente (estancia creada, habitación en `Occupied`). La notificación permanece registrada para reintento en segundo plano, evitando retener al huésped en recepción.
- **Idempotencia en notificaciones**: Toda notificación saliente cuenta con un identificador único que permite al sistema receptor reconocer reintentos y descartar duplicados.
- **Día operativo fijo**: El Check-In solo puede realizarse cuando `fechaActual == startDate` (hora Colombia, UTC-5, 00:00–23:59).
- **Stay previo para la misma habitación**: Si ya existe un `Stay` para el par (`reservationRef`, `roomId`), el sistema rechaza el Check-In como operación ya realizada.
- **Regla de conteo de huéspedes y alojamiento del titular**: El titular de la reserva NO cuenta como ocupante predeterminado. Solo se cuenta si también se aloja físicamente en la habitación. El conteo de huéspedes parte desde cero y se incrementa por cada huésped que realmente ingresa, incluyendo al titular si se aloja.
- **Estado de la habitación para Check-In**: El Check-In solo se formaliza si la habitación está en `Reserved` y apartada para esa reserva (`reservedByReservationRef`). Módulo 1 la aparta antes de la llegada: con la lista de las 00:00, con un `ADDED` del día o al terminar su limpieza (máquina de estados de la habitación). Una habitación en `Available` con llegada hoy no admite Check-In; el panel de recepción muestra su estado.
- **`updatedAt` ausente en mensajes de Módulo 2**: Si falta, ordenar por `sequenceNumber`.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado "Recepcionista" iniciar y formalizar el registro de Check-In en Módulo 1.
- **FR-002**: El sistema DEBE estructurar el flujo de Check-In a través de 4 etapas visuales y operativas:
  1. *Validar reserva*
  2. *Datos de huéspedes*
  3. *Confirmación*
  4. *Check-In*
- **FR-003**: La búsqueda y selección de la reserva y habitación se realizan **previamente** en el **Panel de Recepción** (`spec-consultar-panel-recepcion.md`). Al pulsar el botón "Check-in" en el panel, el flujo llega al paso 1 con la referencia de reserva y habitación ya cargadas. El paso 1 DEBE validar **localmente** contra la copia local (`daily_reservation`, `daily_reservation_room`): reserva existente, habitación en `Reserved` apartada para esa reserva (FR-004), ausencia de `Stay` previo para el par (`reservationRef`, `roomId`). El paso 1 NO realiza consultas REST a Módulo 2.
- **FR-004**: El sistema DEBE validar que la habitación asignada esté en estado `Reserved` y apartada para esa reserva (`reservedByReservationRef` igual a la `reservationRef`). En cualquier otro estado (`Available`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`), o si está apartada para otra reserva, DEBE rechazar la admisión con un mensaje de error controlado que indique el estado actual.
- **FR-005**: El sistema DEBE validar que la reserva figure en la copia local del día (`startDate = fechaActual`). Si no figura, DEBE rechazar el Check-In.
- **FR-006**: En el paso 2, el sistema DEBE capturar en formulario los campos con nombres y apellidos separados para cada huésped: `firstName`, `lastName`, `documentType` (uno de `RC`, `TI`, `CC`, `CE`, `PAS` o `NIT`, los tipos que acepta Módulo 2), `documentNumber`, `nationality` y `birthDate` (obligatoria para todos los huéspedes, fecha pasada, `AAAA-MM-DD`; Módulo 2 la exige en la notificación). Para ocupantes con `nationality ≠ Colombia`, DEBE abrir el panel de datos migratorios capturando `originPlace` y `destinationPlace` (texto libre, asistencia de catálogo, formato "Ciudad, País"). El sistema asigna automáticamente `movementType = ENTRY` y `movementDate = checkInDate` sin pedirlos al Recepcionista.
- **FR-007:** El sistema DEBE precargar los datos del titular desde la copia local (daily_reservation), presentándolos en solo lectura (firstName, lastName, documentType, documentNumber, nationality) para identificación de la reserva. El titular NO cuenta como ocupante predeterminado; solo se cuenta y se incluye en guests[] si también se aloja físicamente en la habitación, momento en el que se agrega como cualquier otro huésped con todos sus campos obligatorios (marcado con isReservationGuest = true). El conteo de guests[] parte desde cero y se incrementa por cada huésped que realmente ingresa, incluyendo al titular si se aloja.
- **FR-008**: El sistema DEBE validar que la cantidad total de personas coincida exactamente con `guestCount` de la habitación (confirmado por Módulo 2: `rooms[]` de la lista del día incluye `guestCount` por habitación; respaldo: `maxCapacity`) y no supere `maxCapacity`. Si difiere, DEBE bloquear el avance al paso 3.
- **FR-009**: Panel de datos migratorios para extranjeros: Cuando `nationality ≠ Colombia`, el sistema DEBE abrir el panel solicitando exclusivamente `originPlace` y `destinationPlace`. Si la nacionalidad se corrige a Colombia, el panel se cierra y se vacía. Los dos campos son obligatorios para avanzar al paso 3. La `birthDate` no pertenece al panel: se captura para todos los huéspedes (FR-006) y debe ser una fecha pasada (no hoy ni futura).
- **FR-010**: En el paso 3, el sistema DEBE presentar el resumen con cantidad de huéspedes, habitación (número y tipo), estadía (noches y fechas) y cuadro informativo indicando que la habitación pasará a "Occupied" y la reserva pasará a "En curso". No se muestran indicadores técnicos de colas, módulos ni conteos de extranjeros al Recepcionista.
- **FR-011**: Al confirmar en el paso 3, el sistema DEBE ejecutar de forma síncrona y atómica:
  1. Creación de `Stay` con `source`, `checkInDate`, `expectedCheckinTime`, `expectedCheckoutTime`, `titularFirstName`, `titularLastName`, `titularDocumentNumber`.
  2. Creación de los registros `RoomGuest` inmutables con `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`; para extranjeros: también `originPlace`, `destinationPlace`; y `isReservationGuest`.
  3. Transición a `Occupied` en el inventario (`Reserved → Occupied`).
  4. Registro del recepcionista responsable (`receptionistIdCheckIn`).
- **FR-012**: Al confirmar el Check-In, el sistema DEBE generar una notificación asíncrona de movimiento de entrada dirigida a Módulo 2 que consolide a la totalidad de los huéspedes admitidos (nacionales y extranjeros) en un único aviso, sin canales separados para extranjeros. La notificación DEBE incluir:
  - Identificador único para control de duplicados e idempotencia.
  - Referencia de reserva e identificador de habitación.
  - Tipo de movimiento (entrada) y fecha de ingreso.
  - Datos de identificación de cada huésped: nombres, apellidos, tipo de documento, número de documento, fecha de nacimiento y nacionalidad.
  - Datos migratorios exclusivamente para ocupantes con nacionalidad distinta a Colombia: lugar de procedencia y lugar de destino.
  - Validación previa a la emisión que asegure la presencia de los datos requeridos y la coherencia de las fechas.

- **FR-013**: En el paso 4, el sistema DEBE mostrar la pantalla de confirmación exitosa con las etiquetas "Habitación: Ocupada" y "Estado de la reserva: En curso" (sin mencionar módulos ni sistemas externos), sin mostrar horas y con el botón único "Volver al inicio".
- **FR-014**: El Check-In NO DEBE bloquearse ante lentitud o fallas de comunicación con Módulo 2. La Estancia se consolida localmente y la notificación queda registrada para entrega garantizada mediante reintentos en segundo plano.
- **FR-015**: El sistema DEBE registrar en la bitácora de auditoría (evento `CHECK_IN`, dentro de la misma transacción) el ID de la habitación, la referencia de la reserva, el ID de la Estancia creada, el recepcionista responsable y la marca de tiempo de confirmación.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional. Atributos clave: `roomId` (UUID), número, piso, tipo, `maxCapacity`, tarifa base, `status` (8 estados), `reservedByReservationRef` (solo en `Reserved`).
- **Stay**: Estancia física. Atributos: `id`, `reservationRef`, `roomId`, `source` (`DIRECTA` o nombre de la OTA), `checkInDate`, `checkOutDate`, `expectedCheckinTime`, `expectedCheckoutTime`, `titularFirstName`, `titularLastName`, `titularDocumentNumber`, `receptionistIdCheckIn`, `receptionistIdCheckOut`.
- **RoomGuest**: Ocupante inmutable vinculado a la Estancia. Atributos: `id`, `stayId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`, `originPlace` (solo extranjeros), `destinationPlace` (solo extranjeros), `isReservationGuest`.
- **DailyReservation / DailyReservationRoom**: Copia local de la reserva. Fuente de verdad en el paso 1 y para el precargado del titular. De 1 a 10 habitaciones por reserva. No persiste `nights`, `status`, `guestRef`, `externalConfirmationCode`.
- **Receptionist**: Actor que ejecuta el flujo de admisión.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista completa el flujo de 4 pasos en menos de 2 minutos para nacionales y menos de 3 minutos para extranjeros.
- **SC-002**: El 100% de los Check-Ins confirmados transicionan la habitación a `Occupied` y persisten `source` en `Stay`.
- **SC-003**: El 100% de los Check-Ins rechaza la cantidad de ocupantes que no coincide con `guestCount` de la habitación.
- **SC-004**: El 100% de los Check-Ins rechaza habitaciones que no estén en `Reserved` apartadas para esa reserva, y admite las que sí lo están.
- **SC-005**: El 100% de admisiones con extranjeros capturan fecha de nacimiento, lugar de procedencia y destino válidos; la notificación a Módulo 2 incluye a la totalidad de los huéspedes admitidos.
- **SC-006**: El 100% de las fallas temporales de comunicación con Módulo 2 consolidan la ocupación local y conservan la notificación para entrega diferida.
