# Especificación de Funcionalidad: Registrar Check-Out

**Creado**: 2026-09-19  
**Módulo Propietario**: Módulo 1 (Gestión de Habitaciones e Inventario de Aforo, Check-In y Check-Out)

---

## Use Case (Caso de Uso)

### Descripción del problema

El cierre de la estancia y la salida física del huésped (Check-Out) es un momento crítico en la operación hotelera. Requiere coordinar de forma sincronizada la liberación física de la habitación para que el personal de limpieza pueda iniciar el reacondicionamiento sin demora, la obtención del cálculo financiero de liquidación para informar al cliente y la notificación del fin de la estadía al sistema de reservas.

En modelos anteriores, el check-out se encontraba fragmentado, involucraba la captura manual de consumos locales dispersos (como minibar o servicio a la habitación) y dependía de llamadas externas para actualizar el estado físico de los cuartos, generando demoras en recepción y desalineación operativa.

Bajo la arquitectura actual del sistema:
1. El **Módulo 1** es el responsable directo y propietario del caso de uso **Registrar Check-Out**.
2. **Sin consumos locales**: Se eliminan por completo los registros de consumos de minibar o room service en el flujo de Check-Out; la estancia se liquida exclusivamente en función del hospedaje y las reglas de temporada/canal calculadas por el Módulo 3.
3. **Distinción de casos de uso de liquidación (Naming)**:
   - El Módulo 1 incluye en este flujo su propio caso de uso denominado **"Consultar liquidación"**, mediante el cual empaqueta y envía los datos de la estadía al Módulo 3 para solicitar el cálculo del cobro final.
   - Este caso de uso debe diferenciarse estrictamente del caso de uso de Módulo 3 llamado **"Consulta de liquidación"**, el cual es una función independiente de solo lectura destinada a actores externos autorizados (como intermediarios u OTAs) para inspeccionar liquidaciones ya existentes sin recalcular nada.
4. **Presentación puramente informativa**: El Módulo 1 recibe el cálculo retornado por el Módulo 3 y se limita a mostrarlo en pantalla de manera informativa para el recepcionista y el huésped. El Módulo 1 **no ejecuta cobros, no maneja dinero ni integra pasarelas de pago**.
5. **Salida anticipada (Early Check-Out)**: Si el huésped se retira antes del `endDate` pactado originalmente, se cobran únicamente las noches efectivamente hospedadas más una penalización por salida anticipada. El Módulo 1 no calcula dicha penalización ni define sus nombres de campos internos: únicamente envía las fechas y horas reales en el payload a Módulo 3, quien ejecuta dicho cálculo.
6. **Transición física síncrona**: Al confirmarse la salida, el Módulo 1 transiciona de forma **interna y 100% síncrona** el estado de la habitación de **`Occupied` a `PendingCleaning`**, mediante el caso de uso interno incluido **"Marcar pendiente a limpieza"**, poniéndola de inmediato a disposición del **Personal de limpieza**.
7. **Cierre asíncrono y resiliente con Módulo 2**: La notificación para actualizar el estado de la reserva a `CHECKED_OUT` en Módulo 2 se emite de manera **asíncrona**. Ninguna falla de comunicación externa con Módulo 2 o lentitud en Módulo 3 bloquea la salida física del huésped ni la liberación de la habitación (se gestiona con estado de regularización local `PENDING` para reintento en segundo plano).

### Flujo de Usuario de Alto Nivel

1. El **Recepcionista** inicia el registro de Check-Out en la interfaz de Módulo 1 ingresando el número de habitación o la referencia de la reserva (`reservationRef`).
2. Módulo 1 invoca el caso de uso interno incluido **"Consultar reservas" (`<<includes>>`)** contra Módulo 2 para verificar que la reserva se encuentre en estado `CHECKED_IN`, y extraer las fechas contractuales (`startDate`, `endDate`), el canal de origen (`source`) y la habitación asignada.
3. Módulo 1 verifica localmente que la habitación se encuentre físicamente en estado **`Occupied`**.
4. Módulo 1 ejecuta su caso de uso interno incluido **"Consultar liquidación" (`<<includes>>`)**, enviando a Módulo 3 el payload estructurado con la información de la estadía:
   ```json
   {
     "reservationRef": "RES-12345",
     "eventType": "CHECK_OUT",
     "originalStay": { "startDate": "...", "endDate": "..." },
     "actualStay": { "arrivalTime": "...", "checkOutTime": "..." },
     "source": "DIRECT",
     "assignedHabitation": { "habitationId": "...", "categoryHabitation": "..." }
   }
   ```
