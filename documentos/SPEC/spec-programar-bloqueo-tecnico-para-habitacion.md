# Especificación de Funcionalidad: Programar Bloqueo Técnico para Habitación

**Creado**: 2026-09-07  
**Actualizado**: 2026-09-19 (según `changelog-decisiones-modulo1.md`, punto 10 y glosario de 8 estados)  
**Módulo Propietario**: Módulo 1 — Gestión de Habitaciones e Inventario de Aforo, Check-In y Check-Out  
**Actor Principal**: Personal de mantenimiento (`MaintenanceStaff`)

---

## Use Case (Caso de Uso)

### Descripción del problema

El mantenimiento preventivo periódico de las habitaciones (revisión de aires acondicionados, fontanería, pintura, mobiliario o redes eléctricas) es esencial para preservar los estándares de calidad del hotel sin degradar la experiencia de los huéspedes. Sin embargo, ejecutar o programar una intervención técnica sobre una habitación sin verificar previamente los compromisos de reservas adquiridos ocasiona conflictos operativos graves, tales como reubicaciones forzosas de clientes, cancelaciones de última hora o habitaciones ocupadas durante visitas técnicas.

Para solucionar este problema, el **Módulo 1** provee la funcionalidad **"Programar bloqueo técnico para habitación"**. Mediante este caso de uso:
1. El **Personal de mantenimiento** agenda una ventana de intervención preventiva indicando obligatoriamente fecha/hora de inicio (`startDateTime`), fecha/hora de fin (`endDateTime`) y el motivo de los trabajos.
2. Como paso obligatorio de inclusión (`<<includes>>`), el sistema ejecuta **"Consultar reservas"** comunicándose con el **Módulo 2** para comprobar si existen reservas confirmadas o activas superpuestas con el rango temporal solicitado.
3. Si existe al menos una reserva en conflicto dentro de dicha ventana, el sistema **bloquea la programación** y no altera el estado de la habitación.
4. Si Módulo 2 confirma que no existen reservas en conflicto, el sistema registra el agendamiento del mantenimiento (`TechnicalBlockSchedule`). Si la ventana programada inicia de inmediato, transiciona de forma síncrona la habitación del estado **`Available`** a **`TechnicalBlock`**, excluyéndola inmediatamente de la disponibilidad comercial.
5. El sistema garantiza la integridad de la máquina de estados: una habitación solo puede entrar a `TechnicalBlock` si se encuentra físicamente en **`Available`**, y solo puede salir de él hacia `PendingCleaning` a través del caso de uso **"Confirmar reparación finalizada"**.

### Flujo de Usuario de Alto Nivel

1. El **Personal de mantenimiento** selecciona una habitación en el sistema e ingresa los parámetros de la intervención: fecha/hora de inicio, fecha/hora de finalización y motivo.
2. Módulo 1 verifica que la habitación se encuentre físicamente en estado **`Available`**. Si se encuentra en cualquier otro estado (`Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`), la operación es rechazada de inmediato.
3. Módulo 1 invoca de manera síncrona el caso de uso interno incluido **"Consultar reservas" (`<<includes>>`)**, enviando el identificador de la habitación y el rango de fechas al **Módulo 2**.
4. Módulo 2 responde confirmando si existen reservas vigentes superpuestas con el rango consultado.
5. Si existen reservas en conflicto, Módulo 1 rechaza la solicitud, informa el conflicto al usuario y no realiza cambios.
6. Si no existen reservas en conflicto:
   - Se persiste la entidad de agendamiento `TechnicalBlockSchedule`.
   - Si la ventana de mantenimiento es de ejecución inmediata (`startDateTime <= fechaActual`), el sistema cambia de forma atómica y síncrona el estado de la habitación de `Available` a **`TechnicalBlock`**.
   - Si la ventana es futura (`startDateTime > fechaActual`), se bloquea el aforo/disponibilidad para ese intervalo y la habitación transicionará a `TechnicalBlock` al iniciar la ventana programada.
7. El sistema confirma la operación y registra la auditoría correspondiente.

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Programar mantenimiento preventivo con ventana de fecha/hora (Prioridad: P1)

Como **Personal de mantenimiento**, quiero programar una habitación en "Bloqueo técnico" indicando una fecha/hora de inicio y una fecha/hora de finalización, de modo que quede excluida del inventario vendible durante esa ventana sin que sea asignada a huéspedes, y de modo que el sistema verifique previamente que no existan reservas en Módulo 2 que choquen con dicha ventana.

