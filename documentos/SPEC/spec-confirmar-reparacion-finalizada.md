# Especificación del Caso de Uso: Confirmar Reparación Finalizada

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de mantenimiento
**Creado**: 2026-09-04

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Inicio de reparaciones desde el panel de mantenimiento (Prioridad: P1)

Como miembro del personal de mantenimiento quiero ver las habitaciones que requieren intervención y tomar una para empezar a repararla, para que quede claro que alguien está trabajando en ella.

**Por qué esta prioridad**: Es la entrada al ciclo de mantenimiento: sin una tarea iniciada, nadie puede confirmar el fin de la reparación.

**Prueba independiente**: Entrar al panel de mantenimiento, pulsar "Iniciar reparaciones" sobre una habitación en `DisabledForRepairs` o `TechnicalBlock` sin tarea abierta y verificar que se crea la `ReparationTask` del técnico, que el estado de la habitación no cambia y que el técnico pasa a la vista de tarea activa.

**Escenarios de aceptación**:

1. **Escenario**: Inicio de reparaciones
   - **Dado que** El técnico no tiene tareas activas y la habitación "101" está en `DisabledForRepairs` (o `TechnicalBlock`) sin tarea de reparación abierta
   - **Cuando** Pulsa "Iniciar reparaciones" sobre la habitación "101"
   - **Entonces** El sistema crea la `ReparationTask` con el técnico y la fecha y hora de inicio, sin cambiar el estado de la habitación, y lo redirige a la vista de tarea activa

2. **Escenario**: Habitación con una reparación en curso
   - **Dado que** La habitación "102" tiene una `ReparationTask` abierta de otro técnico
   - **Cuando** Un técnico consulta el panel
   - **Entonces** La habitación no ofrece "Iniciar reparaciones": pertenece a su titular hasta que este la confirme o la libere (FR-011)

---

### Historia de Usuario 2 - Finalización de reparación y pase a limpieza (Prioridad: P1)

Como miembro del personal de mantenimiento quiero poder indicar que he finalizado las labores de reparación o revisión técnica en una habitación, para así poder formalizar mi participación en las labores definidas para mi área e indicar que la unidad se encuentra nuevamente disponible para ser asignada a labores de limpieza previo a su habilitación comercial.

**Por qué esta prioridad**: Es el cierre del ciclo de mantenimiento. Sin esta confirmación, la habitación queda bloqueada en el inventario por motivos técnicos y la tarea del técnico queda abierta indefinidamente.

**Prueba independiente**: Desde la vista de tarea activa, confirmar el fin de la reparación y verificar que la habitación pasa a `PendingCleaning`, que la `ReparationTask` se cierra con resultado `Completed` y su fecha y hora de fin, y que el técnico vuelve al panel general de mantenimiento.

**Escenarios de aceptación**:

1. **Escenario**: Fin de reparación exitoso
   - **Dado que** El técnico tiene la tarea activa de la habitación "101", en `DisabledForRepairs` (o `TechnicalBlock`)
   - **Cuando** Confirma que ha finalizado las labores de reparación
   - **Entonces** El sistema, mediante *Marcar pendiente a limpieza*, pasa la habitación a `PendingCleaning`, cierra la `ReparationTask` con resultado `Completed` y libera al técnico de la tarea activa

2. **Escenario**: Intento de finalización de una tarea ajena o liberada
   - **Dado que** La tarea de la habitación "105" pertenece a "Usuario B", o la tarea de "Usuario A" fue liberada por el Administrador
   - **Cuando** "Usuario A" intenta confirmar el fin de la reparación
   - **Entonces** El sistema rechaza la operación (FR-006) y, si su tarea fue liberada, se lo informa y lo devuelve al panel (FR-011)

---

### Historia de Usuario 3 - Liberar una reparación que no puedo terminar (Prioridad: P2)

Como miembro del personal de mantenimiento, quiero poder dejar una reparación que no podré terminar (fin de turno, falta de repuestos u otra prioridad), para que otro técnico pueda iniciarla sin que nadie se quede con mi tarea sin mi consentimiento.

**Por qué esta prioridad**: Solo el titular puede cerrar su tarea (FR-011). Sin esta opción, una reparación que el titular no puede terminar dejaría la habitación sin salida.

**Prueba independiente**: Con una `ReparationTask` abierta de "Usuario B" sobre la habitación "102", pulsar "Liberar tarea" como "Usuario B", confirmar y verificar que la tarea queda cerrada como `Released`, que la habitación sigue en su estado y que vuelve a ofrecer "Iniciar reparaciones" a cualquier técnico.