5. El Módulo 3 procesa los datos, calcula el total (noches reales de hospedaje, penalización por salida anticipada si aplica, impuestos) y retorna el detalle financiero.
6. Módulo 1 presenta en pantalla el resumen consolidado de liquidación de forma puramente informativa para el Recepcionista y el huésped.
7. El Recepcionista confirma la formalización de la salida en el sistema.
8. En ese instante, Módulo 1 invoca de manera obligatoria el caso de uso interno **"Marcar pendiente a limpieza" (`<<includes>>`)**, cambiando el estado de la habitación de `Occupied` a **`PendingCleaning`** de forma atómica y síncrona.
9. Módulo 1 envía una notificación externa y asíncrona a Módulo 2 para actualizar el estado de la reserva a **`CHECKED_OUT`**.

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Formalización de Check-Out y liberación de habitación (Prioridad: P1)

Como **Recepcionista**, quiero registrar la salida física del huésped consultando la liquidación final con Módulo 3 y liberando la habitación al estado "PendingCleaning", para que el personal de limpieza reciba la unidad de inmediato y Módulo 2 registre el cierre de la estadía.

**Por qué esta prioridad**: Es la operación de cierre de estadía física en el hotel. Garantiza que la habitación se libere de forma inmediata y síncrona en el inventario hacia el personal de aseo, maximizando la rotación de habitaciones vendibles, a la vez que entrega un resumen fidedigno de liquidación generado por el área de pricing (Módulo 3) sin retener al huésped en recepción.

**Prueba Independiente**: Se puede probar de forma aislada iniciando sesión como Recepcionista, seleccionando una reserva en estado `CHECKED_IN` cuya habitación esté en estado `Occupied` en Módulo 1, solicitando el Check-Out. Se comprueba que Módulo 1 envía el payload a Módulo 3, recibe y exhibe el resumen informativo de cobro, y que al pulsar confirmar, la habitación pasa de forma síncrona a `PendingCleaning` (visible para el Personal de limpieza) y se despacha la notificación a Módulo 2 para pasar la reserva a `CHECKED_OUT`.

**Escenarios de Aceptación**:

*Escenarios de Éxito (Happy Path)*

1. **Escenario**: Check-out estándar en la fecha pactada
   - **Dado** una habitación en estado operativo `Occupied` asociada a una reserva activa en Módulo 2 en estado `CHECKED_IN`, cuya fecha de salida programada coincide con hoy
   - **Cuando** el Recepcionista inicia el Check-Out, Módulo 1 ejecuta "Consultar liquidación" enviando el payload con las fechas reservadas y reales al Módulo 3, y este devuelve el cálculo final
   - **Entonces** el sistema exhibe el resumen informativo en pantalla; al recibir la confirmación explícita del Recepcionista, invoca "Marcar pendiente a limpieza", transiciona la habitación a `PendingCleaning` de forma síncrona y despacha la notificación asíncrona a Módulo 2 para marcar la reserva como `CHECKED_OUT`

2. **Escenario**: Check-out con salida anticipada (Early Check-Out)
   - **Dado** un huésped con reserva `CHECKED_IN` cuya fecha de finalización pactada (`endDate`) es posterior a hoy (por ejemplo, reservó 5 noches pero sale al finalizar la noche 2)
   - **Cuando** el Recepcionista procesa el Check-Out hoy
   - **Entonces** Módulo 1 envía en el payload la fecha de hoy como `checkOutTime`; el Módulo 3 liquida únicamente las noches efectivamente hospedadas más la penalización por salida anticipada correspondiente; Módulo 1 muestra dicho valor informativo en pantalla y, tras la confirmación, libera la habitación a `PendingCleaning` y notifica a Módulo 2

