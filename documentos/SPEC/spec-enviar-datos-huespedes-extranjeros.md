# Especificación de Funcionalidad: Enviar Datos de Huéspedes Extranjeros

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista (indirecto, vía extensión en "Procesar datos de huéspedes")
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Asignación automática de datos migratorios para extranjeros (Prioridad: P1)

Como Recepcionista, quiero que el sistema capture automáticamente los datos de control migratorio obligatorios (tipo de movimiento `ENTRY` y fecha `checkInDate`) de todo ocupante extranjero, y que el Recepcionista ingrese los campos de identidad, procedencia (`originPlace`) y destino (`destinationPlace`) requeridos por el SIRE (acuerdo B15), para conformar el paquete completo de diez campos exigidos por las autoridades migratorias sin errores de digitación.

**Por qué esta prioridad**: Garantiza el cumplimiento de la legislación migratoria sin entorpecer el flujo ágil de recepción ni desvirtuar el modelo de datos de Módulo 1. Conforme al acuerdo B15, el SIRE exige **diez campos** por huésped extranjero: nombre, apellido, tipo y número de documento, fecha de nacimiento, nacionalidad, tipo de movimiento (`ENTRY` — asignado automáticamente por Módulo 1), fecha de movimiento (`checkInDate` — asignada automáticamente por Módulo 1), procedencia (`originPlace`) y destino (`destinationPlace`). Los dos últimos campos sí son capturados por el Recepcionista en el formulario, ya que SIRE los rechaza si llegan vacíos. El sistema informa oportunamente al Recepcionista que se enviarán los datos a Módulo 2 (SIRE).

**Prueba Independiente**: Se prueba de forma aislada registrando a un ocupante con nacionalidad distinta de Colombia en "Procesar datos de huéspedes", verificando que el sistema informe el envío a Módulo 2 (SIRE) y estructure internamente los parámetros migratorios (`movementType = "ENTRADA"`, `fecha = checkInDate`) sin requerir datos adicionales al Recepcionista.

**Escenarios de Aceptación**:

1. **Escenario**: Asignación automática de datos migratorios al detectar extranjero (Happy Path)
   - **Dado** que durante la captura de ocupantes se registra a una persona con nacionalidad distinta de Colombia
   - **Cuando** se validan los datos y se avanza hacia la formalización del Check-In
   - **Entonces** el sistema informa al Recepcionista que los datos migratorios se enviarán a Módulo 2 (SIRE), habilita en el formulario los campos `originPlace` (procedencia) y `destinationPlace` (destino) para que el Recepcionista los ingrese, y asocia automáticamente al ocupante extranjero el tipo de movimiento `ENTRY` y la fecha correspondiente al `checkInDate` de la Estancia (solo fecha en hora Colombia UTC-5), sin requerir estos dos parámetros al Recepcionista y sin persistirlos en la entidad local RoomGuest.

2. **Escenario**: Supresión de armado migratorio ante ausencia de `checkInDate`
   - **Dado** que por una interrupción interna no se ha generado una fecha válida de llegada (`checkInDate`)
   - **Cuando** el sistema prepara el paquete migratorio para su consolidación
   - **Entonces** el sistema bloquea el despacho del paquete migratorio hacia Módulo 2 hasta contar con el `checkInDate` definitivo, previniendo notificaciones con fechas incompletas o inconsistentes.

3. **Escenario**: Coexistencia de huéspedes nacionales y extranjeros
   - **Dado** una habitación con un ocupante colombiano y un ocupante extranjero
   - **Cuando** se completa la captura de ocupantes
   - **Entonces** el sistema activa la extensión migratoria filtrando exclusivamente los datos del ocupante extranjero para el paquete de Módulo 2, preservando a ambos localmente como `RoomGuest` vinculados a la Estancia.

---

### Historia de Usuario 2 - Consolidación en la notificación a Módulo 2 y resiliencia (Prioridad: P2)

