# Especificación del Caso de Uso: UC-06 - Marcar Habitación Inhabilitada por Reparaciones

**Fecha de creación**: 20/09/2026

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Reporte de daño en habitación (Prioridad: P1)

Como Personal de limpieza o Personal de mantenimiento quiero poder reportar un daño físico o estructural que detecte en una habitación, para que esta quede fuera de servicio hasta que sea reparada y no se asigne a huéspedes en condiciones inadecuadas.

**Por qué esta prioridad**: Es una funcionalidad crítica para la calidad del servicio. Las habitaciones dañadas deben ser retiradas de operación inmediatamente para evitar malas experiencias a huéspedes.

**Prueba independiente**: Puede ser probada reportando un daño en una habitación y verificando que queda fuera de servicio transicionando al estado `DisabledForRepairs`.

**Escenarios de aceptación**:

1. **Escenario**: Reporte de daño desde mantenimiento
   - **Dado que** el Personal de mantenimiento está autenticado y la habitación "101" está disponible
   - **Cuando** el Personal de mantenimiento reporta un daño estructural con justificación
   - **Entonces** la habitación queda fuera de servicio y se registra el reporte

2. **Escenario**: Reporte de daño en habitación disponible
   - **Dado que** el Personal de limpieza está autenticado y la habitación "101" está disponible
   - **Cuando** el Personal de limpieza reporta un daño detectado durante una inspección preventiva
   - **Entonces** la habitación queda fuera de servicio

---

### Casos Límite

1. **¿Qué ocurre si se reporta un daño sin proporcionar justificación?**
   El sistema debe rechazar la operación. Toda inhabilitación debe tener una justificación documentada para trazabilidad.

2. **¿Qué ocurre si se reporta un daño en una habitación que ya está fuera de servicio?**
   El sistema debe mostrar un error indicando que la habitación ya está inhabilitada.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al personal de limpieza y personal de mantenimiento autenticados reportar daños y recibir confirmación visual de la acción que están realizando.
- **FR-002**: El sistema DEBE aceptar reportes desde habitaciones disponibles.
- **FR-003**: El sistema DEBE requerir una justificación documentada del daño.
- **FR-004**: El sistema DEBE cambiar el estado a `DisabledForRepairs`.
- **FR-005**: El sistema DEBE registrar el identificador del personal, la descripción del daño y la fecha/hora.
- **FR-006**: El sistema DEBE rechazar reportes en habitaciones ocupadas, ya inhabilitadas o inactivas.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `Available` a `DisabledForRepairs`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El personal puede reportar un daño en menos de 30 segundos.
- **SC-002**: El 100% de los reportes incluyen justificación documentada.
- **SC-003**: La habitación inhabilitada desaparece del inventario operativo en menos de 2 segundos.