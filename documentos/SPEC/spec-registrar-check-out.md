# Especificación de Funcionalidad: Registrar Check-out

**Creado**: 2026-09-07

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Registro de salida y liberación de habitación a limpieza (Prioridad: P1)

Como Módulo 2, quiero notificar el check-out de una estancia sobre una habitación ocupada para invocar la rutina de marcado a pendiente de limpieza, liberando la unidad física del inventario activo y enviándola a la cola de trabajo del personal de aseo.

**Por qué esta prioridad**: El check-out representa el cierre de la estancia física. Sin esta operación, la habitación permanece bloqueada indefinidamente en estado "Occupied", reduciendo el aforo disponible e impidiendo que el ciclo de limpieza e higienización comience.

**Prueba Independiente**: Puede probarse de forma independiente enviando una petición de check-out desde el Módulo 2 con el identificador de una habitación en estado `Occupied`, verificando que el sistema ejecute la inclusión hacia "Marcar pendiente a limpieza", transicione su estado a `PendingCleaning` y la refleje en la bandeja de aseo.

**Escenarios de Aceptación**:

1. **Escenario**: Check-out exitoso de habitación con estancia activa
   - **Dado** una habitación registrada que se encuentra en estado operativo `Occupied`
   - **Cuando** el Módulo 2 envía la solicitud de check-out con el identificador de la habitación y la confirmación de salida
   - **Entonces** el sistema invoca el caso de uso incluido `Marcar pendiente a limpieza` (`<<includes>>`), transiciona la habitación a `PendingCleaning` y registra la fecha y hora de salida

2. **Escenario**: Reflejo inmediato de habitación liberada para el personal de limpieza
   - **Dado** se ha completado exitosamente un check-out solicitado por el Módulo 2
   - **Cuando** se consulta el inventario operativo o la bandeja de trabajo de aseo
   - **Entonces** la habitación aparece con estado `PendingCleaning`, quedando habilitada para que el Personal de limpieza inicie el aseo

---

### Historia de Usuario 2 - Rechazo de check-out sobre habitación no ocupada (Prioridad: P1)

Como Módulo 2, quiero recibir una respuesta de rechazo explícita cuando se intente registrar un check-out en una habitación que no se encuentre ocupada, para evitar transiciones inconsistentes que alteren el inventario operativo.

**Por qué esta prioridad**: Ejecutar un check-out sobre una unidad que no está en estado `Occupied` generaría inconsistencias en la máquina de estados, pudiendo enviar a limpieza habitaciones disponibles o bajo intervención técnica.

**Prueba Independiente**: Puede probarse enviando solicitudes de check-out desde el Módulo 2 hacia habitaciones en cada uno de los estados distintos de `Occupied`, comprobando que el sistema rechace la petición en todos los casos sin modificar el estado.

**Escenarios de Aceptación**:

3. **Escenario**: Intento de check-out en habitación disponible
   - **Dado** una habitación que se encuentra en estado `Available`
   - **Cuando** el Módulo 2 intenta registrar un check-out sobre dicha habitación
   - **Entonces** el sistema rechaza la operación e informa que la habitación no cuenta con una ocupación activa

4. **Escenario**: Intento de check-out en habitación en procesMarcao de aseo
   - **Dado** una habitación que se encuentra en estado `PendingCleaning` o `InCleaning`
   - **Cuando** el Módulo 2 intenta registrar un check-out
   - **Entonces** el sistema rechaza la operación e informa que la habitación ya se encuentra dentro del flujo de limpieza

5. **Escenario**: Intento de check-out en habitación inhabilitada, en bloqueo técnico o inactiva
   - **Dado** una habitación que se encuentra en estado `DisabledForRepairs`, `TechnicalBlock` o `Inactive`
   - **Cuando** el Módulo 2 intenta registrar un check-out
   - **Entonces** el sistema rechaza la operación e informa el estado operativo actual de la unidad

---

### Casos Borde

- **Solicitud de check-out sin identificador de habitación**: El sistema rechaza la petición exigiendo el UUID de la habitación para proceder.
- **Peticiones simultáneas o redundantes de check-out**: Ante dos solicitudes concurrentes para la misma habitación, la primera ejecuta la transición a `PendingCleaning` de forma atómica y la segunda es rechazada informando que la unidad ya no se encuentra ocupada.
- **Fallo de comunicación o caída del sistema durante la confirmación**: La transacción se cancela atómicamente mediante rollback; si la rutina de inclusión hacia "Marcar pendiente a limpieza" falla, la habitación permanece inalterada en estado `Occupied`.
- **Check-out anticipado respecto a la fecha pactada de salida**: El sistema procesa la solicitud del Módulo 2 de manera inmediata sin validar la fecha contractual, completando la transición física a `PendingCleaning`.
- **Habitación con reservas futuras asignadas en Módulo 2**: La transición a `PendingCleaning` se realiza con normalidad, ya que las fechas de reserva futuras son gestionadas de forma independiente por el Módulo 2.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: Precondición: la habitación debe estar en el estado `Occupied`.
- **FR-002**: El sistema DEBE permitir únicamente al actor "Módulo 2" invocar el registro de check-out.
- **FR-003**: El sistema DEBE invocar obligatoriamente el caso de uso interno **"Marcar pendiente a limpieza" (`<<includes>>`)** al registrarse exitosamente la solicitud de check-out.
- **FR-004**: Transición de estado: El sistema DEBE actualizar de forma atómica el estado de la entidad `Room` de `Occupied` a `PendingCleaning` a través de la inclusión ejecutada.
- **FR-005**: El sistema DEBE rechazar la solicitud de check-out si la habitación se encuentra en cualquiera de los otros estados operativos (`Available`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`).
- **FR-006**: El sistema DEBE hacer visible de forma inmediata la habitación en la bandeja de trabajo de habitaciones pendientes de aseo.
- **FR-007**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, el identificador de la estancia/reserva suministrado por el Módulo 2 y la marca de tiempo exacta (timestamp) de la salida.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional física en el Módulo 1. Su atributo `status` transiciona de `Occupied` a `PendingCleaning` mediante la inclusión de servicio. Atributos involucrados: ID único (UUID), número de habitación, piso/ala y estado operativo actual.
- **Module2**: Módulo externo del sistema responsable de procesar el egreso de los huéspedes y coordinar la liberación de la estancia, actuando como disparador de la operación de check-out.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las solicitudes válidas de check-out invocan la rutina "Marcar pendiente a limpieza", actualizando el estado a `PendingCleaning` en menos de 1 segundo.
- **SC-002**: El sistema rechaza el 100% de los intentos de check-out sobre habitaciones cuyo estado sea diferente a `Occupied`.
- **SC-003**: Cero inconsistencias de concurrencia o duplicación de transiciones ante solicitudes simultáneas de salida para la misma habitación.
- **SC-004**: El 100% de los check-outs procesados quedan registrados en la bitácora con su marca de tiempo y referencia de estancia.
