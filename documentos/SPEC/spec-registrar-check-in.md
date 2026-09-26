# Especificación de Funcionalidad: Registrar Check-In

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-19

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Admisión física de huéspedes y ocupación en inventario (Prioridad: P1)

Como Recepcionista, quiero registrar el Check-In presencial guiado por el flujo de 4 pasos de la interfaz (Validar reserva → Datos de huéspedes → Confirmación → Check-In completado), validando la reserva activa y capturando la identidad de los ocupantes, para que se cree formalmente la estancia física, la habitación transicione de forma síncrona a "Occupied" en el inventario y se sincronice el estado con Módulo 2 consolidando los datos migratorios de extranjeros, permitiendo la entrega ágil de la habitación.

**Por qué esta prioridad**: Es el flujo misional principal de admisión presencial en el hotel. Garantiza la creación formal de la entidad Estancia y que la habitación quede registrada inmediatamente como "Occupied" en el inventario de Módulo 1 (evitando sobreventas físicas o asignaciones cruzadas), sincronizando en una única petición con Módulo 2 la transición de la reserva a "IN_PROGRESS" junto con los datos migratorios SIRE si aplican.

**Prueba Independiente**: Se prueba de forma aislada iniciando sesión como Recepcionista y ejecutando el siguiente flujo: Primero, búsqueda y validación de una reserva en estado "ACTIVE" provista por Módulo 2 con habitación en estado "Reserved"; segundo, captura de ocupantes en el formulario (Huésped Titular fijo y Huésped adicional) verificando la alerta informativa migratoria si hay extranjeros; tercero, visualización del resumen de confirmación sin cálculos financieros; y cuarto, confirmación final verificando la pantalla de éxito con hora real de llegada (`checkInTime`), etiquetas "Habitación: Ocupada" y "Notificación a Módulo 2: Sincronizada", creación de la entidad Estancia y registro de RoomGuest.

**Escenarios de Aceptación**:

1. **Escenario**: Flujo completo de Check-In exitoso para huéspedes nacionales
   - **Dado** una reserva en Módulo 2 en estado "ACTIVE" cuya fecha actual coincide con la ventana contractual ("startDate" a "endDate"), con habitación asignada en estado físico "Reserved" en Módulo 1
   - **Cuando** el Recepcionista ingresa el código de reserva, documento del huésped o nombre del huésped para buscar la reserva,luego ingresa los Datos de huéspedes (nombre completo, tipo de documento, número de documento y nacionalidad) de los ocupantes dentro de la capacidad de la habitación, el sistema muestra el resumen de confirmación, y confirma la admisión
   - **Entonces** el sistema crea de forma síncrona la entidad Estancia (Stay) vinculada a la reserva y a la habitación, registra a los ocupantes como RoomGuest, transiciona la habitación a "Occupied" en el inventario, registra la hora real de llegada (`checkInTime`), despacha la notificación de check in al Módulo 2 actualizando la reserva a "IN_PROGRESS", y despliega la pantalla de éxito con las etiquetas de validación ("Habitación: Ocupada", "Notificación a Módulo 2: Sincronizada") y los botones para "Volver al inicio" o "Registrar otro Check-In".

2. **Escenario**: Check-in con huésped extranjero, alerta SIRE y notificación consolidada
   - **Dado** una reserva activa con habitación asignada en estado "Reserved"
   - **Cuando** en el paso 2 ("Datos de huéspedes") el Recepcionista registra a un ocupante con nacionalidad extranjera
   - **Entonces** el sistema despliega de inmediato una alerta visual informativa indicando que los datos migratorios se enviarán automáticamente al Módulo 2, asignando internamente el tipo de movimiento ("ENTRADA") y la fecha (`checkInTime`) sin solicitar estos datos en pantalla al Recepcionista
   - **Cuando** se confirma la admisión.
   - **Entonces** el sistema transiciona la habitación a "Occupied", crea la Estancia local y envía a Módulo 2 una única petición consolidada conteniendo la referencia de reserva y los datos migratorios del extranjero para su registro en `MigratoryMovement`, mostrando en el paso 4 la confirmación sincronizada.

3. **Escenario**: Visualización del resumen de confirmación sin cálculos financieros
   - **Dado** que se han validado la reserva y los datos de los ocupantes (pasos 1 y 2)
   - **Cuando** el Recepcionista avanza al paso 3 ("Confirmación")
   - **Entonces** el sistema presenta un resumen visual con la cantidad de huéspedes (ej. 2), la habitación asignada (ej. Hab. 304), el número de noches contratadas con sus fechas respectivas, y un cuadro informativo advirtiendo que la habitación pasará a estado "Occupied" y se notificará a Módulo 2, aclarando explícitamente que en este paso no se realizan cálculos financieros ni liquidaciones.

