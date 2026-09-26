# Especificación de Funcionalidad: Registrar Check-Out

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-19

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Formalización de Check-Out y liberación de habitación (Prioridad: P1)

Como Recepcionista, quiero formalizar la salida física del huésped consultando la liquidación informativa calculada por Módulo 3 (resumen para el huésped con valor de hospedaje, IVA y total a pagar; e información operativa con canal de origen, comisión OTA, ingreso neto y factura definitiva asociada) y confirmando la operación, para que la habitación transicione inmediatamente al estado "PendingCleaning" pasando a la bandeja del Personal de limpieza y se notifique de forma asíncrona al Módulo 2 para el cierre de la reserva.

**Por qué esta prioridad**: Es la operación misional de cierre de la estancia física en el hotel. Garantiza que la habitación se libere de forma síncrona en el inventario hacia el personal de aseo (maximizando la rotación de cuartos limpios), muestra al huésped un resumen fidedigno de los rubros financieros provistos por Módulo 3 sin procesar cobros locales, y notifica asíncronamente a Módulo 2 la finalización de la estadía.

**Prueba Independiente**: Se prueba de forma aislada iniciando sesión como Recepcionista y recorriendo el flujo de salida: (1) búsqueda por código de reserva en estado "CHECKED_IN" (en Módulo 2 "IN_PROGRESS") con habitación en "Occupied"; (2) recepción y consulta del desglose financiero informativo de Módulo 3 estructurado en resumen para el huésped e información operativa con factura definitiva asociada; (3) confirmación explícita de salida; y (4) verificación de la transición a "PendingCleaning", su envío a la bandeja del personal de limpieza y el despacho asíncrono a Módulo 2.

**Escenarios de Aceptación**:

1. **Escenario**: Flujo completo de Check-Out en fecha pactada (Happy Path)
   - **Dado** una habitación en estado operativo "Occupied" vinculada a una reserva en Módulo 2 en estado "CHECKED_IN" (estado "IN_PROGRESS" en Módulo 2), cuya fecha de salida programada coincide con hoy
   - **Cuando** el Recepcionista consulta la reserva, revisa la liquidación calculada por Módulo 3 diferenciando el resumen para el huésped (valor del hospedaje total ya calculado, IVA, total a pagar) y la información operativa de la reserva (canal de origen, porcentaje y valor de comisión OTA si aplica, ingreso neto y factura definitiva asociada emitida por Módulo 3 sin recalcularla), y confirma la salida del huésped
   - **Entonces** el sistema transiciona la habitación a "PendingCleaning" de forma síncrona mediante la invocación interna a "Marcar pendiente a limpieza", envía la notificación asíncrona a Módulo 2 para pasar la reserva a "COMPLETED" (etiquetada como "CHECKED_OUT"), y certifica la liberación de la habitación informando que ha sido enviada a la bandeja del personal de limpieza.

2. **Escenario**: Check-Out con salida anticipada (Early Check-Out) y penalización incorporada
   - **Dado** un huésped con reserva en estado "CHECKED_IN" cuya fecha de finalización pactada ("endDate") es posterior a la fecha actual
   - **Cuando** el Recepcionista consulta la reserva e inicia el Check-Out en la fecha de hoy
   - **Entonces** Módulo 3 entrega en la liquidación el valor total de hospedaje liquidado (con el cálculo y penalizaciones ya incorporados por Módulo 3 sin requerir desglose por noche), el IVA, el total a pagar, junto con los datos de canal, comisión OTA, ingreso neto y factura definitiva asociada
   - **Y cuando** el Recepcionista verifica la información (atendiendo a que la salida anticipada incluye penalizaciones, que no se gestionan consumos locales en este flujo y que la actualización hacia Módulo 2 es asíncrona) y confirma la salida
   - **Entonces** el sistema libera la habitación a "PendingCleaning" de inmediato y despacha la notificación a Módulo 2.

