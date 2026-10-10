# Especificación de Funcionalidad: Registrar Check-Out

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-19
**Actualizado**: 2026-10-09

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Formalización de Check-Out y liberación de habitación (Prioridad: P1)

Como Recepcionista, quiero formalizar la salida física del huésped recorriendo el flujo de 5 pasos de la interfaz (Consultar reserva → Liquidación → Pago → Confirmación → Liberar habitación), localizando la estancia desde los datos locales de Módulo 1 (`Stay` + `Room` + `RoomGuest`), consultando la liquidación a Módulo 3 y confirmando la operación, para que la habitación transicione inmediatamente a "PendingCleaning" y se notifique de forma asíncrona a Módulo 2 la salida física de todos los huéspedes.

**Por qué esta prioridad**: Es la operación misional de cierre de la estancia. Módulo 1 opera de forma 100% autónoma en recepción (datos de la estancia desde `Stay`, titular desde `Stay.titularFirstName/LastName`). La notificación asíncrona hacia Módulo 2 consolida a la totalidad de los huéspedes (nacionales y extranjeros) en un único aviso con el movimiento de salida y la fecha de egreso (reutilizando los lugares de procedencia y destino registrados en el Check-In [NEEDS_CONFIRMATION_MODULO_2]). No existe notificación separada para extranjeros.

**Prueba Independiente**: Iniciar desde el Panel de Recepción con una estancia seleccionada. Recorrer los 5 pasos: (1) datos de la estancia desde `Stay`/`Room`/`RoomGuest` — titular desde `Stay.titularFirstName/LastName`, fuente desde `Stay.source`; (2) liquidación de Módulo 3 mostrando `settlementType`, `invoiceNumber` (o "Pendiente de facturación" si null), fuente desde `Stay.source`, comisión OTA, ingreso neto; (3) pago con `accommodationTotalAmount`, `taxAmount`, `totalAmount` de M3 (o "Pendiente de facturación" si null), noches, `checkInDate`, `checkOutDate`; (4) confirmación; (5) pantalla de éxito con habitación en PendingCleaning. Verificar el registro de la notificación saliente para Módulo 2 con el movimiento de salida, fecha de egreso y la nómina completa de todos los huéspedes.

**Escenarios de Aceptación**:

1. **Escenario**: Flujo completo de Check-Out en fecha pactada (Happy Path)
   - **Dado** una habitación en `Occupied` con `Stay` activo, `source` en `Stay`, titular identificado en `Stay.titularFirstName`/`Stay.titularLastName`
   - **Cuando** el Recepcionista recorre los 5 pasos, confirma en el paso 4 la casilla obligatoria
   - **Entonces** el sistema transiciona la habitación a `PendingCleaning` de forma síncrona, cierra la `Estancia` registrando `checkOutDate` y `receptionistIdCheckOut`, y emite la notificación asíncrona hacia Módulo 2 con el movimiento de salida, fecha de egreso y la nómina de todos los huéspedes. En el paso 2 se muestra el `settlementType` ("Liquidación informativa" o "Liquidación final") y el `invoiceNumber` (entero consecutivo o no mostrado si informativa). En el paso 3 el `totalAmount` viene de M3; en liquidación informativa se muestra solo hospedaje, comisión e ingreso neto, sin IVA, sin total y sin factura.

2. **Escenario**: Check-Out con salida anticipada (Early Check-Out)
   - **Dado** una estancia cuya `expectedCheckoutTime` es posterior a hoy
   - **Cuando** el Recepcionista inicia el Check-Out
   - **Entonces** Módulo 3 entrega el valor consolidado de hospedaje (penalizaciones resueltas internamente por M3). El flujo continúa normalmente

3. **Escenario**: Bloqueo por falta de validación explícita
   - **Dado** que se han revisado los pasos de Liquidación y Pago
   - **Cuando** el Recepcionista intenta confirmar sin marcar la casilla obligatoria
   - **Entonces** el sistema mantiene bloqueada la confirmación

4. **Escenario**: Habitación en PendingCleaning aparece en la bandeja de limpieza
   - **Dado** Check-Out confirmado
   - **Cuando** el Personal de limpieza consulta su panel
   - **Entonces** la habitación figura de inmediato en `PendingCleaning`

---

### Historia de Usuario 2 - Bloqueo de salidas inconsistentes y resiliencia (Prioridad: P2)

**Escenarios de Aceptación**:

1. **Escenario**: Rechazo por habitación no ocupada
   - **Dado** una habitación en estado distinto a `Occupied`
   - **Cuando** se intenta el Check-Out
   - **Entonces** el sistema rechaza informando el estado real