3. **Escenario**: Reflejo inmediato de la habitación en la bandeja de trabajo de limpieza
   - **Dado** que se ha confirmado exitosamente un Check-Out en Módulo 1
   - **Cuando** el Personal de limpieza consulta su lista o bandeja de habitaciones asignadas
   - **Entonces** la habitación figura de inmediato en estado `PendingCleaning`, lista para iniciar la fase de aseo

---

### Historia de Usuario 2 - Bloqueo de salidas inconsistentes y resiliencia de integración (Prioridad: P1)

Como **Recepcionista**, quiero que el sistema rechace intentos de Check-Out sobre habitaciones o reservas en estados incompatibles y mantenga la resiliencia operativa ante fallas de red, para evitar inconsistencias en el inventario y no perjudicar la salida física del huésped.

**Por qué esta prioridad**: Asegura la consistencia lógica de la máquina de estados e impide que fallas de servicios externos (Módulo 2 o Módulo 3) bloqueen la operación física del hotel o dejen habitaciones en estados huérfanos.

**Prueba Independiente**: Se prueba intentando registrar el Check-Out sobre habitaciones no ocupadas (`Available`, `InCleaning`, etc.), reservas no vigentes en Módulo 2, o simulando indisponibilidad en los servicios externos de Módulo 2 o Módulo 3, verificando los bloqueos controlados y los mecanismos de resiliencia.

**Escenarios de Aceptación**:

*Escenarios de Validación y Manejo de Errores*

4. **Escenario**: Rechazo de Check-Out por habitación no ocupada
   - **Dado** una habitación cuyo estado físico en Módulo 1 es `Available`, `Reserved`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`
   - **Cuando** el Recepcionista intenta ejecutar el Check-Out
   - **Entonces** el sistema rechaza la operación informando que la habitación no cuenta con una estancia activa para finalizar

5. **Escenario**: Rechazo de Check-Out por reserva en estado no apto en Módulo 2
   - **Dado** una reserva consultada en Módulo 2 que se encuentra en estado `ACTIVE` (sin check-in previo), `CANCELLED` o ya en `CHECKED_OUT`
   - **Cuando** el Recepcionista intenta realizar el Check-Out
   - **Entonces** el sistema bloquea el flujo con error HTTP 400 e informa el estado no válido de la reserva

6. **Escenario**: Prevención de Check-Out duplicado
   - **Dado** una estancia cuyo Check-Out ya fue confirmado y procesado con anterioridad
   - **Cuando** se intenta volver a confirmar el Check-Out para la misma habitación o reserva
   - **Entonces** el sistema rechaza la acción indicando que la salida ya fue registrada, sin generar nuevas transacciones ni invocar duplicadamente la rutina de limpieza

7. **Escenario**: Resiliencia ante caída o indisponibilidad temporal de Módulo 3 (Pricing)
   - **Dado** una habitación en estado `Occupied` con reserva en `CHECKED_IN` al momento en que el servicio de Módulo 3 no responde o se encuentra fuera de línea
   - **Cuando** el Recepcionista confirma la salida física del huésped
   - **Entonces** el sistema Módulo 1 permite completar la salida física, transiciona la habitación a `PendingCleaning` para no retrasar el aseo, y registra la transacción de salida con una marca de regularización pendiente (`PENDING`), sin arrojar un error HTTP 500 no controlado

8. **Escenario**: Resiliencia ante falla de notificación a Módulo 2
   - **Dado** que el Check-Out físico fue confirmado y la habitación pasó a `PendingCleaning`, pero la red hacia Módulo 2 sufre una interrupción
   - **Cuando** Módulo 1 intenta despachar la actualización a `CHECKED_OUT`
   - **Entonces** el cambio de estado de la habitación se mantiene firme en `PendingCleaning` y la notificación externa queda registrada localmente como `PENDING` para reintento en segundo plano

---

### Casos Borde

- **Indisponibilidad o timeout de Módulo 3 al consultar liquidación**: Si la petición a "Consultar liquidación" supera el tiempo límite o retorna error, el sistema ofrece al Recepcionista un mecanismo de contingencia para autorizar la salida física del huésped; la habitación se libera a `PendingCleaning` y se registra una alerta de regularización de cobro pendiente para auditoría.
- **Fallo de comunicación con Módulo 2 tras la confirmación de salida**: La transacción física y local de Módulo 1 es prioritaria; la habitación transiciona a `PendingCleaning` y la notificación a Módulo 2 se guarda en cola local con estado `PENDING` para reintento automático en segundo plano.
- **Peticiones simultáneas de Check-Out sobre la misma habitación**: La primera petición en ejecutarse transiciona la habitación a `PendingCleaning`; cualquier petición concurrente posterior es rechazada inmediatamente por colisión de concurrencia informando que la habitación ya no se encuentra en estado `Occupied`.
- **Salida tardía posterior a la hora pactada de Check-Out (Late Check-Out)**: Módulo 1 no aplica recargos ni penalizaciones de forma autónoma; simplemente envía la hora exacta de salida (`checkOutTime`) dentro del payload a Módulo 3, delegando en este último la aplicación de tarifas por salida tardía según sus políticas de facturación.
- **Pérdida de conexión antes de la confirmación**: Si ocurre una desconexión antes de que el Recepcionista confirme en pantalla, no se persiste ninguna modificación y la habitación permanece inalterada en estado `Occupied`.
- **Datos mal formados en la solicitud**: Toda petición con campos faltantes o datos no válidos es interceptada en el controlador de Módulo 1 y devuelta como un error estructurado **HTTP 400 (Bad Request)** amigable, protegiendo al sistema de errores no controlados **HTTP 500**.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado **Recepcionista** iniciar y registrar el Check-Out en el Módulo 1.
- **FR-002**: El sistema DEBE invocar obligatoriamente el caso de uso **"Consultar reservas" (`<<includes>>`)** contra el Módulo 2 para verificar que la reserva exista, validar que su estado sea `CHECKED_IN`, y obtener las fechas de estancia contractuales (`startDate`, `endDate`), el canal (`source`) y la habitación asignada.
- **FR-003**: Precondición física: La habitación asignada DEBE encontrarse estrictamente en estado **`Occupied`** dentro del inventario de Módulo 1.
- **FR-004**: El sistema DEBE rechazar la solicitud de Check-Out si la habitación se encuentra en cualquiera de los otros 7 estados operativos: `Available`, `Reserved`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`, informando el motivo del bloqueo.
- **FR-005**: El sistema DEBE invocar obligatoriamente el caso de uso interno **"Consultar liquidación" (`<<includes>>`)**, el cual empaqueta y envía al Módulo 3 el payload de salida con:
  - `reservationRef`: Identificador de la reserva.
  - `eventType`: Literal `"CHECK_OUT"`.
  - `originalStay`: Fechas reservadas (`startDate`, `endDate`).
  - `actualStay`: Fechas y horas reales de la estancia (`arrivalTime`, `checkOutTime`).
  - `source`: Canal de procedencia de la reserva (`DIRECT` u `OTA`).
  - `assignedHabitation`: Datos de la unidad (`habitationId`, `categoryHabitation`).