**Por qué esta prioridad**: El bloqueo técnico es el mecanismo formal que permite realizar mantenimiento preventivo sin afectar la experiencia del huésped. Registrar la ventana de fecha/hora exacta le sirve a Módulo 1 para la trazabilidad y el control físico, y permite a Módulo 2 anticipar y prevenir conflictos con el aforo y la asignación de reservas.

**Prueba Independiente**: Puede probarse de forma independiente iniciando sesión como Personal de mantenimiento, seleccionando una habitación en estado `Available`, indicando fecha/hora de inicio, fin y motivo del mantenimiento. Se verifica que:
- (a) El sistema consulta a Módulo 2 y comprueba que no hay reservas superpuestas con esa ventana.
- (b) La habitación transiciona a `TechnicalBlock` y se crea el registro en `TechnicalBlockSchedule`.
- (c) La habitación queda excluida de la disponibilidad para reservas comerciales durante dicho periodo.

**Escenarios de Aceptación**:

*Escenarios de Éxito (Happy Path)*

1. **Escenario**: Programación exitosa de bloqueo técnico sin conflicto de reservas
   - **Dado** una habitación se encuentra físicamente en estado `Available` y no tiene reservas asociadas en Módulo 2 dentro del rango indicado
   - **Cuando** el Personal de mantenimiento programa el bloqueo técnico indicando fecha/hora de inicio, fecha/hora de finalización y el motivo del mantenimiento
   - **Entonces** el sistema invoca "Consultar reservas" contra Módulo 2, confirma la ausencia de reservas superpuestas, registra la ventana en `TechnicalBlockSchedule`, cambia el estado de la habitación de `Available` a `TechnicalBlock`, y la excluye de los resultados de disponibilidad comercial para ese rango de fechas

2. **Escenario**: Rechazo por existir una reserva superpuesta con la ventana solicitada
   - **Dado** una habitación se encuentra en estado `Available`, pero Módulo 2 reporta al menos una reserva cuyas fechas se superponen con la ventana de mantenimiento solicitada
   - **Cuando** el Personal de mantenimiento intenta programar el bloqueo técnico
   - **Entonces** el sistema rechaza la operación, informa en pantalla que existe una reserva en conflicto con las fechas indicadas, y no transiciona el estado de la habitación

3. **Escenario**: Habitación en bloqueo técnico excluida de la disponibilidad comercial
   - **Dado** una habitación ha sido marcada en estado `TechnicalBlock` con una ventana de fecha/hora definida
   - **Cuando** recepción o los canales de venta consultan el inventario de habitaciones disponibles para reservas dentro de esa ventana
   - **Entonces** la habitación no aparece en los resultados de disponibilidad comercial

4. **Escenario**: Visualización de habitaciones en bloqueo técnico por mantenimiento
   - **Dado** existen habitaciones en estado `TechnicalBlock`
   - **Cuando** el Personal de mantenimiento consulta la lista o tablero de mantenimiento preventivo
   - **Entonces** el sistema muestra las unidades con el motivo, la fecha/hora de inicio, la fecha/hora de finalización y el usuario responsable

---

### Historia de Usuario 2 - Bloqueo preventivo de transiciones inválidas y validación de estados (Prioridad: P1)

Como **Personal de mantenimiento**, quiero que el sistema rechace cualquier intento de programar un bloqueo técnico sobre una habitación que no se encuentre en un estado operativo apto, para evitar transiciones inválidas en la máquina de estados y proteger la operación del hotel.

**Por qué esta prioridad**: Aplicar `TechnicalBlock` sobre una habitación que está ocupada por un huésped, reservada, en aseo, averiada o inactiva rompería la máquina de estados y causaría inconsistencias operativas críticas.

**Prueba Independiente**: Se prueba intentando programar o marcar como "Bloqueo técnico" habitaciones en cada uno de los 7 estados distintos de `Available` (`Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`), verificando que el sistema rechace la solicitud en todos los casos sin alterar los datos existentes.

**Escenarios de Aceptación**:

*Escenarios de Rechazo por Estado No Permitido*

5. **Escenario**: Intento de bloquear una habitación con reserva asignada (estado Reserved)
   - **Dado** una habitación se encuentra en estado `Reserved` (existe una reserva activa asignada por Módulo 2)
   - **Cuando** el Personal de mantenimiento intenta programar un bloqueo técnico sobre ella
   - **Entonces** el sistema rechaza la operación e informa que la habitación se encuentra en estado `Reserved` y no admite bloqueos técnicos directos

