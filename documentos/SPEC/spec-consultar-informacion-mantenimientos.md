# Especificación del Caso de Uso: Consultar Información de Mantenimientos

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de mantenimiento / Módulo 2 (sistema externo)
**Creado**: 2026-09-26

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Verificar si una habitación está libre de mantenimientos en un rango (Prioridad: P1)

Como Módulo 2 (al asignar una habitación a una reserva) o como miembro del personal de mantenimiento (al programar un bloqueo técnico), quiero saber si una habitación está libre de mantenimientos en un rango de fechas, para no crear reservas ni mantenimientos que se crucen entre sí.

**Por qué esta prioridad**: Es la verificación que evita que Módulo 2 asigne una habitación que estará en intervención, y que mantenimiento programe dos intervenciones superpuestas.

**Prueba independiente**: Consultar una habitación y un rango y verificar que la respuesta es "factible" si no hay mantenimientos que se crucen, o "no factible" con el motivo en caso contrario.

**Escenarios de aceptación**:

1. **Escenario**: Rango libre
   - **Dado que** La habitación "101" no tiene bloqueos `Scheduled` que se crucen con "22-10-2026" a "26-10-2026" y no está en `DisabledForRepairs`, `TechnicalBlock` ni `Inactive`
   - **Cuando** Módulo 2 o el Personal de mantenimiento consulta ese rango
   - **Entonces** El sistema responde "factible"

2. **Escenario**: Cruce con un mantenimiento programado
   - **Dado que** Existe un bloqueo `Scheduled` para la habitación "101" entre "24-10-2026" y "28-10-2026"
   - **Cuando** Se consulta "22-10-2026" a "26-10-2026"
   - **Entonces** El sistema responde "no factible" con el motivo "mantenimiento programado"

3. **Escenario**: Habitación fuera de servicio
   - **Dado que** La habitación "101" está en `DisabledForRepairs`, `TechnicalBlock` o `Inactive`
   - **Cuando** Se consulta cualquier rango válido
   - **Entonces** El sistema responde "no factible" con el motivo correspondiente (en reparación, en bloqueo técnico o inactiva), porque no hay fecha cierta de regreso al servicio

4. **Escenario**: Rango inválido
   - **Dado que** Un actor autorizado consulta la habitación "101"
   - **Cuando** El rango tiene una fecha de fin anterior a la de inicio, una fecha inexistente, una fecha vacía o una fecha de inicio pasada
   - **Entonces** El sistema responde con un error de validación indicando la regla incumplida, distinto de una respuesta "no factible"

---

### Casos Límite

1. **¿Qué ocurre si el actor no está autorizado?**
   El sistema rechaza la consulta indicando el motivo.

2. **¿Qué ocurre si los mantenimientos cambian mientras Módulo 2 procesa la respuesta?**
   La respuesta refleja el momento de la consulta. Si después llega un evento de reserva sobre una habitación en mantenimiento, la regla de pendientes lo resuelve sin perder la reserva.

3. **¿Qué ocurre si el rango es de un solo día?**
   Es válido y se evalúa normalmente.

4. **¿Qué ocurre si un fallo interno impide evaluar la consulta?**
   El sistema responde con un error técnico, distinto de "no factible" y del error de validación. Nunca responde "factible" si no pudo evaluar; quien consulta (Módulo 2 o *Programar bloqueo técnico*) debe tratarlo como "no se pudo verificar" y reintentar.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir la consulta solo a Módulo 2 (autorizado) y al Personal de mantenimiento autenticado. La consulta es de solo lectura.
- **FR-002**: La consulta DEBE recibir `roomId` (o número de habitación), fecha de inicio y fecha de fin (DD-MM-YYYY).
- **FR-003**: El sistema DEBE validar el rango antes de evaluar: fechas obligatorias y existentes, fin no anterior al inicio, inicio no anterior a hoy, duración máxima de 90 días e inicio a no más de 365 días de hoy (los mismos límites de *Programar bloqueo técnico para habitación*).
- **FR-004**: El sistema DEBE responder "no factible" si:
  - existe un `TechnicalBlockReport` en estado `Scheduled` cuyo rango se cruce con el consultado (incluidos los días extremos), o
  - la habitación está actualmente en `DisabledForRepairs`, `TechnicalBlock` o `Inactive`.
- **FR-005**: En cualquier otro caso, el sistema DEBE responder "factible".
- **FR-006**: La respuesta DEBE contener únicamente el resultado (factible / no factible) y, si es negativo, el motivo; no incluye sugerencias de fechas alternativas.
- **FR-007**: Si un fallo interno impide evaluar la consulta, el sistema DEBE responder con un error técnico, diferenciado de "no factible" y del error de validación, y NUNCA DEBE responder "factible".

### Entidades Clave

- **Room**: Se consulta su estado actual.
- **TechnicalBlockReport**: Definido en *Programar Bloqueo Técnico para Habitación*; se evalúan los informes `Scheduled` (los `Applied` ya se reflejan en el estado `TechnicalBlock` de la habitación).

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: La consulta responde en menos de 200 milisegundos.
- **SC-002**: El 100% de las consultas con rangos inválidos se responden con error de validación, sin evaluar mantenimientos.
- **SC-003**: Cero respuestas "factible" para habitaciones en `DisabledForRepairs`, `TechnicalBlock` o `Inactive`.
