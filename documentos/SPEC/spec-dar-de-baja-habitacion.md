# Especificación de Funcionalidad: Dar de Baja Habitación

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Retirar una habitación del inventario activo (Prioridad: P1)

Como Administrador, quiero dar de baja una habitación existente en el inventario, para excluirla de futuros usos cuando ya no está disponible para operar, sin perder el historial de uso y consumos ya asociado a ella.

**Por qué esta prioridad**: Es una operación esencial del ciclo de vida de la habitación dentro del Módulo 1 (Gestión de Habitaciones e Inventario). Sin ella, habitaciones que ya no deben operar seguirían apareciendo como disponibles, afectando la integridad del inventario. Corresponde directamente al caso de uso "Dar de baja habitación" del diagrama oficial.

**Prueba Independiente**: Puede probarse de forma independiente iniciando sesión como Administrador, seleccionando una habitación sin actividad pendiente ni huéspedes actuales, confirmando la baja, y verificando que la habitación deje de aparecer en los resultados de disponibilidad.

**Escenarios de Aceptación**:

1. **Escenario**: Baja exitosa de una habitación sin actividad pendiente
   - **Dado** una habitación existe en estado "Available" y no tiene actividad pendiente
   - **Cuando** el Administrador selecciona la habitación y confirma la acción de dar de baja
   - **Entonces** el sistema cambia el estado de la habitación a "Inactive", la excluye de los resultados de disponibilidad y conserva su historial

2. **Escenario**: Intento de baja sobre una habitación ocupada
   - **Dado** una habitación se encuentra en estado "Occupied" (huésped en sitio)
   - **Cuando** el Administrador intenta darla de baja
   - **Entonces** el sistema rechaza la operación y muestra un mensaje indicando que la habitación tiene un huésped activo, validando que debe estar en estado "Available".

4. **Escenario**: Reactivación de una habitación dada de baja
   - **Dado** una habitación se encuentra en estado "Inactive"
   - **Cuando** el Administrador ejecuta el caso de uso "Marcar habitación como disponible" para revertir la baja
   - **Entonces** el sistema cambia el estado de la habitación directamente de "Inactive" a "Available"

5. **Escenario**: Confirmación explícita antes de ejecutar la baja
   - **Dado** el Administrador ha seleccionado una habitación elegible para ser dada de baja
   - **Cuando** inicia la acción de dar de baja
   - **Entonces** el sistema solicita una confirmación explícita antes de aplicar el cambio, dado el impacto de la operación sobre el inventario

---

### Casos Borde

- Habitación ya dada de baja: el sistema no muestra la opción de dar de baja para habitaciones que ya están en estado "Inactive".
- Reactivación de una habitación: el Administrador puede revertir la baja y devolver la habitación al estado "Available" mediante el caso de uso "Marcar habitación como disponible".
- Pérdida de conexión o fallo del proceso tras confirmar: la transacción se cancela, evitando que la habitación quede en un estado intermedio inconsistente.


## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor "Administrador" dar de baja habitaciones del inventario.

- **FR-003**: El sistema DEBE impedir dar de baja una habitación que no se encuentre en estado "Available" (por ejemplo, "Occupied", "InCleaning", "TechnicalBlock" o "DisabledForRepairs"), independientemente de la causa que originó ese estado.

- **FR-005**: El sistema DEBE solicitar una confirmación explícita del Administrador antes de ejecutar la baja.
- **FR-006**: El sistema DEBE cambiar el estado de la habitación al nuevo estado formal **"Inactive"** tras la confirmación de la baja, sin eliminar físicamente el registro de la habitación. "Inactive" es uno de los 7 estados vigentes del ciclo de vida de la habitación (Available, Occupied, PendingCleaning, InCleaning, TechnicalBlock, DisabledForRepairs, Inactive). Solo se permite la transición hacia "Inactive" desde el estado "Available".


- **FR-009**: El sistema DEBE registrar el usuario responsable y la fecha/hora en que se ejecutó la baja, para efectos de trazabilidad.
- **FR-010**: El sistema DEBE permitir registrar un motivo de baja mediante la selección obligatoria de una categoría predefinida (por ejemplo: remodelación permanente, cierre definitivo de ala/piso, decisión administrativa, otro), pudiendo complementarse opcionalmente con un campo de texto libre para detalles adicionales.
- **FR-011**: El sistema DEBE permitir al Administrador revertir una baja, llevando la habitación del estado "Inactive" directamente al estado "Available" mediante el caso de uso "Marcar habitación como disponible", como una asociación directa del Administrador sin relación `<<extend>>`.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 7 estados del ciclo de vida: Available, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Administrator**: Actor responsable de gestionar el inventario de habitaciones, incluyendo su registro, edición y baja.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Administrador puede completar la baja de una habitación elegible en menos de 1 minuto.
- **SC-002**: El 100% de los intentos de baja sobre habitaciones que no estén en estado "Available" son rechazados por el sistema.
- **SC-003**: El 0% de las habitaciones dadas de baja aparece en los resultados de disponibilidad.
- **SC-004**: El 100% de las bajas ejecutadas quedan registradas con usuario responsable y fecha/hora, sin pérdida del historial previo de la habitación.