4. **Escenario**: Titular de la reserva inferido y fijo en el formulario de huéspedes
   - **Dado** una reserva activa obtenida en el paso 1 mediante "Consultar reservas"
   - **Cuando** el Recepcionista accede al paso 2 ("Datos de huéspedes")
   - **Entonces** el sistema infiere la titularidad comparando el documento ingresado contra el `guestRef` obtenido de Módulo 2 y presenta los campos del Huésped Titular (Nombre completo, Tipo de documento, Número de documento, Nacionalidad) como fijos y no removibles, permitiendo únicamente gestionar al Huésped 2 (o acompañantes adicionales) hasta la capacidad máxima de la habitación.

5. **Escenario**: Check-in tardío dentro del rango de vigencia de la reserva
   - **Dado** una reserva "ACTIVE" cuya fecha de inicio ("startDate") es anterior a la fecha actual, pero cuya fecha de fin ("endDate") es igual o posterior a la fecha actual
   - **Cuando** el Recepcionista realiza el Check-In
   - **Entonces** el sistema autoriza el flujo normalmente, creando la Estancia con la hora real de llegada (`checkInTime`) y ocupando físicamente la habitación.

---

### Historia de Usuario 2 - Bloqueo de admisiones inválidas e integridad de estados (Prioridad: P2)

Como Recepcionista, quiero que el sistema bloquee cualquier intento de Check-In que no cumpla las condiciones operativas, temporales o físicas, mostrando alertas claras y específicas en pantalla, para evitar ingresos no autorizados, asignaciones erróneas o inconsistencias en la máquina de estados.

**Por qué esta prioridad**: Asignar una habitación que no esté lista físicamente o sobre una reserva inactiva produce graves fallas operativas (huéspedes ingresando a habitaciones sucias, unidades dañadas o dobles admisiones). Esta historia consolida las validaciones preventivas del flujo.

**Prueba Independiente**: Se prueba intentando ejecutar el Check-In con reservas en estados no válidos ("CANCELLED", "IN_PROGRESS"), reservas fuera de fecha, habitaciones en estados distintos de "Reserved", o excediendo la capacidad máxima de la habitación, verificando que todas sean rechazadas con mensajes informativos controlados.

**Escenarios de Aceptación**:

1. **Escenario**: Rechazo de Check-In por habitación en estado Available (no reservada)
   - **Dado** que existe una reserva en estado "ACTIVE" en Módulo 2 cuya habitación asignada se encuentra físicamente en estado "Available" en Módulo 1 (es decir, sin haber pasado por el evento previo de reserva)
   - **Cuando** el Recepcionista intenta procesar el Check-In
   - **Entonces** el sistema rechaza la operación indicando que la habitación debe encontrarse en estado "Reserved" según la máquina de estados (documentos/SPEC/referencias/maquina-estados-habitacion.md) y no es posible admitir al huésped hasta que lo esté.

2. **Escenario**: Rechazo de Check-In por habitación en estado operativo no apto
   - **Dado** una reserva activa cuya habitación asignada se encuentra en Módulo 1 en estado "Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"
   - **Cuando** el Recepcionista intenta procesar el Check-In
   - **Entonces** el sistema bloquea la confirmación en el paso 1, informa en pantalla el estado físico real de la habitación y no permite avanzar al formulario de huéspedes ni a la ocupación.

3. **Escenario**: Rechazo por reserva en estado inválido en Módulo 2
   - **Dado** que la reserva consultada en Módulo 2 se encuentra en estado "CANCELLED", "PENDING", o ya en "IN_PROGRESS"
   - **Cuando** el Recepcionista consulta el código de reserva en el paso 1
   - **Entonces** el sistema rechaza la admisión informando que la reserva no admite Check-In en su estado actual.

4. **Escenario**: Rechazo por llegada anticipada (previo al inicio de estadía)
   - **Dado** una reserva en estado "ACTIVE" cuya fecha de inicio ("startDate") es estrictamente posterior a la fecha actual del sistema
   - **Cuando** el Recepcionista busca el código de reserva
   - **Entonces** el sistema bloquea el flujo informando que la estadía aún no inicia según el calendario contractual de la reserva.

