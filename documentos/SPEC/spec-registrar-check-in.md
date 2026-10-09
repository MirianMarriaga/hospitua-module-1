# Especificación de Funcionalidad: Registrar Check-In

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-19
**Actualizado**: 2026-10-09

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Admisión física de huéspedes y ocupación en inventario (Prioridad: P1)

Como Recepcionista, quiero registrar el Check-In presencial guiado por el flujo de 4 pasos de la interfaz (Validar reserva → Datos de huéspedes → Confirmación → Check-In), validando la reserva activa contra la copia local de la lista del día (`daily_reservation`, `daily_reservation_room`), la ausencia de un `Stay` previo para ese par (`reservationRef`, `roomId`) y capturando la identidad de todos los ocupantes (abriendo el panel de datos migratorios para cada extranjero con procedencia y destino en formato texto libre asistido por catálogo), para que se cree formalmente la estancia física persistiendo la fuente (`source`), la habitación transicione de forma síncrona a "Occupied" en el inventario y se notifique a Módulo 2 mediante la cola `m2.habitacion.checkin.queue` con **todos los huéspedes** (nacionales y extranjeros) en el array `guests[]`, permitiendo la entrega ágil de la habitación.

**Por qué esta prioridad**: Es el flujo misional principal de admisión presencial en el hotel. Conforme a los acuerdos con Módulo 2, la notificación de check-in emitida por cola asíncrona lleva el formato plano: `messageId`, `sequenceNumber`, `reservationRef`, `roomId`, `movementType` (`ENTRY`), `movementDate` (`checkInDate`) y `guests[]` con **todos** los huéspedes, nacionales y extranjeros. No existe cola separada de extranjeros. `movementType` y `movementDate` los asigna el sistema, no el Recepcionista.

**Prueba Independiente**: Iniciar desde el Panel de Recepción con una habitación y reserva seleccionadas desde la copia local. Ejecutar: (1) validación local contra `daily_reservation`/`daily_reservation_room` — reserva ACTIVE, habitación en Reserved, sin Stay previo; (2) captura de huéspedes con `firstName`/`lastName` separados, titular precargado de solo lectura, apertura del panel migratorio ante nacionalidad ≠ Colombia solicitando `birthDate` (pasada), `originPlace` y `destinationPlace`; (3) resumen de confirmación; (4) confirmación final verificando Stay creado, transición a Occupied, y payload del outbox con `movementType = ENTRY`, `movementDate = checkInDate` y `guests[]` completo con todos los huéspedes.

**Escenarios de Aceptación**:

1. **Escenario**: Flujo completo de Check-In exitoso para huéspedes nacionales
   - **Dado** una reserva en la copia local con `startDate = hoy`, habitación en estado `Reserved`, sin `Stay` previo para el par (`reservationRef`, `roomId`), `guestCount = 2` para la habitación
   - **Cuando** el Recepcionista valida la reserva en el paso 1 (local), registra 2 ocupantes colombianos en el paso 2 y confirma en el paso 3
   - **Entonces** el sistema crea la `Estancia` (Stay) con `source`, `checkInDate`, `expectedCheckinTime`, `expectedCheckoutTime`, `titularFirstName`, `titularLastName`, `titularDocumentNumber`; registra los `RoomGuest` con `firstName`, `lastName`, `isReservationGuest`; transiciona la habitación a `Occupied`; despacha por `m2.habitacion.checkin.queue` el mensaje con `messageId`, `sequenceNumber`, `reservationRef`, `roomId`, `movementType = ENTRY`, `movementDate = checkInDate` y `guests[]` con todos los huéspedes (sin campos migratorios para colombianos); y muestra la pantalla de éxito

2. **Escenario**: Check-In con huésped extranjero y panel migratorio
   - **Dado** una reserva activa en la copia local con habitación en `Reserved`
   - **Cuando** en el paso 2 el Recepcionista registra un ocupante con `nationality ≠ Colombia`
   - **Entonces** el sistema abre el panel migratorio para ese ocupante, solicitando obligatoriamente `birthDate` (fecha pasada, `AAAA-MM-DD`), `originPlace` y `destinationPlace` (texto libre con asistencia de catálogo, formato "Ciudad, País"). El sistema asigna `movementType = ENTRY` y `movementDate = checkInDate` automáticamente. Al confirmar, el mensaje en `m2.habitacion.checkin.queue` incluye en `guests[]` al extranjero con sus diez campos: `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`, `originPlace`, `destinationPlace`, `movementType`, `movementDate`. El Check-In nunca se bloquea esperando confirmación de la cola

