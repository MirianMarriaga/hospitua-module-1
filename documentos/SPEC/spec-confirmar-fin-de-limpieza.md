# Especificación del Caso de Uso: Confirmar Fin de Limpieza de Habitación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de limpieza
**Creado**: 2026-09-08

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Finalización de limpieza y disponibilidad de habitación (Prioridad: P1)

Como miembro del personal de limpieza quiero poder confirmar que he completado las labores de limpieza en una habitación, es importante que pueda formalizar mi participación en las labores designadas a mi área y notificar esta acción al sistema de forma oportuna para evitar posibles inconsistencias en otras operaciones del hotel.

**Por qué esta prioridad**: Es el cierre del ciclo de limpieza. Sin esta confirmación, la habitación queda bloqueada en medio del flujo de limpieza y disminuye la capacidad operativa del hotel.

**Prueba independiente**: Puede ser probada confirmando el fin de limpieza de una habitación en estado `InCleaning`, verificando que el sistema cierra la tarea con su fecha y hora de fin, deja la habitación en `Available` (o en `Reserved` / `TechnicalBlock` si tenía un pendiente vigente) y redirige al miembro del personal al panel general de limpieza.

**Escenarios de aceptación**:

1. **Escenario**: Fin de limpieza exitoso
   - **Dado que** El miembro del personal de limpieza está autenticado y la habitación "101" está en `InCleaning` con su tarea activa, sin pendientes
   - **Cuando** Confirma la finalización de labores de limpieza
   - **Entonces** El sistema cierra la `CleaningTask` y, mediante el caso de uso incluido *Marcar habitación como disponible*, deja la habitación en `Available`

2. **Escenario**: Fin de limpieza con reserva pendiente
   - **Dado que** La habitación "101" está en `InCleaning` con la tarea activa del usuario y tiene una reserva pendiente vigente (el evento de Módulo 2 llegó mientras estaba ocupada o en limpieza)
   - **Cuando** El miembro del personal confirma la finalización
   - **Entonces** *Marcar habitación como disponible* deja la habitación en `Available` y, en la misma operación, aplica la reserva pendiente: la habitación queda en `Reserved`

3. **Escenario**: Habitación no está en proceso de limpieza
   - **Dado que** La habitación "101" acaba de ser liberada (`PendingCleaning`) y nadie la ha tomado para limpiar
   - **Cuando** Un miembro del personal intenta confirmar el fin de limpieza
   - **Entonces** El sistema rechaza la operación informando el estado actual (FR-002)

4. **Escenario**: Intento de finalización de tarea ajena
   - **Dado que** La habitación "105" está en `InCleaning` con la tarea iniciada por "Usuario B"
   - **Cuando** "Usuario A" intenta confirmar el fin de limpieza
   - **Entonces** El sistema rechaza la operación porque la tarea pertenece a otro miembro del personal (FR-002); si la tarea era de "Usuario A" y el Administrador la liberó, se lo informa y lo devuelve al panel (FR-008)

---

### Casos Límite

1. **¿Qué ocurre si el miembro del personal confirma el fin, pero la habitación evidentemente no está limpia?**
   Es un problema operativo que el sistema no puede validar. El Gerente puede revisar el historial de estados para detectar tiempos de limpieza inusuales.

2. **¿Qué ocurre si durante la limpieza se detecta un daño en la habitación?**
   El miembro del personal no confirma el fin: desde la vista de tarea activa usa "Reportar daño", que ejecuta *Marcar habitación inhabilitada por reparaciones* desde `InCleaning` y cierra su tarea. Los pendientes de la habitación se conservan y se aplicarán cuando vuelva a `Available` tras la reparación y la limpieza, si siguen vigentes.

3. **¿Qué ocurre si el miembro del personal no puede terminar la limpieza (fin de turno, ausencia)?**
   El titular la libera con "Liberar tarea" desde su vista de tarea activa; la habitación vuelve a `PendingCleaning` y cualquier miembro puede iniciarla (regla en *Marcar habitación en limpieza*, FR-005; vista en `spec-consultar-panel-limpieza.md`).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al personal de limpieza autenticado confirmar el fin de limpieza y recibir retroalimentación visual de la acción que está realizando.