3. **Escenario**: Bloqueo de confirmación por falta de validación explícita
   - **Dado** que se ha consultado la reserva y la liquidación de la estadía
   - **Cuando** el Recepcionista intenta finalizar la salida sin haber confirmado explícitamente que la información mostrada es correcta
   - **Entonces** el sistema mantiene bloqueada la finalización, impidiendo cualquier transición física de la habitación o notificación a Módulo 2 hasta contar con la aceptación explícita.

4. **Escenario**: Recepción y visualización inmediata en la bandeja de trabajo de limpieza
   - **Dado** que se ha confirmado exitosamente un Check-Out
   - **Cuando** el Personal de limpieza consulta su listado o bandeja de trabajo
   - **Entonces** la habitación figura de inmediato en estado "PendingCleaning", habilitando el inicio del proceso de aseo.

---

### Historia de Usuario 2 - Bloqueo de salidas inconsistentes y resiliencia de integración (Prioridad: P2)

Como Recepcionista, quiero que el sistema rechace intentos de Check-Out sobre habitaciones o reservas en estados incompatibles y mantenga la resiliencia operativa ante fallas o indisponibilidad externa, para evitar inconsistencias en el inventario y no perjudicar la salida física del huésped.

**Por qué esta prioridad**: Asegura la consistencia lógica de la máquina de estados e impide que caídas de servicios externos (Módulo 2 o Módulo 3) bloqueen la operación física del hotel o dejen habitaciones en estados huérfanos.

**Prueba Independiente**: Se prueba intentando registrar el Check-Out sobre habitaciones en estados diferentes a "Occupied", reservas en estados incompatibles en Módulo 2 ("ACTIVE", "CANCELLED", "COMPLETED"), o simulando indisponibilidad en los servicios externos de Módulo 2 o Módulo 3, verificando los bloqueos controlados y los mecanismos de contingencia.

**Escenarios de Aceptación**:

1. **Escenario**: Rechazo de Check-Out por habitación no ocupada
   - **Dado** una habitación cuyo estado físico en Módulo 1 es "Available", "Reserved", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"
   - **Cuando** el Recepcionista intenta consultar o ejecutar el Check-Out
   - **Entonces** el sistema rechaza la operación informando que la habitación no se encuentra en estado "Occupied" y no tiene una estancia física activa para finalizar.

2. **Escenario**: Rechazo de Check-Out por reserva en estado no apto en Módulo 2
   - **Dado** una reserva consultada en Módulo 2 que se encuentra en estado "ACTIVE" (sin Check-In previo), "CANCELLED" o "COMPLETED" / "CHECKED_OUT"
   - **Cuando** el Recepcionista busca el código de reserva
   - **Entonces** el sistema bloquea el avance con un mensaje controlado indicando que la reserva no registra un ingreso previo o ya fue finalizada.

3. **Escenario**: Resiliencia ante caída o indisponibilidad de Módulo 3 (Facturación)
   - **Dado** una habitación en estado "Occupied" con reserva "CHECKED_IN" al momento en que el servicio de Módulo 3 no responde o se demora al solicitar la liquidación
   - **Cuando** el Recepcionista gestiona la salida física del huésped
   - **Entonces** el sistema Módulo 1 presenta un informe controlado de la indisponibilidad temporal del cálculo financiero, ofreciendo al Recepcionista la opción de autorizar la liberación física de la habitación a "PendingCleaning" para no retrasar el aseo ni retener al huésped, registrando la transacción como pendiente de regularización financiera sin provocar fallos técnicos no controlados.

4. **Escenario**: Resiliencia ante falla de notificación asíncrona a Módulo 2
   - **Dado** que el Check-Out físico fue confirmado y la habitación transicionó a "PendingCleaning", pero la comunicación con Módulo 2 sufre una desconexión
   - **Cuando** el sistema despacha la notificación asíncrona a Módulo 2
   - **Entonces** el cambio de estado físico de la habitación se mantiene firme e irreversible en "PendingCleaning", el flujo concluye exitosamente y la notificación a Módulo 2 se programa para reintento en segundo plano.

