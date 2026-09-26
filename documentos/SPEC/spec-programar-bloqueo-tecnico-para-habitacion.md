# Especificación de Funcionalidad: Programar Bloqueo Técnico para Habitación

**Creado**: 2026-09-20

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Programación de bloqueo preventivo sobre habitación disponible (Prioridad: P1)

Como Personal de mantenimiento (`MaintenanceStaff`), quiero programar el bloqueo técnico de una habitación indicando su justificación técnica y verificando que no existan reservas en conflicto, para inhabilitarla temporalmente del inventario operativo sin afectar estancias ni compromisos de venta existentes.

**Por qué esta prioridad**: Permite ejecutar inspecciones y mantenimientos preventivos protegiendo los estándares de infraestructura del hotel, garantizando que ninguna unidad con mantenimientos programados sea asignada a huéspedes o vendida por recepción.

**Prueba Independiente**: Iniciar sesión como Personal de mantenimiento, seleccionar una habitación en estado `Available`, ingresar la justificación técnica, confirmar la operación y verificar que se invoque el caso de uso incluido "Consultar reservas", que el estado de la habitación cambie a `TechnicalBlock` y que quede excluida del catálogo de disponibilidad.

---

**Escenarios de Aceptación**:

1. **Escenario**: Bloqueo técnico exitoso sin conflicto de reservas
   - **Dado** una habitación registrada que se encuentra en estado operativo `Available` 
   - **Cuando** el Personal de mantenimiento programa el bloqueo técnico ingresando una justificación válida
   - **Entonces** el sistema ejecuta el caso de uso incluido `Consultar reservas` (`<<includes>>`), comprueba que no existen reservas asociadas, transiciona la habitación a `TechnicalBlock`, la retira del inventario disponible y registra la novedad en auditoría.

2. **Escenario**: Rechazo de bloqueo técnico por conflicto con reservas existentes
   - **Dado** una habitación en estado `Available` que registra una reserva programada en el Módulo 2 dentro del rango de intervención técnica
   - **Cuando** el Personal de mantenimiento intenta programar el bloqueo técnico
   - **Entonces** el sistema invoca `Consultar reservas`, detecta la colisión con la reserva futura, rechaza el cambio de estado, mantiene la habitación en `Available` y alerta sobre el conflicto para su reubicación en recepción.

3. **Escenario**: Intento de programar bloqueo técnico en habitación con estado no disponible
   - **Dado** una habitación que se encuentra en un estado diferente a `Available` (`Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`) conforme a `documentos/SPEC/referencias/maquina-estados-habitacion.md`
   - **Cuando** el Personal de mantenimiento intenta programar el bloqueo técnico
   - **Entonces** el sistema rechaza la operación, preserva el estado actual y emite una alerta indicando que la unidad no se encuentra disponible para bloqueo preventivo.

4. **Escenario**: Intento de programación con justificación técnica vacía
   - **Dado** una habitación en estado `Available`
   - **Cuando** el Personal de mantenimiento intenta confirmar la programación dejando el campo de justificación vacío o con caracteres en blanco
   - **Entonces** el sistema bloquea el guardado, no ejecuta la consulta de reservas y exige documentar el motivo técnico preventivo[cite: 10, 11, 12].

5. **Escenario**: Control de acceso por rol no autorizado
   - **Dado** una habitación en estado `Available`
   - **Cuando** un usuario con rol distinto a Personal de mantenimiento intenta ejecutar la programación de bloqueo técnico
   - **Entonces** el sistema deniega el acceso por política de permisos (RBAC) y mantiene el estado de la habitación inalterado.

---

### Casos Borde

