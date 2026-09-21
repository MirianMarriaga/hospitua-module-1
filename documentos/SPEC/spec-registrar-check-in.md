# Especificación de Funcionalidad: Registrar Check-In

**Creado**: 2026-09-19  
**Módulo Propietario**: Módulo 1 (Gestión de Habitaciones e Inventario de Aforo, Check-In y Check-Out)

---

## Use Case (Caso de Uso)

### Descripción del problema

La llegada del huésped a la recepción del hotel es el punto de partida de la experiencia de alojamiento. El hotel requiere formalizar la admisión física en sitio de manera ágil y segura, garantizando la integridad de su inventario de habitaciones. 

Anteriormente, la gestión de Check-In se encontraba desacoplada del inventario físico o atribuida a otros módulos, lo que generaba riesgos de sobreventa física, retrasos en la asignación de cuartos y desalineación entre el estado real de la habitación y la reserva. Con la arquitectura unificada, el **Módulo 1** asume la responsabilidad directa de la admisión física (Check-In).

Para completar una admisión válida:
1. El sistema debe validar que la reserva exista en el **Módulo 2**, se encuentre en estado `ACTIVE`, y que la fecha actual coincida con la ventana de estadía pactada.
2. La unidad física asignada (`Room`/`Habitation`) debe encontrarse en estado **`Reserved`** (marcada previamente por Módulo 2 vía evento), dentro del catálogo oficial de 8 estados de Módulo 1.
3. El sistema debe capturar y validar localmente las identidades de los huéspedes en recepción, respetando la capacidad máxima de la habitación.
4. Cuando se presenten huéspedes de nacionalidad extranjera, Módulo 1 debe capturar sus datos migratorios y transferirlos inmediatamente al Módulo 2 para su incorporación en la validación migratoria (`MigratoryValidation` / reporte SIRE). Módulo 1 no gestiona el reporte directo ante Migración Colombia ni duplica validaciones normativas.
5. Al confirmarse el Check-In, la transición de la habitación a **`Occupied`** se ejecuta de manera **interna y 100% síncrona** en el Módulo 1.
6. La actualización de la reserva a `CHECKED_IN` en el Módulo 2 es **externa y asíncrona**; ningún fallo de comunicación con Módulo 2 bloquea el ingreso físico del huésped (se gestiona con estado `PENDING` para reintento).
7. **Regla estricta de facturación**: Durante el Check-In **no interviene el Módulo 3 ni se realiza ninguna llamada de liquidación**. No se genera prefactura, no se fija IVA, ni se abre ninguna cuenta preliminar (`Settlement`); el 100% de los aspectos financieros se liquidan exclusivamente al momento del Check-Out.

### Flujo de Usuario de Alto Nivel

1. El **Recepcionista** inicia el registro de Check-In en la pantalla de Módulo 1 ingresando el código o referencia de la reserva (`reservationRef`).
2. Módulo 1 ejecuta el caso de uso interno incluido **"Consultar reservas" (`<<includes>>`)**, comunicándose con Módulo 2 para verificar que la reserva exista, esté en estado `ACTIVE`, que la fecha actual esté dentro de la ventana de estadía (`startDate` a `endDate`), y extraer la habitación asignada y el aforo contratado.
3. Módulo 1 verifica localmente que la habitación física asignada se encuentre en estado **`Reserved`**.
4. Módulo 1 ejecuta el caso de uso interno incluido **"Procesar datos de huéspedes" (`<<includes>>`)**, capturando las identificaciones de los ocupantes y verificando que no excedan la capacidad máxima de la habitación.
5. Si alguno de los huéspedes es extranjero, se activa el caso de uso extensor **"Enviar datos de huéspedes extranjeros" (`<<extend>>`)**, remitiendo inmediatamente dichos datos migratorios a Módulo 2.
6. El sistema presenta al Recepcionista una pantalla de resumen con los datos de la reserva, habitación, fechas y huéspedes para su confirmación visual explícita.
7. Tras la aprobación del Recepcionista, Módulo 1 transiciona de forma atómica, interna y síncrona el estado de la habitación de `Reserved` a **`Occupied`**.
8. Módulo 1 envía una notificación externa y asíncrona a Módulo 2 para actualizar la reserva a estado **`CHECKED_IN`**, registrando la hora de llegada real (`arrivalTime`) y el identificador del recepcionista.

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Admisión física de huéspedes y ocupación en inventario (Prioridad: P1)