3. **Escenario**: Resumen de confirmación (paso 3)
   - **Dado** que los pasos 1 y 2 están validados
   - **Cuando** el Recepcionista avanza al paso 3
   - **Entonces** el sistema muestra: cantidad de huéspedes de la habitación, habitación asignada (número y tipo), estadía (noches calculadas y rango de fechas), y cuadro informativo indicando que al confirmar la habitación pasará a "Occupied" y la reserva pasará a "En curso" cuando todas sus habitaciones completen Check-In. No se muestran indicadores técnicos de colas ni módulos al Recepcionista

4. **Escenario**: Titular precargado y fijo en el paso 2
   - **Dado** una reserva obtenida en el paso 1 desde la copia local
   - **Cuando** el Recepcionista accede al paso 2
   - **Entonces** el sistema presenta los datos del titular (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`) en solo lectura, con selector de nacionalidad deshabilitado, sin ícono de eliminar, marcado internamente con `isReservationGuest = true`. [NEEDS_CONFIRMATION_MODULO_2] Si el titular llega con `fullName` en lugar de `firstName`/`lastName` separados, se muestra `fullName` y —si es extranjero— se piden nombres y apellidos separados en el formulario (editables)

---

### Historia de Usuario 2 - Bloqueo de admisiones inválidas (Prioridad: P2)

Como Recepcionista, quiero que el sistema rechace Check-Ins inválidos con alertas claras.

**Escenarios de Aceptación**:

1. **Escenario**: Rechazo por discrepancia con `guestCount` de la habitación
   - **Dado** `guestCount = 2` para la habitación
   - **Cuando** se intenta confirmar con 1 o 3 ocupantes
   - **Entonces** el sistema bloquea el avance e informa la discrepancia

2. **Escenario**: Rechazo por habitación en Available (no reservada)
   - **Dado** habitación en estado `Available` (no `Reserved`)
   - **Cuando** el Recepcionista intenta el Check-In
   - **Entonces** el sistema rechaza indicando que la habitación debe estar en `Reserved`

3. **Escenario**: Rechazo por habitación en estado operativo no apto
   - **Dado** habitación en `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`
   - **Cuando** se intenta el Check-In
   - **Entonces** el sistema bloquea en el paso 1 informando el estado real

4. **Escenario**: Rechazo por reserva no presente en la copia local del día
   - **Dado** reserva no encontrada en `daily_reservation` (cancelada, vencida o con `REMOVED`)
   - **Cuando** se intenta validar en el paso 1
   - **Entonces** el sistema rechaza indicando que la reserva no figura en la lista del día

5. **Escenario**: Rechazo por superar la capacidad máxima (`maxCapacity`)
   - **Dado** `maxCapacity = 2`
   - **Cuando** se intentan registrar 3 o más ocupantes
   - **Entonces** el sistema deshabilita el botón de agregar huéspedes e informa que se alcanzó la capacidad máxima

6. **Escenario**: Rechazo por datos migratorios incompletos o `birthDate` no válida
   - **Dado** un ocupante extranjero con panel migratorio incompleto o `birthDate` futura/hoy
   - **Cuando** se intenta avanzar al paso 3
   - **Entonces** el sistema bloquea el avance, resalta los campos faltantes o inválidos

---

### Casos Borde

- **Falla de la cola al notificar a Módulo 2**: El Check-In físico se consolida localmente (Stay creado, habitación en `Occupied`). El mensaje en `m2.habitacion.checkin.queue` permanece en el outbox para reintento en segundo plano. El huésped no es retenido en recepción.
- **Idempotencia del `messageId`**: Todo mensaje lleva `messageId` único (UUID). En reintentos se usa el mismo `messageId`. El receptor descarta duplicados.
- **Día operativo fijo**: El Check-In solo puede realizarse cuando `fechaActual == startDate` (hora Colombia, UTC-5, 00:00–23:59).
- **Stay previo para la misma habitación**: Si ya existe un `Stay` para el par (`reservationRef`, `roomId`), el sistema rechaza el Check-In como operación ya realizada.
- **Titular con `fullName` en lugar de `firstName`/`lastName`**: [NEEDS_CONFIRMATION_MODULO_2] Mostrar `fullName`; si es extranjero, pedir nombres y apellidos separados (editables) en el formulario.
- **`updatedAt` ausente en mensajes de Módulo 2**: [NEEDS_CONFIRMATION_MODULO_2] Si falta, ordenar por `sequenceNumber`.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado "Recepcionista" iniciar y formalizar el registro de Check-In en Módulo 1.
- **FR-002**: El sistema DEBE estructurar el flujo de Check-In a través de 4 etapas visuales y operativas:
  1. *Validar reserva*
  2. *Datos de huéspedes*
  3. *Confirmación*
  4. *Check-In*
- **FR-003**: La búsqueda y selección de la reserva y habitación se realizan **previamente** en el **Panel de Recepción** (`spec-consultar-panel-recepcion.md`). Al pulsar el botón "Check-in" en el panel, el flujo llega al paso 1 con la referencia de reserva y habitación ya cargadas. El paso 1 DEBE validar **localmente** contra la copia local (`daily_reservation`, `daily_reservation_room`): reserva existente, habitación en `Reserved`, ausencia de `Stay` previo para el par (`reservationRef`, `roomId`). El paso 1 NO realiza consultas REST a Módulo 2.
- **FR-004**: El sistema DEBE validar que la habitación asignada esté en estado `Reserved`. Si está en cualquier otro estado, DEBE rechazar la admisión con mensaje de error controlado.
- **FR-005**: El sistema DEBE validar que la reserva figure en la copia local del día (`startDate = fechaActual`). Si no figura, DEBE rechazar el Check-In.
- **FR-006**: En el paso 2, el sistema DEBE capturar en formulario los campos con nombres y apellidos separados para cada huésped: `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`. Para ocupantes con `nationality ≠ Colombia`, DEBE abrir el panel de datos migratorios capturando `birthDate` (fecha pasada, `AAAA-MM-DD`), `originPlace` y `destinationPlace` (texto libre, asistencia de catálogo, formato "Ciudad, País"). El sistema asigna automáticamente `movementType = ENTRY` y `movementDate = checkInDate` sin pedirlos al Recepcionista.
- **FR-007**: El sistema DEBE precargar los datos del titular desde la copia local (`daily_reservation`), presentándolos en solo lectura (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`), con selector de nacionalidad deshabilitado y sin opción de eliminar, marcándolo con `isReservationGuest = true`. [NEEDS_CONFIRMATION_MODULO_2] Si el titular llega con `fullName` (sin `firstName`/`lastName` separados), mostrar `fullName`; si es extranjero, pedir nombres y apellidos separados en el formulario (editables).
- **FR-008**: El sistema DEBE validar que la cantidad total de personas coincida exactamente con `guestCount` de la habitación (respaldo: `maxCapacity` si Módulo 2 no envía `guestCount` por habitación [NEEDS_CONFIRMATION_MODULO_2]) y no supere `maxCapacity`. Si difiere, DEBE bloquear el avance al paso 3.
- **FR-009**: Panel de datos migratorios para extranjeros: Cuando `nationality ≠ Colombia`, el sistema DEBE abrir el panel solicitando exclusivamente `birthDate`, `originPlace` y `destinationPlace`. Si la nacionalidad se corrige a Colombia, el panel se cierra y se vacía. Los tres campos son obligatorios para avanzar al paso 3. `birthDate` debe ser una fecha pasada (no hoy ni futura).
- **FR-010**: En el paso 3, el sistema DEBE presentar el resumen con cantidad de huéspedes, habitación (número y tipo), estadía (noches y fechas) y cuadro informativo indicando que la habitación pasará a "Occupied" y la reserva pasará a "En curso". No se muestran indicadores técnicos de colas, módulos ni conteos de extranjeros al Recepcionista.
- **FR-011**: Al confirmar en el paso 3, el sistema DEBE ejecutar de forma síncrona y atómica:
  1. Creación de `Stay` con `source`, `checkInDate`, `expectedCheckinTime`, `expectedCheckoutTime`, `titularFirstName`, `titularLastName`, `titularDocumentNumber`.
  2. Creación de los registros `RoomGuest` inmutables con `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`; para extranjeros: también `birthDate`, `originPlace`, `destinationPlace`; y `isReservationGuest`.
  3. Transición `Reserved → Occupied` en el inventario.
  4. Registro del recepcionista responsable (`receptionistIdCheckIn`).
