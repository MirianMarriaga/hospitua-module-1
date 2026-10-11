# Especificación de Funcionalidad: Dar de Baja Habitación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Administrador
**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Retirar una habitación del inventario activo (Prioridad: P1)

Como Administrador, quiero dar de baja una habitación existente en el inventario, para excluirla de futuros usos cuando ya no está disponible para operar, sin perder el historial de uso y consumos ya asociado a ella.

**Por qué esta prioridad**: Es una operación esencial del ciclo de vida de la habitación dentro del Módulo 1 (Gestión de Habitaciones e Inventario). Sin ella, habitaciones que ya no deben operar seguirían apareciendo como disponibles, afectando la integridad del inventario. Corresponde directamente al caso de uso "Dar de baja habitación" del diagrama oficial.

**Prueba Independiente**: Puede probarse de forma independiente iniciando sesión como Administrador, seleccionando una habitación sin actividad pendiente ni huéspedes actuales, confirmando la baja, y verificando que se invoque el caso de uso incluido "Consultar reservas" y que la habitación deje de aparecer en los resultados de disponibilidad.

**Escenarios de Aceptación**:

1. **Escenario**: Baja exitosa de una habitación sin actividad pendiente
   - **Dado** una habitación existe en estado "Available" y no tiene reservas vigentes asignadas en Módulo 2 desde la fecha actual en adelante
   - **Cuando** el Administrador selecciona la habitación, indica el motivo de la baja y confirma la acción de dar de baja
   - **Entonces** el sistema ejecuta el caso de uso incluido "Consultar reservas" (`<<includes>>`), comprueba que no existen reservas asignadas, cambia el estado de la habitación a "Inactive", la excluye de los resultados de disponibilidad y conserva su historial

2. **Escenario**: Intento de baja sobre una habitación ocupada
   - **Dado** una habitación se encuentra en estado "Occupied" (huésped en sitio)
   - **Cuando** el Administrador intenta darla de baja
   - **Entonces** el sistema rechaza la operación y muestra un mensaje indicando que la habitación tiene un huésped activo, validando que debe estar en estado "Available".

3. **Escenario**: Rechazo de la baja por reservas vigentes
   - **Dado** una habitación en estado "Available" que tiene una o más reservas vigentes asignadas en Módulo 2 desde la fecha actual en adelante, sin importar qué tan lejana sea su fecha
   - **Cuando** el Administrador intenta darla de baja
   - **Entonces** el sistema invoca "Consultar reservas" con el `roomId` de la habitación, recibe una o más reservas vigentes, rechaza la baja, mantiene la habitación en "Available" y alerta al Administrador listando por cada reserva en conflicto su `reservationRef`, fechas de estadía (`startDate`, `endDate`) y estado (`status`), para que sean reasignadas antes de reintentar la baja.

4. **Escenario**: Reactivación de una habitación dada de baja
   - **Dado** una habitación se encuentra en estado "Inactive"
   - **Cuando** el Administrador ejecuta el caso de uso "Marcar habitación como disponible" para revertir la baja
   - **Entonces** el sistema cambia el estado de la habitación directamente de "Inactive" a "Available"

5. **Escenario**: Confirmación explícita antes de ejecutar la baja
   - **Dado** el Administrador ha seleccionado una habitación elegible para ser dada de baja
   - **Cuando** inicia la acción de dar de baja
   - **Entonces** el sistema solicita una confirmación explícita antes de aplicar el cambio, dado el impacto de la operación sobre el inventario

---

### Casos Borde

- Habitación ya dada de baja: el sistema no muestra la opción de dar de baja para habitaciones que ya están en estado "Inactive".
- Reactivación de una habitación: el Administrador puede revertir la baja y devolver la habitación al estado "Available" mediante el caso de uso "Marcar habitación como disponible".
- Pérdida de conexión o fallo del proceso tras confirmar: la transacción se cancela, evitando que la habitación quede en un estado intermedio inconsistente.
- Fallo o indisponibilidad de Módulo 2 al consultar reservas: si la invocación de "Consultar reservas" presenta una caída de red o no responde, el sistema cancela la operación, la habitación permanece en estado "Available" y se informa al Administrador que la baja no pudo verificarse, ofreciéndole la opción "Reintentar", que vuelve a ejecutar la verificación de reservas con el mismo motivo ya registrado.
- Reservas lejanas: la consulta no tiene límite superior de fechas, por lo que cualquier reserva vigente desde hoy impide la baja. Así ninguna reserva queda sobre una habitación dada de baja, y Módulo 2 tampoco puede crear nuevas, porque *Consultar inventario de habitaciones* no le ofrece habitaciones "Inactive".
- Motivo de baja sin categoría: el sistema impide confirmar la baja hasta que el Administrador seleccione una categoría de motivo.
- Detalle demasiado largo: el campo de texto no admite más de 500 caracteres.
- Motivo "Otro" sin descripción: si el Administrador selecciona la categoría "Otro" y deja vacío el campo de texto (o solo con espacios), el sistema impide continuar e indica que debe escribir el motivo de la baja.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor "Administrador" dar de baja habitaciones del inventario.

