# Especificación del Caso de Uso: Programar Bloqueo Técnico para Habitación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de mantenimiento (programación) / Módulo 1 (Autónomo, aplicación del bloqueo)
**Creado**: 2026-09-21

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Programación de un mantenimiento preventivo (Prioridad: P1)

Como miembro del personal de mantenimiento quiero poder programar mantenimientos preventivos en habitaciones, para así formalizar mi participación en labores definidas para mi área e indicar de forma documentada la necesidad de intervenir y realizar reparaciones, reemplazos o remociones de manera preventiva sobre una habitación de forma temporal.

**Por qué esta prioridad**: Permite ejecutar inspecciones y mantenimientos preventivos sin chocar con reservas, evitando asignar a huéspedes habitaciones que estarán en intervención.

**Prueba independiente**: Programar un mantenimiento sobre una habitación con una justificación y un rango válidos, verificar que se consultan reservas y mantenimientos, que se registra el `TechnicalBlockReport` en estado `Scheduled` y que, al llegar la fecha de inicio con la habitación en `Available`, el sistema la pasa a `TechnicalBlock` y la muestra en el panel de mantenimiento.

**Escenarios de aceptación**:

1. **Escenario**: Programación exitosa
   - **Dado que** Existe una habitación que no está en `DisabledForRepairs`, `TechnicalBlock` ni `Inactive`
   - **Cuando** El miembro del personal de mantenimiento la busca en su panel, pulsa "Programar bloqueo" e ingresa una justificación válida, fecha de inicio y fecha estimada de fin válidas
   - **Entonces** El sistema consulta reservas y mantenimientos de la habitación en ese rango, comprueba que no hay cruces y registra el `TechnicalBlockReport` en estado `Scheduled`, sin cambiar el estado de la habitación

2. **Escenario**: Rechazo por cruce con reservas
   - **Dado que** La habitación tiene una reserva en Módulo 2 dentro del rango solicitado
   - **Cuando** El miembro del personal de mantenimiento intenta programar el mantenimiento
   - **Entonces** El sistema rechaza la programación e indica que hay una reserva en ese rango para que se elija otro rango o se coordine con Módulo 2

3. **Escenario**: Rechazo por cruce con otro mantenimiento
   - **Dado que** La habitación ya tiene un mantenimiento programado (`Scheduled`) cuyo rango se cruza con el solicitado
   - **Cuando** El miembro del personal de mantenimiento intenta programar el mantenimiento
   - **Entonces** El sistema rechaza la programación e indica el rango del mantenimiento existente

4. **Escenario**: Datos inválidos
   - **Dado que** El miembro del personal de mantenimiento está autenticado
   - **Cuando** Intenta confirmar con la justificación vacía, una fecha de inicio pasada, una fecha de fin anterior a la de inicio, una fecha inexistente (ej. 31-02-2026) o un rango fuera de los límites
   - **Entonces** El sistema bloquea la confirmación indicando la regla incumplida, sin ejecutar las consultas de reservas ni mantenimientos

5. **Escenario**: Cancelación de un bloqueo programado por error
   - **Dado que** Existe un bloqueo en estado `Scheduled` (por ejemplo, con una fecha o habitación equivocada)
   - **Cuando** Un miembro del personal de mantenimiento pulsa "Cancelar" en el listado "Programados" del panel y confirma
   - **Entonces** El informe pasa a `Cancelled`, se registra en la bitácora quién lo canceló y cuándo, y deja de bloquear la habitación: Módulo 2 puede volver a asignarla en ese rango y el Administrador puede darla de baja

---

### Historia de Usuario 2 (Autónoma) - Aplicación del bloqueo (Prioridad: P1)

Como Módulo 1, quiero poner la habitación en bloqueo técnico cuando llegue la fecha programada, para que quede fuera del inventario operativo durante la intervención sin que nadie tenga que hacerlo a mano.

**Por qué esta prioridad**: Sin la aplicación automática, el mantenimiento programado nunca retiraría la habitación del inventario.

**Prueba independiente**: Con un bloqueo `Scheduled`, simular la llegada de la fecha de inicio con la habitación en `Available` y verificar que pasa a `TechnicalBlock`; repetir con la habitación `Occupied` y verificar que el bloqueo se aplica cuando la habitación vuelve a `Available` dentro del rango, o caduca si el rango termina antes.

**Escenarios de aceptación**:

1. **Escenario**: Aplicación en la fecha de inicio
   - **Dado que** Existe un bloqueo `Scheduled` cuya fecha de inicio es hoy y la habitación está en `Available`
   - **Cuando** Comienza el día de inicio
   - **Entonces** El sistema pasa la habitación a `TechnicalBlock` y el informe a `Applied`