2. **Escenario**: Resiliencia ante caída de Módulo 3
   - **Dado** que Módulo 3 no responde o devuelve error (timeout > 3 s)
   - **Cuando** el Recepcionista gestiona la salida
   - **Entonces** Módulo 1 presenta el informe de indisponibilidad temporal y ofrece al Recepcionista la opción de liberar la habitación a `PendingCleaning` sin bloquear al huésped

3. **Escenario**: Resiliencia ante falla de comunicación con Módulo 2
   - **Dado** que el Check-Out fue confirmado y la habitación pasó a `PendingCleaning`
   - **Cuando** la comunicación con Módulo 2 experimenta interrupción temporal
   - **Entonces** la liberación física se mantiene firme y la notificación queda registrada para entrega garantizada en segundo plano

---

### Casos Borde

- **Confirmación explícita requerida**: El sistema no despacha la transacción sin la casilla obligatoria del paso 4.
- **Sin gestión de consumos locales**: Este flujo no incluye minibar, lavandería ni pasarelas de pago.
- **`totalAmount` de M3**: Si Módulo 3 devuelve `totalAmount = null` (liquidación informativa), Módulo 1 muestra "Pendiente de facturación" en el paso 3. No recalcula localmente.
- **`invoiceNumber` como entero**: El número de factura es un entero consecutivo (no lleva prefijo como "F-2026-").
- **Idempotencia en notificaciones**: Toda notificación saliente cuenta con un identificador único que permite reconocer reintentos y descartar duplicados.
- **Reutilización de procedencia y destino migratorios**: [NEEDS_CONFIRMATION_MODULO_2] En la notificación de salida, la procedencia y el destino de extranjeros se reutilizan de los registros del Check-In (`RoomGuest`).
- **Día operativo fijo**: `checkOutDate` corresponde a la fecha del sistema en hora Colombia (UTC-5), solo fecha, sin hora.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado "Recepcionista" registrar el Check-Out.
- **FR-002**: El sistema DEBE estructurar el proceso en 5 etapas:
  1. *Consultar reserva*
  2. *Liquidación*
  3. *Pago*
  4. *Confirmación*
  5. *Liberar habitación*
- **FR-003**: La estancia se selecciona previamente en el **Panel de Recepción**. El paso 1 DEBE obtener y mostrar la información desde `Stay` + `Room` + `RoomGuest` sin peticiones externas a Módulo 2. Muestra: código de reserva, titular (`Stay.titularFirstName`, `Stay.titularLastName`), rango de fechas, fuente (`Stay.source`: `DIRECTA` o nombre de la OTA), habitación en `Occupied`. El paso 1 NO dispone de barra de búsqueda propia y NO muestra "Estado de la reserva en Módulo 2".
- **FR-004**: El sistema DEBE validar que la habitación esté en `Occupied`. Cualquier otro estado produce rechazo con mensaje de error controlado.
- **FR-005**: En el paso 2, el sistema DEBE consultar mediante `<<includes>>` a "Consultar liquidación" (`spec-consultar-liquidacion.md`) enviando: `reservationRef`, `checkInDate`, `checkOutDate`, `source` (de `Stay.source`), `roomId`, `categoryRoom`. Los parámetros `eventType`, `startDate` y `endDate` **no se envían**.
- **FR-006**: El sistema DEBE presentar la información de liquidación distribuida en dos etapas:
  1. *Paso 2 (Liquidación)*:
     - `settlementType`: "Liquidación informativa" o "Liquidación final"
     - `invoiceNumber`: entero consecutivo (si es liquidación informativa y null, no se muestra número o se indica como "Pendiente de facturación")
     - Fuente (desde `Stay.source` local — M3 NO la devuelve)
     - `otaCommissionPercentage`, `otaCommissionAmount`, `netIncomeAmount`
  2. *Paso 3 (Pago)*:
     - En liquidación FINAL: `invoiceNumber`, `accommodationTotalAmount`, `taxAmount`, `totalAmount` de M3 (Módulo 1 **no lo recalcula**), noches, `checkInDate`, `checkOutDate`.
     - En liquidación INFORMATIVA: Muestra exclusivamente el desglose informativo con `accommodationTotalAmount`, comisión OTA e `netIncomeAmount`. No incluye IVA, total a pagar ni número de factura.
