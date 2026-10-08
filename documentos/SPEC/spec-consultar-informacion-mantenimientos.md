# Especificación del Caso de Uso: Consultar Información de Mantenimientos

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta de mantenimientos por parte del módulo de reservas o personal de mantenimiento (Prioridad: P1)

Como sistema del módulo de reservas y miembro del personal de mantenimiento quiero poder consultar qué habitaciones se encuentran con mantenimientos programados, para poder formalizar la labor de consulta cruzada en referencia a la programación de eventos a futuro y de este modo mantener la consistencia operativa del hotel, evitar asignaciones de eventos en rangos temporales erróneos o contradictorios que puedan perjudicar o ralentizar el funcionamiento continuo de la infraestructura digital del hotel.

**Por qué esta prioridad**: Sin esta información, tanto el módulo de reservas como los miembros del personal de mantenimiento podrían crear eventos de interés (reservas, mantenimientos programados) a través de acciones inválidas, causando conflictos operativos con huéspedes o registrando eventos con rangos temporales de ejecución solapados o superpuestos entre sí.

**Prueba independiente**: Puede ser probada autenticándose como miembro del personal de mantenimiento o simulando una consulta proveniente del sistema de reservas, verificando que la consulta realiza validaciones en la tabla de mantenimientos programados e indica si la operación es factible de realizarse o no en el rango temporal indicado.

**Escenarios de aceptación**:

1. **Escenario**: Consulta exitosa por parte del personal de mantenimiento
   - **Dado que** El miembro del personal de mantenimiento está autenticado y desea programar un mantenimiento para la habitación "101" entre el rango temporal comprendido desde "22-10-2026" hasta "26-10-2026"
   - **Cuando** El sistema valida la existencia de mantenimientos programados vinculados a la habitación "101" en el rango temporal definido
   - **Entonces** El sistema retorna una verificación de que se puede realizar la programación del mantenimiento puesto que no hay mantenimientos programados dentro del rango definido y es seguro realizar el registro del evento

2. **Escenario**: Consulta exitosa por parte del módulo de reservas
   - **Dado que** El sistema del módulo de reservas está autorizado y desea vincular la habitación "101" a una reserva entre el rango temporal comprendido desde "22-10-2026" hasta "26-10-2026"
   - **Cuando** El sistema valida la existencia de mantenimientos programados vinculados a la habitación "101" en el rango temporal definido
   - **Entonces** El sistema retorna una verificación de que se puede realizar la vinculación de la habitación a la reserva puesto que no hay mantenimientos programados dentro del rango definido y es seguro realizar el registro del evento

3. **Escenario**: Consulta con mantenimiento solapado
   - **Dado que** Existe un mantenimiento programado para la habitación "101" entre "24-10-2026" y "28-10-2026"
   - **Cuando** Se consulta la habitación "101" para el rango "22-10-2026" a "26-10-2026"
   - **Entonces** El sistema retorna una verificación negativa indicando que el evento no es válido, sin sugerencias adicionales

4. **Escenario**: Consulta con rango temporal sin sentido
   - **Dado que** Un actor autorizado realiza una consulta para la habitación "101"
   - **Cuando** El rango enviado tiene una fecha de fin anterior a la de inicio, una fecha inexistente en el calendario o una fecha nula
   - **Entonces** El sistema rechaza la consulta, no evalúa mantenimientos y retorna un error de validación indicando la regla temporal incumplida

---

### Casos Límite

1. **¿Qué ocurre si el módulo de reservas no está autorizado?**
   El sistema debe rechazar la consulta retornando un error de autorización que indique el motivo, sin evaluar mantenimientos.

2. **¿Qué ocurre si la información de mantenimiento cambia mientras el módulo de reservas la procesa?**
   El sistema debe retornar la información más reciente disponible. El módulo de reservas debe validar disponibilidad al momento de crear la reserva con la información proporcionada, se pueden proponer políticas de reintento sobre detección de novedades.

3. **¿Qué ocurre si el rango consultado es de un solo día (fecha de inicio igual a fecha de fin)?**
   El rango es válido y se evalúa contra los mantenimientos existentes. Un rango con fin anterior al inicio es siempre inválido.

4. **¿Qué ocurre si se consulta un rango totalmente en el pasado?**
   El sistema lo rechaza como consulta con rango temporal sin sentido, dado que no es posible crear nuevos eventos en fechas pasadas.