- **FR-006**: Ausencia de consumos locales: El sistema **NO DEBE** solicitar, registrar ni enviar información sobre consumos locales (minibar, lavandería o servicio a la habitación) en el flujo de Check-Out.
- **FR-007**: Salida anticipada: Cuando el `checkOutTime` sea anterior al `endDate` reservado, el sistema DEBE enviar las marcas de tiempo reales en el payload de "Consultar liquidación", consumiendo el cálculo de cobro generado por el Módulo 3 (noches reales más penalización por salida anticipada) sin aplicar fórmulas locales en Módulo 1.
- **FR-008**: Carácter informativo: El sistema DEBE exhibir en pantalla el desglose y monto total retornado por Módulo 3 con carácter **estrictamente informativo** para revisión visual, sin ejecutar transacciones de cobro ni procesar pasarelas de pago dentro de Módulo 1.
- **FR-009**: Transición síncrona mediante inclusión: Al recibir la confirmación explícita del Recepcionista, el sistema DEBE invocar obligatoriamente el caso de uso interno **"Marcar pendiente a limpieza" (`<<includes>>`)**, transicionando de forma interna, síncrona y atómica el estado de la entidad `Room` de `Occupied` a **`PendingCleaning`**.
- **FR-010**: Disponibilidad inmediata de aseo: La habitación con estado `PendingCleaning` DEBE aparecer de forma inmediata en la lista de trabajo del Personal de limpieza (`CleaningStaff`).
- **FR-011**: Notificación externa y asíncrona: El sistema DEBE despachar una notificación asíncrona al Módulo 2 informando la formalización del Check-Out con el identificador de la reserva y la marca de tiempo de salida, solicitando la actualización de la reserva al estado **`CHECKED_OUT`**.
- **FR-012**: Resiliencia operativa: Ningún fallo, lentitud o indisponibilidad de Módulo 2 o Módulo 3 debe impedir la salida física del huésped ni revertir el paso de la habitación a `PendingCleaning`; las operaciones no confirmadas externamente quedarán registradas con estado `PENDING` para sincronización posterior.
- **FR-013**: Auditoría: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, la referencia de la reserva, el identificador del Recepcionista, la fecha y hora exacta de salida (`checkOutTime`) y la referencia de la liquidación obtenida.