- **FR-012**: Al confirmar el Check-In, el sistema DEBE insertar en el outbox un mensaje con formato plano hacia `m2.habitacion.checkin.queue`:
  ```json
  {
    "messageId": "UUIDv4",
    "sequenceNumber": <entero>,
    "reservationRef": "RES-000123",
    "roomId": "uuid",
    "movementType": "ENTRY",
    "movementDate": "YYYY-MM-DD",
    "guests": [
      {
        "firstName": "string", "lastName": "string",
        "documentType": "string", "documentNumber": "string",
        "nationality": "string",
        "birthDate": "YYYY-MM-DD",
        "originPlace": "Ciudad, País",
        "destinationPlace": "Ciudad, País"
      }
    ]
  }
  ```
  Los campos `birthDate`, `originPlace` y `destinationPlace` **solo** se incluyen para huéspedes con `nationality ≠ Colombia`. El array `guests[]` contiene **todos** los huéspedes. No existe cola separada de extranjeros.

  Validaciones aplicadas antes de publicar:
  - Todos los campos de `guests[]` presentes.
  - Para extranjeros: `birthDate` en el pasado; `birthDate`, `originPlace` y `destinationPlace` obligatorios.
  - `movementDate` nunca futura.
  - `movementType` coherente con el evento (siempre `ENTRY` en Check-In).

