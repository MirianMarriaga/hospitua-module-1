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
   - **Dado que** Existe una habitación en estado `Available` y el miembro del personal de mantenimiento está autenticado
   - **Cuando** El miembro del personal de mantenimiento intenta confirmar la programación con una fecha de inicio pasada, una fecha estimada de finalización anterior a la de inicio, una fecha inexistente en el calendario (ej. 31-02-2026) o un rango que excede los límites permitidos
   - **Entonces** El sistema bloquea la confirmación, no ejecuta las consultas de reservas ni mantenimientos, mantiene la habitación en `Available` y muestra un error indicando la regla temporal incumplida.

6. **Escenario**: Programación exitosa con inicio en una fecha futura
   - **Dado que** Existe una habitación en estado `Available` sin reservas ni mantenimientos en el rango definido
   - **Cuando** El miembro del personal de mantenimiento programa el bloqueo técnico con una fecha de inicio posterior a la fecha actual
   - **Entonces** El sistema registra el `TechnicalBlockReport` en estado `Scheduled`, mantiene la habitación en `Available` y la muestra en el panel de mantenimiento con el rango del mantenimiento programado

7. **Escenario**: Aplicación autónoma del bloqueo en la fecha de inicio
   - **Dado que** Existe un `TechnicalBlockReport` en estado `Scheduled` cuya fecha de inicio es la fecha actual y la habitación está en `Available`
   - **Cuando** Se ejecuta el trabajo autónomo de las 00:00, después de la ingesta de la lista diaria de reservas
   - **Entonces** El sistema transiciona la habitación a `TechnicalBlock` y marca el informe como `Applied`

8. **Escenario**: Habitación no disponible en la fecha de inicio
   - **Dado que** Existe un `TechnicalBlockReport` en estado `Scheduled` cuyo rango contiene la fecha actual y la habitación está en un estado distinto de `Available` (ej. `Occupied` o `Reserved`)
   - **Cuando** Se ejecuta el trabajo autónomo de las 00:00
   - **Entonces** El sistema no modifica el estado de la habitación, mantiene el informe en `Scheduled` y lo vuelve a evaluar a las 00:00 siguientes; si la fecha actual supera la fecha estimada de finalización sin haberse aplicado, marca el informe como `Expired`

