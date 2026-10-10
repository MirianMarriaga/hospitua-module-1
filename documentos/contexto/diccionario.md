# Diccionario General del Proyecto HOSPITUA

**Creado**: 2026-09-07

Glosario compartido por los tres módulos del proyecto, para que todos usemos los mismos términos con el mismo significado.

## Actores

* **Administrador**: Usuario interno con permisos transversales. En Módulo 1: registrar, editar, dar de baja y reactivar habitaciones del inventario. En Módulo 3: gestionar facturación, revisar/modificar tarifas por temporada, y actualizar el porcentaje de IVA.
* **Personal de limpieza**: Actor de Módulo 1 responsable de ejecutar el aseo de las habitaciones: desde el panel de limpieza marca el inicio de la limpieza y, desde la vista de tarea activa, confirma su finalización (reportando un daño si lo encuentra) o libera la tarea para que otro miembro la retome. También puede reportar daños en habitaciones disponibles.
* **Personal de mantenimiento**: Actor de Módulo 1 que, desde el panel de mantenimiento, reporta daños (inhabilitando habitaciones por reparaciones), programa bloqueos técnicos preventivos e inicia reparaciones; desde la vista de tarea activa confirma cuando la intervención finaliza o libera la tarea para que otro miembro la retome.
* **Gerente**: Actor de Módulo 1 con acceso de solo lectura a reportes de estado y al inventario completo de habitaciones.
* **Recepcionista**: Usuario interno encargado de la operación en el front-desk del hotel. Es actor de **Módulo 1** para admitir huéspedes (Check-In) y formalizar salidas (Check-Out), y actor de **Módulo 2** para gestionar reservas directas y procesar cancelaciones.
* **Migración**: Ente externo gubernamental (Migración Colombia). En el sistema, actúa como actor que ingresa de forma autenticada a una ventana de autogestión o portal exclusivo para filtrar por fechas y descargar de manera directa el archivo estructurado .TXT (SIRE) generado por el Módulo 2. El hotel no realiza envíos automáticos; la descarga asíncrona la realiza este actor.
* **Módulo 1 (Gestión de Habitaciones e Inventario de Aforo, Check-In y Check-Out)**: Digitaliza la infraestructura física del hotel, controla la disponibilidad en tiempo real, y gestiona directamente la admisión y salida física de los huéspedes.
* **Módulo 2 (Operación de Reservas y Cumplimiento Legal)**: Gestiona el ciclo de vida de la reserva, el origen de la reserva y el cumplimiento migratorio (SIRE). Es la fuente de los datos de reserva que consumen Módulo 1 y Módulo 3, y recibe de Módulo 1 las notificaciones de Check-In/Check-Out para actualizar el estado de sus reservas.
* **OTA (Booking, Airbnb, Expedia)**: Intermediario externo que origina reservas con comisión pactada. Aparece como dato del canal de la reserva en casi todos los casos de uso de Módulo 3, y como actor que consulta directamente en "Consultar liquidación".
* **Huésped**: Persona que se aloja en una habitación durante una estancia.
* **Responsable de facturación (rol)**: Forma en que varias historias de Módulo 3 nombran a quien necesita el ingreso neto, la comisión y el IVA correctamente reflejados para conciliar; no es un actor propio del diagrama, sino el Administrador actuando en su función de facturación.

## Módulo 1: Gestión de Habitaciones e Inventario de Aforo, Check-In y Check-Out

* **Habitación**: Unidad de alojamiento con identificación (UUID, número, piso), categorización (tipo, capacidad máxima) y tarifa base.
* **Estado de habitación**: Situación operativa actual de una habitación (`Room.status`), con **8 valores vigentes**: `Available` (Disponible), `Reserved` (existe una reserva asociada en Módulo 2, aún no ocupada; se marca por evento de Módulo 2, no por consulta activa de Módulo 1), `Occupied` (Ocupada), `PendingCleaning` (Pendiente de limpieza), `InCleaning` (En limpieza), `DisabledForRepairs` (Inhabilitada por reparaciones), `TechnicalBlock` (Bloqueo técnico) e `Inactive` (Inactiva).
* **Tarifa base**: Valor regular de una habitación, usado como punto de partida del cálculo de tarifa dinámica (Módulo 3).
* **Check-in / Check-out**: Eventos gestionados directamente por **Módulo 1**, ejecutados por el Recepcionista.
  * El **Check-In** transiciona la habitación de `Reserved` a `Occupied`, de forma interna y síncrona. No genera ninguna liquidación ni interviene Módulo 3. Notifica de forma asíncrona a Módulo 2 el Check-In de la habitación (la reserva pasa a `IN_PROGRESS` con su primera habitación).
  * El **Check-Out** transiciona la habitación de `Occupied` a `PendingCleaning`, de forma interna y síncrona. Incluye el paso **"Consultar liquidación"**: envía a Módulo 3 las fechas reservadas y reales de la estancia para obtener la liquidación final, y notifica de forma asíncrona a Módulo 2 la salida de la habitación (la reserva pasa a `COMPLETED` cuando ya no quedan habitaciones ocupadas ni por llegar).
  * Ningún fallo de integración con Módulo 2 o Módulo 3 bloquea la transición física de la habitación en ninguno de los dos eventos.