5. **¿Qué ocurre con los mantenimientos ya finalizados o caducados?**
   No se consideran: un `TechnicalBlockReport` en estado `Completed` o `Expired` no genera solapamiento, aunque su rango coincida con el consultado.

6. **¿Qué ocurre si la habitación sigue en bloqueo técnico después de la fecha estimada de finalización?**
   El informe en estado `Applied` sigue generando solapamiento desde su fecha de inicio hasta que se confirme el fin de la reparación, porque la habitación continúa fuera de servicio.

7. **¿Qué ocurre si se consulta una habitación que no existe?**
   El sistema retorna un error de "habitación no encontrada", distinto de la verificación negativa por solapamiento.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al sistema del módulo de reservas en contextos donde esté autorizado y a miembros del personal de mantenimiento autenticados consultar información de mantenimientos.
- **FR-002**: El sistema DEBE retornar un valor de confirmación indicando si es válida o no la creación del evento basado en los parámetros de la consulta, no se realizan sugerencias adicionales en la respuesta.
- **FR-003**: El sistema DEBE recibir como parámetros de la consulta el identificador de una única habitación (`roomId`), la fecha de inicio y la fecha de fin del rango, y evaluarlos contra los campos `TechnicalBlockStartDate` y `EstimatedTechnicalBlockEndDate` de los `TechnicalBlockReport` de esa habitación.
- **FR-004**: El sistema DEBE asegurarse de que cada consulta realizada, independiente del actor que la realice, sea una operación de solo lectura (READ-ONLY).
- **FR-005**: El sistema DEBE validar que las fechas del rango consultado sean obligatorias, no nulas, con formato DD-MM-YYYY y correspondan a fechas reales de calendario (ej. rechazar 31-02-2026).
- **FR-006**: El sistema DEBE rechazar rangos cuya fecha de fin sea anterior a la fecha de inicio; un rango con fecha de fin igual a la de inicio es válido.
- **FR-007**: El sistema DEBE rechazar consultas cuyo rango inicie en una fecha anterior a la fecha actual del servidor.
- **FR-008**: El sistema DEBE restringir la duración máxima del rango consultado y la antelación máxima respecto a la fecha actual a los mismos límites configurables definidos en el caso de uso *Programar Bloqueo Técnico para Habitación* (valores por defecto sugeridos: 90 y 365 días), rechazando rangos que los excedan.
- **FR-009**: El sistema DEBE ejecutar las validaciones temporales (FR-005 a FR-008) antes de consultar la tabla de mantenimientos y DEBE retornar un error de validación que indique la regla específica incumplida, diferenciándolo de una respuesta de confirmación negativa por solapamiento.
- **FR-010**: El sistema DEBE tratar como solapamiento cualquier `TechnicalBlockReport` en estado `Scheduled` cuyo rango (`TechnicalBlockStartDate`, `EstimatedTechnicalBlockEndDate`) intersecte con el rango consultado, incluyendo coincidencia en los días extremos, y cualquier `TechnicalBlockReport` en estado `Applied` cuyo periodo, desde `TechnicalBlockStartDate` y sin fecha de fin mientras la reparación no se confirme, intersecte con el rango consultado, aunque ya se haya superado `EstimatedTechnicalBlockEndDate`; los informes en estado `Completed` o `Expired` NO DEBEN considerarse.
- **FR-011**: El sistema NO DEBE generar registros en `RoomStateHistory` ni modificar el estado de la habitación, ya que este caso de uso no ejecuta transiciones de estado.
- **FR-012**: El sistema DEBE rechazar la consulta con un error de "habitación no encontrada" cuando el `roomId` recibido no corresponda a una habitación registrada, sin evaluar mantenimientos y diferenciándolo de una respuesta de confirmación negativa por solapamiento.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel.
- **TechnicalBlockReport**: Entidad que representa el reporte generado por la programación de un mantenimiento (definida en el caso de uso *Programar Bloqueo Técnico para Habitación*). Solo los informes en estado `Scheduled` o `Applied` cuentan para el solapamiento.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: La consulta retorna el valor de confirmación relacionado a la consulta en menos de 200 milisegundos.
- **SC-002**: El 100% de las consultas con fechas nulas, inexistentes, pasadas, con rango invertido o fuera de los límites configurados son rechazadas con un error de validación sin consultar la tabla de mantenimientos.