9. **Escenario**: Programación con inicio futuro sobre una habitación que hoy no está disponible
   - **Dado que** Existe una habitación en estado `Occupied` (o `Reserved`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs` o `TechnicalBlock`) sin reservas ni mantenimientos en el rango definido
   - **Cuando** El miembro del personal de mantenimiento programa el bloqueo técnico con una fecha de inicio posterior a la fecha actual
   - **Entonces** El sistema registra el `TechnicalBlockReport` en estado `Scheduled` sin modificar el estado de la habitación; el trabajo autónomo lo aplicará en su fecha de inicio si la habitación está en `Available` (escenarios 7 y 8)

10. **Escenario**: Rechazo por estado de la habitación
   - **Dado que** Existe una habitación en estado `Inactive`, o una habitación en un estado distinto de `Available` y el bloqueo inicia en la fecha actual
   - **Cuando** El miembro del personal de mantenimiento intenta programar el mantenimiento
   - **Entonces** El sistema rechaza la operación sin consultar reservas ni mantenimientos, no modifica la habitación e informa su estado actual

---

### Casos Límite

1. **Conflicto con reservas activas o futuras detectadas en sistema de reservas**: Al invocar el caso de uso incluido `Consultar reservas`, si la consulta devuelve reservas vigentes que se cruzan con el periodo de trabajo preventivo, no registra la programación, mantiene la habitación en su estado actual y notifica al usuario que coordine la reasignación de la reserva en el Módulo 2.
2. **Peticiones simultáneas de bloqueo técnico sobre la misma unidad (concurrencia)**: Ante dos intentos simultáneos de programación con rangos solapados, el sistema procesa la primera solicitud y rechaza la segunda informando el conflicto con el mantenimiento ya programado.
3. **Justificación técnica vacía o insuficiente**: El sistema exige de forma obligatoria un texto descriptivo del mantenimiento preventivo planificado antes de habilitar la confirmación y realizar la operación.
4. **Fallo o no disponibilidad en la consulta de reservas**: La operación se ejecuta de manera atómica; si la consulta sobre reservas futuras asignadas vinculadas a la habitación presenta una caída de red o no hay respuesta, el sistema ejecuta rollback y la habitación permanece en su estado actual.
5. **Fallo o no disponibilidad en la consulta de mantenimientos**: La operación se ejecuta de manera atómica; si la consulta sobre mantenimientos futuros programados vinculados a la habitación presenta una caída de red o no hay respuesta, el sistema ejecuta rollback y la habitación permanece en su estado actual.
6. **Detección de daño físico mayor durante la intervención**: Si durante el mantenimiento preventivo en `TechnicalBlock` se detecta una avería crítica imprevista, la unidad culmina su ciclo hacia `PendingCleaning` mediante *Confirmar Fin de Reparación de Habitación*; el nuevo daño se reporta posteriormente como un flujo independiente, cuando la habitación vuelva a estar en `Available`.
7. **Rango temporal de un solo día**: Una fecha estimada de finalización igual a la fecha de inicio es válida (duración mínima de un día). Una fecha de finalización anterior a la de inicio es siempre inválida.
8. **Fechas en formato incorrecto o inexistentes en el calendario**: El sistema rechaza valores como `2026-13-45`, `31-02-2026` o texto libre, indicando el formato esperado DD-MM-YYYY.
9. **Cancelación de un mantenimiento programado**: La cancelación o modificación de un `TechnicalBlockReport` queda fuera del alcance de este caso de uso; una programación que no llega a aplicarse caduca al superar su fecha estimada de finalización.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE verificar que la habitación sobre la que se desea operar no se encuentre en `Inactive`. Si la fecha de inicio es la fecha actual, la habitación DEBE además encontrarse en `Available`, porque el bloqueo se aplica de inmediato (FR-002). Con una fecha de inicio futura, el estado actual no impide la programación: el trabajo autónomo evalúa el estado en la fecha de inicio (FR-018).
- **FR-002**: El sistema DEBE ejecutar el bloqueo técnico en dos pasos: (1) al confirmar la programación, registrar el `TechnicalBlockReport` en estado `Scheduled`; (2) mediante un trabajo autónomo ejecutado a las 00:00, después de la ingesta de la lista diaria de reservas, transicionar la habitación de `Available` a `TechnicalBlock` cuando la fecha actual esté contenida en el rango del informe, marcándolo como `Applied`. Si la fecha de inicio es la fecha actual, ambos pasos DEBEN ejecutarse en la misma transacción al confirmar la programación.
- **FR-003**: El sistema DEBE permitir únicamente al actor "miembro del personal de mantenimiento" (`MaintenanceStaff`) ejecutar la programación de mantenimientos.
- **FR-004**: El sistema DEBE consultar, usando el identificador único de la habitación y el rango del mantenimiento, las reservas del Módulo 2 mediante el caso de uso *Consultar reservas* y los mantenimientos mediante el caso de uso *Consultar Información de Mantenimientos*, para evitar el solapamiento de eventos en el mismo rango.
- **FR-005**: El sistema DEBE rechazar la programación del bloqueo técnico si *Consultar reservas* devuelve al menos una reserva (solo devuelve reservas vigentes cuya estadía se cruza con el rango, sin contar el día de salida) o si *Consultar Información de Mantenimientos* responde que la habitación no está disponible en el rango.
- **FR-006**: El sistema DEBE requerir y validar como obligatorio el ingreso de una justificación técnica detallada que fundamente el mantenimiento preventivo programado, con un máximo de 500 caracteres.
- **FR-007**: El sistema DEBE rechazar la solicitud, después de las validaciones temporales (FR-016) y antes de consultar reservas y mantenimientos, si la habitación está en `Inactive`, o si la fecha de inicio es la fecha actual y la habitación está en cualquier estado distinto de `Available` (`Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs` o `TechnicalBlock`), informando el estado actual.
- **FR-008**: Desde que se registra el `TechnicalBlockReport` (`Scheduled`), el sistema DEBE reportar su rango como cruce en *Consultar Información de Mantenimientos*, que es la consulta con la que Módulo 2 decide si puede asignar la habitación a una reserva (Módulo 2 no usa el estado físico de la habitación).
- **FR-009**: El sistema DEBE crear un registro para auditoría (tabla de mantenimientos programados) vinculando la habitación, el técnico responsable, la justificación técnica registrada, fecha de inicio del mantenimiento, fecha estimada de finalización del mantenimiento y la marca de tiempo (timestamp) exacta de la operación.
- **FR-010**: El sistema DEBE permitir la salida del estado `TechnicalBlock` únicamente mediante el caso de uso *Confirmar Fin de Reparación de Habitación*.
- **FR-011**: El sistema DEBE garantizar la disponibilidad de un recurso de consulta correspondiente al registro del informe de mantenimiento programado (acción **Ver informe**) de manera persistente para toda unidad habitacional con un `TechnicalBlockReport` en estado `Scheduled` o `Applied`. Si la habitación tiene varios, el recurso muestra el `Applied` o, si no hay, el `Scheduled` de fecha de inicio más temprana.
  - **FR-011.1**: El recurso **Ver informe** DEBE mostrar la justificación técnica, el responsable, el rango temporal del mantenimiento y la fecha y hora de la programación.
  - **FR-011.2**: El sistema DEBE calcular y autorizar las acciones operativas disponibles sobre las habitaciones en bloqueo técnico (`TechnicalBlock`) aplicando las siguientes políticas de control de acceso según la sesión activa:
    - **Para el rol `Personal de mantenimiento`**: Autorización híbrida (lectura y escritura); el sistema expondrá tanto la acción de consulta (**Ver informe**) como la acción **Iniciar reparaciones** (definida en el caso de uso *Confirmar Fin de Reparación de Habitación*), esta última únicamente cuando la habitación ya se encuentre en `TechnicalBlock`.
- **FR-012**: El sistema DEBE validar que `TechnicalBlockStartDate` y `EstimatedTechnicalBlockEndDate` sean obligatorias, no nulas y con formato DD-MM-YYYY, y que correspondan a fechas reales de calendario (ej. rechazar 31-02-2026 o 29-02 en año no bisiesto).
- **FR-013**: El sistema DEBE rechazar una `TechnicalBlockStartDate` anterior a la fecha actual del servidor.
- **FR-014**: El sistema DEBE rechazar una `EstimatedTechnicalBlockEndDate` anterior a `TechnicalBlockStartDate`; una fecha de finalización igual a la de inicio es válida (duración mínima de un día).
- **FR-015**: El sistema DEBE restringir la duración máxima del rango temporal (`EstimatedTechnicalBlockEndDate` − `TechnicalBlockStartDate`) a un límite configurable (valor por defecto sugerido: 90 días) y la antelación máxima de programación (`TechnicalBlockStartDate` − fecha actual) a un límite configurable (valor por defecto sugerido: 365 días), rechazando rangos que los excedan.
- **FR-016**: El sistema DEBE ejecutar las validaciones temporales (FR-012 a FR-015) antes de realizar las consultas de reservas y mantenimientos, y DEBE informar mediante retroalimentación visual la regla específica incumplida, indicando el formato o límite esperado.
- **FR-017**: El sistema DEBE generar `ReportDateTime` exclusivamente en el servidor, sin aceptar fechas u horas de operación enviadas por el cliente.
- **FR-018**: Si al ejecutarse el trabajo autónomo la habitación no se encuentra en `Available`, el sistema NO DEBE sobrescribir su estado; DEBE mantener el informe en `Scheduled` y reevaluarlo a las 00:00 siguientes mientras la fecha actual no supere `EstimatedTechnicalBlockEndDate`. Superada esa fecha sin haberse aplicado, DEBE marcar el informe como `Expired`. Si la habitación está en `Inactive` (dada de baja), DEBE marcar el informe como `Expired` en esa misma ejecución; si la habitación se reactiva antes de la fecha de inicio, el informe sigue en `Scheduled` y se aplica con normalidad. Si la habitación queda en `Available` durante el día de inicio, el bloqueo se aplica en la ejecución de las 00:00 siguiente; mientras tanto su rango ya impide nuevas reservas (FR-008).
- **FR-019**: El sistema NO DEBE permitir cancelar ni modificar un `TechnicalBlockReport` una vez registrado; este caso queda fuera del alcance del módulo.
- **FR-020**: El sistema DEBE registrar la transición a `TechnicalBlock` en `RoomStateHistory` dentro de la misma transacción: cerrar el periodo abierto de la habitación y abrir uno nuevo con `Status` = `TechnicalBlock`, `PreviousStatus` = `Available`, `StartDateTime` = fecha y hora del servidor en que se aplica, `ActorId` = miembro del personal de mantenimiento cuando se aplica al programar con inicio en la fecha actual o nulo cuando la aplica el trabajo autónomo, y `SourceFlow` = *Programar Bloqueo Técnico para Habitación*. La sola programación (`Scheduled`) no genera registros en `RoomStateHistory`.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Entidad que representa una habitación, que transiciona de `Available` a `TechnicalBlock`.
- **TechnicalBlockReport**: Entidad que representa el informe generado por la programación del mantenimiento, esta almacena:
  - **RoomId**: Identificador de la habitación
  - **MaintenanceStaffMemberId**: Identificador del miembro del personal de mantenimiento que programó el mantenimiento
  - **TechnicalBlockReason**: Cadena de texto que representa el motivo de la programación del mantenimiento
  - **TechnicalBlockStartDate**: Fecha de inicio del mantenimiento - Formato DD-MM-YYYY
  - **EstimatedTechnicalBlockEndDate**: Fecha de finalización estimada del mantenimiento - Formato DD-MM-YYYY
  - **ReportDateTime**: TimeStamp de realización de la programación del mantenimiento - Formato DD-MM-YYYY HH:MM
  - **Status**: Estado del informe - `Scheduled` (programado, aún no aplicado), `Applied` (habitación en `TechnicalBlock`), `Completed` (reparación confirmada como finalizada) o `Expired` (no aplicado antes de su fecha estimada de finalización)
- **RoomStateHistory**: Historial común de estados de la habitación. En este caso de uso se abre el periodo `TechnicalBlock` al aplicarse el bloqueo.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las programaciones válidas realizan la consulta de reservas al sistema de reservas y la consulta de mantenimientos, y registran el `TechnicalBlockReport` en menos de 2 segundos; el 100% de las programaciones cuyo rango contiene la fecha actual y cuya habitación está en `Available` transicionan a `TechnicalBlock` en la ejecución correspondiente (inmediata o de las 00:00).
- **SC-002**: El sistema intercepta y rechaza el 100% de los intentos de bloqueo técnico sobre habitaciones en `Inactive`, sobre habitaciones que no están en `Available` cuando el bloqueo inicia en la fecha actual, o con reservas o mantenimientos programados en colisión.
- **SC-003**: El 100% de los rangos de informes `Scheduled` o `Applied` se reportan como cruce en *Consultar Información de Mantenimientos* desde que se registran (salvo un informe `Applied` vencido, que solo bloquea el día actual).
- **SC-004**: Cero registros de bloqueo técnico completados sin justificación obligatoria o sin la traza correspondiente de auditoría.
- **SC-005**: El 100% de los intentos con fechas nulas, inexistentes, pasadas, con rango invertido o fuera de los límites configurados son rechazados antes de ejecutar consultas externas.