6. **Escenario**: Intento de bloquear una habitación ocupada por un huésped
   - **Dado** una habitación se encuentra en estado `Occupied`
   - **When** el Personal de mantenimiento intenta programar un bloqueo técnico
   - **Then** el sistema rechaza la solicitud e informa que la habitación se encuentra ocupada

7. **Escenario**: Intento de bloquear una habitación en ciclo de aseo
   - **Dado** una habitación se encuentra en estado `PendingCleaning` o `InCleaning`
   - **Cuando** el Personal de mantenimiento intenta programarla en bloqueo técnico
   - **Entonces** el sistema rechaza la acción e informa que la unidad se encuentra en proceso de limpieza

8. **Escenario**: Intento de bloquear una habitación inhabilitada por reparaciones o inactiva
   - **Dado** una habitación se encuentra en estado `DisabledForRepairs` (daño físico reportado) o `Inactive` (dada de baja)
   - **Cuando** el Personal de mantenimiento intenta programarla en bloqueo técnico
   - **Entonces** el sistema bloquea la operación indicando que la habitación no está disponible para mantenimiento preventivo

9. **Escenario**: Intento de bloquear una habitación que ya se encuentra en bloqueo técnico
   - **Dado** una habitación que ya posee el estado `TechnicalBlock`
   - **Cuando** el Personal de mantenimiento intenta programar otro bloqueo técnico sobre la misma unidad
   - **Entonces** el sistema detecta que la unidad ya está en `TechnicalBlock` y no aplica una nueva transición, solicitando editar la ventana existente si se requiere extender el plazo

---

### Casos Borde