Como **Recepcionista**, quiero registrar el check-in de los huéspedes validando su reserva activa y sus datos de identidad, para que la habitación asignada transicione inmediatamente a "Occupied" en el inventario físico y se notifique a Módulo 2, permitiendo la entrega de la habitación sin demoras.

**Por qué esta prioridad**: Es el flujo misional principal de admisión en el hotel. Garantiza que la habitación quede registrada como ocupada de forma síncrona en el inventario de Módulo 1, previniendo sobreventas físicas o asignaciones cruzadas, mientras envía los datos requeridos a Módulo 2 para trazabilidad de la reserva y cumplimiento legal.

**Prueba Independiente**: Puede probarse de forma aislada iniciando sesión como Recepcionista, seleccionando una reserva en estado `ACTIVE` provista por Módulo 2 cuya habitación esté en estado `Reserved`, completando los datos de huéspedes (nacionales y extranjeros) y confirmando el registro. Se verifica que la habitación pase a `Occupied` en Módulo 1 de forma síncrona, que se envíen los datos migratorios si aplica, y que se despache la notificación asíncrona a Módulo 2, sin realizar ninguna interacción con Módulo 3.

**Escenarios de Aceptación**:

*Escenarios de Éxito (Happy Path)*

1. **Escenario**: Check-in exitoso de huésped nacional con habitación preasignada en estado Reserved
   - **Dado** que existe una reserva en Módulo 2 en estado `ACTIVE` para un huésped nacional, cuya fecha actual coincide con la ventana de estadía (`startDate` a `endDate`), y tiene asignada una habitación que en Módulo 1 está en estado `Reserved`
   - **Cuando** el Recepcionista busca la reserva, verifica la identidad de los huéspedes presentes y confirma el Check-In en pantalla
   - **Entonces** el sistema transiciona de forma síncrona e interna el estado de la habitación a `Occupied`, registra la hora de llegada real (`arrivalTime`) y el recepcionista responsable, y envía una notificación asíncrona a Módulo 2 para marcar la reserva como `CHECKED_IN`, sin realizar ninguna llamada ni cálculo financiero con Módulo 3

2. **Escenario**: Rechazo de Check-In por habitación en estado Available (no reservada)
   - **Dado** que existe una reserva en estado `ACTIVE` en Módulo 2 cuya habitación asignada se encuentra físicamente en estado `Available` en Módulo 1 (es decir, sin haber pasado por el evento de `Reserved`)
   - **Cuando** el Recepcionista intenta procesar el Check-In
   - **Entonces** el sistema rechaza la operación indicando que la habitación no ha sido marcada como `Reserved` por Módulo 2 y no es posible admitir al huésped hasta que lo esté

3. **Escenario**: Check-in exitoso con huésped extranjero y transferencia migratoria
   - **Dado** que la reserva corresponde a uno o más huéspedes de nacionalidad extranjera
   - **Cuando** el Recepcionista ingresa los datos de los huéspedes en "Procesar datos de huéspedes", el sistema activa la extensión "Enviar datos de huéspedes extranjeros" capturando tipo de documento, número, nacionalidad, tipo de visa y fechas de estancia
   - **Entonces** Módulo 1 envía inmediatamente dicho paquete de datos a Módulo 2 para su registro en `MigratoryValidation` (SIRE), completa la transición física de la habitación a `Occupied` y notifica a Módulo 2 el estado `CHECKED_IN`

