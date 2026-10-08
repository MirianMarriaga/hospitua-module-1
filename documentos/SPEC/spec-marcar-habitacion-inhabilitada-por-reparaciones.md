# Especificación del Caso de Uso: Marcar Habitación Inhabilitada por Reparaciones

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de limpieza / Personal de mantenimiento
**Creado**: 2026-09-08

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Reporte de daño en habitación (Prioridad: P1)

Como miembro del personal de limpieza o mantenimiento quiero poder reportar daños físicos o estructurales en una habitación, para así poder formalizar la labor de reporte de anomalías sobre unidades habitacionales, agilizando la intervención del personal calificado en la resolución de los conflictos descritos en el informe generado por el reporte.

**Por qué esta prioridad**: Es una funcionalidad crítica para mantener los estándares de calidad del hotel. Las habitaciones con daños deben ser reportadas y retiradas del inventario operativo del hotel de forma temporal para evitar asignaciones, mitigar malas experiencias con los huéspedes y prevenir el deterioro prolongado de la infraestructura.

**Prueba independiente**: Puede ser probada autenticándose en el sistema como miembro del personal de limpieza o mantenimiento, seleccionando una habitación con estado `Available` (o, para limpieza, la habitación de su tarea activa en `InCleaning`), diligenciando y enviando el formulario de reporte con una descripción textual válida del daño y verificando que el sistema transiciona la habitación a `DisabledForRepairs`, persiste un nuevo registro en la entidad de reportes vinculado al usuario en sesión, la habitación junto con la fecha y hora del reporte, retira la habitación del inventario operativo y del panel de limpieza y verificando su aparición en el panel de mantenimiento con el estado `DisabledForRepairs`.

**Escenarios de aceptación**:

1. **Escenario**: Reporte exitoso de daño estructural o físico
   - **Dado que** El miembro del personal (de limpieza o mantenimiento) está autenticado y existe una habitación "101" cuyo estado actual es `Available`
   - **Cuando** El miembro del personal (de limpieza o mantenimiento) diligencia y envía el reporte adjuntando la descripción textual detallada del daño
   - **Entonces** El sistema transiciona el estado de la habitación a `DisabledForRepairs`, genera un registro histórico con la justificación ingresada, vinculándolo con el autor del reporte junto con la fecha y hora proporcionadas por el servidor.

2. **Escenario**: Reporte de daño durante una limpieza activa
   - **Dado que** El miembro del personal de limpieza está autenticado y tiene la tarea activa de limpieza de la habitación "102" en estado `InCleaning`
   - **Cuando** Desde la vista de tarea activa pulsa "Reportar daño" y envía el reporte con una descripción válida
   - **Entonces** El sistema cierra su `CleaningTask` con resultado `DamageReported`, transiciona la habitación de `InCleaning` a `DisabledForRepairs`, genera el `DamageReport` y redirige al miembro del personal al panel general de limpieza, todo en una misma operación atómica

3. **Escenario**: Intento de reporte fallido por omisión de justificación (Fallo de validación)
   - **Dado que** El miembro del personal (de limpieza o mantenimiento) está autenticado e intenta reportar un daño en la habitación "101"
   - **Cuando** El miembro del personal (de limpieza o mantenimiento) intenta enviar la confirmación del reporte dejando nulo o vacío el campo correspondiente a la descripción del daño
   - **Entonces** El sistema evita la confirmación de realización del reporte, mantiene el estado actual de la habitación y expone un error de validación indicando que la descripción del daño es un campo estrictamente obligatorio.

4. **Escenario**: Intento de reporte sobre unidad habitacional con estado incompatible
   - **Dado que** El miembro del personal (de limpieza o mantenimiento) está autenticado y existe una habitación "105" en estado `Occupied` (o cualquier estado distinto a `Available` o `InCleaning`)
   - **Cuando** El miembro del personal (de limpieza o mantenimiento) intenta reportar un daño en dicha habitación
   - **Entonces** El sistema evita la realización del reporte, bloquea la alteración del estado y retorna retroalimentación visual indicando el estado de la habitación y que la acción no está permitida sobre dicha habitación, y actualiza la vista para dicho usuario.

5. **Escenario**: Intento de reporte sobre una limpieza ajena
   - **Dado que** La habitación "106" está en `InCleaning` con la tarea activa de "Usuario B"
   - **Cuando** "Usuario A" (limpieza o mantenimiento) intenta reportar un daño en ella
   - **Entonces** El sistema rechaza el reporte indicando que solo el titular de la tarea de limpieza activa puede reportar daños durante la limpieza.

---

### Casos Límite

1. **¿Qué ocurre si dos actores intentan inhabilitar la misma habitación a la vez?**
   Solo el primero procede; el segundo recibe el estado actual `DisabledForRepairs` (FR-010).

2. **¿Qué ocurre si la descripción excede la longitud máxima?**
   El sistema impide enviar el reporte e indica el límite de 500 caracteres.

3. **¿Qué ocurre si se detecta un daño durante una limpieza activa (`InCleaning`)?**
   El titular de la tarea de limpieza lo reporta desde la vista de tarea activa (HU-1, escenario 2), sin confirmar antes el fin de limpieza. Su `CleaningTask` se cierra en la misma operación.