### Requisitos No Funcionales

- **NFR-001**: La ejecución de la inclusión "Marcar pendiente a limpieza" y la actualización síncrona de la habitación a `PendingCleaning` deben completarse en un tiempo inferior a 500 milisegundos tras la confirmación del Recepcionista.
- **NFR-002**: La llamada al caso de uso "Consultar liquidación" hacia Módulo 3 debe contar con un tiempo límite de espera (timeout) de 3 segundos bajo condiciones normales de red antes de activar el modo de contingencia.
- **NFR-003**: Todas las respuestas por datos mal formados, estados no válidos o colisiones de concurrencia deben retornar códigos HTTP 400 (Bad Request) o HTTP 409 (Conflict) controlados, previniendo errores de servidor HTTP 500.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room / Habitation**: Unidad habitacional administrada por el Módulo 1.
  - *Atributos clave*: `habitationId` (UUID), `numberHabitation`, `floorWing`, `categoryHabitation`, `stateHabitation` (catálogo oficial de 8 estados).
  - *Comportamiento*: Transiciona de `Occupied` a `PendingCleaning` mediante la inclusión de "Marcar pendiente a limpieza".
- **CheckOut**: Transacción operativa de salida registrada en Módulo 1.
  - *Atributos clave*: `id` (UUID), `reservationRef`, `habitationId`, `checkOutTime`, `processedBy` (Recepcionista), `liquidationRef` (referencia devuelta por Módulo 3), `syncStatusM2` (`SYNCED` | `PENDING`).
- **SettlementSummary (Resumen de Liquidación)**: Objeto de datos informativo retornado por Módulo 3 y consumido por "Consultar liquidación".
  - *Atributos informativos*: `liquidationRef`, `stayAmount`, `earlyDeparturePenalty` (si aplica), `taxes`, `totalAmount`. Módulo 1 no persiste desglose contable, solo exhibe y guarda la referencia para auditoría.
- **Reservation (Referencia de Módulo 2)**: Registro externo de la reserva.
  - *Atributos consumidos/notificados*: `reservationRef`, `startDate`, `endDate`, `source`, `assignedHabitation`, `state` (`CHECKED_IN` -> notificado a `CHECKED_OUT`).
- **CleaningStaff (Personal de limpieza)**: Actor de Módulo 1 que recibe la habitación liberada en estado `PendingCleaning` como insumo de trabajo.
- **Receptionist (Recepcionista)**: Usuario que opera y confirma el Check-Out en el front-desk.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede completar el proceso de Check-Out en menos de 1 minuto a partir de que el sistema recibe la respuesta de liquidación del Módulo 3.
- **SC-002**: El 100% de los Check-Outs confirmados transicionan de forma atómica y síncrona la habitación a `PendingCleaning`, quedando visible de inmediato en la bandeja del Personal de limpieza.
- **SC-003**: El sistema rechaza el 100% de los intentos de Check-Out sobre habitaciones cuyo estado sea diferente de `Occupied` o sobre reservas que no estén en estado `CHECKED_IN`.
- **SC-004**: Ante indisponibilidad de Módulo 2 o Módulo 3, el 100% de los casos permiten completar la liberación física de la habitación a `PendingCleaning`, registrando el estado `PENDING` para regularización sin retener al huésped en recepción.
- **SC-005**: Cero cálculos manuales de tarifas, cero cargos por minibar/consumos locales y cero operaciones de cobro pasarela procesadas en Módulo 1.
