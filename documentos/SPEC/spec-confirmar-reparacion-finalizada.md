# Especificación de Funcionalidad: Confirmar Reparación Finalizada

**Creado**: 2026-09-20

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Conclusión de reparación correctiva y pase a limpieza (Prioridad: P1)

Como Personal de mantenimiento (`MaintenanceStaff`), quiero confirmar en el sistema la finalización de la reparación de una habitación inhabilitada, para que el sistema invoque la rutina de marcado a pendiente de limpieza y la habitación ingrese al ciclo de desinfección antes de volver a la venta.

**Por qué esta prioridad**: Es el cierre técnico del mantenimiento correctivo. Sin esta confirmación, la unidad permanece inmovilizada en `DisabledForRepairs`, restando aforo comercial al hotel. Toda intervención con herramientas y operarios genera suciedad, por lo que la habitación no puede transicionar directo a disponible, requiriendo pasar obligatoriamente a `PendingCleaning`.

**Prueba Independiente**: Iniciar sesión como Personal de mantenimiento, seleccionar una habitación en estado `DisabledForRepairs`, diligenciar el informe técnico de los trabajos realizados, confirmar la operación y verificar que se ejecute la inclusión a "Marcar pendiente a limpieza", dejando la unidad en estado `PendingCleaning` y visible para el equipo de aseo.

---

### Historia de Usuario 2 - Conclusión de mantenimiento preventivo desde bloqueo técnico (Prioridad: P1)

Como Personal de mantenimiento (`MaintenanceStaff`), quiero registrar la culminación del mantenimiento preventivo o inspección de una habitación en bloqueo técnico, para que el sistema libere el bloqueo y envíe la unidad a limpieza general.

**Por qué esta prioridad**: Permite reactivar las habitaciones reservadas por trabajos preventivos planificados. Al igual que en las reparaciones por daño físico, las revisiones preventivas exigen una higienización previa por parte de `CleaningStaff` antes de habilitarse para recepción.

**Prueba Independiente**: Iniciar sesión como Personal de mantenimiento, seleccionar una habitación en estado `TechnicalBlock`, confirmar la finalización del trabajo preventivo y verificar que la habitación transicione atómicamente a `PendingCleaning`.

---

**Escenarios de Aceptación**:

1. **Escenario**: Confirmación exitosa de reparación desde DisabledForRepairs
   - **Dado** una habitación registrada que se encuentra en estado operativo `DisabledForRepairs` conforme a `documentos/SPEC/referencias/maquina-estados-habitacion.md`
   - **Cuando** el Personal de mantenimiento confirma la culminación de la labor e ingresa el informe técnico de los arreglos
   - **Entonces** el sistema valida la precondición, ejecuta el caso de uso incluido `Marcar pendiente a limpieza` (`<<includes>>`), transiciona la entidad a `PendingCleaning` y persiste la traza de auditoría con la fecha y hora de cierre.

2. **Escenario**: Confirmación exitosa de mantenimiento desde TechnicalBlock
   - **Dado** una habitación que se encuentra en estado operativo `TechnicalBlock` según `documentos/SPEC/referencias/maquina-estados-habitacion.md`
   - **Cuando** el Personal de mantenimiento confirma que la inspección preventiva ha finalizado
   - **Entonces** el sistema invoca `Marcar pendiente a limpieza` (`<<includes>>`), cambia el estado a `PendingCleaning` y retira el bloqueo técnico.

3. **Escenario**: Descarte técnico o falsa alarma de avería
   - **Dado** una habitación en estado `DisabledForRepairs` reportada preventivamente por aseo
   - **Cuando** el Personal de mantenimiento inspecciona la unidad, determina que no amerita reparación física y confirma el cierre bajo la justificación de "Falsa alarma / Daño descartado"
   - **Entonces** el sistema registra el informe y transiciona la unidad a `PendingCleaning` para que el personal de limpieza retome su acondicionamiento.

4. **Escenario**: Intento de confirmación en habitación con estado no habilitado
   - **Dado** una habitación en un estado distinto a `DisabledForRepairs` y `TechnicalBlock` (`Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning` o `Inactive`)
   - **Cuando** el Personal de mantenimiento intenta ejecutar la confirmación de reparación
   - **Entonces** el sistema bloquea la transacción e informa que la habitación no cuenta con una orden técnica activa de reparación o bloqueo preventivo.

5. **Escenario**: Intento de confirmación con informe técnico faltante
   - **Dado** una habitación en estado `DisabledForRepairs`
   - **Cuando** el técnico intenta confirmar la finalización dejando el campo de detalle de trabajo en blanco
   - **Entonces** el sistema detiene el proceso y exige registrar el informe de los arreglos efectuados antes de autorizar el cambio de estado.