- **FR-007**: El sistema NO DEBE solicitar ni registrar consumos locales ni procesar pagos. El paso 3 ("Pago") es exclusivamente de visualización.
- **FR-008**: En el paso 4, el sistema DEBE exigir la casilla obligatoria de confirmación antes de habilitar el botón de Check-Out.
- **FR-009**: El texto informativo del paso 4 DEBE indicar que si falla la actualización, la notificación queda en cola para reintento. No se deben mencionar módulos, colas ni sistemas externos en la interfaz. El estado de la reserva es gestionado por el sistema de reservas y pasa a "Completada" cuando todas las habitaciones de la reserva hayan registrado salida.
- **FR-010**: Al confirmar en el paso 4, el sistema DEBE ejecutar de forma síncrona y atómica:
  1. Transición `Occupied → PendingCleaning` (vía `<<includes>>` a "Marcar pendiente a limpieza").
  2. Cierre de `Stay` registrando `checkOutDate` (solo fecha, UTC-5) y `receptionistIdCheckOut`.
- **FR-011**: La habitación en `PendingCleaning` DEBE aparecer de inmediato en la bandeja del Personal de limpieza.
- **FR-012**: Al confirmar la salida, el sistema DEBE generar una notificación asíncrona de movimiento de salida dirigida a Módulo 2 que consolide a la totalidad de los huéspedes alojados (nacionales y extranjeros) en un único aviso, sin canales separados para extranjeros. La notificación DEBE incluir:
  - Identificador único para control de duplicados e idempotencia.
  - Referencia de reserva e identificador de habitación.
  - Tipo de movimiento (salida) y fecha de egreso (`checkOutDate`).
  - Lista completa de todos los huéspedes alojados con sus nombres, apellidos, tipo y número de documento y nacionalidad.
  - Para los huéspedes de nacionalidad extranjera, los datos migratorios complementarios: fecha de nacimiento, lugar de procedencia y lugar de destino (reutilizados de los registros de Check-In [NEEDS_CONFIRMATION_MODULO_2]).
  - Validaciones previas a la emisión: presencia de los datos de todos los huéspedes y coherencia cronológica (la fecha de salida no puede ser anterior a la fecha de entrada del mismo huésped).

- **FR-013**: En el paso 5, el sistema DEBE mostrar: "Habitación: Pendiente de limpieza", etiqueta de notificación enviada (sin mencionar módulos), sin horas, botón único "Volver al inicio". Las etiquetas de estado se muestran en español.
- **FR-014**: Si Módulo 2 o Módulo 3 experimentan fallas, el sistema NO DEBE bloquear la liberación física a `PendingCleaning`. Las notificaciones se programan para reintento en segundo plano.
- **FR-015**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, la referencia de la reserva, el ID de la Estancia, el recepcionista responsable, `checkOutDate` y la referencia de liquidación.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: 8 estados canónicos. Transiciona de `Occupied` a `PendingCleaning` en este flujo.
- **Stay**: Estancia física. Atributos: `id`, `reservationRef`, `roomId`, `source`, `checkInDate`, `checkOutDate`, `expectedCheckinTime`, `expectedCheckoutTime`, `titularFirstName`, `titularLastName`, `titularDocumentNumber`, `receptionistIdCheckIn`, `receptionistIdCheckOut`.
- **RoomGuest**: Ocupante inmutable. Atributos: `id`, `stayId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`, `originPlace`, `destinationPlace`, `isReservationGuest`.
- **SettlementSummary**: Estructura devuelta por Módulo 3. Contiene: `invoiceNumber` (entero o null), `accommodationTotalAmount`, `otaCommissionPercentage`, `otaCommissionAmount`, `taxAmount` (null si informativa), `netIncomeAmount`, `totalAmount` (null si informativa), `settlementType`. No incluye `source`.
- **CleaningStaff**: Recibe la habitación en `PendingCleaning` en su bandeja de trabajo.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista completa el Check-Out en menos de 1 minuto desde que recibe la respuesta de Módulo 3.
- **SC-002**: El 100% de los Check-Outs transicionan atómicamente la habitación a `PendingCleaning`.
- **SC-003**: El sistema impide el 100% de las salidas sin la casilla obligatoria del paso 4.
- **SC-004**: El sistema rechaza el 100% de los intentos sobre habitaciones en estado distinto de `Occupied`.
- **SC-005**: Cero cálculos locales de `totalAmount`; el valor siempre viene de M3 o se muestra "Pendiente de facturación".
- **SC-006**: Ante indisponibilidad de M2 o M3, el 100% de los casos permiten liberar la habitación a `PendingCleaning`.