---

### Casos Borde

- **Confirmación explícita requerida para la salida**: El sistema no permite el despacho de la transacción de Check-Out bajo ninguna circunstancia si el Recepcionista no ha validado explícitamente que la información es correcta.
- **Ausencia de gestión de consumos locales**: Coherente con las reglas de negocio, este flujo NO incluye campos ni gestión de minibar, lavandería ni consumos de restaurante. La liquidación provista por Módulo 3 se limita exclusivamente a hospedaje, comisiones intermediarias, impuestos y la factura definitiva asociada.
- **Indisponibilidad o falta de respuesta de Módulo 3 al consultar liquidación**: Si la consulta a Módulo 3 supera el tiempo de espera o retorna error, el sistema ofrece una vía de contingencia para liberar la habitación física a "PendingCleaning" y registra la transacción para regularización posterior sin interrumpir la atención.
- **Notificación duplicada hacia Módulo 2**: Si la notificación de Check-Out se reenvía hacia Módulo 2 para una reserva que ya alcanzó el estado "COMPLETED", Módulo 2 confirma la recepción sin duplicar registros ni generar alteraciones redundantes.
- **Peticiones simultáneas de Check-Out sobre la misma habitación**: La primera petición transiciona la habitación a "PendingCleaning"; cualquier intento concurrente posterior es bloqueado de inmediato informando colisión y notificando que la habitación ya no figura como "Occupied".

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado "Recepcionista" registrar la salida física y formalizar el Check-Out en Módulo 1.
- **FR-002**: El sistema DEBE estructurar el proceso de Check-Out a través de las etapas funcionales: (1) Consulta de reserva, (2) Liquidación de la estadía, (3) Confirmación de salida y (4) Finalización y liberación de habitación.
- **FR-003**: En la consulta inicial de reserva, el sistema DEBE invocar obligatoriamente el caso de uso "Consultar reservas" (`<<includes>>`) contra Módulo 2 mediante el código de reserva (`reservationRef`), confirmando que el estado de la reserva sea "CHECKED_IN" (estado "IN_PROGRESS" en Módulo 2) y que la habitación actual figure en estado "Occupied" ("Ocupada").
- **FR-004**: El sistema DEBE validar como precondición física obligatoria que la habitación asignada se encuentre estrictamente en estado "Occupied" según documentos/SPEC/referencias/maquina-estados-habitacion.md. Si la habitación está en cualquier otro estado, DEBE rechazar el inicio del Check-Out con un mensaje de error controlado, detallando el estado físico real.
- **FR-005**: En la fase de liquidación de la estadía, el sistema DEBE invocar obligatoriamente el caso de uso interno "Consultar liquidación" (`<<includes>>`), enviando a Módulo 3 las fechas reservadas y marcas de tiempo reales (`checkInTime` y `checkOutTime`).
- **FR-006**: El sistema DEBE presentar la información de liquidación provista por Módulo 3 estructurada en dos grupos informativos:
  1. **Resumen para el huésped**:
     - Valor del hospedaje (total, ya calculado y consolidado por Módulo 3, no desglosado por noche)
     - Impuesto al Valor Agregado (IVA)
     - Total a pagar
  2. **Información de la reserva / operación (para uso de recepción)**:
     - Canal de origen (Booking / Web / Directo)
     - Porcentaje de comisión OTA (si aplica)
     - Valor monetario de la comisión OTA (si aplica)
     - Ingreso neto
     - Factura definitiva asociada emitida por Módulo 3 (con su número consecutivo oficial y desglose según FR-010 de generar_factura_final.md)
