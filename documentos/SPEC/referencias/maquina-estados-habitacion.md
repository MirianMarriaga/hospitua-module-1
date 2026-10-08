# Referencia: Máquina de estados de Room

**Módulo propietario**: Módulo 1 — Gestión de Habitaciones e Inventario
**Tipo de documento**: Referencia compartida (no es un SPEC de feature, no sigue spec-template.md)
**Actualizado**: 2026-10-07

## Propósito

Este documento es la fuente única de verdad sobre los estados posibles de una habitación
(`Room`), las transiciones válidas entre ellos, los pendientes de una habitación y el historial
común de transiciones. Cualquier SPEC —de este módulo o de otro— que necesite validar en qué
estado está una habitación antes de ejecutar su lógica debe citar este documento en su sección
de precondiciones.

Las reglas de cada caso de uso (acceso, marcas de tiempo, concurrencia, operación completa,
rechazos, tareas de trabajo y alcance) no se definen aquí: cada SPEC las incluye como
requisitos funcionales propios.

**Diagrama**: `documentos/diagramas/maquina-de-estados.mmd` (Mermaid) representa esta tabla y debe actualizarse junto con ella. La imagen `maquina-de-estados.png` es anterior a estos cambios y queda como referencia histórica.

## Estados vigentes (8)

El nombre en inglés es el canónico (el que debe usarse en todo SPEC, PLAN y código).
El nombre en español entre paréntesis es de referencia para los diagramas del equipo.

1. **Available** (Disponible)
2. **Reserved** (Reservada)
3. **Occupied** (Ocupada)
4. **PendingCleaning** (Pendiente de limpieza)
5. **InCleaning** (En limpieza)
6. **DisabledForRepairs** (Inhabilitada por reparaciones)
7. **TechnicalBlock** (Bloqueo técnico)
8. **Inactive** (Inactiva)

## Tabla de transiciones

Módulo 1 es el dueño absoluto de `Room.status`.

| Desde | Hacia | Disparador | Actor / Componente | Caso de uso |
| --- | --- | --- | --- | --- |
| Available | Reserved | Aplicación de una reserva pendiente (ver *Pendientes*) | Módulo 1 (Autónomo) | Marcar habitación como reservada |
| Reserved | Available | La reserva no figura en la lista del día recibida a las 00:00, es retirada (`REMOVED`) o Módulo 2 la reasigna a otra habitación (`UPDATED`). El no-show lo marca Módulo 2 | Módulo 1 (Autónomo) | Marcar habitación como reservada |
| Reserved | Occupied | Check-in confirmado | Recepcionista | Registrar check-in |
| Occupied | PendingCleaning | Check-out confirmado | Recepcionista | Registrar check-out → Marcar pendiente a limpieza |
| Available | InCleaning | Marcar en limpieza (aseo preventivo) | Personal de limpieza | Marcar habitación en limpieza |
| PendingCleaning | InCleaning | Marcar en limpieza | Personal de limpieza | Marcar habitación en limpieza |
| InCleaning | Available | Confirmar fin de limpieza | Personal de limpieza | Confirmar fin de limpieza → Marcar habitación como disponible |
| InCleaning | PendingCleaning | Liberar tarea de limpieza | Personal de limpieza (titular) / Administrador | Marcar habitación en limpieza → Marcar pendiente a limpieza |
| Available | DisabledForRepairs | Reportar daño | Personal de limpieza / Personal de mantenimiento | Marcar habitación inhabilitada por reparaciones |
| InCleaning | DisabledForRepairs | Reportar daño durante la limpieza activa (cierra la `CleaningTask`) | Personal de limpieza | Marcar habitación inhabilitada por reparaciones |
| DisabledForRepairs | PendingCleaning | Confirmar reparación finalizada | Personal de mantenimiento | Confirmar reparación finalizada → Marcar pendiente a limpieza |
| Available | TechnicalBlock | Aplicación de un bloqueo técnico pendiente (ver *Pendientes*) | Módulo 1 (Autónomo) | Programar bloqueo técnico para habitación |
| TechnicalBlock | PendingCleaning | Confirmar reparación finalizada | Personal de mantenimiento | Confirmar reparación finalizada → Marcar pendiente a limpieza |
| Available | Inactive | Dar de baja habitación | Administrador | Dar de baja habitación |
| Inactive | Available | Reactivar habitación | Administrador | Marcar habitación como disponible |