- **FR-002**: El sistema DEBE invocar obligatoriamente el caso de uso "Consultar reservas" (`<<includes>>`) antes de ejecutar la baja, enviando el identificador de la habitación (`roomId`), `startDate` = fecha actual y sin límite superior de fechas ("Consultar reservas", FR-007), y recibir la lista de `ReservationSummary` de las reservas vigentes asignadas a esa habitación desde hoy.

- **FR-003**: El sistema DEBE impedir dar de baja una habitación que no se encuentre en estado "Available" ("Reserved", "Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"), independientemente de la causa que originó ese estado, según documentos/SPEC/referencias/maquina-estados-habitacion.md.

- **FR-004**: El sistema DEBE rechazar la baja si la consulta devuelve al menos una reserva (Módulo 2 solo devuelve las vigentes: `PENDING`, `ACTIVE` o `IN_PROGRESS`), manteniendo la habitación en estado "Available" e informando al Administrador la `reservationRef`, las fechas (`startDate`, `endDate`) y el estado de cada reserva en conflicto. Si la consulta falla o Módulo 2 no responde, el sistema DEBE cancelar la operación sin modificar el estado de la habitación.

- **FR-005**: El sistema DEBE solicitar una confirmación explícita del Administrador antes de ejecutar la baja.

- **FR-006**: El sistema DEBE cambiar el estado de la habitación a "Inactive" tras confirmar la baja.

- **FR-007**: El sistema DEBE registrar el usuario responsable y la fecha/hora en que se ejecutó la baja, para efectos de trazabilidad.

- **FR-008**: El sistema DEBE exigir el registro de un motivo de baja mediante la selección obligatoria de una categoría predefinida (remodelación permanente, cierre definitivo de piso, decisión administrativa, otro), que puede complementarse con un campo de texto libre para detalles adicionales. El detalle es opcional, salvo cuando la categoría seleccionada es "Otro": en ese caso el Administrador DEBE escribir obligatoriamente el motivo de la baja en el campo de texto. El campo de texto admite como máximo 500 caracteres.

- **FR-009**: Si la verificación de reservas falla porque Módulo 2 no responde, el sistema DEBE ofrecer al Administrador la opción "Reintentar", que repite la consulta de reservas sin volver a solicitar el motivo ni la confirmación.

- **FR-010**: En el paso de selección del motivo, en la confirmación y en el resultado exitoso, el sistema DEBE mostrar los datos de la habitación (número, piso, tipo y estado). En la confirmación y en el resultado exitoso DEBE mostrar además el motivo seleccionado y el detalle, si existe, y en el resultado exitoso DEBE indicar que la habitación ya no aparece entre las habitaciones disponibles. Durante la verificación de reservas, el sistema DEBE indicar que está comprobando las reservas de la habitación.

- **FR-011**: Al ejecutar la baja, el sistema DEBE registrar la transición en `RoomStateHistory` dentro de la misma transacción del cambio de estado: cerrar el periodo abierto de la habitación (`EndDateTime`) y abrir uno nuevo con `Status` = `Inactive`, `PreviousStatus` = `Available`, `StartDateTime` = fecha y hora del servidor, `ActorId` = Administrador responsable y `SourceFlow` = *Dar de baja habitación*.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Administrator**: Actor responsable de gestionar el inventario de habitaciones, incluyendo su registro, edición y baja.
- **ReservationSummary**: Estructura devuelta por Módulo 2 a través de "Consultar reservas" (ver diccionario). En este caso de uso se usan `reservationRef`, `roomId`, `startDate`, `endDate` y `status`: cualquier reserva devuelta bloquea la baja (FR-004) y sus campos se muestran al Administrador cuando se rechaza.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Administrador puede completar la baja de una habitación elegible en menos de 1 minuto.
- **SC-002**: El 100% de los intentos de baja sobre habitaciones que no estén en estado "Available" son rechazados por el sistema.
- **SC-003**: El 0% de las habitaciones dadas de baja aparece en los resultados de disponibilidad.
- **SC-004**: El 100% de las bajas ejecutadas quedan registradas con usuario responsable, fecha/hora y motivo, sin pérdida del historial previo de la habitación.
- **SC-005**: El 100% de los intentos de baja sobre habitaciones con reservas vigentes asignadas desde la fecha actual son rechazados, sin que la habitación cambie de estado.