- **Peticiones simultáneas de bloqueo técnico sobre la misma habitación**: Si dos miembros del Personal de mantenimiento intentan programar la misma habitación en el mismo instante, solo el primer intento procesado exitosamente cambia el estado a `TechnicalBlock`; el segundo es rechazado por colisión de concurrencia informando que la habitación ya cambió de estado.
- **Rango temporal inválido (`endDateTime <= startDateTime`)**: Si la fecha/hora de finalización es anterior o igual a la de inicio, el sistema rechaza el formulario y exige un intervalo cronológicamente válido.
- **Falla o indisponibilidad de Módulo 2 al consultar reservas (Política de Fallo Seguro)**: Si el Módulo 2 no responde o presenta timeout al invocar "Consultar reservas", el sistema **NO autoriza** el bloqueo técnico. Ante la imposibilidad de verificar la ausencia de conflictos, la operación se cancela con error controlado (no se asume disponibilidad por defecto).
- **Caída del sistema o desconexión durante el guardado**: La operación se ejecuta en una transacción atómica; ante una falla de red o de base de datos, se ejecuta rollback y la habitación permanece inalterada en estado `Available`.
- **Descubrimiento de daño físico grave durante el mantenimiento preventivo**: En estricto apego a la máquina de estados ([`maquina-estados-habitacion.md`](file:///c:/Users/equipo/Documents/Universidad/semestre6/Ingenieria%20de%20Software/HOSPITUA/hospitua-module-1/documentos/SPEC/referencias/maquina-estados-habitacion.md)), no existe una transición directa `TechnicalBlock → DisabledForRepairs`. La habitación debe concluir su intervención preventiva mediante "Confirmar reparación finalizada" (pasando a `PendingCleaning` para higienización) o, si debe ser inhabilitada por avería mayor, debe ser gestionada a través del flujo correspondiente una vez liberada, garantizando la trazabilidad formal de cada estado.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor autenticado **Personal de mantenimiento (`MaintenanceStaff`)** programar una habitación en estado de bloqueo técnico (`TechnicalBlock`).
- **FR-002**: Precondición física: El sistema DEBE validar que la habitación se encuentre estrictamente en estado **`Available`** dentro del catálogo de 8 estados de Módulo 1 antes de autorizar el bloqueo.
- **FR-003**: El sistema DEBE rechazar la programación de bloqueo técnico si la habitación se encuentra en cualquiera de los otros 7 estados operativos: `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`, informando el estado actual.
- **FR-004**: El sistema DEBE requerir obligatoriamente los siguientes parámetros para programar el bloqueo técnico: identificador de la habitación (`habitationId`), fecha/hora de inicio (`startDateTime`), fecha/hora de finalización (`endDateTime`) y motivo descriptivo del mantenimiento (`reason`).
- **FR-005**: El sistema DEBE validar que la fecha/hora de finalización sea estrictamente posterior a la fecha/hora de inicio (`endDateTime > startDateTime`).
- **FR-006**: Inclusión de consulta de reservas: Antes de confirmar el bloqueo, el sistema DEBE invocar obligatoriamente el caso de uso interno **"Consultar reservas" (`<<includes>>`)** comunicándose con el Módulo 2 para comprobar si existen reservas activas o confirmadas asociadas a la habitación en el rango temporal indicado.
- **FR-007**: El sistema DEBE rechazar la programación del bloqueo técnico si Módulo 2 reporta al menos una reserva cuyas fechas entren en conflicto o se superpongan con la ventana solicitada.
- **FR-008**: Fallo seguro ante contingencia de Módulo 2: Si la llamada a Módulo 2 falla o supera el tiempo de espera, el sistema DEBE abortar la programación del bloqueo técnico, informando al usuario que no se pudo verificar la disponibilidad de reservas y preservando la habitación en estado `Available`.
- **FR-009**: Transición y persistencia de agendamiento: Al confirmar que no existen reservas en conflicto, el sistema DEBE registrar la ventana en `TechnicalBlockSchedule` y cambiar de forma síncrona el estado de la habitación a **`TechnicalBlock`** (si la fecha de inicio es inmediata).
- **FR-010**: Exclusión de disponibilidad comercial: El sistema DEBE excluir la habitación en estado `TechnicalBlock` de los resultados de disponibilidad comercial utilizados para reservas o admisiones durante la vigencia de su ventana programada.
- **FR-011**: Regla de salida de la máquina de estados: El sistema DEBE permitir que una habitación en estado `TechnicalBlock` transicione fuera de dicho estado **únicamente hacia `PendingCleaning` a través del caso de uso "Confirmar reparación finalizada"**, prohibiendo cualquier salto directo a otros estados.
- **FR-012**: Auditoría y trazabilidad: El sistema DEBE registrar en la bitácora de auditoría el identificador de la habitación, el usuario responsable de mantenimiento, la marca de tiempo del agendamiento y la ventana de fechas de inicio y fin.

### Requisitos No Funcionales

- **NFR-001**: La consulta síncrona hacia Módulo 2 ("Consultar reservas") debe contar con un tiempo límite de espera (timeout) no superior a 3 segundos bajo condiciones normales de red.
- **NFR-002**: La persistencia local y actualización síncrona de la habitación a `TechnicalBlock` debe completarse en menos de 500 milisegundos tras la respuesta satisfactoria de Módulo 2.
- **NFR-003**: Todas las respuestas ante colisiones de fechas, estados incompatibles o indisponibilidad de Módulo 2 deben presentarse mediante errores estructurados **HTTP 400 (Bad Request)** o **HTTP 409 (Conflict)**, evitando la propagación de fallos de infraestructura no controlados **HTTP 500**.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room / Habitation**: Unidad física de alojamiento del hotel en Módulo 1.
  - *Atributos clave*: `habitationId` (UUID), `numberHabitation`, `floorWing`, `categoryHabitation`, `stateHabitation`.
  - *Comportamiento*: Transiciona de `Available` a `TechnicalBlock`.
  - *Catálogo oficial (8 estados)*: `Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`.
- **TechnicalBlockSchedule**: Registro de la ventana de mantenimiento preventivo planificado.
  - *Atributos*: `id` (UUID), `habitationId`, `startDateTime`, `endDateTime`, `reason`, `scheduledBy` (identificador del Personal de mantenimiento), `scheduledAt` (timestamp de registro).
- **MaintenanceStaff (Personal de mantenimiento)**: Actor autenticado de Módulo 1 encargado de programar, ejecutar y finalizar intervenciones preventivas y correctivas en la infraestructura física.
- **Reservation (Referencia de Módulo 2)**: Registro de reserva consultado externamente para descartar solapamientos de fechas antes de bloquear la unidad física.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las habitaciones marcadas en `TechnicalBlock` quedan excluidas de los resultados de disponibilidad comercial de forma inmediata durante su ventana programada.
- **SC-002**: El Personal de mantenimiento puede programar una habitación en bloqueo técnico en menos de 1 minuto bajo condiciones normales de conectividad con Módulo 2.
- **SC-003**: El sistema rechaza el 100% de los intentos de bloqueo técnico sobre habitaciones que no se encuentren estrictamente en estado `Available` (incluyendo rechazos inmediatos sobre habitaciones en estado `Reserved`).
- **SC-004**: El sistema rechaza el 100% de los intentos de programación que presenten superposición con reservas reportadas por Módulo 2 antes de aplicar cualquier cambio de estado.
- **SC-005**: El 100% de las operaciones exitosas quedan registradas con usuario responsable, motivo, fecha de creación y ventana temporal planificada.
