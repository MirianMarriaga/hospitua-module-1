# Especificación de Funcionalidad: Marcar Habitación en Limpieza

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Marcar una habitación como en proceso de limpieza (Prioridad: P1)

Como Personal de limpieza, quiero marcar una habitación como "En Limpieza" para indicar que se está realizando el aseo, de modo que la habitación no pueda ser asignada a otro huésped ni aparezca como disponible mientras dure el proceso.

**Por qué esta prioridad**: Es el mecanismo central que protege la calidad operativa del hotel: evita que recepción asigne o muestre como disponible una habitación que aún no ha sido aseada tras el check-out. Corresponde directamente al caso de uso "Marcar habitación en limpieza" del diagrama oficial, y es condición previa para que la habitación pueda eventualmente volver a estar "Disponible".

**Prueba Independiente**: Puede probarse de forma independiente iniciando sesión como Personal de limpieza, seleccionando una habitación en estado "Pendiente de limpieza" y marcándola como "En Limpieza", verificando luego que la habitación quede excluida de los resultados de disponibilidad para nuevas reservas.

**Escenarios de Aceptación**:

1. **Escenario**: Marcado manual exitoso de una habitación recién desocupada
   - **Dado** una habitación se encuentra en estado "PendingCleaning" tras la salida de un huésped
   - **Cuando** el Personal de limpieza la marca como "InCleaning"
   - **Entonces** el sistema cambia el estado de la habitación a "InCleaning" y la excluye de los resultados de disponibilidad, verificando previamente como precondición directa que esté en "PendingCleaning" según documentos/SPEC/referencias/maquina-estados-habitacion.md

2. **Escenario**: Intento de marcar una habitación que ya está en limpieza
   - **Dado** una habitación ya se encuentra en estado "InCleaning"
   - **Cuando** el Personal de limpieza intenta marcarla nuevamente como "InCleaning"
   - **Entonces** el sistema detecta que ya se encuentra en ese estado y no aplica una nueva transición, informando al Personal de limpieza

---

### Casos Borde

- Habitación en estado diferente a PendingCleaning (ej. DisabledForRepairs o TechnicalBlock): el sistema rechaza la transición, validando que debe estar estrictamente en estado PendingCleaning (FR-005).
- Habitación con reserva confirmada para el mismo día: la transición procede normalmente, ya que la reserva es un dato independiente y no impide el aseo físico de la habitación.
- Intentos simultáneos de marcar la misma habitación: el primer intento cambia el estado a InCleaning, y el segundo es rechazado porque la habitación ya se encuentra en ese estado (FR-003).
- Fallo de red al marcar la habitación: la transacción se cancela y la habitación permanece en estado PendingCleaning.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al actor "Personal de limpieza" marcar manualmente una habitación como "InCleaning".
- **FR-002**: El sistema DEBE excluir las habitaciones en estado "InCleaning" de los resultados de disponibilidad utilizados para nuevas reservas o asignaciones.
- **FR-003**: El sistema DEBE impedir marcar como "InCleaning" una habitación que ya se encuentra en ese mismo estado.
- **FR-004**: El sistema DEBE validar como precondición directa que la habitación se encuentre en estado "PendingCleaning" según documentos/SPEC/referencias/maquina-estados-habitacion.md antes de aplicar la transición al estado "InCleaning".
- **FR-005**: El sistema DEBE permitir la transición a "InCleaning" únicamente desde el estado "PendingCleaning", rechazando la transición si la habitación se encuentra en cualquier otro estado.
- **FR-006**: El sistema DEBE registrar el usuario responsable (Personal de limpieza) y la fecha/hora en que se marcó la habitación como "InCleaning".
- **FR-007**: El sistema DEBE registrar la transición a "InCleaning" como una acción manual para efectos de trazabilidad.
- **FR-008**: El sistema DEBE permitir que la habitación transite fuera del estado "InCleaning" únicamente a través del caso de uso "Confirmar fin de aseo de habitación".

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Para este caso de uso, transita al estado "InCleaning" (ya definido en el ciclo de vida original del proyecto), quedando temporalmente excluida de la disponibilidad hasta que se confirme el fin del aseo.
- **CleaningStaff**: Actor responsable de ejecutar y confirmar el aseo de las habitaciones, incluyendo el marcado manual de inicio de limpieza.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las habitaciones marcadas como "InCleaning" quedan excluidas de los resultados de disponibilidad de forma inmediata.
- **SC-002**: El Personal de limpieza puede marcar manualmente una habitación como "InCleaning" en menos de 30 segundos.
