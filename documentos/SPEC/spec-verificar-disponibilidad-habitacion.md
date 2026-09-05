# Feature Specification: Verificar Disponibilidad de Habitación

**Created**: 2026-09-03

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Verificar el estado actual antes de una transición de la habitación (Priority: P1)

Como sistema, al recibir una solicitud de cualquier operación que intente cambiar el estado de una habitación (dar de baja, bloquear por convenio, marcar en bloqueo técnico, marcar ocupada, marcar en limpieza, confirmar fin de aseo, marcar inhabilitada por reparaciones, confirmar reparación finalizada, marcar como disponible), debo verificar el estado actual de la habitación para determinar si esa transición es válida antes de aplicarla.

**Why this priority**: Es la lógica de compuerta (`<<include>>`) detrás de prácticamente todos los casos de uso de cambio de estado del Módulo 1. Sin ella, cualquier actor podría ejecutar transiciones inválidas (por ejemplo, dar de baja una habitación con huésped en sitio) y comprometer la integridad del inventario. Es prerrequisito directo del SPEC "Dar de Baja Habitación" ya definido.

**Independent Test**: Puede probarse invocando cada operación de transición desde distintos estados de origen y confirmando que el resultado (permitido/rechazado) corresponde a la matriz de estados válida para esa operación.

**Acceptance Scenarios**:

1. **Scenario**: Habitación disponible permite operación de ocupación
   - **Given** una habitación en estado "Disponible"
   - **When** se invoca una operación que requiere ese estado (por ejemplo, registrar check-in / marcar ocupada)
   - **Then** la verificación retorna disponible = verdadero y la operación invocante puede continuar

2. **Scenario**: Habitación ocupada bloquea dar de baja
   - **Given** una habitación en estado "Ocupada"
   - **When** se invoca la operación "Dar de baja habitación"
   - **Then** la verificación retorna disponible = falso, con causa "Ocupada"

3. **Scenario**: Bloqueo técnico impide reservar
   - **Given** una habitación en estado "Bloqueo Técnico"
   - **When** se invoca la operación "Reservar habitación" (Módulo 2)
   - **Then** la verificación retorna disponible = falso, con causa "Bloqueo Técnico"

4. **Scenario**: Convenio bloquea a través del estado "Ocupada"
   - **Given** una habitación fue marcada como "Ocupada" como resultado de un convenio comercial (caso de uso "Bloquear habitación por convenio")
   - **When** se invoca la operación "Reservar habitación" (Módulo 2)
   - **Then** la verificación retorna disponible = falso, con causa "Ocupada" — el sistema no distingue si la ocupación se originó por un huésped o por un convenio, ambos casos se representan con el mismo estado

---

### User Story 2 - Verificar disponibilidad por rango de fechas para una reserva (Priority: P2)

Como Módulo 2 (Reservar habitación), al crear una reserva para un rango de fechas de check-in/check-out, necesito verificar que la habitación no tenga otra reserva que se solape con ese rango, independientemente de su estado puntual actual.

**Why this priority**: Habilita el flujo de reservas, que es el núcleo de valor del negocio, pero depende de que la lógica base de estados (User Story 1) ya esté definida.

**Independent Test**: Puede probarse creando dos solicitudes de reserva para la misma habitación con fechas que se solapan y confirmando que la segunda es rechazada; y con fechas que no se solapan, confirmando que ambas son aceptadas.

**Acceptance Scenarios**:

1. **Scenario**: Solapamiento de fechas rechazado
   - **Given** una habitación con una reserva confirmada del 10 al 12 de septiembre
   - **When** se solicita una nueva reserva del 11 al 13 de septiembre para la misma habitación
   - **Then** la verificación retorna disponible = falso, indicando conflicto con la reserva existente

2. **Scenario**: Fechas sin solapamiento aceptadas
   - **Given** una habitación con una reserva confirmada del 10 al 12 de septiembre
   - **When** se solicita una nueva reserva del 13 al 15 de septiembre para la misma habitación
   - **Then** la verificación retorna disponible = verdadero

3. **Scenario**: Habitación inactiva rechaza cualquier reserva futura
   - **Given** una habitación en estado "Inactiva"
   - **When** se solicita una reserva para cualquier rango de fechas futuro
   - **Then** la verificación retorna disponible = falso, con causa "Inactiva", sin importar el solapamiento de fechas

---

### Edge Cases