6. **Escenario**: Control de acceso por rol no autorizado
   - **Dado** una habitación en estado `DisabledForRepairs` o `TechnicalBlock`
   - **Cuando** un usuario con rol no autorizado (ej. Recepcionista o Personal de limpieza) intenta confirmar la reparación
   - **Entonces** el sistema rechaza la petición por directiva RBAC y mantiene inalterado el estado operativo de la unidad.

---

### Casos Borde

- **Obligatoriedad de observaciones e insumos utilizados en la reparación**: El sistema exige de manera obligatoria el ingreso de un informe técnico descriptivo de los arreglos realizados antes de permitir la confirmación; el registro de insumos o repuestos utilizados es un campo complementario opcional que queda registrado en la bitácora de auditoría sin impedir la transición de la habitación a `PendingCleaning`.
- **Confirmación simultánea por múltiples técnicos (concurrencia)**: Ante dos intentos simultáneos de confirmación sobre la misma habitación, el sistema procesa la primera transacción de forma atómica transicionándola a `PendingCleaning` vía el include correspondiente, e intercepta la segunda rechazándola por conflicto de concurrencia e informando que la reparación ya fue cerrada.
- **Pérdida de conexión o fallo transaccional tras confirmar**: La transacción se ejecuta bajo control transaccional estricto (ACID); si ocurre una caída de red o error al invocar "Marcar pendiente a limpieza", se aplica un rollback completo y la habitación permanece inalterada en su estado original (`DisabledForRepairs` o `TechnicalBlock`).
- **Detección de daño adicional durante la intervención preventiva**: Si durante el mantenimiento preventivo en `TechnicalBlock` se identifica una avería estructural imprevista, el técnico documenta la novedad en el informe de cierre y confirma la acción, derivando la unidad a `PendingCleaning` para su posterior gestión técnica o aseo.
- **Sincronización inmediata para el personal de limpieza**: Una vez confirmada la reparación, el sistema emite el evento para que la habitación aparezca de forma inmediata en la bandeja de trabajo de `CleaningStaff` bajo el estado `PendingCleaning`.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE restringir la ejecución de este caso de uso exclusivamente al actor "Personal de mantenimiento" (`MaintenanceStaff`).
- **FR-002**: Precondición: la entidad `Room` DEBE encontrarse en estado `DisabledForRepairs` o en estado `TechnicalBlock`, conforme a `documentos/SPEC/referencias/maquina-estados-habitacion.md`.
- **FR-003**: El sistema DEBE rechazar la operación si la habitación se encuentra en cualquiera de los otros estados de la máquina (`Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `Inactive`).
- **FR-004**: El sistema DEBE invocar obligatoriamente el caso de uso interno **"Marcar pendiente a limpieza" (`<<includes>>`)** al registrarse la finalización técnica.
- **FR-005**: Transición de estado: El sistema DEBE actualizar de forma atómica el estado de la entidad `Room` a `PendingCleaning` a través de la inclusión ejecutada.
- **FR-006**: El sistema DEBE validar como obligatorio el ingreso de un informe técnico descriptivo de los arreglos realizados o de la justificación de descarte de la avería antes de procesar el cierre.
- **FR-007**: El sistema DEBE permitir el registro opcional de insumos, materiales o repuestos utilizados durante la intervención física.
- **FR-008**: La habitación en estado `PendingCleaning` NO DEBE aparecer en los resultados de disponibilidad comercial para reservas o check-in, requiriendo culminar el flujo de aseo antes de retornar a `Available`.
- **FR-009**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, el identificador del técnico responsable (`MaintenanceStaff`), el informe técnico registrado, los insumos indicados y la marca de tiempo (timestamp) de finalización.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **`Room`**: Unidad habitacional del hotel. Su atributo `status` transiciona desde `DisabledForRepairs` o `TechnicalBlock` hacia `PendingCleaning` (tres de los 8 estados canónicos de su ciclo de vida). Atributos involucrados: ID único (UUID), número de habitación, piso/ala y estado operativo.
- **`MaintenanceStaff`**: Actor operativo responsable de ejecutar las reparaciones físicas o revisiones preventivas y certificar el cierre de la orden técnica.
- **`CleaningStaff`**: Rol operativo que visualiza la unidad habitacional una vez liberada en la cola de trabajo de habitaciones pendientes de aseo.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las confirmaciones de reparación válidas invocan el caso de uso "Marcar pendiente a limpieza", actualizando el estado a `PendingCleaning` en menos de 1 segundo.
- **SC-002**: El sistema intercepta y rechaza el 100% de los intentos de confirmación sobre habitaciones que no se encuentren en `DisabledForRepairs` ni en `TechnicalBlock`.
- **SC-003**: El 100% de las confirmaciones quedan registradas con informe técnico obligatorio y usuario responsable trazable en auditoría.
- **SC-004**: Tras la confirmación exitosa, la habitación se hace visible de inmediato en la bandeja de pendientes de aseo para el rol `CleaningStaff`.
