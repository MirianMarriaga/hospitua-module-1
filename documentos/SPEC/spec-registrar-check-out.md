# Especificación de Funcionalidad: Registrar Check-Out

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-19

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Formalización de Check-Out y liberación de habitación (Prioridad: P1)

Como Recepcionista, quiero formalizar la salida física del huésped recorriendo el flujo de 5 pasos de la interfaz (Consultar reserva → Liquidación → Pago → Confirmación → Liberar habitación), localizando la estancia a partir de los datos locales de Módulo 1 (`Stay` + `Room` + `RoomGuest`), consultando la liquidación informativa provista por Módulo 3 y confirmando la operación, para que la habitación transicione inmediatamente al estado "PendingCleaning" pasando a la bandeja del Personal de limpieza, se notifique mediante la cola `m2.habitacion.checkout.queue` a Módulo 2 con `reservationRef`, `roomId` y `foreignGuestCount`, y se despache por `m2.huespedes.extranjeros.queue` un mensaje por cada huésped extranjero con movimiento `DEPARTURE`.

**Por qué esta prioridad**: Es la operación misional de cierre de la estancia física en el hotel. Al alimentarse de los datos locales persistidos en la estancia (`Stay`, incluyendo la fuente `source` y los ocupantes `RoomGuest`), Módulo 1 opera de forma 100% autónoma en el mostrador sin realizar peticiones previas a Módulo 2, liberando de forma síncrona la habitación en el inventario hacia el personal de aseo y presentando al huésped la liquidación de Módulo 3 sin recaudar pagos locales. Conforme a los acuerdos actualizados, la notificación de check-out emitida hacia Módulo 2 viaja por `m2.habitacion.checkout.queue` con `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y `foreignGuestCount`. Si existen extranjeros alojados, sus diez campos migratorios con `movementType = DEPARTURE` y `movementDate = checkOutDate` (ambos asignados por Módulo 1) viajan por la cola independiente `m2.huespedes.extranjeros.queue`, reutilizando la procedencia y destino capturados en el Check-In. De esta forma, el Check-Out nunca espera por datos migratorios y si un mensaje falla la cola reintenta solo ese huésped. Módulo 1 no altera el estado de la reserva a nivel global; la transición de la reserva a `IN_PROGRESS` o `COMPLETED` es responsabilidad exclusiva del Módulo 2, quien la deriva cuando todas las habitaciones de la reserva completan su salida.

**Prueba Independiente**: Se prueba de forma aislada iniciando desde el Panel de Recepción con una estancia ya seleccionada y recorriendo el flujo de 5 pasos: (1) presentación de los datos de la estancia activa en estado "Occupied" recuperados localmente de `Stay`, `Room` y `RoomGuest` (titular con `isReservationGuest = true`) sin invocar a Módulo 2 ni mostrar "Estado en Módulo 2"; (2) consulta reactiva REST GET a Módulo 3 ("Consultar liquidación") enviando `source` desde `Stay` y visualización del paso Liquidación mostrando la factura definitiva asociada arriba y la información de la reserva (fuente `source` obtenida de `Stay`, comisión OTA e ingreso neto provistos por Módulo 3 sin que este devuelva `source`); (3) visualización del paso Pago con la factura definitiva asociada arriba, el resumen para el huésped (hospedaje, IVA, total a pagar) y los datos reales de la estadía (noches, fecha de entrada `checkInDate`, fecha de salida `checkOutDate`), sin procesar transacciones monetarias; (4) confirmación explícita de salida; y (5) pantalla de éxito certificando la transición síncrona a "PendingCleaning", su envío a la bandeja del personal de limpieza y el despacho de notificaciones por colas independientes a Módulo 2 (sin mostrar horas y con botón único "Volver al inicio").

**Escenarios de Aceptación**:

1. **Escenario**: Flujo completo de Check-Out en fecha pactada (Happy Path)
   - **Dado** una habitación en estado operativo "Occupied" vinculada a una estancia activa local (`Stay`) con fuente `source` persistida (`DIRECTA` o nombre de la OTA) y titular identificado en `RoomGuest` (`firstName`, `lastName`), seleccionada previamente desde el Panel de Recepción
   - **Cuando** el Recepcionista revisa la información de la estancia en el paso 1, avanza al paso 2 ("Liquidación") consultando síncronamente a Módulo 3 mediante REST GET para obtener la factura definitiva asociada, porcentaje y valor de comisión OTA e ingreso neto (mostrando la fuente desde `Stay.source`), continúa al paso 3 ("Pago") revisando el resumen para el huésped (hospedaje, IVA, total a pagar) y los datos de estadía (noches, fecha de entrada y salida reales), y en el paso 4 marca la casilla de confirmación y confirma la salida
   - **Entonces** el sistema transiciona la habitación a "PendingCleaning" de forma síncrona mediante la invocación interna a "Marcar pendiente a limpieza", emite proactivamente mediante la cola asíncrona `m2.habitacion.checkout.queue` la notificación de salida con `foreignGuestCount`, emite por `m2.huespedes.extranjeros.queue` un mensaje por extranjero con `movementType = DEPARTURE` (reutilizando procedencia y destino del Check-In), y despliega el paso 5 ("Liberar habitación") confirmando que la habitación está en estado "Pendiente de limpieza", la notificación sincronizada, sin mostrar horas y ofreciendo el botón único "Volver al inicio". Módulo 2 actualiza el estado de la reserva a "COMPLETED" cuando todas sus habitaciones han registrado salida.

2. **Escenario**: Check-Out con salida anticipada (Early Check-Out)
   - **Dado** una estancia física activa cuya fecha pactada de finalización (`expectedCheckoutTime`) es posterior a la fecha actual
   - **Cuando** el Recepcionista consulta la estancia e inicia el Check-Out en la fecha de hoy
   - **Entonces** Módulo 3 entrega en la liquidación el valor consolidado de hospedaje correspondiente a las noches reales (con las políticas y penalizaciones resueltas internamente por Módulo 3), el IVA, el total a pagar, comisión OTA, ingreso neto y la factura definitiva asociada (presentando la fuente desde `Stay.source`)
   - **Y cuando** el Recepcionista verifica la información en los pasos de Liquidación y Pago, confirma la casilla en el paso 4 y confirma la salida
   - **Entonces** el sistema libera la habitación a "PendingCleaning" de inmediato y despacha proactivamente mediante las colas asíncronas las notificaciones hacia Módulo 2.

3. **Escenario**: Bloqueo de confirmación por falta de validación explícita
   - **Dado** que se ha consultado la estancia y revisado los pasos de liquidación y pago
   - **Cuando** en el paso 4 el Recepcionista intenta confirmar el Check-Out sin marcar la casilla de confirmación ("Confirmo la salida del huésped y la información mostrada es correcta")
   - **Entonces** el sistema mantiene bloqueada la confirmación, impidiendo la transición física de la habitación y la notificación a Módulo 2 hasta contar con la validación explícita.

4. **Escenario**: Recepción y visualización inmediata en la bandeja de trabajo de limpieza
   - **Dado** que se ha confirmado exitosamente un Check-Out en el paso 4
   - **Cuando** el Personal de limpieza consulta su listado o bandeja de trabajo
   - **Entonces** la habitación figura de inmediato en estado "PendingCleaning", habilitando el inicio del proceso de aseo.

---

### Historia de Usuario 2 - Bloqueo de salidas inconsistentes y resiliencia de integración (Prioridad: P2)

Como Recepcionista, quiero que el sistema rechace intentos de Check-Out sobre habitaciones que no cuenten con una estancia activa ocupada y mantenga la resiliencia operativa ante fallas o indisponibilidad externa, para evitar inconsistencias en el inventario y no perjudicar la salida física del huésped.

**Por qué esta prioridad**: Asegura la consistencia lógica de la máquina de estados e impide que caídas de servicios externos (Módulo 2 o Módulo 3) bloqueen la operación física del hotel o dejen habitaciones en estados huérfanos.

**Prueba Independiente**: Se prueba intentando registrar el Check-Out sobre habitaciones en estados diferentes a "Occupied" o sin estancia física activa (`Stay`), o simulando indisponibilidad en los servicios externos de Módulo 2 o Módulo 3, verificando los bloqueos controlados y los mecanismos de contingencia.

**Escenarios de Aceptación**:

1. **Escenario**: Rechazo de Check-Out por habitación no ocupada
   - **Dado** una habitación cuyo estado físico en Módulo 1 es "Available", "Reserved", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"
   - **Cuando** el Recepcionista intenta consultar o ejecutar el Check-Out
   - **Entonces** el sistema rechaza la operación informando que la habitación no se encuentra en estado "Occupied" y no tiene una estancia física activa para finalizar.

2. **Escenario**: Resiliencia ante caída o indisponibilidad de Módulo 3 (Facturación)
   - **Dado** una habitación en estado "Occupied" con estancia física activa al momento en que el servicio de Módulo 3 no responde o se demora al solicitar la liquidación vía REST GET
   - **Cuando** el Recepcionista gestiona la salida física del huésped
   - **Entonces** Módulo 1 presenta un informe controlado de la indisponibilidad temporal del cálculo financiero, ofreciendo al Recepcionista la opción de autorizar la liberación física de la habitación a "PendingCleaning" para no retrasar el aseo ni retener al huésped, registrando la transacción como pendiente de regularización financiera sin provocar fallos técnicos no controlados.

3. **Escenario**: Resiliencia ante falla de notificación por cola a Módulo 2
   - **Dado** que el Check-Out físico fue confirmado y la habitación transicionó a "PendingCleaning", pero la comunicación con Módulo 2 experimenta una interrupción
   - **Cuando** el sistema emite la notificación a las colas de Módulo 2
   - **Entonces** el cambio de estado físico de la habitación se mantiene firme en "PendingCleaning", las notificaciones permanecen en cola pendientes de reintento en segundo plano y se muestra en pantalla la nota informativa correspondiente.

---

### Casos Borde

- **Confirmación explícita requerida para la salida**: El sistema no permite el despacho de la transacción de Check-Out si el Recepcionista no ha marcado la casilla obligatoria de confirmación en el paso 4.
- **Ausencia de gestión de consumos locales y pasarelas de pago**: Este flujo NO incluye campos ni gestión de minibar, lavandería ni consumos de restaurante, ni procesa transacciones financieras en el paso Pago. La liquidación provista por Módulo 3 se limita exclusivamente a hospedaje, comisiones intermediarias, impuestos y la factura definitiva asociada.
- **Indisponibilidad o falta de respuesta de Módulo 3 al consultar liquidación**: Si la consulta a Módulo 3 supera el tiempo de espera o retorna error, el sistema ofrece una vía de contingencia para liberar la habitación física a "PendingCleaning" y registra la transacción para regularización posterior sin interrumpir la atención.
- **Idempotencia en la mensajería hacia Módulo 2**: Todo mensaje de salida lleva `messageId` único y `sequenceNumber` creciente. Si ocurre un reenvío por falla de red, el receptor descarta duplicados garantizando que no se dupliquen registros ni se alteren estados de forma inconsistente.
- **Integridad de datos migratorios de salida**: Los datos migratorios de salida se generan con los diez campos completos a partir de la información registrada en el Check-In, despachándose de forma íntegra y definitiva hacia Módulo 2.
- **Peticiones simultáneas de Check-Out sobre la misma habitación**: La primera petición transiciona la habitación a "PendingCleaning"; cualquier intento concurrente posterior es bloqueado de inmediato informando colisión y notificando que la habitación ya no figura como "Occupied".
- **Día operativo fijo** (acuerdo B11): El día operativo corresponde al día calendario de Colombia (00:00 a 23:59, UTC-5). El `checkOutDate` se genera con la fecha del sistema en ese instante (solo fecha, sin hora).
- **Salidas prematuras y salidas tardías/vencidas**: Se permiten tanto salidas antes de la fecha final esperada (Early Check-Out) como salidas en estancias cuya fecha de salida esté vencida; en ambos casos la liquidación se efectúa con base en las fechas reales de la estadía (`checkInDate` y `checkOutDate`).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado "Recepcionista" registrar la salida física y formalizar el Check-Out en Módulo 1.
- **FR-002**: El sistema DEBE estructurar el proceso de Check-Out a través de 5 etapas funcionales y visuales:
  1. *Consultar reserva*
  2. *Liquidación*
  3. *Pago*
  4. *Confirmación*
  5. *Liberar habitación*
- **FR-003**: La búsqueda y selección de la estancia para Check-Out se realizan **previamente** en el **Panel de Recepción** (`spec-consultar-panel-recepcion.md`) a través del buscador de Salidas, el cual opera exclusivamente sobre datos locales de Módulo 1 (`Stay` + `Room` + `RoomGuest`) sin invocar a Módulo 2. Al pulsar el botón "Check-out" en el panel, el flujo llega al paso 1 ("Consultar reserva") con la estancia ya identificada. En el paso 1, el sistema DEBE obtener y presentar la información completa de la estadía desde las entidades locales `Stay` + `Room` + `RoomGuest` (identificando al titular mediante `isReservationGuest = true`), sin realizar peticiones externas a Módulo 2 ni desplegar el campo "Estado en Módulo 2". La interfaz debe mostrar: código de reserva, huésped titular (`firstName`, `lastName`), rango de fechas de la estancia, fuente (`source`: `DIRECTA` o nombre de la OTA) y habitación asignada en estado "Ocupada" (`Occupied`). El paso 1 NO dispone de una barra de búsqueda propia.
- **FR-004**: El sistema DEBE validar como precondición física obligatoria que la habitación asignada se encuentre estrictamente en estado "Occupied" según documentos/SPEC/referencias/maquina-estados-habitacion.md. Si la habitación está en cualquier otro estado, DEBE rechazar el inicio del Check-Out con un mensaje de error controlado, detallando el estado físico real.
- **FR-005**: Conforme al contrato de interfaces con Módulo 3 (`mod-1-2-3.drawio`: "Consultar liquidación: REST · GET · Reactivo"), en el paso 2 el sistema DEBE consultar de forma reactiva mediante una petición sincrónica REST GET a Módulo 3 la liquidación de la estadía (`SettlementRequest`), enviando: `reservationRef`, `eventType` (`CHECK_OUT`), fechas esperadas (`startDate`, `endDate`), fechas reales de la estancia (`checkInDate` y `checkOutDate` como fechas sin hora), fuente (`source` obtenido localmente de `Stay`, tal cual `DIRECTA` o nombre de la OTA) y el identificador de la habitación (`roomId`).
- **FR-006**: El sistema DEBE presentar la información de liquidación provista por Módulo 3 y por la entidad local `Stay` distribuida en las dos etapas correspondientes:
  1. *En el paso 2 (Liquidación)*:
     - Factura definitiva asociada (número consecutivo oficial emitido por Módulo 3, ej. FAC-40001, ubicada en la parte superior).
     - **Información de la reserva** (para uso de recepción): fuente (mostrada a partir de `Stay.source`: `DIRECTA` o nombre de la OTA; este campo NO es devuelto por Módulo 3), porcentaje de comisión OTA (si aplica), valor monetario de la comisión OTA (si aplica) e ingreso neto (`netIncomeAmount`).
  2. *En el paso 3 (Pago)*:
     - Factura definitiva asociada (ubicada en la parte superior).
     - **Resumen para el huésped**: valor del hospedaje (total consolidado ya calculado `accommodationTotalAmount`), Impuesto al Valor Agregado (IVA, `taxAmount`) y total a pagar.
     - **Datos de la estadía**: número de noches, fecha de entrada real (`checkInDate`) y fecha de salida real (`checkOutDate`).
- **FR-007**: El sistema NO DEBE solicitar, registrar ni tramitar cobros de consumos locales (minibar, lavandería o restaurante) ni procesar pagos con pasarelas dentro de Módulo 1. En el paso 3 ("Pago"), no se registra ni recibe dinero; su propósito es exclusivamente la visualización del resumen de cobro para revisión con el huésped.
- **FR-008**: En el paso 4 ("Confirmación"), el sistema DEBE exigir que el Recepcionista marque obligatoriamente la casilla de confirmación ("Confirmo la salida del huésped y la información mostrada es correcta") antes de habilitar el botón de confirmación de Check-Out.
- **FR-009**: En el paso 4 ("Confirmación"), el sistema DEBE presentar la siguiente nota informativa de contingencia: "Si falla la actualización, la notificación queda en cola para reintento automático." (indicando que la notificación a Módulo 2 queda pendiente de reintento en la cola; el estado de la reserva es gestionado por Módulo 2 y pasa a `COMPLETED` cuando todas las habitaciones de la reserva hayan completado su salida). No se deben mostrar avisos en pantalla sobre penalizaciones ni aclaraciones de consumos locales.
- **FR-010**: Al confirmar la salida en el paso 4, el sistema DEBE invocar de forma síncrona y atómica el caso de uso interno "Marcar pendiente a limpieza" (`<<includes>>`), transicionando el estado de la entidad Room de "Occupied" a "PendingCleaning", y cerrando la entidad `Estancia` registrando `checkOutDate` (solo fecha, sin hora, en hora Colombia UTC-5, acuerdo B11) y el recepcionista responsable (`receptionistIdCheckOut`).
- **FR-011**: La habitación en estado "PendingCleaning" DEBE figurar de inmediato en la bandeja de trabajo del Personal de limpieza.
- **FR-012**: Conforme al contrato de colas con Módulo 2, al confirmar la salida el sistema DEBE notificar proactivamente mediante colas asíncronas independientes:
  1. Hacia `m2.habitacion.checkout.queue`: un mensaje con `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y `foreignGuestCount` (conteo obligatorio de extranjeros alojados en la habitación, 0 si no hay extranjeros).
  2. Hacia `m2.huespedes.extranjeros.queue`: si existen huéspedes extranjeros alojados en la estancia (`RoomGuest` con nacionalidad distinta de Colombia), un mensaje independiente por cada huésped extranjero con `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y los diez campos migratorios exigidos por el SIRE: `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`DEPARTURE`), `movementDate` (`checkOutDate`, solo fecha), y reutilizando la procedencia (`originPlace`) y destino (`destinationPlace`) capturados en el Check-In.
  El Check-Out físico nunca espera por datos migratorios ni por la respuesta de Módulo 2. Si un mensaje falla en la cola, se reintenta únicamente ese mensaje o huésped fallido en segundo plano. Los diez campos son generados a partir de los registros de entrada y el despacho es definitivo.
