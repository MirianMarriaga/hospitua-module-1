# Especificación de Funcionalidad: Registrar Check-In

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-19

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Admisión física de huéspedes y ocupación en inventario (Prioridad: P1)

Como Recepcionista, quiero registrar el Check-In presencial guiado por el flujo de 4 pasos de la interfaz (Validar reserva → Datos de huéspedes → Confirmación → Check-In), validando la reserva activa y capturando la identidad de los ocupantes, para que se cree formalmente la estancia física persistiendo el canal de origen, la habitación transicione de forma síncrona a "Occupied" en el inventario y se notifique a Módulo 2 mediante cola asíncrona con la `reservationRef`, el `roomId` y los datos migratorios completos de los extranjeros (diez campos por huésped, incluyendo procedencia y destino), permitiendo la entrega ágil de la habitación.

**Por qué esta prioridad**: Es el flujo misional principal de admisión presencial en el hotel. Garantiza la creación formal de la entidad Estancia persistiendo el canal de origen (`source`) para que el Check-Out opere de forma 100% desacoplada, y que la habitación quede registrada inmediatamente como "Occupied" en el inventario de Módulo 1. Conforme al acuerdo B9 y B13, la notificación de check-in emitida por cola asíncrona hacia Módulo 2 debe incluir obligatoriamente la `reservationRef` y el `roomId`; si existen huéspedes extranjeros, se consolidan en la misma notificación sus datos migratorios con los diez campos exigidos por el SIRE (incluyendo `originPlace` y `destinationPlace`). El tipo de movimiento (`ENTRY`) y la fecha de movimiento (`checkInDate`) son asignados automáticamente por Módulo 1; el Recepcionista solo captura los datos de identidad, procedencia y destino de los extranjeros.

**Prueba Independiente**: Se prueba de forma aislada iniciando desde el Panel de Recepción con una reserva ya seleccionada y ejecutando el siguiente flujo: Primero, consulta reactiva REST GET a Módulo 2 y validación de una reserva en estado "ACTIVE" con habitación en estado "Reserved"; segundo, captura de ocupantes en el formulario verificando que coincida exactamente con la cantidad registrada en la reserva (`guestCount`) y alertando si hay extranjeros; tercero, visualización del resumen de confirmación; y cuarto, confirmación final verificando la pantalla de éxito con etiquetas "Habitación: Ocupada" y "Notificación a Módulo 2: Sincronizada" (sin horas), el botón único "Volver al inicio", la creación de la entidad Estancia con `source`, `checkInDate`, `expectedCheckinTime`, `expectedCheckoutTime` y el registro de RoomGuest con `isReservationGuest`.

**Escenarios de Aceptación**:

1. **Escenario**: Flujo completo de Check-In exitoso para huéspedes nacionales
   - **Dado** una reserva en Módulo 2 en estado "ACTIVE" con fecha de inicio hoy y cantidad de huéspedes registrada `guestCount = 2`, con habitación asignada en estado "Reserved" en Módulo 1, seleccionada previamente desde el Panel de Recepción
   - **Cuando** el Recepcionista verifica la reserva en el paso 1, registra en el formulario exactamente a 2 ocupantes (titular precargado y un acompañante), revisa el resumen de confirmación y confirma la admisión
   - **Entonces** el sistema crea de forma síncrona la entidad Estancia (Stay) vinculada a la reserva y a la habitación (almacenando `source`, `checkInDate`, `expectedCheckinTime` y `expectedCheckoutTime`), registra a los ocupantes como RoomGuest indicando `isReservationGuest = true` para el titular, transiciona la habitación a "Occupied" en el inventario, despacha proactivamente mediante cola asíncrona la notificación de check-in hacia el Módulo 2 actualizando la reserva a "IN_PROGRESS", y despliega la pantalla de éxito con las etiquetas de validación ("Habitación: Ocupada", "Notificación a Módulo 2: Sincronizada") y el botón único "Volver al inicio", sin mostrar horas en pantalla.