Como Recepcionista, quiero que los datos migratorios de los extranjeros se emitan de forma proactiva mediante cola asíncrona hacia Módulo 2 (SIRE), asegurando que cualquier lentitud o falla externa no impida ni retrase la entrega física de la habitación.

**Por qué esta prioridad**: Asegura la sincronización con Módulo 2 consolidando en la cola asíncrona el cambio de estado de la reserva y el registro migratorio (según lo definido en el contrato `mod-1-2-3.drawio`), manteniendo la resiliencia operativa para que una indisponibilidad externa nunca deje al huésped esperando en el mostrador.

**Prueba Independiente**: Se ejecuta un Check-In con huéspedes extranjeros simulando caída temporal o falta de respuesta de Módulo 2, comprobando que la habitación pase de inmediato a estado "Occupied" en Módulo 1, que se complete la admisión física y que el mensaje migratorio permanezca en la cola asíncrona para reintento en segundo plano.

**Escenarios de Aceptación**:

1. **Escenario**: Consolidación y envío exitoso en la notificación de Check-In a Módulo 2
   - **Dado** que se han registrado ocupantes con uno o más extranjeros
   - **Cuando** el Recepcionista confirma la admisión física del Check-In
   - **Entonces** el sistema despacha proactivamente mediante cola asíncrona (routing key `habitacion.checkin`) hacia el servicio de Módulo 2 la notificación de Check-In consolidando en el mismo mensaje la `reservationRef`, el `roomId` y la lista `foreignGuests` con los **diez campos** por extranjero: `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY`), `movementDate` (`checkInDate`), `originPlace` y `destinationPlace` (acuerdo B15), solicitando la transición a estado `IN_PROGRESS`.

2. **Escenario**: Resiliencia ante falla o lentitud externa de Módulo 2
   - **Dado** que se confirma el Check-In pero el servicio de Módulo 2 no responde o experimenta demoras
   - **Cuando** el sistema emite el mensaje a la cola asíncrona
   - **Entonces** el sistema no detiene la operación física, transiciona la habitación a "Occupied", crea la Estancia local y conserva la notificación en la cola para reintento en segundo plano sin bloquear la entrega física de la llave ni emitir errores no controlados al Recepcionista.

---

### Casos Borde

- **Múltiples ocupantes extranjeros en la misma habitación**: Si tanto el Huésped Titular como los acompañantes son extranjeros, el sistema consolida a todos en la lista `foreignGuests`, asignando a cada uno sus datos individuales de identidad, el mismo `movementType = ENTRY`, la misma `movementDate = checkInDate`, y los `originPlace`/`destinationPlace` capturados individualmente por el Recepcionista para cada extranjero.
- **Reenvío ante notificaciones previas** (idempotencia): Si la notificación consolidada se retransmite hacia una reserva que ya figura en `IN_PROGRESS` en Módulo 2, Módulo 2 responde 200 sin alterar estados ni duplicar registros (acuerdo sección 3.1).
- **Devolución de datos migratorios (`MigratoryDataReturned`)**: Si Módulo 2 detecta que un extranjero llegó incompleto o inválido (campo faltante, fecha futura, `movementType` incorrecto), devuelve el huésped a Módulo 1 con los `missingFields` y el motivo. Módulo 1 debe completar los datos y reenviar únicamente ese huésped. El Check-In físico y los demás huéspedes de la notificación no se ven afectados (acuerdo sección 4.3).
- **Identificación de acompañantes extranjeros en Módulo 2**: En el modelo de Módulo 2, `MigratoryMovement.guestRef` referencia a una entidad `Guest`. Al remitir datos de acompañantes extranjeros, Módulo 2 asocia el registro migratorio a la reserva y al ocupante correspondiente.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso condicional que extiende (`<<extend>>`) a "Procesar datos de huéspedes", activándose automáticamente cuando la nacionalidad de uno o más ocupantes registrados sea distinta de Colombia.
- **FR-002**: Para cada ocupante extranjero detectado en "Procesar datos de huéspedes", el sistema DEBE asignar automáticamente:
  - Tipo de movimiento migratorio: `ENTRY`
  - Fecha del movimiento: `checkInDate` de la Estancia (solo fecha en hora Colombia UTC-5, sin hora)
  sin solicitar ninguno de estos dos parámetros al Recepcionista.