2. **Escenario**: Bloqueo programado para hoy
   - **Dado que** La habitación está en `Available`
   - **Cuando** El técnico programa un bloqueo cuya fecha de inicio es hoy
   - **Entonces** El sistema registra el informe y, en la misma operación, pasa la habitación a `TechnicalBlock` y el informe a `Applied`

3. **Escenario**: Aplicación diferida
   - **Dado que** Al comenzar el día de inicio la habitación está `Occupied` (o en limpieza o en reparación)
   - **Cuando** La habitación vuelve a `Available` dentro del rango programado
   - **Entonces** *Marcar habitación como disponible* aplica el bloqueo pendiente: la habitación queda en `TechnicalBlock` y el informe en `Applied`

4. **Escenario**: Caducidad
   - **Dado que** El rango programado termina sin que la habitación haya estado en `Available`
   - **Cuando** Termina la fecha estimada de fin
   - **Entonces** El informe pasa a `Expired`, se registra en la bitácora y deja de bloquear la habitación; si el mantenimiento sigue siendo necesario, se programa uno nuevo

---

### Casos Límite

1. **Fallo en la consulta de reservas o de mantenimientos**: No se registra la programación y se informa que no fue posible verificar; el usuario puede reintentar (FR-015).
2. **Dos programaciones simultáneas con rangos cruzados**: Solo la primera se registra; la segunda se rechaza por cruce (FR-014).
3. **Daño mayor detectado durante el bloqueo**: El técnico cierra la intervención con *Confirmar reparación finalizada* y, si hace falta, reporta el daño cuando la habitación vuelva a `Available`.
4. **Rango de un solo día**: Una fecha de fin igual a la de inicio es válida.
5. **Habitación dada de baja con un bloqueo `Scheduled`**: No ocurre; *Dar de baja habitación* rechaza la baja mientras exista un bloqueo `Scheduled`. Si el bloqueo ya no se necesita, se cancela primero (HU-1, escenario 5).
6. **Cancelar un bloqueo ya aplicado**: No es posible; un bloqueo `Applied` termina con *Confirmar reparación finalizada*. Solo se cancelan bloqueos `Scheduled`.
7. **El bloqueo deja de estar programado mientras se confirma su cancelación** (se aplicó al comenzar el día, caducó o lo canceló otro técnico): la cancelación se rechaza informando su estado actual (`Applied`, `Expired` o `Cancelled`) y el listado "Programados" se actualiza (FR-011, FR-014).
8. **Falla la aplicación o la caducidad automática** (por ejemplo, un error al comenzar el día): se reintenta automáticamente mientras el bloqueo siga vigente y el fallo queda en la bitácora (FR-016).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al Personal de mantenimiento programar mantenimientos, sobre habitaciones que no estén en `DisabledForRepairs`, `TechnicalBlock` ni `Inactive` (una habitación que ya está en intervención se programa después de confirmar su reparación). El punto de entrada es la acción "Programar bloqueo" de la búsqueda del panel de mantenimiento (`spec-consultar-panel-mantenimiento.md`).
- **FR-002**: El sistema DEBE exigir una justificación técnica (no vacía, máximo 500 caracteres), una fecha de inicio (`TechnicalBlockStartDate`) y una fecha estimada de fin (`EstimatedTechnicalBlockEndDate`), ambas en formato DD-MM-YYYY y existentes en el calendario.
- **FR-003**: El sistema DEBE rechazar una fecha de inicio anterior a hoy, una fecha de fin anterior a la de inicio, un rango de más de 90 días y una fecha de inicio a más de 365 días de hoy, indicando la regla incumplida.
- **FR-004**: Solo con los datos válidos, el sistema DEBE verificar que no haya cruces en el rango mediante:
  - *Consultar reservas* (`<<includes>>`, consulta REST a Módulo 2 con `roomId`, `startDate`, `endDate`). Cuentan como cruce las reservas en estado `ACTIVE` o `CHECKED_IN`, con el mismo criterio que *Dar de baja habitación*.
  - *Consultar información de mantenimientos*.