- **Conflicto con reservas activas o futuras detectadas en Módulo 2**: Al invocar el caso de uso incluido `Consultar reservas`, si el sistema detecta reservas confirmadas que coinciden con el periodo de trabajo preventivo, cancela la transición a `TechnicalBlock`, mantiene la unidad en `Available` y notifica al usuario que coordine la reasignación de la reserva en el Módulo 2.
- **Peticiones simultáneas de bloqueo técnico sobre la misma unidad (concurrencia)**: Ante dos intentos simultáneos de programación, el sistema procesa la primera solicitud de forma atómica transicionándola a `TechnicalBlock` y rechaza la segunda informando que la unidad ya se encuentra en bloqueo técnico.
- **Justificación técnica vacía o insuficiente**: El sistema exige de forma obligatoria un texto descriptivo del mantenimiento preventivo planificado antes de habilitar la confirmación y persistir la transacción.
- **Fallo o indisponibilidad en la consulta de reservas**: La operación se ejecuta de manera atómica; si la llamada al caso de uso incluido `Consultar reservas` presenta una caída de red o no responde, el sistema ejecuta rollback y la habitación permanece en estado `Available`.
- **Detección de daño físico mayor durante la intervención**: Si durante el mantenimiento preventivo en `TechnicalBlock` se detecta una avería crítica imprevista, la unidad debe culminar su ciclo hacia `PendingCleaning` mediante "Confirmar reparación finalizada" o canalizarse por el flujo correspondiente de inhabilitación según las directrices de mantenimiento.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: Precondición: la habitación debe estar en el estado `Available` según `documentos/SPEC/referencias/maquina-estados-habitacion.md`
- **FR-002**: Transición de estado: El sistema DEBE actualizar de forma atómica el estado de la entidad `Room` de `Available` a `TechnicalBlock` tras confirmarse exitosamente la programación.
- **FR-003**: El sistema DEBE permitir únicamente al actor "Personal de mantenimiento" (`MaintenanceStaff`) ejecutar la programación de bloqueo técnico.
- **FR-004**: El sistema DEBE invocar obligatoriamente el caso de uso interno **"Consultar reservas" (`<<includes>>`)** para comprobar la existencia de reservas asignadas o futuras sobre la habitación.
- **FR-005**: El sistema DEBE rechazar la programación del bloqueo técnico si la consulta de reservas retorna compromisos comerciales programados para la unidad dentro de la ventana de mantenimiento.
- **FR-006**: El sistema DEBE requerir y validar como obligatorio el ingreso de una justificación técnica detallada que fundamente el mantenimiento preventivo programado.
- **FR-007**: El sistema DEBE rechazar la solicitud si la habitación se encuentra en cualquiera de los otros estados operativos (`Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`).
- **FR-008**: El sistema DEBE excluir de forma inmediata las habitaciones en estado `TechnicalBlock` de las consultas comerciales y del catálogo de asignaciones para nuevas reservas.
- **FR-009**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, el identificador del técnico responsable (`MaintenanceStaff`), la justificación técnica registrada y la marca de tiempo (timestamp) exacta de la operación.
- **FR-010**: El sistema DEBE asegurar que la liberación y salida del estado `TechnicalBlock` se gestione exclusivamente a través de los casos de uso autorizados en el ciclo de vida del módulo.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **`Room`**: Unidad habitacional física en el Módulo 1. Su atributo `status` transiciona de `Available` a `TechnicalBlock`. Atributos involucrados: ID único (UUID), número de habitación, piso/ala, tarifa base y estado operativo actual.
- **`MaintenanceStaff`**: Actor operativo responsable del mantenimiento preventivo, inspección técnica y custodia operativa de la infraestructura física del hotel.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las programaciones válidas invocan el caso de uso "Consultar reservas" y transicionan la habitación a `TechnicalBlock` en menos de 1 segundo.
- **SC-002**: El sistema intercepta y rechaza el 100% de los intentos de bloqueo técnico sobre habitaciones que no se encuentren en estado `Available` o que presenten reservas en colisión.
- **SC-003**: El 100% de las habitaciones marcadas en `TechnicalBlock` quedan excluidas de forma inmediata de las consultas de inventario vendible.
- **SC-004**: Cero registros de bloqueo técnico completados sin justificación obligatoria o sin la traza correspondiente en la bitácora de auditoría.