4. **Escenario**: Check-in tardío dentro de la ventana de estadía válida
   - **Dado** una reserva `ACTIVE` cuya fecha de inicio (`startDate`) es anterior a la fecha de hoy, pero cuya fecha de finalización (`endDate`) es igual o posterior a hoy
   - **Cuando** el Recepcionista inicia y confirma el Check-In
   - **Entonces** el sistema autoriza la operación normalmente, registrando la hora de llegada real (`arrivalTime`) de hoy y transicionando la habitación a `Occupied`

---

### Historia de Usuario 2 - Bloqueo de admisiones inválidas e integridad de estados (Prioridad: P1)

Como **Recepcionista**, quiero que el sistema bloquee cualquier intento de Check-In que no cumpla las condiciones operativas o físicas, mostrando alertas claras y específicas, para evitar ingresos no autorizados, asignaciones erróneas o inconsistencias en la máquina de estados.

**Por qué esta prioridad**: Asignar una habitación que no esté lista físicamente o sobre una reserva inactiva produce graves fallas operativas (huéspedes ingresando a habitaciones sucias, unidades dañadas o dobles admisiones). Esta historia consolida las validaciones preventivas del flujo.

**Prueba Independiente**: Se prueba intentando ejecutar el Check-In con reservas en estados no válidos (`CANCELLED`, `CHECKED_IN`), reservas fuera de fecha, habitaciones en estados distintos de `Reserved`, o excediendo la capacidad máxima de la habitación, verificando que todas sean rechazadas con mensajes informativos controlados.

**Escenarios de Aceptación**:

*Escenarios de Rechazo y Control de Integridad*

5. **Escenario**: Rechazo de Check-In por habitación en estado operativo no apto
   - **Dado** una reserva en estado `ACTIVE` cuya habitación asignada se encuentra en Módulo 1 en estado `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`
   - **Cuando** el Recepcionista intenta procesar el Check-In
   - **Entonces** el sistema bloquea la confirmación, informa en pantalla el estado físico real de la habitación (por ejemplo: "La habitación 302 se encuentra en limpieza") y no permite la transición a `Occupied`

6. **Escenario**: Rechazo por reserva en estado inválido en Módulo 2
   - **Dado** que la reserva consultada en Módulo 2 se encuentra en estado `CANCELLED` o ya en `CHECKED_IN`
   - **Cuando** el Recepcionista intenta iniciar el Check-In
   - **Entonces** el sistema rechaza la operación informando que la reserva no se encuentra activa para admisión

7. **Escenario**: Rechazo por llegada anticipada (previo al inicio de estadía)
   - **Dado** una reserva en estado `ACTIVE` cuya fecha de inicio (`startDate`) es estrictamente posterior a la fecha actual del sistema
   - **Cuando** el Recepcionista intenta procesar el Check-In
   - **Entonces** el sistema bloquea la transacción informando que la estadía aún no inicia según el calendario contractual de la reserva

8. **Escenario**: Rechazo por llegada posterior a la finalización de la estadía (estadía vencida)
   - **Dado** una reserva cuya fecha de finalización (`endDate`) es anterior a la fecha actual
   - **Cuando** el Recepcionista intenta realizar el Check-In
   - **Entonces** el sistema bloquea la transacción informando que la reserva ha superado su fecha de vigencia

9. **Escenario**: Rechazo por superar la capacidad máxima de personas de la habitación
   - **Dado** una habitación con capacidad máxima configurada de 2 personas (`Room.maxCapacity = 2`)
   - **Cuando** el Recepcionista intenta procesar el Check-In ingresando 3 o más huéspedes
   - **Entonces** el sistema bloquea el registro en el paso de "Procesar datos de huéspedes", indicando que la cantidad de personas excede la capacidad física autorizada para la habitación

---

### Casos Borde