- **FR-002**: El sistema DEBE aceptar la confirmación únicamente desde habitaciones en `InCleaning` cuya `CleaningTask` activa pertenezca al usuario autenticado: solo el titular puede confirmar el fin de su tarea. En otro caso la rechaza informando el estado actual de la habitación y el motivo.
- **FR-003**: El sistema DEBE cerrar la `CleaningTask` con resultado `Completed`, registrando su fecha y hora de fin.
- **FR-004**: El sistema DEBE delegar la transición a `Available` en *Marcar habitación como disponible* (`<<includes>>`), que además aplica el pendiente vigente de la habitación si lo hay; este caso de uso no reimplementa esa lógica.
- **FR-005**: La acción **Confirmar fin** se ofrece en la vista de tarea activa del panel de limpieza, definida en `spec-consultar-panel-limpieza.md` junto con sus otras acciones (Reportar daño, de *Marcar habitación inhabilitada por reparaciones*, y Liberar tarea, de *Marcar habitación en limpieza*). Al confirmarse, el usuario queda sin tarea activa y vuelve al listado general del panel.
- **FR-006**: Antes de confirmar, el sistema DEBE pedir una confirmación explícita (texto definido en `spec-consultar-panel-limpieza.md`); si el usuario vuelve atrás, la tarea sigue abierta sin cambios.
- **FR-007**: Solo el Personal de limpieza autenticado DEBE poder confirmar el fin. Si la sesión expiró o el rol no corresponde, el sistema NO DEBE ejecutar nada: pide iniciar sesión o informa que la acción no está permitida.
- **FR-008**: Si el Administrador liberó la tarea (*Marcar habitación en limpieza*, FR-005) y el titular intenta confirmar el fin, el sistema DEBE rechazarlo, informarle "Tu tarea en la habitación [número] fue liberada por la administración." y devolverlo al panel.
- **FR-009**: `EndDateTime` DEBE generarse en el servidor en hora Colombia (UTC-5), ignorando cualquier hora enviada por el cliente, y nunca es anterior al `StartDateTime` de la tarea.
- **FR-010**: Si la confirmación compite con otra operación sobre la misma tarea (por ejemplo, la liberación por el Administrador), solo la primera DEBE proceder; la segunda se rechaza informando el estado actual de la habitación.
- **FR-011**: El cierre de la tarea, la transición a `Available`, la aplicación del pendiente vigente y el historial DEBEN confirmarse en una sola operación o no aplicar nada. Ante un fallo, la habitación sigue en `InCleaning` con la tarea abierta, se informa el error y el usuario puede reintentar.
- **FR-012**: Cada transición de la habitación (a `Available` y, si aplica, al estado del pendiente) DEBE registrarse en `RoomStateHistory` con el actor y el flujo de origen, dentro de la misma operación.
- **FR-013**: El sistema NO DEBE incluir inspección ni aprobación de la limpieza por un supervisor, checklists de aseo, inventario de insumos o lencería ni registro de objetos olvidados: la confirmación del titular es suficiente para cerrar la tarea.

### Entidades Clave

- **Room**: Transita de `InCleaning` a `Available` (y, si hay un pendiente vigente, a `Reserved` o `TechnicalBlock` en la misma operación).
- **CleaningTask**: Definida en *Marcar Habitación en Limpieza*. En este caso de uso se completan `EndDateTime` y `Outcome = Completed`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El miembro del personal de limpieza puede confirmar el fin de limpieza en una habitación en menos de 15 segundos.
- **SC-002**: El 100% de las confirmaciones cierran la `CleaningTask` con resultado `Completed` y su fecha y hora de fin.
- **SC-003**: El cambio de estado se refleja en el inventario y en los paneles en menos de 2 segundos.
- **SC-004**: El 100% de las habitaciones con un pendiente vigente quedan en el estado del pendiente (`Reserved` o `TechnicalBlock`) al confirmarse el fin de limpieza.