- **FR-005**: Si alguna verificación encuentra un cruce, el sistema DEBE rechazar la programación indicando el motivo.
- **FR-006**: Si no hay cruces, el sistema DEBE registrar el `TechnicalBlockReport` en estado `Scheduled`, sin cambiar el estado de la habitación.
- **FR-007**: Al comenzar el día de inicio, si la habitación está en `Available`, el sistema DEBE pasarla a `TechnicalBlock` (actor `SYSTEM`) y marcar el informe como `Applied`. Si la fecha de inicio es hoy y la habitación está en `Available` en el momento de programar, la aplicación ocurre en la misma operación de registro. Si no está en `Available`, el bloqueo queda pendiente y lo aplica *Marcar habitación como disponible* cuando la habitación vuelva a `Available` dentro del rango.
- **FR-008**: Al terminar la fecha estimada de fin, un informe que siga en `Scheduled` DEBE pasar a `Expired` y registrarse en la bitácora.
- **FR-009**: El sistema DEBE ofrecer la acción **Ver informe** sobre los bloqueos `Scheduled` y `Applied`, mostrando por separado la justificación técnica, el responsable, la fecha de inicio, la fecha estimada de fin y la fecha y hora de la programación.
- **FR-010**: Las habitaciones en `TechnicalBlock` se gestionan desde el panel de mantenimiento (`spec-consultar-panel-mantenimiento.md`). La salida de `TechnicalBlock` solo ocurre por *Confirmar reparación finalizada*.
- **FR-011**: El sistema DEBE permitir al Personal de mantenimiento cancelar un bloqueo en estado `Scheduled`, previa confirmación, desde el listado "Programados" del panel de mantenimiento (`spec-consultar-panel-mantenimiento.md`). El informe pasa a `Cancelled` y se registra en la bitácora quién lo canceló y cuándo. Los bloqueos `Applied` no se cancelan. Si al confirmar el bloqueo ya no está en `Scheduled`, la cancelación se rechaza informando su estado actual.
- **FR-012**: Solo el Personal de mantenimiento autenticado DEBE poder programar o cancelar un bloqueo. Si la sesión expiró o el rol no corresponde, el sistema NO DEBE ejecutar nada: pide iniciar sesión o informa que la acción no está permitida.
- **FR-013**: `ReportDateTime`, la fecha y hora de cancelación y la de aplicación DEBEN generarse en el servidor en hora Colombia (UTC-5), ignorando cualquier hora enviada por el cliente. "Hoy", para validar y aplicar las fechas, es la fecha del servidor en esa misma zona.
- **FR-014**: Si dos operaciones compiten (dos programaciones con rangos cruzados sobre la misma habitación, o una cancelación y la aplicación del mismo bloqueo), solo la primera DEBE proceder; la segunda se rechaza informando el motivo o el estado actual.
- **FR-015**: La programación (verificaciones, registro del informe y, si empieza hoy, la aplicación) y la cancelación DEBEN confirmarse completas o no aplicar nada. Si una verificación no responde o falla, no se registra nada, se informa que no fue posible verificar y el usuario puede reintentar.
- **FR-016**: La aplicación (FR-007) y la caducidad (FR-008) son operaciones autónomas: si fallan, el sistema DEBE reintentarlas automáticamente mientras el bloqueo siga vigente y registrar cada fallo en la bitácora; ninguna se pierde sin dejar rastro.
- **FR-017**: La transición a `TechnicalBlock` DEBE registrarse en `RoomStateHistory` con el actor y el flujo de origen, dentro de la misma operación.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Pasa de `Available` a `TechnicalBlock` al aplicarse el bloqueo.
- **TechnicalBlockReport**: Informe del mantenimiento programado. Almacena:
  - **RoomId**: Identificador de la habitación
  - **MaintenanceStaffMemberId**: Miembro del personal de mantenimiento que programó
  - **TechnicalBlockReason**: Justificación técnica
  - **TechnicalBlockStartDate**: Fecha de inicio (DD-MM-YYYY)
  - **EstimatedTechnicalBlockEndDate**: Fecha estimada de fin (DD-MM-YYYY)
  - **Status**: `Scheduled` (programado, aún no aplicado), `Applied` (la habitación pasó a `TechnicalBlock`), `Expired` (el rango terminó sin aplicarse) o `Cancelled` (cancelado por mantenimiento antes de aplicarse)
  - **ReportDateTime**: Fecha y hora de la programación

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las programaciones válidas ejecutan ambas verificaciones y quedan registradas en menos de 2 segundos.
- **SC-002**: El 100% de las programaciones con cruce de reservas o mantenimientos, o con datos inválidos, son rechazadas.
- **SC-003**: El 100% de los bloqueos `Scheduled` terminan en `Applied`, `Expired` o `Cancelled`; ninguno queda programado indefinidamente.
- **SC-004**: El 100% de las habitaciones en `TechnicalBlock` quedan fuera del inventario operativo de inmediato.