2. **Escenario**: Check-in con huésped extranjero, alerta SIRE y notificación consolidada
   - **Dado** una reserva activa con habitación asignada en estado "Reserved"
   - **Cuando** en el paso 2 ("Datos de huéspedes") el Recepcionista registra a un ocupante con nacionalidad distinta de Colombia
   - **Entonces** el sistema despliega de inmediato una alerta visual informativa indicando que se detectó un huésped extranjero y que los datos migratorios se enviarán automáticamente a Módulo 2 (SIRE), asignando internamente el tipo de movimiento (`ENTRY`) y la fecha de movimiento (`checkInDate`) sin solicitar estos campos al Recepcionista; el Recepcionista sí debe ingresar los campos de identidad, procedencia (`originPlace`) y destino (`destinationPlace`) del extranjero, requeridos por el SIRE (acuerdo B15)
   - **Cuando** se confirma la admisión
   - **Entonces** el sistema transiciona la habitación a "Occupied", crea la Estancia local y emite proactivamente hacia Módulo 2 por cola asíncrona la notificación de check-in (`habitacion.checkin`) consolidando en el mismo mensaje la `reservationRef`, el `roomId` y la lista `foreignGuests` con los diez campos migratorios de cada extranjero, mostrando en el paso 4 la confirmación sincronizada.

3. **Escenario**: Visualización del resumen de confirmación
   - **Dado** que se han validado la reserva y los datos de los ocupantes (pasos 1 y 2)
   - **Cuando** el Recepcionista avanza al paso 3 ("Confirmación")
   - **Entonces** el sistema presenta un resumen visual con la cantidad de huéspedes, la habitación asignada (número como "Hab. 304" y debajo el tipo en gris, ej. "Doble"), la estadía (número de noches y debajo el rango de fechas en gris), y un cuadro informativo indicando que al confirmar el check-in la habitación pasará a estado "Occupied" de forma inmediata y se notificará a Módulo 2 (reserva a "IN_PROGRESS" / En curso).

4. **Escenario**: Titular de la reserva inferido y fijo en el formulario de huéspedes
   - **Dado** una reserva activa obtenida en el paso 1 mediante "Consultar reservas"
   - **Cuando** el Recepcionista accede al paso 2 ("Datos de huéspedes")
   - **Entonces** el sistema presenta los datos del Huésped Titular (Nombre completo, Tipo de documento, Número de documento y Nacionalidad en lista desplegable deshabilitada) como precargados, de solo lectura, sin ícono de eliminar y con la marca interna `isReservationGuest = true`, permitiendo únicamente gestionar acompañantes adicionales hasta completar el `guestCount` de la reserva.

---

### Historia de Usuario 2 - Bloqueo de admisiones inválidas e integridad de estados (Prioridad: P2)

Como Recepcionista, quiero que el sistema bloquee cualquier intento de Check-In que no cumpla las condiciones operativas, temporales, físicas o de correspondencia con la reserva, mostrando alertas claras y específicas en pantalla, para evitar ingresos no autorizados, asignaciones erróneas o inconsistencias en la máquina de estados.

**Por qué esta prioridad**: Asignar una habitación que no esté lista físicamente, sobre una reserva inactiva o con un número de huéspedes que discrepe de lo contratado produce fallas operativas y legales. Esta historia consolida las validaciones preventivas del flujo.

**Prueba Independiente**: Se prueba intentando ejecutar el Check-In con reservas en estados no válidos ("PENDING", "CANCELLED", "IN_PROGRESS", "COMPLETED", "NO_SHOW"), con discrepancia entre ocupantes y `guestCount`, reservas fuera de la fecha de inicio válida, habitaciones en estados distintos de "Reserved", o excediendo la capacidad máxima de la habitación, verificando que todas sean rechazadas con mensajes informativos controlados.

**Escenarios de Aceptación**:

1. **Escenario**: Rechazo de Check-In por discrepancia con la cantidad de huéspedes registrada en la reserva (`guestCount`)
   - **Dado** una reserva activa donde la cantidad de huéspedes contratada es `guestCount = 2`
   - **Cuando** el Recepcionista intenta confirmar la admisión habiendo registrado únicamente al titular (1 ocupante) o intentando registrar a 3 ocupantes
   - **Entonces** el sistema bloquea el avance e informa en pantalla que la cantidad de huéspedes que hacen check-in debe coincidir exactamente con la cantidad registrada en la reserva (`guestCount = 2`).

2. **Escenario**: Rechazo de Check-In por habitación en estado Available (no reservada)
   - **Dado** que existe una reserva en estado "ACTIVE" en Módulo 2 cuya habitación asignada se encuentra físicamente en estado "Available" en Módulo 1 (es decir, sin haber pasado por el evento previo de reserva)
   - **Cuando** el Recepcionista intenta procesar el Check-In
   - **Entonces** el sistema rechaza la operación indicando que la habitación debe encontrarse en estado "Reserved" según la máquina de estados (documentos/SPEC/referencias/maquina-estados-habitacion.md) y no es posible admitir al huésped hasta que lo esté.

