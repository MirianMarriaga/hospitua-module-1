# Especificación del Caso de Uso: Programar Bloqueo Técnico para Habitación

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Programación de bloqueo preventivo sobre habitación disponible (Prioridad: P1)

Como miembro del personal de mantenimiento quiero poder programar mantenimientos preventivos en habitaciones, para así formalizar mi participación en labores definidas para mi área e indicar de forma documentada la necesidad de intervenir y realizar reparaciones, reemplazos o remociones de manera preventiva sobre una habitación de forma temporal.

**Por qué esta prioridad**: Permite ejecutar inspecciones y mantenimientos preventivos protegiendo los estándares de infraestructura del hotel, evitando la asignación de unidades habitacionales en posible mal estado y por consiguiente evitar a los huéspedes malas experiencias.

**Prueba independiente**: Puede ser probada indicando que se programará un mantenimiento en una habitación que no esté dada de baja (`Inactive`), seleccionando dicha habitación, ingresando la justificación técnica del mantenimiento y un rango temporal válido, verificando que realiza validaciones relacionadas a reservas y mantenimientos futuros previamente a la programación, confirmando la operación y verificando que se registre el `TechnicalBlockReport` y que, al llegar la fecha de inicio (o de inmediato si la fecha de inicio es la fecha actual), el estado de la habitación cambie a `TechnicalBlock` (si está en `Available`) y figure así en el panel de mantenimiento.

**Escenarios de aceptación**:

1. **Escenario**: Bloqueo técnico exitoso con inicio en la fecha actual
   - **Dado que** Existe una habitación listada que se encuentra en estado `Available`
   - **Cuando** El miembro del personal de mantenimiento programa el bloqueo técnico ingresando una justificación válida, la fecha actual como fecha de inicio y una fecha estimada de finalización válida
   - **Entonces** El sistema realiza consultas sobre las reservas y mantenimientos futuros vinculados a la habitación, comprueba que no existen conflictos y, en la misma operación, registra el `TechnicalBlockReport` en estado `Applied` y transiciona la habitación a `TechnicalBlock`.

2. **Escenario**: Rechazo de bloqueo técnico por conflicto con reservas existentes
   - **Dado que** Existe una habitación en estado `Available` que registra una reserva programada, según la consulta de reservas al sistema de reservas, dentro del rango definido para la intervención
   - **Cuando** El miembro del personal de mantenimiento intenta programar el mantenimiento
   - **Entonces** El sistema realiza consultas sobre las reservas futuras vinculadas a la habitación, rechaza el cambio de estado, mantiene la habitación en `Available` y alerta sobre el conflicto para reprogramar el mantenimiento.

3. **Escenario**: Rechazo de bloqueo técnico por conflicto con mantenimientos existentes en el rango temporal definido
   - **Dado que** Existe una habitación en estado `Available` que registra un mantenimiento preventivo ya programado en la tabla de mantenimientos dentro del rango definido para la intervención
   - **Cuando** El miembro del personal de mantenimiento intenta programar el mantenimiento
   - **Entonces** El sistema realiza consultas sobre los mantenimientos futuros vinculados a la habitación, rechaza el cambio de estado, mantiene la habitación en `Available` y alerta sobre el conflicto para reprogramar el mantenimiento.

4. **Escenario**: Intento de programación con justificación técnica vacía
   - **Dado que** Existe una habitación en estado `Available`
   - **Cuando** El miembro del personal de mantenimiento intenta confirmar la programación dejando el campo de justificación vacío o con caracteres en blanco
   - **Entonces** El sistema bloquea el diligenciamiento, no ejecuta la consulta de reservas y exige documentar el motivo técnico preventivo.

5. **Escenario**: Intento de programación con fechas o rango temporal sin sentido
   - **Dado que** Existe una habitación en estado `Available`
   - **Cuando** El miembro del personal de mantenimiento intenta confirmar la programación con una fecha de inicio pasada, una fecha estimada de finalización anterior a la de inicio, una fecha inexistente en el calendario (ej. 31-02-2026) o un rango que excede los límites permitidos
   - **Entonces** El sistema bloquea la confirmación, no ejecuta las consultas de reservas ni mantenimientos, mantiene la habitación en `Available` y muestra un error indicando la regla temporal incumplida.