* **Estancia (Stay)**: Entidad que representa la ocupación física real de una habitación durante un período determinado. Se crea de forma síncrona al confirmar el Check-In y se cierra al registrar el Check-Out. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`; solo fecha, sin hora), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
* **Ocupante (RoomGuest)**: Entidad inmutable de Módulo 1 que representa a cada persona físicamente alojada en la habitación durante la estancia. Se crea al confirmar el Check-In y no puede modificarse posteriormente. Atributos: `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `isReservationGuest` (titular de la reserva) y, si es extranjero, `birthDate`, `originPlace` y `destinationPlace`. Vinculada a la Estancia.
* **Reserva (Reservation) — referencia desde Módulo 1**: Registro contractual que origina una estancia, administrado por Módulo 2. Módulo 1 la conoce por la copia local de la lista del día (*Consultar reservas*), con: `reservationRef`, `startDate`, `endDate`, `source`, datos del titular (nombres, apellidos, tipo y número de documento, nacionalidad), cantidad de huéspedes y sus habitaciones (`roomId`, `roomNumber`, `categoryRoom` y cantidad de huéspedes por habitación). La copia local no guarda su `status`.
* **ReservationQuery**: Criterio con el que la Recepcionista busca llegadas en la copia local de la lista del día, sin consultar a Módulo 2: código de reserva (`reservationRef`), número de documento o nombre del titular.
* **ReservationSummary**: Elemento de `items` en la respuesta de Módulo 2 a la consulta de reservas por rango (`GET /api/reservations`, *Consultar reservas* FR-023 de Módulo 2), que usan *Programar bloqueo técnico para habitación* y *Dar de baja habitación*. Contiene: `reservationRef`, `status` (solo vigentes: `PENDING`, `ACTIVE` o `IN_PROGRESS`), `startDate`, `endDate` y, por cada habitación de la reserva, `roomId`, `roomNumber` y `categoryRoom`. No incluye datos del huésped ni datos financieros.
* **SettlementRequest**: Objeto conceptual que Módulo 1 envía a Módulo 3 al ejecutar "Consultar liquidación" durante el Check-Out (`GET /api/settlements`). Incluye: `reservationRef`, `checkInDate`, `checkOutDate`, `source` (de `Stay.source`), `roomId` y `categoryRoom`. No envía `eventType`, `startDate` ni `endDate`.
* **SettlementSummary**: Estructura informativa de solo lectura devuelta por Módulo 3 como respuesta a la solicitud de liquidación. Contiene dos grupos: (1) **Resumen para el huésped**: valor del hospedaje (total ya calculado), IVA y total a pagar; y (2) **Información de la operación**: canal de origen, porcentaje de comisión OTA (si aplica), valor de comisión OTA (si aplica), ingreso neto y factura definitiva asociada. Módulo 1 la presenta en recepción pero no recalcula ni modifica sus valores.
* **CleaningTask (tarea de limpieza)**: Asignación de la limpieza de una habitación a un miembro del Personal de limpieza. Se crea al marcar la habitación en limpieza y se cierra al confirmar el fin o al liberarla. Atributos: `RoomId`, `CleaningStaffMemberId`, `StartDateTime`, `EndDateTime` y `Outcome` (`Completed`, `DamageReported` o `Released`). Un miembro solo puede tener una tarea abierta.
* **ReparationTask (tarea de reparación)**: Asignación de la intervención de una habitación inhabilitada o en bloqueo técnico a un miembro del Personal de mantenimiento. Se crea con la acción "Iniciar reparaciones" y se cierra al confirmar el fin o al liberarla. Atributos: `MaintenanceStaffMemberId`, `RoomId`, `StartDateTime`, `EndDateTime` y `Outcome` (`Completed` o `Released`). Una habitación y un miembro solo pueden tener una tarea abierta.
* **DamageReport (reporte de daño)**: Registro inmutable del daño que lleva una habitación a `DisabledForRepairs`. Atributos: `DamageDescription` (máximo 500 caracteres), `UserId` (autor), `RoomId` y `ReportDateTime`. Se consulta con la acción "Ver informe".
* **TechnicalBlockReport (informe de bloqueo técnico)**: Registro de un mantenimiento preventivo programado. Atributos: `RoomId`, `MaintenanceStaffMemberId`, `TechnicalBlockReason`, `TechnicalBlockStartDate`, `EstimatedTechnicalBlockEndDate`, `ReportDateTime` y `Status` (`Scheduled`, `Applied`, `Completed` o `Expired`). Un trabajo autónomo de las 00:00 lo aplica, pasando la habitación a `TechnicalBlock`, cuando llega su fecha de inicio. Un informe `Applied` cuya fecha estimada de fin es anterior a la fecha actual se considera **vencido**: no bloquea reservas de fechas futuras y el panel de mantenimiento lo muestra como "Bloqueo técnico (vencido)".
* **RoomStateHistory (historial de estados)**: Registro común de los periodos de cada habitación en cada estado (`Status`, `PreviousStatus`, `StartDateTime`, `EndDateTime`, `ActorId`, `SourceFlow`, `ReservationRef`). Cada transición cierra el periodo abierto y abre uno nuevo; es la fuente de "Consultar historial de estados".
* **Bitácora de auditoría (`room_audit_log`)**: Registro de los eventos de una habitación que no son transiciones de estado, como un conflicto al apartarla (`RESERVATION_STATE_CONFLICT`) o la edición de sus datos. Las transiciones de estado no se registran aquí, sino en `RoomStateHistory`.
* **Vista de tarea activa**: Pantalla exclusiva a la que se redirige al Personal de limpieza o de mantenimiento mientras tiene una tarea abierta; solo permite "Confirmar fin" o "Liberar tarea".
* **Liberar tarea**: Acción con la que el dueño de una tarea abierta la cierra sin terminarla (`Outcome` = `Released`) para que otro miembro la retome desde el panel. En limpieza devuelve la habitación a `PendingCleaning`; en reparación no cambia el estado.