5. **Escenario**: Rechazo por llegada posterior a la finalización de la estadía (estadía vencida)
   - **Dado** una reserva cuya fecha de finalización ("endDate") es anterior a la fecha actual
   - **Cuando** el Recepcionista intenta realizar el Check-In
   - **Entonces** el sistema bloquea la transacción informando que la reserva ha superado su fecha de vigencia.

6. **Escenario**: Rechazo por superar la capacidad máxima de personas de la habitación
   - **Dado** una habitación con capacidad máxima configurada de 2 personas
   - **Cuando** el Recepcionista intenta registrar 3 o más ocupantes en el paso 2 ("Datos de huéspedes")
   - **Entonces** el sistema bloquea el registro en el formulario, indicando que la cantidad de personas excede la capacidad física autorizada para la habitación.

---

### Casos Borde

- **Selección obligatoria ante múltiples reservas coincidentes por número de documento**: Si el Recepcionista realiza la búsqueda alternativa por "documentNumber" y esta retorna más de una reserva coincidente, el sistema lista las coincidencias mostrando referencia ("reservationRef"), fechas de estancia y estado ("status"), exigiendo que el Recepcionista seleccione explícitamente una reserva antes de avanzar al paso 2.
- **Falla o indisponibilidad de Módulo 2 al notificar la confirmación**: En condiciones normales la notificación a Módulo 2 se sincroniza de inmediato. Si Módulo 2 experimenta una caída de red o falta de respuesta al confirmar el paso 3, Módulo 1 mantiene en firme la creación de la Estancia y la transición síncrona de la habitación a "Occupied" para no detener la entrega física de la llave ni bloquear al huésped en recepción, registrando la notificación pendiente para reintento automático en segundo plano.
- **Comportamiento ante reenvío de notificación a Módulo 2**: Si una notificación de Check-In se reenvía sobre una reserva que ya figura en "IN_PROGRESS" en Módulo 2, este confirma la operación sin generar registros duplicados, actualizando únicamente el `MigratoryMovement` a `COMPLETE` si se incorporaron datos migratorios completos.
- **Ausencia de `checkInTime` válido al momento de conformar el paquete migratorio**: El sistema genera el `checkInTime` en el instante atómico de la confirmación local; no se despacha ninguna notificación migratoria a Módulo 2 sin una marca de tiempo válida.
- **Pendiente de confirmación contractual sobre `guestRef` de acompañantes extranjeros**: Al remitir los datos migratorios de un acompañante extranjero adicional que no sea el titular, el contrato de Módulo 2 (`spec (3).md`) asocia el `MigratoryMovement` a la reserva; queda registrado como pendiente coordinar si Módulo 2 creará un `Guest` automáticamente (creación o actualización automática) para el acompañante o si lo vinculará directamente a la reserva.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado "Recepcionista" iniciar y formalizar el registro de Check-In en Módulo 1.
- **FR-002**: El sistema DEBE estructurar el flujo de Check-In a través de 4 etapas:
  1. *Validar reserva*
  2. *Datos de huéspedes*
  3. *Confirmación*
  4. *Check-In completado*