- **Falla o indisponibilidad de Módulo 2 al notificar la confirmación**: Si Módulo 2 no responde al emitir la notificación asíncrona de `CHECKED_IN`, Módulo 1 mantiene firme la transición síncrona de la habitación a `Occupied`, persiste el registro de Check-In localmente con estado de sincronización `PENDING`, y programa reintentos en segundo plano sin detener la entrega de la llave ni bloquear al huésped en recepción.
- **Peticiones simultáneas de Check-In sobre la misma habitación**: Ante dos solicitudes concurrentes para la misma habitación física, solo la primera solicitud en obtener el bloqueo a nivel de base de datos ejecuta la transición a `Occupied`; la segunda es rechazada inmediatamente con aviso de colisión de estado ("La habitación ya se encuentra en estado Occupied").
- **Huésped extranjero con datos migratorios incompletos**: Si durante la captura faltan campos obligatorios (número de pasaporte, país de nacionalidad, visa o vigencia), el sistema resalta los campos faltantes e impide avanzar hasta que la información requerida por SIRE esté debidamente diligenciada.
- **Habitación asignada en estado no apto al momento de la llegada**: Si la habitación asociada se encuentra en `Available`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs` o `TechnicalBlock`, el sistema prohíbe el Check-In. En el caso de `Available`, la habitación aún no fue marcada como `Reserved` por Módulo 2, por lo que el ingreso no está autorizado. En los demás casos, el Recepcionista debe gestionar la reasignación o aguardar a que el personal correspondiente culmine la tarea.
- **Fallo de conectividad o energía durante la confirmación local**: La operación se ejecuta bajo una transacción atómica; si el guardado local se interrumpe, se revierte por completo (rollback) y la habitación permanece en su estado previo (`Reserved`).
- **Política de No-Show y reservas no reclamadas**: [TBD — Regla y política gobernada exclusivamente por Módulo 2]. Módulo 1 no aplica cancelaciones automáticas ni políticas de liberación por no-show.
- **Manejo de estado EXPIRED para reservas**: [TBD — Lógica y estados internos gobernados exclusivamente por Módulo 2].

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado **Recepcionista** iniciar y formalizar el registro de Check-In en el Módulo 1.
- **FR-002**: El sistema DEBE invocar obligatoriamente el caso de uso **"Consultar reservas" (`<<includes>>`)** contra el Módulo 2 para verificar la existencia de la reserva, validar que su estado sea `ACTIVE`, obtener la ventana de fechas (`startDate`, `endDate`), la cantidad de huéspedes y la habitación preasignada.
- **FR-003**: El sistema DEBE validar que la fecha actual del sistema se encuentre dentro de la ventana de estadía de la reserva (`startDate <= fechaActual <= endDate`), bloqueando la admisión si la fecha actual es anterior a `startDate` o posterior a `endDate`.
- **FR-004**: El sistema DEBE invocar obligatoriamente el caso de uso interno **"Procesar datos de huéspedes" (`<<includes>>`)** para capturar los datos de identidad de los huéspedes presentes y validar que la cantidad de huéspedes no supere la capacidad máxima (`Room.maxCapacity`) de la habitación.
- **FR-005**: Cuando uno o más huéspedes sean de nacionalidad extranjera, el sistema DEBE extender el flujo mediante el caso de uso **"Enviar datos de huéspedes extranjeros" (`<<extend>>`)**, capturando documento, nacionalidad, tipo de visa y fechas de estancia, y remitiendo inmediatamente este paquete al Módulo 2 para alimentar `MigratoryValidation` (SIRE).
- **FR-006**: Precondición física: La habitación asignada DEBE encontrarse en estado **`Reserved`** según el catálogo oficial de 8 estados de `Habitation.stateHabitation` en Módulo 1. Cualquier otro estado (incluido `Available`) impide la operación.
- **FR-007**: El sistema DEBE rechazar la operación de Check-In si la habitación se encuentra en cualquiera de los otros 7 estados operativos: `Available`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`, informando al usuario el estado real de la habitación.
- **FR-008**: Transición interna y síncrona: Al recibir la confirmación explícita del Recepcionista, el sistema DEBE transicionar de forma atómica y síncrona el estado de la entidad `Room` de `Reserved` a **`Occupied`**.
- **FR-009**: Notificación externa y asíncrona: El sistema DEBE emitir una notificación asíncrona hacia el Módulo 2 informando la confirmación del Check-In con el identificador de la reserva, la hora real de llegada (`arrivalTime`) y el identificador del Recepcionista, solicitando actualizar la reserva a estado `CHECKED_IN`.
- **FR-010**: Resiliencia operativa: Si la notificación hacia Módulo 2 falla por indisponibilidad externa o caída de red, el sistema DEBE persistir la notificación con estado `PENDING` para reintento en segundo plano, sin revertir la ocupación física de la habitación ni bloquear la admisión del huésped.
- **FR-011**: Ausencia de llamadas de liquidación: El flujo de Check-In en Módulo 1 **NO DEBE** invocar al Módulo 3, ni abrir cuentas de liquidación (`Settlement`), ni fijar porcentajes de IVA, ni generar borradores de prefactura; todos los procesos financieros quedan postergados al Check-Out.
- **FR-012**: Trazabilidad y auditoría: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, la referencia de la reserva (`reservationRef`), el usuario del Recepcionista responsable y la marca de tiempo exacta de la confirmación.