> "Iniciar reparaciones" y liberar una tarea de reparación no son transiciones de estado: solo abren o cierran una `ReparationTask` (ver *Confirmar reparación finalizada*).

## Pendientes de la habitación

Una habitación puede tener, como máximo, una **reserva pendiente** y un **bloqueo técnico pendiente**. Un pendiente es un compromiso futuro que todavía no se refleja en el estado porque la habitación no está en `Available`.

| Pendiente | Se registra cuando | Se aplica (transición) | Caduca cuando |
| --- | --- | --- | --- |
| Reserva pendiente (`pendingReservationRef`) | Llega el evento de reserva de Módulo 2 (12 h antes del `startDate`) o, como respaldo, la reserva figura con esa habitación en la lista del día (00:00 o `ADDED`/`UPDATED`) | `Available → Reserved` | La reserva no figura en la lista del día recibida a las 00:00, es retirada (`REMOVED`) o se reasigna a otra habitación (`UPDATED`). El no-show lo marca Módulo 2 |
| Bloqueo técnico pendiente (`TechnicalBlockReport` en estado `Scheduled`) | El Personal de mantenimiento programa el bloqueo | `Available → TechnicalBlock`, solo si la fecha actual está dentro del rango programado | Termina el rango programado sin haberse aplicado (`Expired`) o mantenimiento lo cancela (`Cancelled`) |

**Regla única de aplicación**: un pendiente vigente se aplica en el mismo momento en que la habitación queda en `Available`, sea porque ya lo estaba cuando se registró, porque llegó la fecha de inicio del bloqueo o porque la habitación volvió a `Available` por cualquier flujo (fin de limpieza o reactivación). La aplicación es parte de la misma operación que dejó la habitación en `Available`.

- Si hay una reserva y un bloqueo pendientes a la vez, se aplica la reserva; el bloqueo sigue pendiente y se aplicará si la habitación vuelve a `Available` dentro de su rango. El caso se registra en la bitácora. No debería ocurrir, porque *Programar bloqueo técnico* consulta las reservas y Módulo 2 consulta *Consultar información de mantenimientos* antes de asignar.
- Un pendiente que caduca se registra en la bitácora de auditoría y desaparece; no requiere acción manual.
- El único conflicto es la **doble reserva**: si llega un evento de reserva para una habitación que ya está `Reserved` o ya tiene una reserva pendiente con otra `reservationRef`, el evento no se aplica y se registra `RESERVATION_STATE_CONFLICT`. Recepción lo ve en su panel: la llegada afectada aparece como "Habitación reservada para otra reserva" y el Check-In de esa reserva queda bloqueado.

## Historial común de transiciones (`RoomStateHistory`)

Es la fuente de datos de *Consultar historial de estados*. Al registrar una habitación se crea su primera fila (`Available`). Cada caso de uso que cambia el estado de una habitación registra aquí su transición, en la misma operación, según lo indica su propio spec.

Cada registro almacena:

- **RoomId**: Identificador de la habitación
- **PreviousStatus** / **NewStatus**: Estado de origen (vacío en la fila inicial) y estado destino
- **StartDateTime**: Momento en que la habitación entra en `NewStatus`
- **ActorId**: Usuario responsable, o `SYSTEM` para transiciones autónomas
- **SourceFlow**: Caso de uso que originó la transición (ej. *Confirmar fin de limpieza*)

Los registros de `RoomStateHistory` son inmutables: nunca se actualizan. El fin de cada periodo es el `StartDateTime` de la fila siguiente de la misma habitación; la última fila es el periodo "En curso". Las entidades propias de cada caso de uso (`CleaningTask`, `DamageReport`, `TechnicalBlockReport`, `ReparationTask`) guardan datos de la tarea y no sustituyen a este historial.

## Cómo referenciar este documento desde un SPEC

En la sección de precondiciones o requisitos funcionales, usar:
`Precondición: la habitación debe estar en el estado [EstadoEnInglés] según
documentos/SPEC/referencias/maquina-estados-habitacion.md`.

Usa siempre el nombre en inglés (`Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`,
`DisabledForRepairs`, `TechnicalBlock`, `Inactive`) al citar un estado dentro de un
Functional Requirement, Acceptance Scenario o Key Entity. El español se usa en el título
del caso de uso y en la prosa narrativa de las User Stories.