- **FR-003**: En el paso 1 ("Validar reserva"), el sistema DEBE invocar obligatoriamente el caso de uso "Consultar reservas" (`<<includes>>`) utilizando como criterio principal el código de reserva (`reservationRef`), desplegando la tarjeta informativa con el código, rango de fechas de estadía, estado de la reserva (`ACTIVE`), huésped principal, canal de origen y detalles de la habitación asignada con su estado actual.
- **FR-004**: El sistema DEBE validar como precondición física obligatoria que la habitación asignada se encuentre en estado físico "Reserved". Si la habitación se encuentra en cualquiera de los otros 7 estados ("Available", "Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"), el sistema DEBE rechazar la admisión con un mensaje de error controlado, detallando el estado físico real.
- **FR-005**: El sistema DEBE validar que la fecha actual se encuentre dentro de la ventana contractual de la reserva ("startDate <= fechaActual <= endDate").
- **FR-006**: En el paso 2 ("Datos de huéspedes"), el sistema DEBE invocar obligatoriamente el caso de uso interno "Procesar datos de huéspedes" (`<<includes>>`) para capturar en formulario los campos: Nombre completo, Tipo de documento, Número de documento y Nacionalidad, tanto para el Huésped Titular como para el Huésped 2 (hasta la capacidad máxima de la habitación).
- **FR-007**: El sistema DEBE inferir la identidad del titular comparando el documento contra el `guestRef` obtenido de "Consultar reservas", presentándolo fijo y no editable en el formulario, permitiendo únicamente agregar o remover ocupantes adicionales sin exceder la capacidad física de la habitación (`maxCapacity`).
- **FR-008**: Cuando uno o más ocupantes ingresados tengan nacionalidad extranjera, el sistema DEBE activar la extensión "Enviar datos de huéspedes extranjeros" (`<<extend>>`), mostrando de inmediato una alerta visual informativa indicando que los datos migratorios se enviarán automáticamente al "Módulo 2 (SIRE)", asignando internamente el tipo de movimiento ("ENTRADA") y la fecha (`checkInTime`) sin solicitar estos datos en pantalla al Recepcionista.
- **FR-009**: En el paso 3 ("Confirmación"), el sistema DEBE presentar un resumen visual con la cantidad de huéspedes, la habitación asignada, el número de noches contratadas y sus fechas, e incluir un cuadro informativo advirtiendo que al confirmar la habitación pasará a estado "Occupied" y se notificará a Módulo 2, aclarando expresamente que NO se realizan cálculos financieros ni liquidaciones en esta etapa.
- **FR-010**: Al confirmar la admisión en el paso 3, el sistema DEBE ejecutar de forma síncrona y atómica:
  1. La creación de la entidad `Estancia` (Stay) vinculada a la reserva y a la habitación.
  2. La creación inmutable de los ocupantes como registros conceptuales `RoomGuest`.
  3. La transición del estado de la habitación de "Reserved" a "Occupied" en el inventario físico de Módulo 1.
  4. El registro de la hora exacta de llegada real (`checkInTime`) y el recepcionista responsable.
- **FR-011**: El sistema DEBE emitir una petición de notificación síncrona hacia el servicio de Módulo 2 con la `reservationRef` y, si existen huéspedes extranjeros, consolidar en la misma solicitud sus datos migratorios (tipo de movimiento "ENTRADA" y fecha `checkInTime`), solicitando la actualización de la reserva a estado "IN_PROGRESS".
- **FR-012**: En el paso 4 ("Check-In completado"), el sistema DEBE mostrar la pantalla de éxito con icono de verificación, registrar la hora exacta de llegada y exhibir las etiquetas de validación ("Habitación: Ocupada", "Notificación a Módulo 2: Sincronizada"), ofreciendo las opciones "Volver al inicio" y "Registrar otro Check-In".
- **FR-013**: Ante lentitud extrema, falta de respuesta o caída de red de Módulo 2 durante la confirmación, el sistema NO DEBE bloquear la entrega física de la habitación ni revertir el estado "Occupied"; la Estancia se consolida localmente y la notificación a Módulo 2 se programa para reintento en segundo plano.
- **FR-014**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, la referencia de la reserva, el identificador de la Estancia creada, el recepcionista responsable y la marca de tiempo de confirmación.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), fecha/hora de llegada real (`checkInTime`), fecha/hora de salida (`checkOutTime`), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **RoomGuest**: Entidad conceptual que representa a cada individuo físicamente alojado. Registro inmutable vinculado a la Estancia. Atributos clave: Nombre completo, tipo de documento de identidad, número de documento y nacionalidad.
- **Receptionist**: Actor de recepcionista que opera el flujo de registro de check-in y check-out.
- **Reservation**: Entidad conceptual de reserva que representa la reserva de una habitación. Atributos clave: ID único, referencia de reserva (`reservationId`), fecha de inicio (`startDate`), fecha de fin (`endDate`), estado (`status`), habitación asignada (`assignedRoomId`), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede completar el flujo de 4 pasos de Check-In en menos de 2 minutos para huéspedes nacionales y en menos de 3 minutos para huéspedes extranjeros.
- **SC-002**: El 100% de los Check-Ins confirmados transicionan de inmediato la habitación al estado "Occupied" en el inventario físico local.
- **SC-003**: El sistema rechaza el 100% de los intentos de Check-In sobre habitaciones cuyo estado sea diferente de "Reserved", incluyendo habitaciones en estado "Available".
- **SC-004**: El 100% de las admisiones con huéspedes extranjeros disparan la alerta visual de envío migratorio al Módulo 2 (SIRE) y consolidan los datos migratorios en la notificación sin solicitar campos adicionales en pantalla.
- **SC-005**: En el 100% de los casos de contingencia o caída externa de Módulo 2, el sistema consolida la ocupación física local de la habitación y programa el reintento de la notificación en segundo plano, evitando el bloqueo del huésped en el mostrador.