3. **Escenario**: Rechazo de Check-In por habitación en estado operativo no apto
   - **Dado** una reserva activa cuya habitación asignada se encuentra en Módulo 1 en estado "Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"
   - **Cuando** el Recepcionista intenta procesar el Check-In
   - **Entonces** el sistema bloquea la confirmación en el paso 1, informa en pantalla el estado físico real de la habitación y no permite avanzar al formulario de huéspedes ni a la ocupación.

4. **Escenario**: Rechazo por reserva en estado inválido en Módulo 2
   - **Dado** que la reserva consultada en Módulo 2 se encuentra en cualquiera de los estados no aptos para Check-In: "PENDING", "CANCELLED", "IN_PROGRESS", "COMPLETED" o "NO_SHOW"
   - **Cuando** el Recepcionista consulta el código de reserva en el paso 1
   - **Entonces** el sistema rechaza la admisión informando que la reserva no admite Check-In en su estado actual.

5. **Escenario**: Rechazo por llegada anticipada (fecha de inicio posterior a hoy)
   - **Dado** una reserva en estado "ACTIVE" cuya fecha de inicio (`startDate`) es estrictamente posterior a la fecha actual del sistema, recibida en el paso 1 desde el Panel de Recepción
   - **Cuando** el sistema valida la reserva en el paso 1
   - **Entonces** el sistema bloquea el flujo informando que la estadía aún no inicia según el calendario contractual de la reserva.

6. **Escenario**: Rechazo por llegada con fecha de inicio no vigente
   - **Dado** una reserva cuya fecha de inicio es anterior a la fecha actual o cuya fecha de fin ya expiró, recibida en el paso 1
   - **Cuando** el sistema valida la reserva en el paso 1
   - **Entonces** el sistema bloquea la transacción informando que el Check-In solo puede efectuarse en la fecha de inicio programada de la reserva.

7. **Escenario**: Rechazo por superar la capacidad máxima de personas de la habitación
   - **Dado** una habitación con capacidad máxima configurada de 2 personas
   - **Cuando** el Recepcionista intenta registrar 3 o más ocupantes en el paso 2 ("Datos de huéspedes")
   - **Entonces** el sistema deshabilita el botón de agregar huéspedes e informa en el formulario que se ha alcanzado la capacidad máxima física de la habitación.

---

### Casos Borde

