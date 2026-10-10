# Especificación del Caso de Uso: Consultar Información de Mantenimientos

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta de mantenimientos por parte del módulo de reservas o personal de mantenimiento (Prioridad: P1)

Como sistema del módulo de reservas y miembro del personal de mantenimiento quiero poder consultar qué habitaciones se encuentran con mantenimientos programados, para poder formalizar la labor de consulta cruzada en referencia a la programación de eventos a futuro y de este modo mantener la consistencia operativa del hotel, evitar asignaciones de eventos en rangos temporales erróneos o contradictorios que puedan perjudicar o ralentizar el funcionamiento continuo de la infraestructura digital del hotel.

**Por qué esta prioridad**: Sin esta información, tanto el módulo de reservas como los miembros del personal de mantenimiento podrían crear eventos de interés (reservas, mantenimientos programados) a través de acciones inválidas, causando conflictos operativos con huéspedes o registrando eventos con rangos temporales de ejecución solapados o superpuestos entre sí.

**Prueba independiente**: Puede ser probada simulando una consulta proveniente del sistema de reservas o la programación de un mantenimiento, verificando que la consulta revisa los mantenimientos programados o en curso de la habitación e indica si está disponible en el rango indicado y, si no lo está, las fechas de cada mantenimiento que se cruza.

**Escenarios de aceptación**:

1. **Escenario**: Consulta exitosa al programar un mantenimiento
   - **Dado que** El miembro del personal de mantenimiento está autenticado y desea programar un mantenimiento para la habitación "101" entre el rango temporal comprendido desde "22-10-2026" hasta "26-10-2026"
   - **Cuando** *Programar Bloqueo Técnico para Habitación* consulta los mantenimientos de la habitación "101" en el rango definido
   - **Entonces** El sistema retorna que la habitación está disponible, sin cruces, por lo que se puede registrar el mantenimiento

2. **Escenario**: Consulta exitosa por parte del módulo de reservas
   - **Dado que** El sistema del módulo de reservas está autorizado y desea vincular la habitación "101" a una reserva con llegada el "22-10-2026" y salida el "26-10-2026"
   - **Cuando** El sistema consulta los mantenimientos de la habitación "101" para esa estadía
   - **Entonces** El sistema retorna que la habitación está disponible, sin cruces, por lo que se puede vincular la habitación a la reserva

3. **Escenario**: Consulta con mantenimiento solapado
   - **Dado que** Existe un mantenimiento programado para la habitación "101" entre "24-10-2026" y "28-10-2026"
   - **Cuando** Se consulta la habitación "101" para una estadía con llegada el "22-10-2026" y salida el "26-10-2026"
   - **Entonces** El sistema retorna que la habitación no está disponible e incluye el cruce con su inicio "24-10-2026" y su fin estimado "28-10-2026", sin la justificación del mantenimiento

4. **Escenario**: Consulta con rango temporal sin sentido
   - **Dado que** Un actor autorizado realiza una consulta para la habitación "101"
   - **Cuando** El rango enviado tiene una fecha de fin anterior a la de inicio, una fecha inexistente en el calendario, una fecha nula o termina antes de la fecha actual
   - **Entonces** El sistema rechaza la consulta, no evalúa mantenimientos y retorna un error de validación indicando la regla temporal incumplida

5. **Escenario**: Mantenimiento que termina antes de la llegada
   - **Dado que** Existe un mantenimiento programado para la habitación "101" entre "18-10-2026" y "21-10-2026"
   - **Cuando** Se consulta la habitación "101" para una estadía con llegada el "22-10-2026" y salida el "26-10-2026"
   - **Entonces** El sistema retorna que la habitación está disponible, sin cruces, porque el mantenimiento termina antes de la llegada

6. **Escenario**: Bloqueo técnico en curso con la fecha estimada de fin vencida
   - **Dado que** La habitación "101" sigue en `TechnicalBlock` con un informe `Applied` cuya fecha estimada de fin fue "20-10-2026" y la fecha actual es "21-10-2026"
   - **Cuando** Se consulta la habitación "101" para una estadía con llegada el "22-10-2026" y salida el "26-10-2026"
   - **Entonces** El sistema retorna que la habitación está disponible, sin cruces: para fechas futuras la reserva tiene prioridad sobre el bloqueo vencido, que el panel de mantenimiento marca como vencido