- ¿Qué sucede si se solicita la verificación sobre un identificador de habitación inexistente?
- ¿Qué ocurre si dos solicitudes de verificación + reserva llegan simultáneamente para la misma habitación y mismo rango de fechas (condición de carrera)?
- ¿Una reserva cancelada libera la habitación de inmediato para nuevas solicitudes, o existe algún período de retención?
- ¿Si una habitación está en "Bloqueo Técnico" o "Inhabilitada por reparaciones" sin fecha estimada de fin, se bloquean también las reservas a fechas muy futuras, o solo el estado inmediato?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE exponer una operación de soporte que reciba el identificador de **una única habitación** y, opcionalmente, un rango de fechas (check-in/check-out), y retorne si esa habitación está disponible o no para la operación invocante. Es una operación consumida por otra operación (vía `<<include>>`), no una vista o pantalla de consulta independiente. Este SPEC no cubre verificaciones o listados sobre múltiples habitaciones a la vez.
- **FR-002**: El sistema DEBE evaluar el estado actual de la habitación entre los seis estados vigentes del ciclo de vida (Disponible, Ocupada, En Limpieza, Bloqueo Técnico, Inhabilitada por reparaciones, Inactiva) para resolver la verificación.
- **FR-003**: El sistema DEBE considerar el estado "Disponible" como base para permitir operaciones inmediatas de ocupación y transiciones administrativas, salvo que la operación invocante aplique reglas adicionales propias.
- **FR-004**: El sistema DEBE, para operaciones con rango de fechas futuro (Reservar habitación), verificar la ausencia de solapamiento con cualquier otra reserva ya registrada para la misma habitación, de forma independiente al estado puntual actual.
- **FR-005**: El sistema DEBE excluir de forma absoluta e incondicional las habitaciones en estado "Inactiva" de cualquier resultado de disponibilidad, sin importar el rango de fechas consultado.
- **FR-006**: El sistema DEBE retornar, cuando la disponibilidad es negada, el estado causante y, si aplica, los datos de la reserva en conflicto (fechas, identificador), para que la operación invocante informe al actor correspondiente.
- **FR-007**: El sistema DEBE ejecutar la verificación de forma atómica junto con la operación de cambio de estado o creación de reserva que la invoca, evitando condiciones de carrera que permitan doble reserva o transiciones simultáneas inválidas sobre la misma habitación.
- **FR-008**: El sistema DEBE actuar como mecanismo genérico y reutilizable de verificación, consumido por cada caso de uso invocante (Dar de baja, Reservar, Marcar ocupada, etc.). Este SPEC define el mecanismo base (estados, causas de bloqueo, resultado retornado); la matriz específica de qué estados permiten o bloquean cada operación particular se define en el SPEC de ese caso de uso (por ejemplo, ya definida para "Dar de Baja Habitación": bloquea el estado "Ocupada" y bloquea también ante cualquier reserva futura confirmada, verificada de forma independiente al estado físico según FR-004), no en este documento.
- **FR-009**: El sistema DEBE permitir que una habitación tenga múltiples reservas confirmadas para distintos rangos de fechas no solapados, sin que la existencia de una reserva futura altere el estado físico de la habitación ni bloquee su disponibilidad para otros rangos de fechas no conflictivos (por ejemplo, una habitación reservada dentro de 10 días sigue disponible para ser reservada dentro de 5 días).

### Key Entities *(include if feature involves data)*

- **Habitación**: Unidad habitacional del hotel. Para esta verificación, su atributo relevante es el estado actual, con seis valores posibles: Disponible, Ocupada, En Limpieza, Bloqueo Técnico, Inhabilitada por reparaciones e Inactiva. Este SPEC consulta ese estado; no define cómo ni cuándo cambia.


## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos de operación sobre estados incompatibles (por ejemplo, dar de baja una habitación Ocupada, reservar una habitación Inactiva) son rechazados por el sistema.
- **SC-002**: El 0% de las reservas confirmadas presenta solapamiento de fechas con otra reserva confirmada para la misma habitación.
- **SC-003**: El 100% de las respuestas de "no disponible" incluye la causa específica de bloqueo (estado o reserva en conflicto).
- **SC-004**: La verificación de disponibilidad se resuelve en menos de 1 segundo por solicitud, sin afectar perceptiblemente el tiempo de respuesta de las operaciones que la invocan.
- **SC-005**: El 0% de los incidentes de doble-reserva o transición inválida ocurre por condiciones de carrera entre solicitudes concurrentes sobre la misma habitación.