- **Selección obligatoria ante múltiples reservas coincidentes por número de documento o nombre**: Si la búsqueda arroja más de una reserva coincidente, el sistema lista las opciones mostrando referencia (`reservationRef`), titular, fechas de estadía y estado (`status`), exigiendo la selección explícita antes de avanzar al paso 2.
- **Falla o indisponibilidad de Módulo 2 al notificar la confirmación por cola**: En condiciones normales la notificación a Módulo 2 se emite de inmediato por cola asíncrona (routing key `habitacion.checkin`). Si Módulo 2 experimenta una caída de red o falta de respuesta al confirmar el paso 3, Módulo 1 mantiene en firme la creación de la Estancia y la transición síncrona de la habitación a "Occupied" para no detener la entrega física de la llave ni bloquear al huésped en recepción, registrando el mensaje en la cola para reintento automático en segundo plano.
- **Comportamiento ante reenvío de notificación a Módulo 2**: Conforme al acuerdo de idempotencia, si la notificación de Check-In se reenvía sobre una reserva cuya habitación ya figura en `CHECKED_IN` en Módulo 2, este confirma la operación con código 200 sin generar registros duplicados ni alterar estados.
- **Datos migratorios devueltos por Módulo 2 (`MigratoryDataReturned`)**: Si Módulo 2 detecta que un huésped extranjero llegó con campos faltantes o inválidos (acuerdo sección 4.3), Módulo 1 recibe la devolución, identifica el huésped por `documentNumber`, completa los datos y reenvía únicamente al huésped corregido. Los demás huéspedes de la misma notificación ya fueron registrados normalmente.
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
- **FR-003**: La búsqueda y selección de la reserva para Check-In se realizan **previamente** en el **Panel de Recepción** (`spec-consultar-panel-recepcion.md`) a través del buscador de Llegadas. Al pulsar el botón "Check-in" en el panel, el flujo llega al paso 1 ("Validar reserva") con la referencia de reserva ya cargada. Conforme al diagrama de interfaces con Módulo 2 (`mod-1-2-3.drawio`), en el paso 1 el sistema DEBE consultar de forma reactiva mediante una petición sincrónica REST GET a Módulo 2 ("Consultar reservas") para obtener y validar los datos contractuales completos de la reserva, desplegando el código, titular, fechas (solo fechas), cantidad de huéspedes (`guestCount`), canal ("Directo" u "OTA"), estado en Módulo 2 (`ACTIVE` / Activa) y detalles de la habitación asignada con su estado en Módulo 1 (`Reserved` / Reservada). El paso 1 NO dispone de una barra de búsqueda propia.
- **FR-004**: El sistema DEBE validar como precondición física obligatoria que la habitación asignada se encuentre en estado físico "Reserved" según documentos/SPEC/referencias/maquina-estados-habitacion.md. Si la habitación se encuentra en cualquiera de los otros 7 estados ("Available", "Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"), el sistema DEBE rechazar la admisión con un mensaje de error controlado, detallando el estado físico real.
- **FR-005**: El sistema DEBE validar que la fecha del sistema coincida con la fecha de inicio de la reserva (`startDate = fechaActual`). En búsquedas por código de reserva (`reservationRef`), el sistema DEBE rechazar reservas anticipadas (`startDate > fechaActual`) o vencidas (`endDate < fechaActual` o `startDate < fechaActual`).
- **FR-006**: En el paso 2 ("Datos de huéspedes"), el sistema DEBE invocar obligatoriamente el caso de uso interno "Procesar datos de huéspedes" (`<<includes>>`) para capturar en formulario los campos: Nombre completo, Tipo de documento, Número de documento y Nacionalidad, tanto para el Huésped Titular como para los acompañantes.
- **FR-007**: El sistema DEBE precargar la identidad del titular desde la reserva obtenida de Módulo 2, presentándolo de forma fija y de solo lectura en el formulario (incluyendo su nacionalidad) sin opción de eliminar, marcándolo internamente con `isReservationGuest = true`.
- **FR-008**: El sistema DEBE validar de forma obligatoria que la cantidad total de personas que realizan el Check-In (titular más acompañantes registrados) coincida exactamente con la cantidad de huéspedes registrada en la reserva (`guestCount`), y no exceda la capacidad máxima de la habitación (`maxCapacity`). Si la cantidad de personas registradas difiere de `guestCount`, el sistema DEBE impedir el avance hacia la confirmación informando la discrepancia al Recepcionista.
- **FR-009**: Cuando uno o más ocupantes ingresados tengan nacionalidad distinta de Colombia, el sistema DEBE activar la extensión "Enviar datos de huéspedes extranjeros" (`<<extend>>`), mostrando de inmediato una alerta visual informativa indicando que los datos migratorios se enviarán automáticamente a Módulo 2 (SIRE). El Recepcionista DEBE ingresar los campos `originPlace` (procedencia) y `destinationPlace` (destino) de cada extranjero, exigidos por el SIRE (acuerdo B15). El sistema asigna automáticamente el tipo de movimiento (`ENTRY`) y la fecha de movimiento (`checkInDate`) sin solicitarlos en pantalla.
- **FR-010**: En el paso 3 ("Confirmación"), el sistema DEBE presentar un resumen visual con la cantidad de huéspedes, la habitación asignada (número y tipo en gris), la estadía (número de noches y fechas en gris), e incluir un cuadro informativo advirtiendo que al confirmar el check-in la habitación pasará a estado "Occupied" de forma inmediata y se notificará a Módulo 2 (reserva a "IN_PROGRESS" / En curso).
- **FR-011**: Al confirmar la admisión en el paso 3, el sistema DEBE ejecutar de forma síncrona y atómica:
  1. La creación de la entidad `Estancia` (Stay) vinculada a la reserva y a la habitación, persistiendo el canal de origen (`source`: "Directo" u "OTA"), la fecha de llegada real (`checkInDate`), y las fechas esperadas de la reserva (`expectedCheckinTime`, `expectedCheckoutTime` como fechas sin hora).
  2. La creación inmutable de los ocupantes como registros conceptuales `RoomGuest`, asignando el indicador `isReservationGuest = true` para el titular y `false` para los acompañantes.
  3. La transición del estado de la habitación de "Reserved" a "Occupied" en el inventario físico de Módulo 1.
  4. El registro del recepcionista responsable (`receptionistIdCheckIn`).
