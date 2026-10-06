# Especificación de Funcionalidad: Enviar Datos de Huéspedes Extranjeros

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista (indirecto, vía extensión en "Procesar datos de huéspedes")
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Asignación automática de datos migratorios para extranjeros (Prioridad: P1)

Como Recepcionista, quiero que el sistema asigne automáticamente los datos de movimiento migratorio obligatorios (`movementType = ENTRY` en Check-In o `DEPARTURE` en Check-Out, y `movementDate`) para todo ocupante extranjero, y que a través del panel de datos migratorios se capturen los campos de fecha de nacimiento (`birthDate`), procedencia (`originPlace`) y destino (`destinationPlace`) en texto libre con asistencia de catálogo (formato "Ciudad, País"), para conformar el paquete de diez campos exigidos por las autoridades migratorias (SIRE).

**Por qué esta prioridad**: Garantiza el cumplimiento de la legislación migratoria sin entorpecer el flujo de recepción. Conforme a los acuerdos actualizados, el SIRE exige diez campos por huésped extranjero: `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY` o `DEPARTURE`), `movementDate` (`checkInDate` o `checkOutDate`), `originPlace` y `destinationPlace`. Los campos `birthDate`, `originPlace` y `destinationPlace` se capturan en el panel de datos migratorios de "Procesar datos de huéspedes" (estos dos últimos mediante combobox con sugerencias de países; se acepta texto libre en formato "Ciudad, País" que exige el SIRE). Los datos migratorios ya no viajan dentro de la notificación de Check-In ni de Check-Out; se transmiten de forma totalmente independiente a través de la cola `m2.huespedes.extranjeros.queue`, enviando un mensaje individual por cada huésped extranjero con su `messageId` y `sequenceNumber`, garantizando que la entrega de la habitación nunca espere por el trámite migratorio.

**Prueba Independiente**: Se prueba de forma aislada registrando a un ocupante con nacionalidad distinta de Colombia en "Procesar datos de huéspedes", verificando que el sistema abra el panel migratorio solicitando obligatoriamente `birthDate`, `originPlace` y `destinationPlace` (texto libre con formato "Ciudad, País", asistido por catálogo), y estructure internamente los parámetros migratorios con `movementType = ENTRY` y `movementDate = checkInDate` (sin requerir estos dos parámetros al Recepcionista).

**Escenarios de Aceptación**:

1. **Escenario**: Asignación automática de datos migratorios al detectar extranjero (Happy Path)
   - **Dado** que durante la captura de ocupantes en el Check-In se registra a una persona con nacionalidad distinta de Colombia (`nationality !== 'Colombia'`)
   - **Cuando** se completan los campos obligatorios del panel migratorio (`birthDate` pasada válida, y `originPlace`/`destinationPlace` con texto no vacío) y se avanza hacia la formalización
   - **Entonces** el sistema informa al Recepcionista que los datos migratorios se enviarán a Módulo 2 (SIRE), y asocia automáticamente al ocupante extranjero el tipo de movimiento `ENTRY` y la fecha correspondiente al `checkInDate` de la Estancia (solo fecha en hora Colombia UTC-5), sin requerir estos dos parámetros al Recepcionista y conformando la estructura de diez campos.

2. **Escenario**: Supresión de armado migratorio ante ausencia de `checkInDate`
   - **Dado** que por una interrupción interna no se ha generado una fecha válida de llegada (`checkInDate`)
   - **Cuando** el sistema prepara el paquete migratorio para su despacho
   - **Entonces** el sistema bloquea el despacho del mensaje migratorio hacia Módulo 2 hasta contar con el `checkInDate` definitivo, previniendo notificaciones con fechas incompletas o inconsistentes.

3. **Escenario**: Coexistencia de huéspedes nacionales y extranjeros
   - **Dado** una habitación con un ocupante colombiano y un ocupante extranjero
   - **Cuando** se completa la captura de ocupantes
   - **Entonces** el sistema activa la extensión migratoria filtrando exclusivamente los datos del ocupante extranjero para emitir su mensaje independiente por `m2.huespedes.extranjeros.queue`, preservando a ambos localmente como `RoomGuest` vinculados a la Estancia.

---

### Historia de Usuario 2 - Emisión desacoplada por cola asíncrona y resiliencia (Prioridad: P2)

