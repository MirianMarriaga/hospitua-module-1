# Especificación del Caso de Uso: Consultar Información de Mantenimientos

**Fecha de creación**: 20/09/2026

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta de mantenimientos por parte del módulo de reservas (Prioridad: P1)

Como sistema del módulo de reservas quiero poder consultar qué habitaciones están en proceso de reparación o bloqueadas por mantenimiento, para que pueda tomar decisiones informadas sobre reservas y evitar asignar habitaciones que no están disponibles.

**Por qué esta prioridad**: Sin esta información, el módulo de reservas podría crear reservas sobre habitaciones que no están disponibles, causando conflictos operativos con huéspedes.

**Prueba independiente**: Puede ser probada simulando una consulta del módulo de reservas y verificando que retorna las habitaciones que están fuera de servicio por mantenimiento.

**Escenarios de aceptación**:

1. **Escenario**: Consulta exitosa de mantenimientos
    - **Dado que** el módulo de reservas tiene acceso autorizado y existen habitaciones en mantenimiento
    - **Cuando** el módulo de reservas consulta información de mantenimientos
    - **Entonces** el sistema retorna la lista de habitaciones fuera de servicio  con sus detalles

2. **Escenario**: Sin mantenimientos activos
    - **Dado que** el módulo de reservas tiene acceso autorizado y no existen habitaciones en mantenimiento
    - **Cuando** el módulo de reservas consulta información de mantenimientos
    - **Entonces** el sistema retorna una lista vacía

---

### Casos Límite

1. **¿Qué ocurre si el módulo de reservas no está autorizado?**
   El sistema debe rechazar la consulta y registrar el intento no autorizado para auditoría.

2. **¿Qué ocurre si la información de mantenimiento cambia mientras el módulo de reservas la procesa?**
   El sistema debe retornar la información más reciente disponible. El módulo de reservas debe validar disponibilidad al momento de crear la reserva.

3. **¿Qué ocurre si hay muchas habitaciones en mantenimiento?**
   El sistema debe paginar los resultados o retornar la lista completa dependiendo del volumen. La implementación depende de decisiones técnicas futuras.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al módulo de reservas autenticado consultar información de mantenimientos.
- **FR-002**: El sistema DEBE retornar habitaciones en estado `TechnicalBlock`.
- **FR-003**: El sistema DEBE incluir para cada habitación: número, fecha de inicio del mantenimiento, fecha fin (estimada) y razón del mantenimiento.
- **FR-004**: El sistema DEBE retornar una lista vacía cuando no hay mantenimientos activos.
- **FR-005**: El sistema DEBE registrar intentos de consulta no autorizados para auditoría.
- **FR-006**: El sistema DEBE retornar información sin modificar datos del inventario.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso se consulta su estado de mantenimiento.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: La consulta se retorna en menos de 2 segundos.
- **SC-002**: El 100% de las consultas autorizadas retornan información consistente.
- **SC-003**: El 100% de los intentos no autorizados quedan registrados en el log de auditoría.