## Módulo 2: Operación de Reservas y Cumplimiento Legal

* **Reserva**: Registro de la relación entre un huésped y una habitación, con fechas de check-in y check-out, tipo de habitación y canal de origen.
* Ciclo de Vida de la Reserva (Reservation.status): Estados oficiales gobernados por el Módulo 2 (diccionario de Módulo 2):

  * PENDING: Estado inicial de las reservas de canal OTA mientras se confirman.
  * ACTIVE: Reserva confirmada, aún sin Check-In.
  * IN\_PROGRESS: El Módulo 1 notificó el Check-In de la primera habitación de la reserva.
  * COMPLETED: Ya no queda ninguna habitación de la reserva ocupada ni por llegar.
  * CANCELLED: Reserva anulada por solicitud explícita; si estaba en la lista del día, el Módulo 1 recibe `REMOVED`.
  * NO\_SHOW: El huésped no se presentó en la fecha de llegada sin previo aviso.
  * El estado de cada habitación dentro de la reserva (`CHECKED_IN`, `CHECKED_OUT`, `NOT_ARRIVED`) es distinto del estado de la reserva.
* **Canal de origen**: Clasificación de la reserva como **Canal Directo** (recepción, teléfono, portal propio; 0% comisión) o **OTA** (intermediario con comisión pactada y código de confirmación externo). Sin canal registrado, se asume Canal Directo.
* Desacoplamiento de Inventario ("Aforo Lógico"): Mecanismo con el que el Módulo 2 calcula la disponibilidad para reservas y modificaciones (*Verificar disponibilidades*): consulta de forma síncrona al Módulo 1 las habitaciones vendibles (*Consultar inventario de habitaciones*) y su calendario de mantenimientos (*Consultar información de mantenimientos*), y cruza localmente sus reservas vigentes. No usa el estado físico de hoy de las habitaciones.
* **Código de confirmación externo**: Identificador que la OTA asigna a la reserva; obligatorio en canal OTA, usado para trazabilidad y para restringir que cada OTA solo consulte sus propias reservas.
* **SIRE (Validación Migratoria)**: Conjunto de datos obligatorios (documento de identidad, nacionalidad, tipo de visa, fechas de estancia), capturados por Módulo 1 durante el Check-In y enviados a Módulo 2, quien los valida y exporta como archivo plano `.TXT` para las autoridades de migración.
* Estado de Exportación SIRE (sireExportStatus): Atributo de control dentro de la validación migratoria (MigratoryValidation) del Módulo 2. Maneja únicamente dos estados:

  * PENDING: Registro de extranjero validado en el Check-In y pendiente de ser exportado en el reporte .TXT.
  * EXPORTED: Registro ya descargado por el actor Migración en un archivo plano de reporte.