4. **¿Qué ocurre con los pendientes de la habitación (reserva o bloqueo técnico)?**
   Se conservan. Si siguen vigentes cuando la habitación vuelva a `Available` (después de la reparación y la limpieza), se aplican; si caducan antes, se descartan. Mientras tanto, Recepción ve la alerta de reserva pendiente en su panel.

5. **¿Qué ocurre si el daño se detecta en una habitación `Occupied`, `Reserved` o `PendingCleaning`?**
   No se admite (FR-002): el daño se reporta cuando la habitación pase por limpieza (`InCleaning`) o quede `Available`.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE ofrecer un formulario de reporte con una descripción textual del daño, obligatoria, no vacía y de máximo 500 caracteres, accesible desde la acción "Reportar daño" de:
  - el panel de limpieza (habitaciones `Available`) y su vista de tarea activa (habitación en `InCleaning`), definidos en `spec-consultar-panel-limpieza.md`;
  - la búsqueda del panel de mantenimiento (habitaciones `Available`), definida en `spec-consultar-panel-mantenimiento.md`.
- **FR-002**: El sistema DEBE aceptar el reporte únicamente si la habitación está en `Available`, o en `InCleaning` cuando el solicitante es el titular de la `CleaningTask` activa; en otro caso lo rechaza informando el estado actual de la habitación y el motivo. Los daños en habitaciones `Occupied`, `Reserved` o `PendingCleaning` no se reportan: se reportan cuando la habitación pase por limpieza o quede `Available`.
- **FR-003**: Al procesar el reporte, el sistema DEBE cambiar la habitación a `DisabledForRepairs` y crear el `DamageReport` con el autor, la habitación, el estado previo y la fecha y hora del reporte.
- **FR-004**: Cuando el reporte se realiza desde `InCleaning`, el sistema DEBE cerrar en la misma operación la `CleaningTask` activa con resultado `DamageReported`; el usuario queda sin tarea activa y vuelve al listado del panel de limpieza (`spec-consultar-panel-limpieza.md`).
- **FR-005**: El sistema DEBE ofrecer la acción **Ver informe** para toda habitación en `DisabledForRepairs`, mostrando la descripción del daño, el autor y la fecha y hora del reporte (DD-MM-YYYY HH:MM).
- **FR-006**: Las habitaciones en `DisabledForRepairs` se gestionan únicamente desde el panel de mantenimiento (`spec-consultar-panel-mantenimiento.md`); el panel de limpieza no las muestra.
- **FR-007**: El `DamageReport` NO DEBE poder modificarse ni eliminarse una vez creado.
- **FR-008**: Solo el Personal de limpieza o de mantenimiento autenticado DEBE poder reportar un daño. Si la sesión expiró o el rol no corresponde, el sistema NO DEBE ejecutar nada: pide iniciar sesión o informa que la acción no está permitida.
- **FR-009**: `ReportDateTime` (y, desde `InCleaning`, el `EndDateTime` de la `CleaningTask`) DEBEN generarse en el servidor en hora Colombia (UTC-5), ignorando cualquier hora enviada por el cliente.
- **FR-010**: Si dos reportes, o un reporte y otra operación, compiten sobre la misma habitación, solo el primero DEBE proceder; el segundo se rechaza informando el estado actual de la habitación.
- **FR-011**: La transición, el `DamageReport`, el cierre de la `CleaningTask` (si aplica) y el historial DEBEN confirmarse en una sola operación o no aplicar nada. Ante un fallo, la habitación conserva su estado, se informa el error y el usuario puede reintentar sin perder la descripción escrita.
- **FR-012**: La transición a `DisabledForRepairs` DEBE registrarse en `RoomStateHistory` con el autor del reporte y el flujo de origen, dentro de la misma operación.

### Entidades Clave

- **Room**: En este caso de uso transita de `Available` o `InCleaning` a `DisabledForRepairs`.
- **DamageReport**: Registro inmutable del daño reportado. Almacena:
  - **DamageDescription**: Descripción del daño (máximo 500 caracteres)
  - **UserId**: Usuario (de limpieza o mantenimiento) que reporta
  - **RoomId**: Habitación reportada
  - **PreviousRoomStatus**: Estado de la habitación al momento del reporte (`Available` o `InCleaning`)
  - **ReportDateTime**: Fecha y hora del reporte
- **CleaningTask**: Definida en *Marcar Habitación en Limpieza*; se cierra con resultado `DamageReported` cuando el reporte se hace desde `InCleaning`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El miembro del personal puede reportar un daño, incluida la redacción de la descripción, en menos de 45 segundos.
- **SC-002**: El 100% de los reportes dejan la habitación en `DisabledForRepairs` con su `DamageReport` (y, desde `InCleaning`, con la `CleaningTask` cerrada) en una sola operación.
- **SC-003**: La habitación sale del inventario operativo y del panel de limpieza, y aparece en el panel de mantenimiento, en menos de 2 segundos.