- **FR-013**: En el paso 4, el sistema DEBE mostrar la pantalla de confirmación exitosa con las etiquetas "Habitación: Ocupada" y "Estado de la reserva: En curso" (sin mencionar módulos ni sistemas externos), sin mostrar horas y con el botón único "Volver al inicio".
- **FR-014**: El Check-In NO DEBE bloquearse ante lentitud o caída de la cola o de Módulo 2. La Estancia se consolida localmente y la notificación permanece en el outbox para reintento en segundo plano.
- **FR-015**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, la referencia de la reserva, el ID de la Estancia creada, el recepcionista responsable y la marca de tiempo de confirmación.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional. Atributos clave: `roomId` (UUID), número, piso, tipo, `maxCapacity`, tarifa base, `status` (8 estados), `reservedByReservationRef` (solo en `Reserved`).
- **Stay**: Estancia física. Atributos: `id`, `reservationRef`, `roomId`, `source` (`DIRECTA` o nombre de la OTA), `checkInDate`, `checkOutDate`, `expectedCheckinTime`, `expectedCheckoutTime`, `titularFirstName`, `titularLastName`, `titularDocumentNumber`, `receptionistIdCheckIn`, `receptionistIdCheckOut`.
- **RoomGuest**: Ocupante inmutable vinculado a la Estancia. Atributos: `id`, `stayId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate` (solo extranjeros), `originPlace` (solo extranjeros), `destinationPlace` (solo extranjeros), `isReservationGuest`.
- **DailyReservation / DailyReservationRoom**: Copia local de la reserva. Fuente de verdad en el paso 1 y para el precargado del titular. De 1 a 10 habitaciones por reserva. No persiste `nights`, `status`, `guestRef`, `externalConfirmationCode`.
- **Receptionist**: Actor que ejecuta el flujo de admisión.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista completa el flujo de 4 pasos en menos de 2 minutos para nacionales y menos de 3 minutos para extranjeros.
- **SC-002**: El 100% de los Check-Ins confirmados transicionan la habitación a `Occupied` y persisten `source` en `Stay`.
- **SC-003**: El 100% de los Check-Ins rechaza la cantidad de ocupantes que no coincide con `guestCount` de la habitación.
- **SC-004**: El 100% de los Check-Ins rechaza habitaciones en estado distinto de `Reserved`.
- **SC-005**: El 100% de admisiones con extranjeros capturan `birthDate`, `originPlace` y `destinationPlace` válidos; el mensaje en `m2.habitacion.checkin.queue` incluye a todos los huéspedes en `guests[]`.
- **SC-006**: El 100% de las caídas externas de Módulo 2 consolidan la ocupación local y conservan la notificación en el outbox para reintento.