## Módulo 3: Facturación, Consumos y Liquidación

### Tarifas

* **Temporada (baja / regular / alta)**: Clasificación de una fecha según reglas de estacionalidad, en una de tres categorías — temporada baja, regular o alta — que determina el sentido del ajuste dinámico aplicado sobre la tarifa base (alta = incremento, baja = decremento, regular = ajuste neutro).
* **Tarifa dinámica**: Resultado de ajustar la tarifa base según la regla de temporada aplicable a cada noche.
* **Valor de hospedaje (bruto)**: Suma de las tarifas dinámicas de todas las noches de la estancia, antes de descontar comisión OTA. Es la base que la liquidación reutiliza sin recalcular.

### Comisión OTA

* **Comisión OTA**: Porcentaje pactado con un intermediario, aplicado sobre el valor de hospedaje cuando la reserva proviene de un canal OTA.
* **Fórmula de comisión**: `- (Valor Hospedaje × % Comisión)`. Solo se aplica si el canal es OTA; en Canal Directo o sin canal registrado, la comisión es siempre cero.

### Impuestos

* **IVA**: Impuesto al Valor Agregado, calculado sobre el valor de hospedaje (nunca sobre la comisión OTA descontada). No forma parte del ingreso neto de la liquidación; se incorpora en la generación de la factura final.
* **Base gravable**: Valor de hospedaje (original o de noches adicionales por extensión) sobre el que se calcula el IVA.
* **Porcentaje de IVA vigente**: Tasa configurada por el Administrador; se fija en el check-in para el hospedaje original y solo cambia para noches adicionales de una extensión.

### Liquidación

* **Liquidación**: Resultado del proceso de liquidar una estancia. Incluye estado, valor de hospedaje, comisión OTA aplicada (si corresponde) e ingreso neto. Solo existe a partir de un evento de un check-out.
* **Ingreso neto**: Valor de hospedaje menos la comisión OTA aplicable, sin incluir impuestos. Es el valor que reutiliza la generación de la factura final.
* **Detalle / Desglose de liquidación**: Desglose que identifica el valor de hospedaje, el canal, la comisión aplicada y el ingreso neto de una liquidación específica.

### Factura

* **Prefactura**: Documento en borrador generado al check-in; sin numeración oficial, mutable mientras la liquidación se mantenga en estado `Preliminary`; no es un documento fiscal válido ante terceros.
* **Factura fiscal definitiva**: Documento formal generado al check-out; con numeración consecutiva oficial, inmutable una vez emitida.
* **Numeración consecutiva oficial**: Secuencia única y ordenada de números asignados exclusivamente a facturas definitivas; nunca a prefacturas.
* **Cliente responsable de facturación**: Datos tributarios mínimos (nombre o razón social, documento fiscal) requeridos para emitir una factura definitiva.
* **Desglose facturable**: Hospedaje, comisión OTA (solo como referencia informativa, nunca como cargo al huésped), IVA y total, expuestos en cada factura.

### Gestión y consulta

* **Consulta de liquidación**: Solicitud de un actor autorizado para obtener el desglose y estado de la liquidación de una estancia, sin recalcularla.
* **Resultado de consulta**: Desglose de hospedaje, comisión OTA, IVA e ingreso neto, junto con el estado (`Preliminary`, `Final` o `Cancelled`) y la factura asociada, devuelto por una consulta de liquidación.
* **Ámbito de acceso por actor**: Regla de visibilidad que limita a cada actor externo a ver únicamente las liquidaciones que le corresponden.
* **Criterio de búsqueda / Resultado de búsqueda**: Filtros (estancia, cliente, canal, rango de fechas, estado) y resultados que el Administrador usa para localizar facturas ya emitidas, sin crear ni modificar nada.
* **Detalle de factura consultada**: Vista de solo lectura del desglose completo y la trazabilidad de una factura específica, idéntica a la generada originalmente.
* **Resumen consolidado**: Agregado de totales de hospedaje, comisión OTA e IVA por canal de origen y por estado (`Final` o `Preliminary`), calculado sobre un rango de fechas, usado por el Administrador para conciliar con cada OTA.