Como Recepcionista, quiero que los datos migratorios de los extranjeros se emitan de forma proactiva mediante la cola asíncrona independiente `m2.huespedes.extranjeros.queue` (un mensaje por extranjero), asegurando que cualquier lentitud o falla externa no impida ni retrase la entrega física ni la liberación de la habitación.

**Por qué esta prioridad**: Separa a los extranjeros en su propia cola `m2.huespedes.extranjeros.queue` para que el Check-In (`m2.habitacion.checkin.queue`) y el Check-Out (`m2.habitacion.checkout.queue`) nunca esperen por datos migratorios. Si un mensaje falla, la cola reintenta solo ese huésped, manteniendo la resiliencia operativa para que una indisponibilidad externa nunca deje al huésped esperando en el mostrador.

**Prueba Independiente**: Se ejecuta un Check-In o Check-Out con huéspedes extranjeros simulando caída temporal o falta de respuesta de Módulo 2 en `m2.huespedes.extranjeros.queue`, comprobando que la habitación pase de inmediato a su estado físico ("Occupied" o "PendingCleaning"), que la admisión o salida física concluya normalmente y que el mensaje individual del extranjero permanezca en la cola para reintento en segundo plano.

**Escenarios de Aceptación**:

1. **Escenario**: Envío desacoplado y exitoso en la cola de extranjeros
   - **Dado** que se han registrado ocupantes con uno o más extranjeros
   - **Cuando** el Recepcionista confirma la admisión física del Check-In (o la salida en Check-Out)
   - **Entonces** el sistema despacha proactivamente a la cola `m2.huespedes.extranjeros.queue` un mensaje independiente por cada huésped extranjero con `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y los diez campos migratorios: `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY` en Check-In o `DEPARTURE` en Check-Out), `movementDate` (`checkInDate` o `checkOutDate`), `originPlace` y `destinationPlace`. La notificación de habitación viaja en paralelo por su propia cola (`m2.habitacion.checkin.queue` o `m2.habitacion.checkout.queue`) llevando el `foreignGuestCount`.

2. **Escenario**: Resiliencia ante falla o lentitud externa en la cola de extranjeros
   - **Dado** que se confirma el Check-In pero la cola de extranjeros experimenta fallas de entrega o demoras externas
   - **Cuando** el sistema emite el mensaje a la cola asíncrona
   - **Entonces** el sistema no detiene la operación física, transiciona la habitación a "Occupied", crea la Estancia local y conserva el mensaje migratorio en la cola para reintento en segundo plano sin bloquear la entrega física de la llave ni emitir errores al Recepcionista.

---

### Casos Borde

- **Múltiples ocupantes extranjeros en la misma habitación**: Si existen múltiples extranjeros (titular o acompañantes), el sistema emite un mensaje individual independiente por cada uno a través de `m2.huespedes.extranjeros.queue`, asignando a cada uno sus datos individuales de identidad, su `birthDate`, sus valores de `originPlace`/`destinationPlace`, y el `movementType`/`movementDate` correspondiente.
- **Idempotencia en la mensajería hacia Módulo 2**: Todo mensaje lleva `messageId` único y `sequenceNumber` creciente. Si ocurre una retransmisión por falla de conectividad, el receptor descarta duplicados garantizando que no se dupliquen registros migratorios.
- **Eliminación de devoluciones migratorias (`MigratoryDataReturned`)**: No existen devoluciones de datos migratorios desde Módulo 2. Los diez campos son estrictamente obligatorios en el panel de Módulo 1 antes de avanzar de paso, y las fechas y `movementType` son validados y asignados por Módulo 1.
- **Reutilización de procedencia y destino en Check-Out**: En la salida (Check-Out), `originPlace` y `destinationPlace` se toman de los datos capturados durante el Check-In y registrados en `RoomGuest`, asociando `movementType = DEPARTURE` y `movementDate = checkOutDate`.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso condicional que extiende (`<<extend>>`) a "Procesar datos de huéspedes", activándose automáticamente cuando la nacionalidad de uno o más ocupantes registrados sea distinta de Colombia (`nationality !== 'Colombia'`).
- **FR-002**: Para cada ocupante extranjero detectado en "Procesar datos de huéspedes", el sistema DEBE asignar automáticamente:
  - Tipo de movimiento migratorio: `ENTRY` en Check-In y `DEPARTURE` en Check-Out.
  - Fecha del movimiento: `checkInDate` en Check-In y `checkOutDate` en Check-Out (solo fecha en hora Colombia UTC-5, sin hora).
  sin solicitar ninguno de estos dos parámetros al Recepcionista.
- **FR-003**: A través del panel de datos migratorios de "Procesar datos de huéspedes", el formulario DEBE capturar obligatoriamente: `birthDate` (fecha válida pasada sin hora en formato `AAAA-MM-DD`), `originPlace` (procedencia) y `destinationPlace` (destino), estos dos últimos mediante campo de texto libre con asistencia de catálogo de países (combobox de sugerencias); se acepta cualquier valor no vacío incluyendo el formato "Ciudad, País" que exige el SIRE (ej. "Madrid, España"). Los datos de identidad (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`) se toman directamente del formulario del ocupante.
- **FR-004**: Conforme al contrato de colas con Módulo 2, al confirmar Check-In o Check-Out el sistema DEBE emitir hacia la cola asíncrona `m2.huespedes.extranjeros.queue` **un mensaje individual por cada huésped extranjero**, incluyendo: `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y los **diez campos** migratorios completos: `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY` o `DEPARTURE`), `movementDate` (`checkInDate` o `checkOutDate`), `originPlace` y `destinationPlace`. La notificación de habitación viaja de forma independiente por `m2.habitacion.checkin.queue` o `m2.habitacion.checkout.queue` con el conteo `foreignGuestCount`.
- **FR-005**: El sistema DEBE condicionar el armado y envío del paquete migratorio a la generación exitosa de una fecha válida del sistema (`checkInDate` o `checkOutDate`) generada en la transacción atómica de la Estancia.
- **FR-006**: La entrega o respuesta de la cola `m2.huespedes.extranjeros.queue` NO DEBE ser una precondición bloqueante para la creación o cierre de la `Estancia`, ni para la transición síncrona de la habitación a "Occupied" o "PendingCleaning" en el inventario de Módulo 1.
- **FR-007**: Si se experimentan fallas de conectividad o demoras en la cola de extranjeros, el sistema DEBE mantener el mensaje individual del huésped en la cola para reintento automático en segundo plano, garantizando que el Recepcionista culmine la entrega física o liberación de la unidad sin demoras.
- **FR-008**: El sistema DEBE registrar en la bitácora de auditoría la referencia de la reserva, el identificador de la habitación, los documentos de los extranjeros notificados y la confirmación o programación de reintento del envío migratorio.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **ForeignGuestData**: Estructura de control migratorio con los **diez campos** exigidos por el SIRE que se despacha por la cola `m2.huespedes.extranjeros.queue` (un mensaje por huésped extranjero): `messageId`, `sequenceNumber`, `reservationRef`, `roomId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY` o `DEPARTURE`), `movementDate` (`checkInDate` o `checkOutDate`), `originPlace` y `destinationPlace`.
- **RoomGuest**: Entidad conceptual que representa a cada individuo físicamente alojado. Registro inmutable vinculado a la Estancia. Atributos clave: `id`, `stayId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`, `originPlace`, `destinationPlace` e `isReservationGuest`.
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), canal de origen (`source`: `DIRECTA` o nombre de la OTA), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`), fechas esperadas de reserva, recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **Module2 (MigratoryValidation / SIRE)**: Componente externo en Módulo 2 encargado de recibir los mensajes migratorios individuales vía cola asíncrona `m2.huespedes.extranjeros.queue`, registrarlos en `MigratoryMovement` y generar el reporte plano `.TXT` para Migración Colombia.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de los ocupantes extranjeros capturados cuentan con sus datos de movimiento migratorio (`ENTRY` o `DEPARTURE`, y fecha correspondiente) asignados automáticamente sin intervención manual del Recepcionista.
- **SC-002**: El 100% de los ocupantes extranjeros confirmados en Check-In o Check-Out generan y despachan su mensaje migratorio individual por la cola `m2.huespedes.extranjeros.queue` de forma desacoplada de la notificación de habitación.
- **SC-003**: Cero bloqueos de entrega de llaves o de liberación de habitaciones físicas en Módulo 1 ocasionados por lentitud o caídas de red al transmitir datos migratorios a Módulo 2.
- **SC-004**: El 100% de los mensajes migratorios individuales que no pudieron ser entregados en el primer intento quedan conservados en la cola asíncrona para reintento en segundo plano.