**Escenarios de aceptación**:

1. **Escenario**: El titular libera su tarea
   - **Dado que** "Usuario B" tiene la tarea activa de la habitación "102", en `DisabledForRepairs`
   - **Cuando** Pulsa "Liberar tarea" en su vista de tarea activa y acepta la confirmación (texto definido en `spec-consultar-panel-mantenimiento.md`)
   - **Entonces** El sistema cierra la `ReparationTask` con resultado `Released`, la habitación sigue en `DisabledForRepairs` sin tarea abierta y "Usuario B" vuelve al panel general

2. **Escenario**: Otro técnico continúa la reparación
   - **Dado que** La habitación "102" quedó sin tarea abierta tras ser liberada
   - **Cuando** "Usuario A", sin tarea activa, pulsa "Iniciar reparaciones" sobre ella
   - **Entonces** Se crea una `ReparationTask` nueva para "Usuario A" con el mismo reporte de origen (HU-1, escenario 1)

3. **Escenario**: Liberación por el Administrador (titular ausente)
   - **Dado que** "Usuario B" no está y su tarea en la habitación "102" sigue abierta
   - **Cuando** El Administrador pulsa "Liberar tarea" sobre la habitación "102" en *Consultar inventario de habitaciones* y confirma
   - **Entonces** Se produce el mismo efecto que en el HU-3, escenario 1

---

### Casos Límite

1. **¿Qué ocurre si el técnico confirma el fin, pero la habitación sigue presentando fallas?**
   Es un problema de calidad que el sistema no puede validar; el historial permite auditar qué técnico cerró la tarea.

2. **¿Qué ocurre si se detecta una avería distinta a la original?**
   El técnico confirma la reparación actual para cerrar su tarea; la nueva avería se reporta después con *Marcar habitación inhabilitada por reparaciones* cuando la habitación pase por limpieza o quede `Available`.

3. **¿Qué ocurre si dos técnicos pulsan "Iniciar reparaciones" sobre la misma habitación a la vez?**
   Solo el primero procede; el segundo recibe un error con el estado actual (FR-013).

4. **¿Qué ocurre si un técnico con una tarea activa intenta iniciar otra?**
   No es posible: mientras tenga una tarea activa solo ve la vista de tarea activa (FR-010).

5. **¿Puede un técnico tomar directamente la reparación que otro tiene en curso?**
   No. La tarea pertenece a su titular hasta que la confirma o la libera; si está ausente, solo el Administrador puede liberarla (FR-003, FR-011).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

#### Inicio y liberación de la reparación

- **FR-001**: El punto de entrada de este caso de uso es el **panel de mantenimiento**, definido en `spec-consultar-panel-mantenimiento.md` (pestañas, columnas, orden, búsqueda de cualquier habitación, vista de tarea activa y textos). Desde él se inicia **Iniciar reparaciones** sobre habitaciones en `DisabledForRepairs` o `TechnicalBlock` sin tarea abierta; desde la vista de tarea activa, **Confirmar fin** y **Liberar tarea**.
- **FR-002**: **Iniciar reparaciones** DEBE crear la `ReparationTask` (técnico autenticado, habitación, reporte de origen, fecha y hora de inicio) y llevar al técnico a la vista de tarea activa. No cambia el estado de la habitación.
- **FR-003**: **Liberar tarea** permite al titular dejar una reparación que no podrá terminar (fin de turno, falta de repuestos u otra prioridad). DEBE pedir confirmación y, al confirmarse, cerrar la `ReparationTask` con resultado `Released` y quién la liberó, sin cambiar el estado de la habitación, que queda disponible para que cualquier técnico la inicie (FR-002). Solo puede ejecutarla el titular o, si el titular está ausente, el Administrador desde *Consultar inventario de habitaciones*.

#### Confirmación del fin

- **FR-004**: Mientras su `ReparationTask` siga abierta, el técnico solo accede a la vista de tarea activa (FR-010), definida en `spec-consultar-panel-mantenimiento.md`, con las acciones **Confirmar fin**, **Liberar tarea** y **Ver informe** (solo lectura, muestra el reporte de origen indicado en `SourceReportId`, para consultar el daño o la justificación mientras repara).
- **FR-005**: Antes de confirmar el fin, el sistema DEBE pedir una confirmación explícita advirtiendo que la habitación pasará a la cola de limpieza.
- **FR-006**: El sistema DEBE aceptar la confirmación solo si la habitación está en `DisabledForRepairs` o `TechnicalBlock` y la `ReparationTask` abierta pertenece al técnico autenticado; en otro caso la rechaza informando el estado actual de la habitación y el motivo.
- **FR-007**: El sistema DEBE delegar la transición a `PendingCleaning` en *Marcar pendiente a limpieza* (`<<includes>>`), sin reimplementar su lógica, y cerrar la `ReparationTask` con resultado `Completed` y su fecha y hora de fin en la misma operación.
- **FR-008**: Tras confirmar el fin o liberar la tarea, el técnico queda sin tarea activa y vuelve al panel general de mantenimiento.