- **FR-007**: El sistema NO DEBE solicitar, registrar ni tramitar cobros de consumos locales (minibar, lavandería o restaurante) ni procesar pagos con pasarelas dentro de Módulo 1.
- **FR-008**: El sistema DEBE exigir una confirmación explícita por parte del Recepcionista validando que la información de salida es correcta antes de autorizar la formalización del Check-Out.
- **FR-009**: El sistema DEBE informar al Recepcionista que la salida anticipada incluye penalizaciones (si aplica), que no se gestionan consumos locales en este flujo y que la notificación de actualización hacia el Módulo 2 es asíncrona.
- **FR-010**: Al confirmar la salida, el sistema DEBE invocar de forma síncrona y atómica el caso de uso interno "Marcar pendiente a limpieza" (`<<includes>>`), transicionando el estado de la entidad Room de "Occupied" a "PendingCleaning".
- **FR-011**: La habitación en estado "PendingCleaning" DEBE figurar de inmediato en la bandeja de trabajo del Personal de limpieza.
- **FR-012**: El sistema DEBE despachar una notificación asíncrona hacia el servicio de Módulo 2 con la `reservationRef` y la hora de salida (`checkOutTime`), solicitando la transición de la reserva al estado "COMPLETED" (etiquetada como "CHECKED_OUT" en recepción).
- **FR-013**: Al formalizarse el Check-Out, el sistema DEBE confirmar que la habitación ha pasado a estado "PendingCleaning" y ha sido transferida a la bandeja del personal de limpieza, certificando la finalización exitosa del flujo.
- **FR-014**: Si Módulo 2 o Módulo 3 experimentan demoras o fallas de comunicación, el sistema NO DEBE bloquear la liberación física de la habitación a "PendingCleaning" ni impedir la salida del huésped; las notificaciones y conciliaciones se programan para resolución en segundo plano.
- **FR-015**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, la referencia de la reserva, el identificador de la Estancia, el recepcionista responsable del check-out (`receptionistIdCheckOut`), la fecha/hora de salida y la referencia de liquidación.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), fecha/hora de llegada real (`checkInTime`), fecha/hora de salida (`checkOutTime`), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **SettlementSummary**: Estructura conceptual informativa provista por Módulo 3 y obtenida vía "Consultar liquidación", estructurada en: (1) Resumen para el huésped: valor del hospedaje (total ya calculado), IVA y total a pagar; y (2) Información de la reserva / operación: canal de origen, porcentaje de comisión OTA (si aplica), valor de comisión OTA (si aplica), ingreso neto y factura definitiva asociada.
- **Receptionist**: Actor de recepcionista que opera el flujo de registro de check-in y check-out.
- **CleaningStaff**: Personal operativo que recibe de forma inmediata la habitación en estado "PendingCleaning" en su bandeja de trabajo.
- **Reservation**: Entidad conceptual de reserva que representa la reserva de una habitación. Atributos clave: ID único, referencia de reserva (`reservationRef`), fecha de inicio (`startDate`), fecha de fin (`endDate`), estado (`status`), habitación asignada (`assignedRoomId`), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede completar el proceso de Check-Out en menos de 1 minuto a partir de que el sistema recibe la respuesta de liquidación de Módulo 3.
- **SC-002**: El 100% de los Check-Outs confirmados transicionan de forma atómica y síncrona la habitación a "PendingCleaning", quedando visible de inmediato en la bandeja del Personal de limpieza.
- **SC-003**: El sistema impide el 100% de los intentos de salida sin la confirmación explícita del Recepcionista.
- **SC-004**: El sistema rechaza el 100% de los intentos de Check-Out sobre habitaciones cuyo estado físico sea diferente de "Occupied" o sobre reservas que no figuren en estado "CHECKED_IN" (estado "IN_PROGRESS" en Módulo 2).
- **SC-005**: Cero cálculos manuales de tarifas, cero cargos por minibar/consumos locales y cero operaciones de cobro pasarela procesadas en Módulo 1 durante este flujo.
- **SC-006**: Ante indisponibilidad de Módulo 2 o Módulo 3, el 100% de los casos permiten completar la liberación física de la habitación a "PendingCleaning", dejando registrada la notificación asíncrona para reintento en segundo plano sin retener al huésped en recepción.