### Requisitos No Funcionales

- **NFR-001**: La actualización síncrona e interna del estado de la habitación a `Occupied` en Módulo 1 debe completarse en un tiempo inferior a 500 milisegundos tras la confirmación del Recepcionista.
- **NFR-002**: Toda validación fallida (reserva inactiva, habitación no apta, datos incompletos) debe responderse mediante mensajes de error estructurados **HTTP 400 (Bad Request)** amigables, impidiendo la generación de errores no controlados **HTTP 500 (Internal Server Error)**.
- **NFR-003**: El flujo de consulta de reserva, validación de huéspedes y confirmación debe estar consolidado en una interfaz intuitiva de paso único para minimizar los tiempos de atención en recepción.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room / Habitation**: Representa la unidad física de hospedaje gestionada centralmente por el Módulo 1.
  - *Atributos involucrados*: `habitationId` (UUID), `numberHabitation`, `floorWing`, `categoryHabitation` (Sencilla, Doble, Suite, Boutique), `maxCapacity`, `baseRate`, y `stateHabitation`.
  - *Valores de estado vigentes (8)*: `Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`.
  - *Comportamiento*: Transiciona de manera síncrona de `Reserved` a `Occupied`.
- **CheckIn**: Representa la transacción operativa de admisión física registrada en el Módulo 1.
  - *Atributos clave*: `id` (UUID), `reservationRef`, `habitationId`, `arrivalTime` (timestamp real de entrada), `processedBy` (identificador del Recepcionista), `syncStatusM2` (`SYNCED` | `PENDING`).
- **Guest (Huésped)**: Datos de las personas físicas alojadas en la habitación, procesados localmente.
  - *Atributos clave*: `documentType`, `documentNumber`, `fullName`, `isForeign`, `nationality`, `visaType`.
- **Reservation (Referencia de Módulo 2)**: Registro externo de la reserva consultado por Módulo 1.
  - *Atributos consumidos/notificados*: `reservationRef`, `startDate`, `endDate`, `guestCount`, `assignedHabitation`, `state` (`ACTIVE` -> notificado a `CHECKED_IN`).
- **Receptionist (Recepcionista)**: Usuario autenticado del hotel que ejecuta y autoriza la admisión en el front-desk.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede completar el flujo de Check-In estándar en menos de 2 minutos para huéspedes nacionales y en menos de 3 minutos para huéspedes extranjeros.
- **SC-002**: El 100% de los Check-Ins confirmados transicionan de forma síncrona e inmediata la habitación al estado `Occupied`, quedando reflejada como no disponible en el inventario general.
- **SC-003**: El sistema rechaza el 100% de los intentos de Check-In sobre habitaciones cuyo estado sea diferente de `Reserved`, incluyendo habitaciones en estado `Available`.
- **SC-004**: En el 100% de los casos de contingencia o caída de Módulo 2, el sistema permite la ocupación física local, marca la notificación externa como `PENDING` y previene el bloqueo del huésped en el mostrador.
- **SC-005**: Cero llamadas de liquidación, cero prefacturas y cero intervenciones de Módulo 3 ejecutadas durante el Check-In.