7. **Escenario**: Mantenimiento que empieza el día de la salida
   - **Dado que** Existe un mantenimiento programado para la habitación "101" entre "26-10-2026" y "27-10-2026"
   - **Cuando** Se consulta la habitación "101" para una estadía con llegada el "22-10-2026" y salida el "26-10-2026"
   - **Entonces** El sistema retorna que la habitación está disponible, sin cruces, porque el día de salida no se considera ocupado por la estadía

8. **Escenario**: Habitación fuera de servicio hoy con llegada hoy
   - **Dado que** La fecha actual es "21-10-2026" y la habitación "101" está en `DisabledForRepairs` (o en `TechnicalBlock` con su informe `Applied` vencido)
   - **Cuando** Se consulta la habitación "101" para una estadía con llegada el "21-10-2026" y salida el "23-10-2026"
   - **Entonces** El sistema retorna que la habitación no está disponible e incluye un cruce con inicio y fin "21-10-2026", porque no puede asignarse hoy

---

### Casos Límite

1. **¿Qué ocurre si el módulo de reservas no está autorizado?**
   El sistema debe rechazar la consulta retornando un error de autorización que indique el motivo, sin evaluar mantenimientos.

2. **¿Qué ocurre si la información de mantenimiento cambia entre la consulta y la creación de la reserva?**
   La consulta retorna la información vigente al momento de responder. Si en ese intervalo se programa un mantenimiento, *Programar Bloqueo Técnico para Habitación* verifica las reservas del módulo de reservas antes de registrarlo. Que ambos eventos ocurran al mismo tiempo es una limitación aceptada.

3. **¿Qué ocurre si la llegada y la salida son el mismo día?**
   El rango es válido y se evalúa solo ese día. Un rango con fin anterior al inicio es siempre inválido.

4. **¿Qué ocurre si se consulta un rango que empieza antes de la fecha actual?**
   Si el rango todavía incluye días a partir de hoy, se evalúa desde la fecha actual (por ejemplo, la extensión de una estadía en curso). Si termina antes de hoy, se rechaza como rango temporal sin sentido.

5. **¿Qué ocurre con los mantenimientos ya finalizados o caducados?**
   No se consideran: un `TechnicalBlockReport` en estado `Completed` o `Expired` no genera solapamiento, aunque su rango coincida con el consultado.

6. **¿Qué ocurre si la habitación sigue en bloqueo técnico después de la fecha estimada de finalización?**
   El informe en estado `Applied` deja de bloquear fechas futuras posteriores a su fecha estimada de fin: la reserva tiene prioridad. La habitación sigue en `TechnicalBlock` hasta que se confirme el fin de la reparación, y el panel de mantenimiento la marca como vencida para que se cierre o se programe un nuevo mantenimiento. Mientras siga bloqueada, no se puede asignar para el día actual (escenario 8). Si el día de la llegada la habitación sigue bloqueada, Recepción ve la alerta de su panel (*Consultar panel de recepción*, FR-005).

7. **¿Qué ocurre si se consulta una habitación que no existe?**
   El sistema retorna un error de "habitación no encontrada", distinto de la verificación negativa por solapamiento.

8. **¿Qué ocurre con una habitación inhabilitada por un daño (`DisabledForRepairs`)?**
   Para fechas futuras no genera cruce, porque un reporte de daño no tiene fecha de fin; solo impide asignarla para el día actual (escenario 8). Si la habitación sigue inhabilitada el día de la llegada, Recepción ve la alerta de su panel (*Consultar panel de recepción*, FR-005).

9. **¿La disponibilidad incluye reservas, ocupación o habitaciones dadas de baja?**
   No. La consulta solo evalúa mantenimientos. Las reservas y la ocupación las verifica el módulo de reservas con sus propios datos, y las habitaciones dadas de baja (`Inactive`) nunca se le ofrecen, porque *Consultar inventario de habitaciones* las excluye de su respuesta (FR-010 de ese caso de uso).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE ofrecer la consulta por servicio REST únicamente al sistema del módulo de reservas autorizado. El personal de mantenimiento la usa de forma interna, a través de *Programar Bloqueo Técnico para Habitación*.