6. **Escenario**: Programación exitosa con inicio en una fecha futura
   - **Dado que** Existe una habitación en estado `Available` sin reservas ni mantenimientos en el rango definido
   - **Cuando** El miembro del personal de mantenimiento programa el bloqueo técnico con una fecha de inicio posterior a la fecha actual
   - **Entonces** El sistema registra el `TechnicalBlockReport` en estado `Scheduled`, mantiene la habitación en `Available` y la muestra en el panel de mantenimiento con el rango del mantenimiento programado

7. **Escenario**: Aplicación autónoma del bloqueo en la fecha de inicio
   - **Dado que** Existe un `TechnicalBlockReport` en estado `Scheduled` cuya fecha de inicio es la fecha actual y la habitación está en `Available`
   - **Cuando** Se ejecuta el trabajo autónomo, tras la ingesta de la lista diaria de reservas y, en todo caso, a las 01:00
   - **Entonces** El sistema transiciona la habitación a `TechnicalBlock` y marca el informe como `Applied`

8. **Escenario**: Habitación no disponible en la fecha de inicio
   - **Dado que** Existe un `TechnicalBlockReport` en estado `Scheduled` cuyo rango contiene la fecha actual y la habitación está en un estado distinto de `Available` (ej. `Occupied` o `Reserved`)
   - **Cuando** Se ejecuta el trabajo autónomo
   - **Entonces** El sistema no modifica el estado de la habitación, mantiene el informe en `Scheduled` y lo aplica cuando la habitación quede `Available` al confirmarse el fin de su limpieza dentro del rango (FR-002) o, si no, lo vuelve a evaluar en la siguiente ejecución del trabajo autónomo; si la fecha actual supera la fecha estimada de finalización sin haberse aplicado, marca el informe como `Expired` (caducado)

