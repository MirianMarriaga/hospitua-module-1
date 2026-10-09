# Especificación de Funcionalidad: Registrar Check-In

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-19

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Admisión física de huéspedes y ocupación en inventario (Prioridad: P1)

Como Recepcionista, quiero registrar el Check-In presencial guiado por el flujo de 4 pasos de la interfaz (Validar reserva → Datos de huéspedes → Confirmación → Check-In), validando la reserva activa contra la copia local de la lista del día (`daily_reservation`, `daily_reservation_room`), la ausencia de un `Stay` previo para ese par (`reservationRef`, `roomId`) y capturando la identidad de los ocupantes (abriendo el panel de datos migratorios para cada extranjero capturando procedencia y destino en formato texto con asistencia de catálogo), para que se cree formalmente la estancia física persistiendo la fuente (`source`), la habitación transicione de forma síncrona a "Occupied" en el inventario, se notifique a Módulo 2 mediante la cola `m2.habitacion.checkin.queue` con la `reservationRef`, el `roomId` y `foreignGuestCount`, y se despache por `m2.huespedes.extranjeros.queue` un mensaje por cada huésped extranjero con sus diez campos migratorios completos y movimiento `ENTRY`, permitiendo la entrega ágil de la habitación.

**Por qué esta prioridad**: Es el flujo misional principal de admisión presencial en el hotel. Garantiza la creación formal de la entidad Estancia persistiendo la fuente de la reserva (`source`: `DIRECTA` o nombre de la OTA) para que el Check-Out opere de forma 100% desacoplada, y que la habitación quede registrada inmediatamente como "Occupied" en el inventario de Módulo 1. Conforme a los acuerdos actualizados, la notificación de check-in emitida por cola asíncrona hacia Módulo 2 (`m2.habitacion.checkin.queue`) incluye obligatoriamente `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y `foreignGuestCount` (obligatorio, 0 si no hay extranjeros). Los datos migratorios de los extranjeros ya no van dentro de la notificación de check-in: viajan por su propia cola independiente (`m2.huespedes.extranjeros.queue`), un mensaje por huésped extranjero con los diez campos exigidos por el SIRE (incluyendo `originPlace` y `destinationPlace` capturados en formato texto libre con asistencia de catálogo de países —ej. "Madrid, España"—). De esta forma el Check-In nunca espera por datos migratorios y si un mensaje falla la cola reintenta solo ese huésped. El tipo de movimiento (`ENTRY`) y la fecha de movimiento (`checkInDate`) son asignados automáticamente por Módulo 1. En el paso 2, para cada ocupante con nacionalidad distinta de Colombia se abre el panel de datos migratorios exigiendo `birthDate`, `originPlace` y `destinationPlace` antes de avanzar al paso 3. El titular de la reserva y sus acompañantes se registran con `firstName` y `lastName` separados.

**Prueba Independiente**: Se prueba de forma aislada iniciando desde el Panel de Recepción con una habitación y reserva ya seleccionadas desde la copia local de la lista del día y ejecutando el siguiente flujo: Primero, validación contra la copia local (`daily_reservation` y `daily_reservation_room`) de una reserva en estado "ACTIVE" con habitación en estado "Reserved" y verificación de que no exista un `Stay` previo para la habitación; segundo, captura de ocupantes en el formulario verificando `firstName` y `lastName` separados para cada huésped, apertura del panel de datos migratorios ante nacionalidad distinta de Colombia solicitando obligatoriamente `birthDate`, `originPlace` y `destinationPlace` (texto libre con formato "Ciudad, País", asistido por catálogo), verificando que la cantidad de ocupantes coincida con `guestCount` de la habitación y no supere `maxCapacity`; tercero, visualización del resumen de confirmación con la cantidad total de huéspedes, la habitación y la estadía (sin exponer indicadores de extranjeros al Recepcionista); y cuarto, confirmación final verificando la pantalla de éxito con etiquetas "Habitación: Ocupada" y "Notificación a Módulo 2: Sincronizada" (sin horas), el botón único "Volver al inicio", la creación de la entidad Estancia con `source`, `checkInDate`, `expectedCheckinTime`, `expectedCheckoutTime` y el registro de RoomGuest con `firstName`, `lastName` e `isReservationGuest`.

**Escenarios de Aceptación**:

1. **Escenario**: Flujo completo de Check-In exitoso para huéspedes nacionales
   - **Dado** una reserva en la copia local de la lista del día en estado "ACTIVE" con fecha de inicio hoy y cantidad de huéspedes de la habitación registrada `guestCount = 2`, con habitación asignada en estado "Reserved" en Módulo 1, sin un registro previo de `Stay` para el par (`reservationRef`, `roomId`), seleccionada previamente desde el Panel de Recepción
   - **Cuando** el Recepcionista verifica la reserva en el paso 1 contra la copia local, registra en el formulario del paso 2 exactamente a 2 ocupantes con `firstName` y `lastName` separados (titular precargado desde la copia local y un acompañante nacional), revisa el resumen de confirmación en el paso 3 y confirma la admisión
   - **Entonces** el sistema crea de forma síncrona la entidad Estancia (Stay) vinculada a la reserva y a la habitación (almacenando `source`, `checkInDate`, `expectedCheckinTime`, `expectedCheckoutTime`, `titularFirstName`, `titularLastName` y `titularDocumentNumber`), registra a los ocupantes como RoomGuest con `firstName` y `lastName` indicando `isReservationGuest = true` para el titular, transiciona la habitación a "Occupied" en el inventario, despacha proactivamente mediante la cola asíncrona `m2.habitacion.checkin.queue` la notificación de check-in (`foreignGuestCount = 0`), y despliega la pantalla de éxito con las etiquetas de validación ("Habitación: Ocupada", "Notificación a Módulo 2: Sincronizada") y el botón único "Volver al inicio", sin mostrar horas en pantalla. El paso de la reserva a "CHECKED_IN" en Módulo 2 es derivado por dicho módulo cuando todas las habitaciones de la reserva completan su Check-In.

2. **Escenario**: Check-in con huésped extranjero, apertura del panel migratorio y colas separadas
   - **Dado** una reserva activa en la copia local con habitación asignada en estado "Reserved"
   - **Cuando** en el paso 2 ("Datos de huéspedes") el Recepcionista registra a un ocupante con nacionalidad distinta de Colombia (`nationality !== 'Colombia'`)
   - **Entonces** el sistema abre de inmediato el panel de datos migratorios para ese ocupante, solicitando obligatoriamente los tres campos faltantes: fecha de nacimiento (`birthDate`: fecha válida pasada sin hora en formato `AAAA-MM-DD`), procedencia (`originPlace`) y destino (`destinationPlace`) en formato texto libre con asistencia de catálogo de países (combobox de sugerencias; se acepta cualquier texto no vacío, incluyendo el formato "Ciudad, País" que exige el SIRE). Los datos de identidad (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`) se reutilizan del formulario del ocupante. El sistema asigna internamente el tipo de movimiento (`ENTRY`) y la fecha de movimiento (`checkInDate`) sin solicitarlos en pantalla. Si el Recepcionista cambia la nacionalidad nuevamente a Colombia, el panel migratorio se cierra de inmediato. El sistema bloquea el avance al paso 3 mientras alguno de los tres campos esté vacío
   - **Cuando** se completan los campos obligatorios del panel, se revisa el resumen en el paso 3 y se confirma la admisión
   - **Entonces** el sistema transiciona la habitación a "Occupied", crea la Estancia local, emite a la cola `m2.habitacion.checkin.queue` el mensaje con `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y `foreignGuestCount`, y emite a la cola `m2.huespedes.extranjeros.queue` un mensaje independiente por cada huésped extranjero con sus diez campos migratorios completos y `movementType = ENTRY`, mostrando en el paso 4 la confirmación de check-in sin esperar por la confirmación migratoria.

3. **Escenario**: Visualización del resumen de confirmación
   - **Dado** que se han validado la reserva y los datos de los ocupantes (pasos 1 y 2)
   - **Cuando** el Recepcionista avanza al paso 3 ("Confirmación")
   - **Entonces** el sistema presenta un resumen visual con la cantidad de huéspedes de la habitación, la habitación asignada (número como "Hab. 304" y debajo el tipo en gris, ej. "Doble"), la estadía (número de noches calculadas y debajo el rango de fechas en gris), y un cuadro informativo indicando que al confirmar el check-in la habitación pasará a estado "Occupied" de forma inmediata y se notificará a Módulo 2 por sus colas correspondientes (el Módulo 2 pasará la reserva a "CHECKED_IN" cuando todas sus habitaciones completen Check-In). El conteo de extranjeros (`foreignGuestCount`) se envía de forma transparente en la cola `m2.habitacion.checkin.queue` y no se expone como indicador visible al Recepcionista.

4. **Escenario**: Titular de la reserva precargado y fijo en el formulario de huéspedes
   - **Dado** una reserva activa obtenida en el paso 1 desde la copia local de la lista del día
   - **Cuando** el Recepcionista accede al paso 2 ("Datos de huéspedes")
   - **Entonces** el sistema presenta los datos del Huésped Titular (`firstName`, `lastName`, Tipo de documento, Número de documento y Nacionalidad en lista desplegable deshabilitada, con `fullName` solo para visualización) precargados desde la copia local, de solo lectura, sin ícono de eliminar y con la marca interna `isReservationGuest = true`, permitiendo únicamente gestionar acompañantes adicionales hasta completar el `guestCount` de la habitación.

---

### Historia de Usuario 2 - Bloqueo de admisiones inválidas e integridad de estados (Prioridad: P2)

Como Recepcionista, quiero que el sistema bloquee cualquier intento de Check-In que no cumpla las condiciones operativas, temporales, físicas o de correspondencia con la reserva, mostrando alertas claras y específicas en pantalla, para evitar ingresos no autorizados, asignaciones erróneas o inconsistencias en la máquina de estados.

**Por qué esta prioridad**: Asignar una habitación que no esté lista físicamente, sobre una reserva inexistente en la lista del día o con un número de huéspedes que discrepe de lo contratado produce fallas operativas y legales. Esta historia consolida las validaciones preventivas del flujo.

**Prueba Independiente**: Se prueba intentando ejecutar el Check-In con reservas que no figuran en la lista del día de hoy, con discrepancia entre ocupantes y `guestCount` de la habitación, habitaciones en estados distintos de "Reserved", excediendo la capacidad máxima de la habitación, o con ocupantes extranjeros con datos migratorios no diligenciados o inválidos en el panel, verificando que todas sean rechazadas con mensajes informativos controlados.

**Escenarios de Aceptación**:

1. **Escenario**: Rechazo de Check-In por discrepancia con la cantidad de huéspedes registrada en la habitación (`guestCount`)
   - **Dado** una reserva activa en la copia local donde la cantidad de huéspedes contratada para la habitación es `guestCount = 2`
   - **Cuando** el Recepcionista intenta confirmar la admisión habiendo registrado únicamente al titular (1 ocupante) o intentando registrar a 3 ocupantes
   - **Entonces** el sistema bloquea el avance e informa en pantalla que la cantidad de huéspedes que hacen check-in debe coincidir exactamente con la cantidad registrada para la habitación (`guestCount = 2`).

2. **Escenario**: Rechazo de Check-In por habitación en estado Available (no reservada)
   - **Dado** que existe una reserva en la copia local de la lista del día cuya habitación asignada se encuentra físicamente en estado "Available" en Módulo 1 (debido a que a las 00:00 se encontraba ocupada o en limpieza y, tras liberarse, aún no ha completado el ciclo para pasar a "Available" y apartarse a "Reserved")
   - **Cuando** el Recepcionista intenta procesar el Check-In
   - **Entonces** el sistema rechaza la operación indicando que la habitación debe encontrarse en estado "Reserved" según la máquina de estados (documentos/SPEC/referencias/maquina-estados-habitacion.md) y no es posible admitir al huésped hasta que lo esté.

3. **Escenario**: Rechazo de Check-In por habitación en estado operativo no apto
   - **Dado** una reserva activa en la copia local cuya habitación asignada se encuentra en Módulo 1 en estado "Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"
   - **Cuando** el Recepcionista intenta procesar el Check-In
   - **Entonces** el sistema bloquea la confirmación en el paso 1, informa en pantalla el estado físico real de la habitación y no permite avanzar al formulario de huéspedes ni a la ocupación.

4. **Escenario**: Rechazo por reserva no presente en la lista del día
   - **Dado** que se intenta procesar una reserva que no figura en la copia local de la lista del día (debido a que se encuentra en un estado contractual no apto en Módulo 2 como "PENDING", "CANCELLED", "CHECKED_OUT" o "NO_SHOW", o fue eliminada mediante `REMOVED`)
   - **Cuando** el Recepcionista intenta validar la reserva en el paso 1
   - **Entonces** el sistema rechaza la admisión informando que la reserva no figura en la lista del día de hoy y no admite Check-In.

5. **Escenario**: Rechazo por reserva con llegada anticipada no presente en la lista del día
   - **Dado** una reserva cuya fecha de inicio (`startDate`) es estrictamente posterior a la fecha actual del sistema y por tanto no forma parte de la lista del día recibida a las 00:00
   - **Cuando** se intenta acceder al Check-In en el paso 1
   - **Entonces** el sistema bloquea el flujo informando que la reserva no figura en la lista del día de hoy ya que la estadía aún no inicia.

6. **Escenario**: Rechazo por reserva con llegada no vigente no presente en la lista del día
   - **Dado** una reserva cuya fecha de inicio es anterior a la fecha actual o cuya fecha de fin ya expiró y por ende no fue remitida en la lista del día
   - **Cuando** se intenta acceder al Check-In en el paso 1
   - **Entonces** el sistema bloquea la transacción informando que la reserva no figura en la lista del día de hoy.

7. **Escenario**: Rechazo por superar la capacidad máxima de personas de la habitación
   - **Dado** una habitación con capacidad máxima configurada de 2 personas
   - **Cuando** el Recepcionista intenta registrar 3 o más ocupantes en el paso 2 ("Datos de huéspedes")
   - **Entonces** el sistema deshabilita el botón de agregar huéspedes e informa en el formulario que se ha alcanzado la capacidad máxima física de la habitación (`maxCapacity`).

8. **Escenario**: Rechazo por datos migratorios no diligenciados o inválidos en ocupante extranjero
   - **Dado** un ocupante registrado con nacionalidad distinta de Colombia en el paso 2
   - **Cuando** el Recepcionista intenta avanzar al paso 3 dejando vacío alguno de los campos del panel migratorio (`birthDate`, `originPlace` o `destinationPlace`), ingresando una fecha de nacimiento futura o del día de hoy, o dejando vacíos los campos de procedencia o destino
   - **Entonces** el sistema bloquea el avance al paso 3, resalta los campos faltantes o inválidos y exige su corrección antes de continuar.

---

### Casos Borde

- **Selección obligatoria ante múltiples reservas coincidentes por número de documento o nombre**: Si la búsqueda sobre la copia local arroja más de una reserva coincidente, el sistema lista las opciones mostrando referencia (`reservationRef`), titular (`firstName`, `lastName`), fechas de estadía y habitación, exigiendo la selección explícita antes de avanzar al paso 2.
- **Falla o indisponibilidad de Módulo 2 al notificar por cola**: Las notificaciones hacia Módulo 2 se emiten por colas asíncronas (`m2.habitacion.checkin.queue` y `m2.huespedes.extranjeros.queue`). Si Módulo 2 experimenta una caída de red o falta de disponibilidad externa al confirmar el paso 3, Módulo 1 mantiene en firme la creación de la Estancia y la transición síncrona de la habitación a "Occupied" para no detener la entrega física de la llave ni bloquear al huésped en recepción. Cada mensaje permanece en su respectiva cola para reintento automático en segundo plano (el reintento de las colas cubre fallas de entrega de red o Módulo 2 caído, no datos faltantes).
- **Idempotencia en la mensajería hacia Módulo 2**: Todo mensaje emitido lleva `messageId` único y `sequenceNumber` creciente. Si ocurre un reenvío por falla de red, el receptor descarta duplicados garantizando que no se alteren estados ni se dupliquen registros en Módulo 2.
- **Validación estricta y datos definitivos**: Los diez campos son estrictamente obligatorios en el formulario de Módulo 1 antes de avanzar de paso, y las fechas y `movementType` son validados y asignados por Módulo 1, garantizando envíos íntegros hacia Módulo 2.
- **Ausencia de `checkInDate` válido al momento de conformar el paquete migratorio**: El sistema genera el `checkInDate` (fecha del sistema en hora Colombia UTC-5, solo fecha) en el instante atómico de la confirmación local; no se despacha ninguna notificación migratoria a Módulo 2 sin una fecha válida.
- **Día operativo fijo** (acuerdo B11): El día operativo corresponde al día calendario de Colombia (00:00 a 23:59, UTC-5). El Check-In solo puede realizarse cuando `fechaActual == startDate` dentro de ese rango horario.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado "Recepcionista" iniciar y formalizar el registro de Check-In en Módulo 1.
- **FR-002**: El sistema DEBE estructurar el flujo de Check-In a través de 4 etapas visuales y operativas:
  1. *Validar reserva*
  2. *Datos de huéspedes*
  3. *Confirmación*
  4. *Check-In*
- **FR-003**: La búsqueda y selección de la reserva y habitación para Check-In se realizan **previamente** en el **Panel de Recepción** (`spec-consultar-panel-recepcion.md`) a través del buscador de Llegadas, el cual opera sobre la copia local de la lista del día (`daily_reservation` y `daily_reservation_room`). Al pulsar el botón "Check-in" en el panel, el flujo llega al paso 1 ("Validar reserva") con la referencia de reserva y habitación ya cargadas. En el paso 1 el sistema DEBE validar de forma local e inmediata los datos contra la copia local, verificando que la reserva exista, la habitación esté en `Reserved` y no exista un `Stay` previo para ese par (`reservationRef`, `roomId`), desplegando el código de reserva, titular (`firstName`, `lastName`), fechas (solo fechas), cantidad de huéspedes de la habitación (`guestCount`), fuente (`source`: `DIRECTA` o nombre de la OTA) y detalles de la habitación asignada con su estado en Módulo 1 (`Reserved` / Reservada). El paso 1 NO realiza consultas REST GET a Módulo 2 ni dispone de una barra de búsqueda propia.
- **FR-004**: El sistema DEBE validar como precondición física obligatoria que la habitación asignada se encuentre en estado físico "Reserved" según documentos/SPEC/referencias/maquina-estados-habitacion.md. Si la habitación se encuentra en cualquiera de los otros 7 estados ("Available", "Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"), el sistema DEBE rechazar la admisión con un mensaje de error controlado, detallando el estado físico real.
- **FR-005**: El sistema DEBE validar que la fecha del sistema coincida con la fecha de inicio de la reserva (`startDate = fechaActual`). Si la reserva no figura en la copia local de la lista del día (reservas anticipadas `startDate > fechaActual` o vencidas `endDate < fechaActual`), el sistema DEBE rechazar el acceso al Check-In.
- **FR-006**: En el paso 2 ("Datos de huéspedes"), el sistema DEBE invocar obligatoriamente el caso de uso interno "Procesar datos de huéspedes" (`<<includes>>`) para capturar en formulario los campos con nombres y apellidos separados: `firstName` (Primer nombre / nombres), `lastName` (Apellidos), `documentType` (Tipo de documento), `documentNumber` (Número de documento) y `nationality` (Nacionalidad), tanto para el Huésped Titular como para los acompañantes. Para ocupantes con nacionalidad distinta de Colombia, el sistema DEBE abrir el panel de datos migratorios capturando `birthDate` (fecha válida pasada `AAAA-MM-DD`), `originPlace` (procedencia) y `destinationPlace` (destino) mediante campo de texto con asistencia de catálogo (combobox de sugerencias de países; acepta texto libre en formato "Ciudad, País").
- **FR-007**: El sistema DEBE precargar la identidad del titular desde la copia local de la lista del día (`daily_reservation`), presentándolo de forma fija y de solo lectura en el formulario (con `firstName`, `lastName`, `documentType`, `documentNumber` y su nacionalidad en lista desplegable deshabilitada; `fullName` solo para visualización) sin opción de eliminar, marcándolo internamente con `isReservationGuest = true`.
- **FR-008**: El sistema DEBE validar de forma obligatoria que la cantidad total de personas que realizan el Check-In coincida exactamente con la cantidad de huéspedes asignada a la habitación en la reserva (`guestCount` de la habitación; pendiente de confirmación por Módulo 2: en caso de no recibirse por habitación, se valida contra la capacidad máxima `maxCapacity`), y no exceda la capacidad máxima física de la habitación (`maxCapacity`). Si la cantidad de personas registradas difiere de `guestCount`, el sistema DEBE impedir el avance hacia el paso 3 informando la discrepancia al Recepcionista.
- **FR-009**: Panel de datos migratorios para extranjeros: Cuando el Recepcionista marca a un ocupante con nacionalidad distinta de Colombia (`nationality !== 'Colombia'`), el sistema DEBE abrir de inmediato el panel de datos migratorios para ese ocupante, solicitando exclusivamente los tres campos faltantes: `birthDate` (fecha de nacimiento: fecha válida sin hora, formato `AAAA-MM-DD` hora Colombia y pasada —no hoy ni futura—), `originPlace` (procedencia) y `destinationPlace` (destino), estos dos últimos como campos de texto libre con asistencia de catálogo de países (combobox de sugerencias); se acepta cualquier valor no vacío incluyendo el formato "Ciudad, País" exigido por el SIRE (ej. "Madrid, España"). Los datos de identidad (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`) se toman del formulario principal del ocupante sin volver a pedirlos. El sistema asigna automáticamente el tipo de movimiento (`ENTRY`) y la fecha de movimiento (`checkInDate`). Los tres campos son obligatorios: el sistema DEBE bloquear el avance al paso 3 mientras alguno esté vacío. Si la nacionalidad se corrige a Colombia, el panel se cierra.
- **FR-010**: En el paso 3 ("Confirmación"), el sistema DEBE presentar un resumen visual con la cantidad total de huéspedes de la habitación, la habitación asignada (número y tipo en gris) y la estadía (número de noches calculadas y fechas en gris). DEBE incluir un cuadro informativo advirtiendo que al confirmar el check-in la habitación pasará a estado "Occupied" de forma inmediata y se notificará a Módulo 2 (la reserva pasará a "CHECKED_IN" en Módulo 2 cuando todas sus habitaciones completen Check-In). El sistema NO DEBE exponer al Recepcionista ningún indicador del conteo de extranjeros; el dato `foreignGuestCount` se calcula internamente y viaja de forma transparente a Módulo 2 en la cola `m2.habitacion.checkin.queue`.
- **FR-011**: Al confirmar la admisión en el paso 3, el sistema DEBE ejecutar de forma síncrona y atómica:
  1. La creación de la entidad `Estancia` (Stay) vinculada a la reserva y a la habitación, persistiendo la fuente (`source`: `DIRECTA` o nombre de la OTA tal como llega de Módulo 2), la fecha de llegada real (`checkInDate`), las fechas esperadas de la reserva (`expectedCheckinTime`, `expectedCheckoutTime` como fechas sin hora), y copiando `titularFirstName`, `titularLastName` y `titularDocumentNumber` para visualización en Salidas.
  2. La creación inmutable de los ocupantes como registros conceptuales `RoomGuest`, asignando `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, los campos migratorios si es extranjero (`birthDate`, `originPlace`, `destinationPlace`), y el indicador `isReservationGuest = true` para el titular y `false` para los acompañantes.
  3. La transición del estado de la habitación de "Reserved" a "Occupied" en el inventario físico de Módulo 1.
  4. El registro del recepcionista responsable (`receptionistIdCheckIn`).
- **FR-012**: Conforme al contrato de colas con Módulo 2, al confirmar el Check-In el sistema DEBE emitir dos tipos de notificaciones por colas asíncronas independientes:
  1. Hacia `m2.habitacion.checkin.queue`: un mensaje con `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y `foreignGuestCount` (conteo obligatorio de extranjeros en la habitación, 0 si no hay extranjeros).
  2. Hacia `m2.huespedes.extranjeros.queue`: si existen huéspedes extranjeros (`nationality !== 'Colombia'`), un mensaje independiente por cada huésped extranjero con `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y los diez campos migratorios exigidos por el SIRE: `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY`), `movementDate` (`checkInDate`), `originPlace` y `destinationPlace`.
  El Check-In físico nunca espera por datos migratorios ni por la respuesta de Módulo 2. Si un mensaje falla en la cola, se reintenta únicamente ese mensaje o huésped fallido en segundo plano. Los diez campos son obligatorios en el formulario y el envío es definitivo.
- **FR-013**: En el paso 4 ("Check-In"), el sistema DEBE mostrar la pantalla de confirmación exitosa con icono de verificación y exhibir las etiquetas de validación ("Habitación: Ocupada", "Notificación a Módulo 2: Sincronizada"), sin mostrar horas en pantalla y ofreciendo como única acción de salida el botón "Volver al inicio".
- **FR-014**: Ante lentitud extrema, falta de respuesta o caída de red de Módulo 2 durante la confirmación, el sistema NO DEBE bloquear la entrega física de la habitación ni revertir el estado "Occupied"; la Estancia se consolida localmente y las notificaciones a Módulo 2 permanecen en sus respectivas colas para reintento en segundo plano.
- **FR-015**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, la referencia de la reserva, el identificador de la Estancia creada, el recepcionista responsable y la marca de tiempo de confirmación.
- **FR-016**: Al confirmar la admisión, el sistema DEBE registrar la transición en `RoomStateHistory` (campos en documentos/SPEC/referencias/maquina-estados-habitacion.md) dentro de la misma transacción del cambio de estado: cerrar el periodo abierto de la habitación (`EndDateTime`) y abrir uno nuevo con `Status` = `Occupied`, `PreviousStatus` = `Reserved`, `StartDateTime` = fecha y hora del servidor, `ActorId` = recepcionista responsable, `SourceFlow` = *Registrar Check-In* y `ReservationRef` = referencia de la reserva.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso, tipo, capacidad máxima de personas (`maxCapacity`), tarifa base, estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive) y `reservedByReservationRef` (referencia de reserva que aparta la habitación, presente solo en estado `Reserved`).
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), fuente (`source`: `DIRECTA` o nombre de la OTA tal como llega de Módulo 2), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`), fechas esperadas de reserva (`expectedCheckinTime`, `expectedCheckoutTime` — fechas sin hora), datos copiados del titular (`titularFirstName`, `titularLastName`, `titularDocumentNumber`), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **RoomGuest**: Entidad conceptual que representa a cada individuo físicamente alojado. Registro inmutable vinculado a la Estancia. Atributos clave: `id`, `stayId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate` (solo extranjeros), `originPlace` (solo extranjeros), `destinationPlace` (solo extranjeros) e `isReservationGuest` (flag booleano que identifica al titular de la reserva).
- **ForeignGuestData**: Estructura enviada por `m2.huespedes.extranjeros.queue` (un mensaje por huésped extranjero) con los diez campos exigidos por el SIRE: `messageId`, `sequenceNumber`, `reservationRef`, `roomId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY`, asignado por Módulo 1), `movementDate` (`checkInDate`, asignado por Módulo 1), `originPlace` y `destinationPlace`.
- **Receptionist**: Actor de recepcionista que opera el flujo de recepción, consultas, registro de check-in y registro de check-out en el hotel.
- **Reservation**: Representación conceptual de la reserva en la copia local de la lista del día (`daily_reservation` y `daily_reservation_room`). De 1 a 10 habitaciones por reserva. Atributos: `reservationRef`, titular (`guestFirstName`, `guestLastName`, `guestDocumentType`, `guestDocumentNumber`, `guestNationality`), `source`, `startDate`, `endDate`, `guestCount` total y `guestCount` por habitación (pendiente Módulo 2), `updatedAt` y habitaciones asignadas (`roomId`, `roomNumber`, `categoryRoom`). No guarda `guestRef`, `externalConfirmationCode`, `createdAt` ni `notes`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede completar el flujo de 4 pasos de Check-In en menos de 2 minutos para huéspedes nacionales y en menos de 3 minutos para huéspedes extranjeros.
- **SC-002**: El 100% de los Check-Ins confirmados transicionan de inmediato la habitación al estado "Occupied" en el inventario físico local y persisten la fuente (`source`: `DIRECTA` o nombre de la OTA) en la entidad `Stay`.
- **SC-003**: El sistema rechaza el 100% de los intentos de Check-In donde la cantidad de ocupantes registrados no coincida exactamente con la cantidad de huéspedes asignada a la habitación (`guestCount`).
- **SC-004**: El sistema rechaza el 100% de los intentos de Check-In sobre habitaciones cuyo estado sea diferente de "Reserved", incluyendo habitaciones en estado "Available".
- **SC-005**: El 100% de las admisiones con huéspedes extranjeros abren el panel migratorio, capturan `birthDate` válida pasada y `originPlace`/`destinationPlace` en formato texto no vacío (con asistencia de catálogo), emitiendo la notificación de Check-In (`m2.habitacion.checkin.queue`) con `foreignGuestCount` y los mensajes migratorios por `m2.huespedes.extranjeros.queue` de forma desacoplada sin bloquear la entrega física de la llave.
- **SC-006**: En el 100% de los casos de contingencia o caída externa de Módulo 2, el sistema consolida la ocupación física local de la habitación y conserva las notificaciones en sus respectivas colas para reintento en segundo plano, evitando el bloqueo del huésped en el mostrador.