#### Reglas de la tarea y de la operación

- **FR-009**: Solo el Personal de mantenimiento autenticado DEBE poder iniciar, confirmar o liberar su propia reparación; el Administrador solo puede liberar tareas (FR-003). Si la sesión expiró o el rol no corresponde, el sistema NO DEBE ejecutar nada: pide iniciar sesión o informa que la acción no está permitida.
- **FR-010**: Una habitación DEBE tener como máximo una `ReparationTask` abierta y cada técnico como máximo una tarea activa. El sistema DEBE rechazar "Iniciar reparaciones" si el técnico ya tiene una tarea abierta o si la habitación ya la tiene.
- **FR-011**: Solo el titular DEBE poder cerrar su `ReparationTask` (confirmar el fin o liberarla); ningún otro técnico puede quedarse con ella. La única excepción es la liberación por el Administrador (FR-003). Si el Administrador liberó la tarea y el titular intenta operar sobre ella, el sistema DEBE informarle "Tu tarea en la habitación [número] fue liberada por la administración." y devolverlo al panel.
- **FR-012**: `StartDateTime` y `EndDateTime` DEBEN generarse en el servidor en hora Colombia (UTC-5), ignorando cualquier hora enviada por el cliente. La hora de fin nunca es anterior a la de inicio, y una hora registrada no se modifica.
- **FR-013**: Si dos operaciones compiten sobre la misma habitación o la misma tarea (dos técnicos inician la misma habitación, o el titular confirma mientras el Administrador libera), solo la primera DEBE proceder; la segunda se rechaza informando el estado actual de la habitación.
- **FR-014**: Iniciar, liberar y confirmar el fin (con *Marcar pendiente a limpieza* y el historial) DEBEN confirmarse completos o no aplicar nada. Ante un fallo, la habitación y la tarea conservan su estado, se informa el error y el usuario puede reintentar.
- **FR-015**: La transición a `PendingCleaning` DEBE registrarse en `RoomStateHistory` con el técnico y el flujo de origen, dentro de la misma operación. Iniciar y liberar una reparación no cambian el estado y no generan historial de estados.
- **FR-016**: El sistema NO DEBE incluir asignación de reparaciones por un supervisor, checklists de reparación ni inventario de repuestos: cada técnico inicia sus tareas desde el panel.

### Entidades Clave

- **Room**: Transita de `DisabledForRepairs` o `TechnicalBlock` a `PendingCleaning` al confirmar el fin; liberar la tarea no cambia su estado.
- **ReparationTask**: Vinculación del técnico con la habitación que repara. Almacena:
  - **MaintenanceStaffMemberId**: Técnico titular
  - **RoomId**: Habitación intervenida
  - **SourceReportId**: Reporte que llevó la habitación a su estado actual: el `DamageReport` más reciente (si está en `DisabledForRepairs`) o el `TechnicalBlockReport` en estado `Applied` (si está en `TechnicalBlock`)
  - **StartDateTime**: Fecha y hora de inicio (al pulsar "Iniciar reparaciones")
  - **EndDateTime**: Fecha y hora de cierre
  - **Outcome**: Resultado del cierre: `Completed` (fin confirmado) o `Released` (liberada por el titular o por el Administrador); vacío mientras la tarea está abierta
  - **ReleasedBy**: Solo si `Outcome = Released`: quién la liberó (el titular o el Administrador)

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El técnico puede iniciar, liberar o confirmar el fin de una reparación en menos de 3 clics, sin redactar justificaciones.
- **SC-002**: La habitación pasa a la cola de limpieza (`PendingCleaning`) en menos de 2 segundos tras la confirmación.
- **SC-003**: El 100% de las confirmaciones cierran la `ReparationTask` correspondiente con resultado `Completed`.
- **SC-004**: Cero habitaciones con más de una `ReparationTask` abierta, y ninguna habitación en intervención sin posibilidad de cierre.
- **SC-005**: Cero tareas de reparación cambian de titular sin haber sido liberadas antes.

> Los criterios de presentación del panel de mantenimiento (listados, orden, búsqueda, mensajes) se miden en `spec-consultar-panel-mantenimiento.md`.