9. **Escenario**: Programación con inicio futuro sobre una habitación que hoy no está disponible
   - **Dado que** Existe una habitación en estado `Occupied` (o `Reserved`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs` o `TechnicalBlock`) sin reservas ni mantenimientos en el rango definido
   - **Cuando** El miembro del personal de mantenimiento programa el bloqueo técnico con una fecha de inicio posterior a la fecha actual
   - **Entonces** El sistema registra el `TechnicalBlockReport` en estado `Scheduled` sin modificar el estado de la habitación; el trabajo autónomo lo aplicará en su fecha de inicio si la habitación está en `Available` (escenarios 7 y 8)

10. **Escenario**: Rechazo por estado de la habitación
   - **Dado que** Existe una habitación en estado `Inactive`, o una habitación en un estado distinto de `Available` y el bloqueo inicia en la fecha actual
   - **Cuando** El miembro del personal de mantenimiento intenta programar el mantenimiento
   - **Entonces** El sistema rechaza la operación sin consultar reservas ni mantenimientos, no modifica la habitación e informa su estado actual

11. **Escenario**: Aviso a Módulo 2 al registrar la programación
   - **Dado que** Existe una habitación en estado `Available` sin reservas ni mantenimientos en el rango definido
   - **Cuando** El miembro del personal de mantenimiento programa con éxito el bloqueo técnico, con inicio en la fecha actual o en una fecha futura
   - **Entonces** El sistema, dentro de la misma operación, avisa a Módulo 2 del informe de bloqueo técnico con la categoría de la habitación y su rango de fechas (inicio y fin estimado, ambos incluidos); si la programación se rechaza o se revierte, no envía ningún aviso (FR-019)

---

### Historia de Usuario 2 - Aviso diario a Módulo 2 de habitaciones fuera de servicio (Prioridad: P2)

Como sistema (trabajo autónomo) quiero avisar cada día a las 00:00 a Módulo 2 de las habitaciones que siguen fuera de servicio, para así evitar que venda la noche actual en sus canales externos antes de su recálculo diario.

**Por qué esta prioridad**: Un reporte de daño no tiene fecha de fin y un informe `Applied` vencido ya no bloquea fechas posteriores a su fecha estimada de fin (*Consultar Información de Mantenimientos*, FR-010); sin este aviso, Módulo 2 podría vender la noche actual de una habitación que sigue fuera de servicio.

**Prueba independiente**: Puede ser probada ejecutando el aviso diario con una fecha fija y verificando que se avisa a Módulo 2 de un mantenimiento con inicio y fin en la fecha actual por cada habitación en `DisabledForRepairs` y por cada informe `Applied` vencido, ninguno por un informe `Applied` vigente o `Scheduled`, y que no cambia el estado de ninguna habitación ni de ningún informe.

**Escenarios de aceptación**:

12. **Escenario**: Aviso diario por habitaciones fuera de servicio
   - **Dado que** La habitación "101" está en `DisabledForRepairs` y la habitación "105" está en `TechnicalBlock` con su informe `Applied` cuya fecha estimada de fin es anterior a la fecha actual
   - **Cuando** Se ejecuta el aviso diario de las 00:00
   - **Entonces** El sistema avisa a Módulo 2 de un mantenimiento con inicio y fin en la fecha actual para la habitación "101", con el identificador de su último reporte de daño, y para la habitación "105", con el identificador del informe, sin cambiar el estado de las habitaciones ni de los informes (FR-024)

13. **Escenario**: Sin aviso diario para bloqueos vigentes o programados
   - **Dado que** La habitación "110" está en `TechnicalBlock` con su informe `Applied` cuya fecha estimada de fin es igual o posterior a la fecha actual, y la habitación "112" tiene un informe `Scheduled`
   - **Cuando** Se ejecuta el aviso diario de las 00:00
   - **Entonces** El sistema no avisa a Módulo 2 por esas habitaciones, porque Módulo 2 ya recibió su rango al registrarse la programación (FR-023, FR-024)

---

### Historia de Usuario 3 - Aplicación del bloqueo programado al confirmar el fin de limpieza (Prioridad: P2)

Como sistema, al confirmar el personal de limpieza el fin de limpieza de una habitación, quiero aplicar el bloqueo técnico programado cuyo rango contiene la fecha actual, para así que el personal de mantenimiento pueda iniciar las reparaciones ese mismo día sin esperar a la siguiente ejecución del trabajo autónomo.

**Por qué esta prioridad**: Si la habitación no está en `Available` cuando corre el trabajo autónomo (por ejemplo, porque un huésped sale ese día), el bloqueo no se aplica (FR-016); sin esta aplicación, la habitación quedaría en `Available` hasta el día siguiente, acortando el mantenimiento programado.

**Prueba independiente**: Puede ser probada con una habitación en `InCleaning`, sin llegada pendiente hoy y con un `TechnicalBlockReport` en `Scheduled` cuyo rango contiene la fecha actual, confirmando el fin de limpieza sin describir un daño y verificando que la habitación transiciona a `Available` y seguidamente a `TechnicalBlock` (`InCleaning` → (`Available` → `TechnicalBlock`)) en la misma transacción, que el informe pasa a `Applied` y que la habitación figura en el panel de mantenimiento.

**Escenarios de aceptación**:

14. **Escenario**: Aplicación del bloqueo al confirmar el fin de limpieza
   - **Dado que** La habitación "101" está en `InCleaning`, sin llegada pendiente hoy, y tiene un `TechnicalBlockReport` en `Scheduled` cuyo rango contiene la fecha actual
   - **Cuando** El miembro del personal de limpieza confirma el fin de limpieza sin describir un daño (*Confirmar Fin de Limpieza de Habitación*, FR-020)
   - **Entonces** El sistema, en la misma transacción, transiciona la habitación de `Available` a `TechnicalBlock`, marca el informe como `Applied` y registra la transición en `RoomStateHistory` con `ActorId` nulo y `SourceFlow` = *Programar Bloqueo Técnico para Habitación* (FR-002, FR-018)

15. **Escenario**: Daño o llegada pendiente al confirmar el fin de limpieza
   - **Dado que** La habitación "101" está en `InCleaning` y tiene un `TechnicalBlockReport` en `Scheduled` cuyo rango contiene la fecha actual
   - **Cuando** El miembro del personal de limpieza confirma el fin de limpieza describiendo un daño, o la habitación tiene una llegada pendiente hoy
   - **Entonces** El sistema no aplica el bloqueo: la habitación queda en `DisabledForRepairs` o en `Reserved` según la prioridad de la regla 6 de la máquina de estados (daño > llegada > mantenimiento), y el informe sigue en `Scheduled` para reevaluarse mientras la fecha actual no supere su fecha estimada de fin (FR-016)

---

### Historia de Usuario 4 - Consulta del informe de bloqueo técnico (Prioridad: P2)

Como miembro del personal de mantenimiento quiero poder ver el informe del bloqueo técnico de una habitación, para así conocer la justificación, el responsable y el rango del mantenimiento y planificar la intervención.

**Por qué esta prioridad**: Sin el informe, el miembro del personal de mantenimiento que interviene la habitación no conoce el motivo técnico ni el rango del mantenimiento cuando no fue quien lo programó.

**Prueba independiente**: Puede ser probada seleccionando en el panel de mantenimiento una habitación con un `TechnicalBlockReport` en `Scheduled` o `Applied`, pulsando **Ver informe** y verificando que se muestran la justificación técnica, el responsable, el rango temporal del mantenimiento y la fecha y hora de la programación.

**Escenarios de aceptación**:

16. **Escenario**: Consulta del informe de bloqueo técnico
   - **Dado que** La habitación "101" tiene un `TechnicalBlockReport` en `Applied` y otro en `Scheduled` con una fecha de inicio posterior
   - **Cuando** El miembro del personal de mantenimiento pulsa **Ver informe** sobre la habitación "101"
   - **Entonces** El sistema muestra el informe `Applied` con la justificación técnica, el responsable, el rango temporal del mantenimiento y la fecha y hora de la programación (FR-009, FR-010)

---

### Casos Límite

1. **Conflicto con reservas activas o futuras detectadas en sistema de reservas**: Al invocar el caso de uso incluido `Consultar reservas`, si la consulta devuelve reservas vigentes que se cruzan con el periodo de trabajo preventivo, no registra la programación, mantiene la habitación en su estado actual y notifica al usuario que coordine la reasignación de la reserva en el Módulo 2.
2. **Peticiones simultáneas de bloqueo técnico sobre la misma unidad (concurrencia)**: Ante dos intentos simultáneos de programación con rangos solapados, el sistema procesa la primera solicitud y rechaza la segunda informando el conflicto con el mantenimiento ya programado.
3. **Justificación técnica vacía o insuficiente**: El sistema exige de forma obligatoria un texto descriptivo del mantenimiento preventivo planificado antes de habilitar la confirmación y realizar la operación.
4. **Fallo o no disponibilidad en la consulta de reservas**: La consulta de reservas se hace antes de registrar nada; si la consulta sobre reservas futuras asignadas vinculadas a la habitación presenta una caída de red o no hay respuesta, el sistema no registra nada y la habitación permanece en su estado actual.
5. **Fallo de persistencia al registrar la programación**: El registro se ejecuta de manera atómica; si falla la consulta de mantenimientos o el registro del `TechnicalBlockReport`, el sistema ejecuta rollback, no envía el aviso y la habitación permanece en su estado actual.
6. **Detección de daño físico mayor durante la intervención**: Si durante el mantenimiento preventivo en `TechnicalBlock` se detecta una avería crítica imprevista, la unidad culmina su ciclo hacia `PendingCleaning` mediante *Confirmar Fin de Reparación de Habitación*; el nuevo daño se reporta posteriormente como un flujo independiente, cuando la habitación vuelva a estar en `Available`.
7. **Rango temporal de un solo día**: (ver FR-013).
8. **Fechas en formato incorrecto o inexistentes en el calendario**: El sistema rechaza valores como `2026-13-45`, `31-02-2026` o texto libre, indicando el formato esperado.
9. **Cancelación de un mantenimiento programado**: (ver FR-017).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE verificar que la habitación sobre la que se desea operar no se encuentre en `Inactive`. Si la fecha de inicio es la fecha actual, la habitación DEBE además encontrarse en `Available`, porque el bloqueo se aplica de inmediato (FR-002). Con una fecha de inicio futura, el estado actual no impide la programación: el trabajo autónomo evalúa el estado en la fecha de inicio (FR-016). Si la habitación no cumple estas condiciones, el sistema DEBE rechazar la solicitud informando su estado actual.
- **FR-002**: El sistema DEBE ejecutar el bloqueo técnico en dos pasos: (1) al confirmar la programación, registrar el `TechnicalBlockReport` en estado `Scheduled`; (2) mediante un trabajo autónomo ejecutado tras la ingesta de la lista diaria de reservas de las 00:00 y, en todo caso, a las 01:00, transicionar la habitación de `Available` a `TechnicalBlock` cuando la fecha actual esté contenida en el rango del informe, marcándolo como `Applied`. Si la fecha de inicio es la fecha actual, ambos pasos DEBEN ejecutarse en la misma transacción al confirmar la programación. El paso (2) también DEBE ejecutarse cuando una habitación con un informe `Scheduled` cuyo rango contiene la fecha actual queda en `Available` al confirmarse el fin de su limpieza (*Confirmar Fin de Limpieza de Habitación* FR-020), en la misma transacción de ese flujo.
- **FR-003**: El sistema DEBE consultar, usando el identificador único de la habitación y el rango del mantenimiento, las reservas del Módulo 2 mediante el caso de uso *Consultar reservas* y los mantenimientos mediante el caso de uso *Consultar Información de Mantenimientos*, para evitar el solapamiento de eventos en el mismo rango.
- **FR-004**: El sistema DEBE rechazar la programación del bloqueo técnico si *Consultar reservas* devuelve al menos una reserva (solo devuelve reservas vigentes cuya estadía se cruza con el rango, sin contar el día de salida) o si *Consultar Información de Mantenimientos* responde que la habitación no está disponible en el rango.
- **FR-005**: El sistema DEBE requerir y validar como obligatorio el ingreso de una justificación técnica detallada que fundamente el mantenimiento preventivo programado, con un máximo de 500 caracteres.
- **FR-006**: El sistema DEBE validar el estado de la habitación (FR-001) después de las validaciones temporales (FR-015) y antes de consultar reservas y mantenimientos.
- **FR-007**: Desde que se registra el `TechnicalBlockReport` (`Scheduled`), el sistema DEBE reportar su rango como cruce en *Consultar Información de Mantenimientos*, que es la consulta con la que Módulo 2 decide si puede asignar la habitación a una reserva (Módulo 2 no usa el estado físico de la habitación).
- **FR-008**: El sistema DEBE crear un registro para auditoría (tabla de mantenimientos programados) vinculando la habitación, el técnico responsable, la justificación técnica registrada, fecha de inicio del mantenimiento, fecha estimada de finalización del mantenimiento y la marca de tiempo (timestamp) exacta de la operación (`ReportDateTime`), generada exclusivamente en el servidor, sin aceptar fechas u horas de operación enviadas por el cliente.
- **FR-009**: El sistema DEBE garantizar la disponibilidad de un recurso de consulta del informe de bloqueo técnico (acción **Ver informe**) para las habitaciones que indica *Consultar panel de mantenimiento* (FR-005). Si la habitación tiene varios `TechnicalBlockReport` en estado `Scheduled` o `Applied`, el recurso muestra el `Applied` o, si no hay, el `Scheduled` de fecha de inicio más temprana.
- **FR-010**: El recurso **Ver informe** (FR-009) DEBE mostrar la justificación técnica, el responsable, el rango temporal del mantenimiento y la fecha y hora de la programación.
- **FR-011**: El sistema DEBE validar que `TechnicalBlockStartDate` y `EstimatedTechnicalBlockEndDate` sean obligatorias, no nulas y que correspondan a fechas reales de calendario (ej. rechazar 31-02-2026 o 29-02 en año no bisiesto). El formato de intercambio en la API lo define el plan del caso de uso; la interfaz las muestra como DD-MM-YYYY.
- **FR-012**: El sistema DEBE rechazar una `TechnicalBlockStartDate` anterior a la fecha actual del servidor.
- **FR-013**: El sistema DEBE rechazar una `EstimatedTechnicalBlockEndDate` anterior a `TechnicalBlockStartDate`; una fecha de finalización igual a la de inicio es válida (duración mínima de un día).
- **FR-014**: El sistema DEBE restringir la duración máxima del rango temporal (`EstimatedTechnicalBlockEndDate` − `TechnicalBlockStartDate`) y la antelación máxima de programación (`TechnicalBlockStartDate` − fecha actual) a límites configurables (`max-range-days`, `max-advance-days`; valores en el plan base, T019), rechazando rangos que los excedan.
- **FR-015**: El sistema DEBE ejecutar las validaciones temporales (FR-011 a FR-014) antes de realizar las consultas de reservas y mantenimientos, y DEBE informar mediante retroalimentación visual la regla específica incumplida, indicando el formato o límite esperado.
- **FR-016**: Si al ejecutarse el trabajo autónomo la habitación no se encuentra en `Available` (incluida `Inactive`), el sistema NO DEBE sobrescribir su estado; DEBE mantener el informe en `Scheduled` y reevaluarlo en cada ejecución mientras la fecha actual no supere `EstimatedTechnicalBlockEndDate`. Superada esa fecha sin haberse aplicado, DEBE marcar el informe como `Expired` (caducado), que es un estado definitivo: no tiene transiciones de salida, no se reprograma y no bloquea reservas; solo queda como registro de que el mantenimiento caducó sin aplicarse. Si la habitación queda en `Available` durante el rango al confirmarse el fin de su limpieza, el bloqueo se aplica en ese momento (FR-002); por cualquier otra vía (incluida la reactivación de una habitación dada de baja), en la siguiente ejecución del trabajo autónomo. Mientras tanto su rango ya impide nuevas reservas (FR-007).
- **FR-017**: El sistema NO DEBE permitir cancelar ni modificar un `TechnicalBlockReport` una vez registrado; este caso queda fuera del alcance del módulo.
- **FR-018**: El sistema DEBE registrar la transición a `TechnicalBlock` en `RoomStateHistory` dentro de la misma transacción: cerrar el periodo abierto de la habitación y abrir uno nuevo con `Status` = `TechnicalBlock`, `PreviousStatus` = `Available`, `StartDateTime` = fecha y hora del servidor en que se aplica, `ActorId` = miembro del personal de mantenimiento cuando se aplica al programar con inicio en la fecha actual o nulo cuando lo aplica el trabajo autónomo o *Confirmar Fin de Limpieza de Habitación*, y `SourceFlow` = *Programar Bloqueo Técnico para Habitación*. La sola programación (`Scheduled`) no genera registros en `RoomStateHistory`.
- **FR-019**: Al registrar el `TechnicalBlockReport` (en `Scheduled`, o en `Applied` si inicia en la fecha actual), el sistema DEBE avisar a Módulo 2, dentro de la misma operación y solo si la programación se registra, del informe de bloqueo técnico con la categoría de la habitación y su rango de fechas (inicio y fin estimado, ambos incluidos), para que deje de vender esas noches en sus canales externos.
- **FR-020**: Cada día a las 00:00, el sistema DEBE avisar a Módulo 2 de un mantenimiento con inicio y fin en la fecha actual por cada habitación en `DisabledForRepairs` (con el identificador de su último reporte de daño) y por cada informe `Applied` vencido (con el identificador del informe), para que Módulo 2 no venda esa noche antes de su recálculo diario. Este aviso no cambia el estado de ninguna habitación ni de ningún informe.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Entidad que representa una habitación, que transiciona de `Available` a `TechnicalBlock`.
- **TechnicalBlockReport**: Entidad que representa el informe generado por la programación del mantenimiento, esta almacena:
  - **RoomId**: Identificador de la habitación
  - **MaintenanceStaffMemberId**: Identificador del miembro del personal de mantenimiento que programó el mantenimiento
  - **TechnicalBlockReason**: Cadena de texto que representa el motivo de la programación del mantenimiento
  - **TechnicalBlockStartDate**: Fecha de inicio del mantenimiento - Formato DD-MM-YYYY
  - **EstimatedTechnicalBlockEndDate**: Fecha de finalización estimada del mantenimiento - Formato DD-MM-YYYY
  - **ReportDateTime**: TimeStamp de realización de la programación del mantenimiento - Formato DD-MM-YYYY HH:MM
  - **Status**: Estado del informe - `Scheduled` (programado, aún no aplicado), `Applied` (habitación en `TechnicalBlock`), `Completed` (reparación confirmada como finalizada) o `Expired` (caducado: no aplicado antes de su fecha estimada de finalización). `Completed` y `Expired` son estados definitivos, sin transiciones de salida. Un informe `Applied` cuya fecha estimada de fin ya pasó se considera **vencido** (etiqueta del panel de mantenimiento), no `Expired`: sigue `Applied` hasta que se confirma el fin de la reparación
- **RoomStateHistory**: Historial común de estados de la habitación. En este caso de uso se abre el periodo `TechnicalBlock` al aplicarse el bloqueo.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las programaciones válidas realizan la consulta de reservas al sistema de reservas y la consulta de mantenimientos, y registran el `TechnicalBlockReport` en menos de 2 segundos, con Módulo 2 respondiendo en su tiempo normal; el 100% de las programaciones cuyo rango contiene la fecha actual y cuya habitación está en `Available` transicionan a `TechnicalBlock` en la ejecución correspondiente (inmediata o del trabajo autónomo).
- **SC-002**: El 100% de los intentos con fechas nulas, inexistentes, pasadas, con rango invertido o fuera de los límites configurados son rechazados antes de ejecutar consultas externas.