- **FR-012**: Conforme al contrato de interfaces con Módulo 2 y el acuerdo B9/B13 (routing key `habitacion.checkin`), al confirmar el Check-In el sistema DEBE emitir una notificación proactiva mediante COLA asíncrona hacia Módulo 2 incluyendo obligatoriamente: `reservationRef`, `roomId` (acuerdo B13) y, si existen extranjeros, la lista `foreignGuests` con los **diez campos** por huésped: `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY`, asignado por Módulo 1), `movementDate` (`checkInDate`, asignado por Módulo 1), `originPlace` y `destinationPlace` (acuerdo B15). Un huésped extranjero incompleto no bloquea el Check-In físico ni a los demás huéspedes de la notificación; Módulo 2 devolverá el huésped incompleto mediante `MigratoryDataReturned` para reenvío.
- **FR-013**: En el paso 4 ("Check-In"), el sistema DEBE mostrar la pantalla de confirmación exitosa con icono de verificación y exhibir las etiquetas de validación ("Habitación: Ocupada", "Notificación a Módulo 2: Sincronizada"), sin mostrar horas en pantalla y ofreciendo como única acción de salida el botón "Volver al inicio".
- **FR-014**: Ante lentitud extrema, falta de respuesta o caída de red de Módulo 2 durante la confirmación, el sistema NO DEBE bloquear la entrega física de la habitación ni revertir el estado "Occupied"; la Estancia se consolida localmente y la notificación a Módulo 2 permanece en la cola para reintento en segundo plano.
- **FR-015**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, la referencia de la reserva, el identificador de la Estancia creada, el recepcionista responsable y la marca de tiempo de confirmación.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), canal de origen (`source`: "Directo" u "OTA"), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`), fechas esperadas de reserva (`expectedCheckinTime`, `expectedCheckoutTime` — fechas sin hora), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **RoomGuest**: Entidad conceptual que representa a cada individuo físicamente alojado. Registro inmutable vinculado a la Estancia. Atributos clave: `id`, `stayId`, `fullName`, `documentType`, `documentNumber`, `nationality` e `isReservationGuest` (flag booleano que identifica al titular de la reserva).
- **ForeignGuestData**: Estructura efímera con los diez campos migratorios exigidos por el SIRE que se consolida en la notificación `habitacion.checkin` hacia Módulo 2. Campos: `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY`, asignado por Módulo 1), `movementDate` (`checkInDate`, asignado por Módulo 1), `originPlace` y `destinationPlace` (acuerdo B15).
- **Receptionist**: Actor de recepcionista que opera el flujo de recepción, consultas, registro de check-in y registro de check-out en el hotel.
- **Reservation**: Entidad conceptual de reserva administrada por Módulo 2. Atributos clave: ID único, referencia de reserva (`reservationRef`), fecha de inicio (`startDate`), fecha de fin (`endDate`), cantidad de huéspedes (`guestCount`), estado (`status`: `PENDING`, `ACTIVE`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`), habitación asignada (`assignedRoomId`) y datos del titular.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede completar el flujo de 4 pasos de Check-In en menos de 2 minutos para huéspedes nacionales y en menos de 3 minutos para huéspedes extranjeros.
- **SC-002**: El 100% de los Check-Ins confirmados transicionan de inmediato la habitación al estado "Occupied" en el inventario físico local y persisten el canal de origen (`source`) en la entidad `Stay`.
- **SC-003**: El sistema rechaza el 100% de los intentos de Check-In donde la cantidad de ocupantes registrados no coincida exactamente con la cantidad de huéspedes de la reserva (`guestCount`).
- **SC-004**: El sistema rechaza el 100% de los intentos de Check-In sobre habitaciones cuyo estado sea diferente de "Reserved", incluyendo habitaciones en estado "Available".
- **SC-005**: El 100% de las admisiones con huéspedes extranjeros disparan la alerta visual de envío migratorio al Módulo 2 (SIRE) y consolidan los datos migratorios en la notificación por cola sin solicitar campos adicionales en pantalla.
- **SC-006**: En el 100% de los casos de contingencia o caída externa de Módulo 2, el sistema consolida la ocupación física local de la habitación y conserva la notificación en cola para reintento en segundo plano, evitando el bloqueo del huésped en el mostrador.

