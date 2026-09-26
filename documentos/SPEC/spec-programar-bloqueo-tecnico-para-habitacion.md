# Especificación del Caso de Uso: Programar Bloqueo Técnico para Habitación

**Fecha de creación**: 20/09/2026

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Programación de mantenimiento preventivo (Prioridad: P1)

Como personal de mantenimiento quiero poder reservar una habitación para hacerle mantenimiento preventivo (pintura, revisión de instalaciones, etc.), de forma que no haya asignación de huéspedes, reservas u otras labores mientras se realizan los trabajos.

**Por qué esta prioridad**: El mantenimiento preventivo es esencial para la conservación del hotel. Sin esta capacidad, el mantenimiento tendría que realizarse de forma reactiva o con riesgo de interferir con huéspedes.

**Prueba independiente**: Puede ser probada reservando una habitación para mantenimiento preventivo y verificando que queda fuera de servicio para el período indicado.

**Escenarios de aceptación**:

1. **Escenario**: Bloqueo técnico exitoso
   - **Dado que** el Personal de mantenimiento está autenticado y la habitación "101" está disponible
   - **Cuando** el Personal de mantenimiento reserva la habitación para mantenimiento del 01/10 al 05/10
   - **Entonces** la habitación queda fuera de servicio para ese período y no puede ser reservada

2. **Escenario**: Bloqueo con reservas existentes
   - **Dado que** la habitación "101" está disponible con una reserva del 02/10 al 04/10
   - **Cuando** el Personal de mantenimiento intenta reservar la habitación para mantenimiento del 01/10 al 05/10
   - **Entonces** el sistema rechaza la operación indicando que existe una reserva que se solapa con el período de mantenimiento

---

### Historia de Usuario 2 - Validación de disponibilidad antes del bloqueo (Prioridad: P2)

Como personal de mantenimiento quiero que el sistema verifique que no existan reservas o mantenimientos programados que se solapen con el período que necesito, para que no se cancelen reservas de huéspedes ni se programen mantenimientos concurrentes.

**Por qué esta prioridad**: Evita conflictos operativos críticos. Programar mantenimiento sobre una habitación reservada dejaría a huéspedes sin alojamiento.

**Prueba independiente**: Puede ser probada intentando programar un mantenimiento que se solape con una reserva existente y verificando que el sistema lo bloquea.

**Escenarios de aceptación**:

1. **Escenario**: Solapamiento con reserva
   - **Dado que** la habitación "101" tiene una reserva del 01/10 al 03/10
   - **Cuando** se intenta programar mantenimiento del 02/10 al 04/10
   - **Entonces** el sistema rechaza la operación por solapamiento

2. **Escenario**: Sin solapamiento
   - **Dado que** la habitación "101" tiene una reserva del 01/10 al 03/10
   - **Cuando** se programa mantenimiento del 05/10 al 10/10
   - **Entonces** el sistema acepta el mantenimiento sin conflicto

---

### Casos Límite

1. **¿Qué ocurre si se programa un mantenimiento con fecha de fin anterior a la de inicio?**
   El sistema debe rechazar la operación indicando que el período es inválido.

2. **¿Qué ocurre si se intenta programar mantenimiento en una habitación que está ocupada por un huésped?**
   El sistema debe rechazar la operación. Solo las habitaciones disponibles pueden ser bloqueadas.

3. **¿Qué ocurre si dos mantenimientos se solapan en la misma habitación?**
   El sistema debe rechazar el segundo mantenimiento indicando que ya existe uno programado en ese período.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al personal de mantenimiento autenticado programar bloqueos técnicos y recibir confirmación visual de la acción que están realizando.
- **FR-002**: El sistema DEBE validar que la habitación esté en estado `Available`.
- **FR-003**: El sistema DEBE verificar que no existan reservas que se solapen con el período de mantenimiento.
- **FR-004**: El sistema DEBE verificar que no existan otros bloqueos técnicos que se solapen con el actual.
- **FR-005**: El sistema DEBE cambiar el estado a `TechnicalBlock`.
- **FR-006**: El sistema DEBE registrar el período de mantenimiento (inicio y fin).
- **FR-007**: El sistema DEBE rechazar períodos con fecha de fin anterior a la de inicio.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `Available` a `TechnicalBlock`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Personal de mantenimiento puede programar un bloqueo en menos de 30 segundos.
- **SC-002**: El 100% de los bloqueos con solapamiento de reservas son rechazados.
- **SC-003**: La habitación bloqueada aparece como no disponible para reservas en el período indicado.