- **FR-003**: Además de los datos de identidad reutilizados desde "Procesar datos de huéspedes" (nombre completo, tipo y número de documento, nacionalidad, fecha de nacimiento), el formulario DEBE presentar y exigir al Recepcionista el ingreso de los campos `originPlace` (procedencia) y `destinationPlace` (destino) para cada extranjero, requeridos por el SIRE (acuerdo B15). Si alguno de estos campos llega vacío, Módulo 2 rechazará el registro migratorio.
- **FR-004**: Conforme al contrato de interfaces con Módulo 2 y el acuerdo B9/B13 (routing key `habitacion.checkin`), al confirmar el Check-In el sistema DEBE emitir proactivamente mediante COLA asíncrona hacia Módulo 2 la notificación de check-in con: `reservationRef`, `roomId` y la lista `foreignGuests` con los diez campos migratorios por extranjero (acuerdo B15).
- **FR-005**: El sistema DEBE condicionar el armado y envío del paquete migratorio a la generación exitosa de un `checkInDate` válido (fecha del sistema) generado en la confirmación local de la Estancia.
- **FR-006**: La entrega o respuesta del servicio de Módulo 2 NO DEBE ser una precondición bloqueante para la creación de la `Estancia` ni para la transición síncrona de la habitación a "Occupied" en el inventario de Módulo 1.
- **FR-007**: Si Módulo 2 experimenta fallas de conectividad, demoras o caídas, el sistema DEBE mantener la notificación migratoria en la cola asíncrona para reintento automático en segundo plano, garantizando que el Recepcionista culmine la entrega física de la unidad sin demoras.
- **FR-008**: El sistema DEBE registrar en la bitácora de auditoría la referencia de la reserva, los documentos de los extranjeros notificados y la confirmación o programación de reintento del envío migratorio.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **RoomGuest**: Entidad conceptual que representa a cada individuo físicamente alojado. Registro inmutable vinculado a la Estancia. Atributos clave: `id`, `stayId`, `fullName`, `documentType`, `documentNumber`, `nationality` e `isReservationGuest` (flag booleano que identifica al titular de la reserva).
- **ForeignGuestData**: Estructura efímera de control migratorio con los **diez campos** exigidos por el SIRE (acuerdo B15): `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY`, asignado por Módulo 1), `movementDate` (`checkInDate`, asignado por Módulo 1), `originPlace` (procedencia, capturado por el Recepcionista) y `destinationPlace` (destino, capturado por el Recepcionista). Se consolida en la notificación `habitacion.checkin` hacia Módulo 2.
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), canal de origen (`source`: "Directo" u "OTA"), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`), fechas esperadas de reserva (`expectedCheckinTime`, `expectedCheckoutTime` — fechas sin hora), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **Module2 (MigratoryValidation / SIRE)**: Componente externo en Módulo 2 encargado de recibir los datos migratorios consolidados vía cola asíncrona, registrarlos en `MigratoryMovement` y generar el reporte plano `.TXT` para Migración Colombia.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de los ocupantes extranjeros capturados cuentan con sus datos migratorios ("ENTRADA" y `checkInDate`) asignados automáticamente sin intervención manual del Recepcionista.
- **SC-002**: El paquete migratorio se consolida y despacha en la petición de notificación de Check-In mediante cola asíncrona hacia Módulo 2 de forma inmediata tras la confirmación física.
- **SC-003**: Cero bloqueos de entrega de llaves o de ocupación de habitaciones físicas en Módulo 1 ocasionados por lentitud o caídas de red al transmitir datos migratorios a Módulo 2.
- **SC-004**: El 100% de las notificaciones que no pudieron ser entregadas en el primer intento quedan conservadas en la cola asíncrona para reintento en segundo plano.
