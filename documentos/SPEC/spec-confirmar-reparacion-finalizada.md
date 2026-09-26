# Especificación del Caso de Uso: Confirmar Reparación Finalizada

**Fecha de creación**: 20/09/2026

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Confirmación de reparación completada (Prioridad: P1)

Como personal de mantenimiento quiero poder confirmar que las reparaciones de una habitación han sido completadas, para que esta quede en cola de limpieza y pueda ser preparada para recibir huéspedes nuevamente.

**Por qué esta prioridad**: Es el cierre del ciclo de reparación. Sin esta confirmación, la habitación queda bloqueada y no puede generar ingresos.

**Prueba independiente**: Puede ser probada confirmando una reparación completada y verificando que la habitación pasa a estar en cola de limpieza.

**Escenarios de aceptación**:

1. **Escenario**: Confirmación desde habitación inhabilitada por reparaciones
   - **Dado que** el Personal de mantenimiento está autenticado y existe una habitación "101" que está inhabilitada por reparaciones
   - **Cuando** el Personal de mantenimiento confirma que la reparación está completada
   - **Entonces** la habitación queda en cola de limpieza y se genera un registro de cierre de orden de mantenimiento

2. **Escenario**: Confirmación desde bloqueo técnico
   - **Dado que** el Personal de mantenimiento está autenticado y existe una habitación "101" que está bloqueada por mantenimiento preventivo
   - **Cuando** el Personal de mantenimiento confirma que el mantenimiento preventivo está completado
   - **Entonces** la habitación queda en cola de limpieza

---

### Casos Límite

1. **¿Qué ocurre si se confirma una reparación pero la habitación presenta otros daños?**
   El sistema debe permitir la confirmación. Los nuevos daños deben ser reportados como un nuevo reporte después de la confirmación.

2. **¿Qué ocurre si se intenta confirmar una reparación en una habitación que no está inhabilitada?**
   El sistema debe rechazar la operación indicando que la habitación no está en un estado que requiera reparación.

3. **¿Qué ocurre si el Personal de mantenimiento no está autenticado?**
   El sistema debe rechazar la operación y solicitar autenticación.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al ersonal de mantenimiento autenticado confirmar reparaciones finalizadas y recibir confirmación visual de la acción que está realizando.
- **FR-002**: El sistema DEBE aceptar confirmaciones desde habitaciones inhabilitadas por reparaciones o bloqueadas técnicamente.
- **FR-003**: El sistema DEBE cambiar el estado a `PendingCleaning`.
- **FR-004**: El sistema DEBE generar un registro de inicio y cierre (hora) del mantenimiento realizado.
- **FR-005**: El sistema DEBE rechazar confirmaciones en habitaciones que no están en un estado válido.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `DisabledForRepairs` o `TechnicalBlock` a `PendingCleaning`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Personal de mantenimiento puede confirmar una reparación en menos de 15 segundos.
- **SC-002**: La habitación entra en cola de limpieza inmediatamente después de la confirmación.
- **SC-003**: El 100% de las confirmaciones generan registro de cierre de mantenimiento.