- **FR-013**: En el paso 5 ("Liberar habitación"), el sistema DEBE certificar la finalización exitosa del flujo confirmando que la habitación ha pasado a "PendingCleaning" (Pendiente de limpieza), mostrando las etiquetas de validación ("Habitación: Pendiente de limpieza", "Notificación a Módulo 2: Sincronizada"), sin mostrar horas en pantalla y proveyendo como única acción de salida el botón "Volver al inicio".
- **FR-014**: Si Módulo 2 o Módulo 3 experimentan demoras o fallas de comunicación, el sistema NO DEBE bloquear la liberación física de la habitación a "PendingCleaning" ni impedir la salida del huésped; las notificaciones y conciliaciones se programan para resolución en segundo plano mediante la cola asíncrona.
- **FR-015**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, la referencia de la reserva, el identificador de la Estancia, el recepcionista responsable del check-out (`receptionistIdCheckOut`), la fecha de salida (`checkOutDate`) y la referencia de liquidación.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), fuente (`source`: `DIRECTA` o nombre de la OTA tal como llega de Módulo 2), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`), fechas esperadas de reserva (`expectedCheckinTime`, `expectedCheckoutTime` — fechas sin hora), datos copiados del titular (`titularFirstName`, `titularLastName`, `titularDocumentNumber`), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **RoomGuest**: Entidad conceptual que representa a cada individuo físicamente alojado. Registro inmutable vinculado a la Estancia. Atributos clave: `id`, `stayId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate`, `originPlace`, `destinationPlace` e `isReservationGuest` (flag booleano que identifica al titular de la reserva).
- **ForeignGuestData**: Estructura enviada por `m2.huespedes.extranjeros.queue` (un mensaje por huésped extranjero) con los diez campos migratorios exigidos por el SIRE: `messageId`, `sequenceNumber`, `reservationRef`, `roomId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`DEPARTURE`, asignado por Módulo 1), `movementDate` (`checkOutDate`, solo fecha, asignado por Módulo 1), `originPlace` y `destinationPlace` (reutilizados del Check-In).
- **SettlementSummary**: Estructura conceptual informativa devuelta por Módulo 3 y consumida vía "Consultar liquidación" conteniendo: factura definitiva asociada (`invoiceNumber`), valor de hospedaje consolidado (`accommodationTotalAmount`), porcentaje de comisión OTA (`otaCommissionPercentage`), valor de comisión OTA (`otaCommissionAmount`), IVA (`taxAmount`) e ingreso neto (`netIncomeAmount`). **No incluye `source`** (la fuente se obtiene localmente de `Stay.source`).
- **Receptionist**: Actor de recepcionista que opera el flujo de recepción, consultas, registro de check-in y registro de check-out en el hotel.
- **CleaningStaff**: Personal operativo que recibe de forma inmediata la habitación en estado "PendingCleaning" en su bandeja de trabajo al registrarse el check-out.
- **Module2 (Operación de Reservas)**: Sistema externo responsable del ciclo de vida contractual de las reservas y de la derivación del estado de la reserva (`IN_PROGRESS` / `COMPLETED`) por habitación.
- **Module3 (Facturación y Liquidación)**: Sistema externo responsable exclusivo de calcular la liquidación, aplicar comisiones e IVA, y generar la factura definitiva oficial.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede completar el proceso de Check-Out en menos de 1 minuto a partir de que el sistema recibe la respuesta de liquidación de Módulo 3.
- **SC-002**: El 100% de los Check-Outs confirmados transicionan de forma atómica y síncrona la habitación a "PendingCleaning", quedando visible de inmediato en la bandeja del Personal de limpieza.
- **SC-003**: El sistema impide el 100% de los intentos de salida sin la confirmación explícita del Recepcionista mediante la casilla obligatoria en el paso 4.
- **SC-004**: El sistema rechaza el 100% de los intentos de Check-Out sobre habitaciones cuyo estado físico sea diferente de "Occupied".
- **SC-005**: Cero cálculos manuales de tarifas, cero cargos por minibar/consumos locales y cero operaciones de cobro pasarela procesadas en Módulo 1 durante este flujo.
- **SC-006**: Ante indisponibilidad de Módulo 2 o Módulo 3, el 100% de los casos permiten completar la liberación física de la habitación a "PendingCleaning", emitiendo las notificaciones a las colas correspondientes para resolución en segundo plano sin retener al huésped en recepción.