- **FR-002**: El sistema DEBE retornar si la habitación está disponible en el rango consultado frente a los mantenimientos y, si no lo está, la fecha de inicio y la fecha estimada de fin de cada mantenimiento que se cruza (FR-010, FR-014). La disponibilidad se refiere solo a mantenimientos; no evalúa reservas, ocupación ni bajas. El sistema NO DEBE incluir en la respuesta la justificación ni otros datos del mantenimiento.
- **FR-003**: El sistema DEBE recibir como parámetros de la consulta el identificador de una única habitación (`roomId`), la fecha de inicio y la fecha de fin del rango, y evaluarlos contra los campos `TechnicalBlockStartDate` y `EstimatedTechnicalBlockEndDate` de los `TechnicalBlockReport` de esa habitación. Cuando consulta el módulo de reservas, la fecha de inicio es la llegada y la fecha de fin es la salida: se evalúan los días de la llegada al día anterior a la salida, porque el día de salida no se considera ocupado (si la llegada y la salida coinciden, se evalúa ese día). Cuando consulta *Programar Bloqueo Técnico para Habitación*, la fecha de fin es el último día del mantenimiento y sí se evalúa.
- **FR-004**: El sistema DEBE asegurarse de que cada consulta realizada, independiente del actor que la realice, sea una operación de solo lectura (READ-ONLY).
- **FR-005**: El sistema DEBE validar que las fechas del rango consultado sean obligatorias, no nulas y correspondan a fechas reales de calendario (ej. rechazar 31-02-2026). El formato de intercambio en la API lo define el plan del caso de uso; la interfaz las muestra como DD-MM-YYYY.
- **FR-006**: El sistema DEBE rechazar rangos cuya fecha de fin sea anterior a la fecha de inicio; un rango con fecha de fin igual a la de inicio es válido.
- **FR-007**: El sistema DEBE rechazar los rangos que terminan antes de la fecha actual del servidor. Si el rango empieza antes de la fecha actual pero incluye días a partir de ella, DEBE evaluarlo desde la fecha actual.
- **FR-008**: El sistema NO DEBE aplicar a esta consulta los límites de duración del rango y de antelación definidos en *Programar Bloqueo Técnico para Habitación*; esos límites los valida ese caso de uso.
- **FR-009**: El sistema DEBE ejecutar las validaciones temporales (FR-005 a FR-007) antes de consultar la tabla de mantenimientos y DEBE retornar un error de validación que indique la regla específica incumplida, diferenciándolo de una respuesta de confirmación negativa por solapamiento.
- **FR-010**: El sistema DEBE considerar que hay cruce cuando el rango (`TechnicalBlockStartDate`, `EstimatedTechnicalBlockEndDate`) de un `TechnicalBlockReport` en estado `Scheduled` o `Applied` se solapa total o parcialmente con los días evaluados (FR-003), incluyendo coincidencia en los días extremos. Un mantenimiento cuya fecha estimada de fin es anterior al primer día evaluado NO DEBE considerarse cruce; por lo tanto, un informe `Applied` con la fecha estimada de fin vencida no bloquea fechas posteriores a ella. Los informes en estado `Completed` o `Expired` NO DEBEN generar cruces. El estado `DisabledForRepairs` por sí solo no genera cruces para fechas futuras (FR-014); los informes de bloqueo técnico de esa habitación se evalúan igual.
- **FR-011**: El sistema NO DEBE generar registros en `RoomStateHistory` ni modificar el estado de la habitación, ya que este caso de uso no ejecuta transiciones de estado.
- **FR-012**: El sistema DEBE rechazar la consulta con un error de "habitación no encontrada" cuando el `roomId` recibido no corresponda a una habitación registrada, sin evaluar mantenimientos y diferenciándolo de una respuesta de confirmación negativa por solapamiento.
- **FR-013**: El sistema DEBE rechazar con un error de validación la consulta cuyo `roomId` no tenga el formato de identificador de habitación, sin evaluar mantenimientos.
- **FR-014**: Si la habitación está hoy en `DisabledForRepairs`, o en `TechnicalBlock` con su informe `Applied` vencido (fecha estimada de fin anterior a la fecha actual), y los días evaluados incluyen la fecha actual, el sistema DEBE considerarla no disponible y retornar un cruce cuyo inicio y fin son la fecha actual, porque no puede asignarse ese día.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. Su estado actual se usa solo para FR-014.
- **TechnicalBlockReport**: Entidad que representa el reporte generado por la programación de un mantenimiento (definida en el caso de uso *Programar Bloqueo Técnico para Habitación*). Solo los informes en estado `Scheduled` o `Applied` cuentan como cruce, dentro de su rango de fechas; la respuesta expone solo su fecha de inicio y su fecha estimada de fin.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: La consulta retorna su respuesta en menos de 200 milisegundos.
- **SC-002**: El 100% de las consultas con fechas nulas, inexistentes, con rango invertido o que terminan antes de la fecha actual son rechazadas con un error de validación sin consultar la tabla de mantenimientos.
