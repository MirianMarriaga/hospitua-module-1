# Especificación del Caso de Uso: Confirmar Fin de Limpieza de Habitación

**Fecha de creación**: 20/09/2026

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Finalización de limpieza y disponibilidad de habitación (Prioridad: P1)

Como Personal de limpieza quiero poder confirmar que he completado la limpieza de una habitación, de modo que esta quede lista para recibir huéspedes y pueda ser asignada o reservada en ocasiones futuras.

**Por qué esta prioridad**: Es el cierre del ciclo de limpieza. Sin esta confirmación, la habitación queda bloqueada en medio del flujo de limpieza y deja de generar ingresos para el hotel.

**Prueba independiente**: Puede ser probada confirmando el fin de limpieza de una habitación y verificando que queda disponible para reservas transicionando al estado `Available`.

**Escenarios de aceptación**:

1. **Escenario**: Fin de limpieza exitoso
   - **Dado que** el Personal de limpieza está autenticado y existe una habitación "101" que está en proceso de limpieza
   - **Cuando** el Personal de limpieza confirma que la limpieza está completada
   - **Entonces** la habitación queda lista para recibir reservas

2. **Escenario**: Habitación no está en proceso de limpieza
   - **Dado que** el Personal de limpieza está autenticado y existe una habitación "101" que acaba de ser liberada, pero nadie la ha tomado para limpiar
   - **Cuando** el Personal de limpieza intenta confirmar fin de limpieza
   - **Entonces** el sistema rechaza la operación indicando que la habitación no está en proceso de limpieza
---

### Casos Límite

1. **¿Qué ocurre si el Personal de limpieza confirma el fin, pero la habitación evidentemente no está limpia?**
   Este es un problema operativo que no puede ser validado técnicamente por el sistema. La responsabilidad recae en la supervisión del Gerente, quien puede consultar el historial de estados para auditar tiempos de limpieza inusuales.

2. **¿Qué ocurre si se confirma el fin de limpieza y la habitación tenía un daño reportado?**
   El sistema debe permitir la confirmación. Si existe un daño, este puede ser reportado posteriormente a la marcación de la finalización de labores de limpieza.

3. **¿Qué ocurre si la limpieza se confirma y la habitación fue reasignada a otro proceso?**
   El sistema debe validar que la habitación esté en proceso de limpieza por otro miembro antes de permitir la confirmación y asignación.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al personal de limpieza autenticado confirmar el fin de limpieza y recibir verificación visual de la acción que está realizando.
- **FR-002**: El sistema DEBE validar que la habitación esté en estado `InCleaning`.
- **FR-003**: El sistema DEBE cambiar el estado a `Available` al confirmar el fin de limpieza.
- **FR-004**: El sistema DEBE registrar la fecha/hora de finalización de limpieza.
- **FR-005**: El sistema DEBE rechazar la confirmación si la habitación no está en estado `InCleaning.
- **FR-006**: El sistema DEBE limitar las acciones de los miembros del personal de limpieza con tareas activas a la marcación de finalización de labores en su unidad habitacional asignada.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `InCleaning` a `Available`, completando el ciclo de limpieza.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Personal de limpieza puede confirmar el fin en menos de 15 segundos.
- **SC-002**: La habitación queda disponible para reservas inmediatamente después de la confirmación.
- **SC-003**: El 100% de las confirmaciones registran la fecha/hora de finalización.
