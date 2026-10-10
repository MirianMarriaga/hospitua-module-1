# Referencia: Máquina de estados de Room

**Módulo propietario**: Módulo 1 — Gestión de Habitaciones e Inventario
**Tipo de documento**: Referencia compartida (no es un SPEC de feature, no sigue spec-template.md)
**Actualizado**: 2026-10-09

## Propósito

Este documento es la fuente única de verdad sobre los estados posibles de una habitación
(`Room`) y las transiciones válidas entre ellos. Cualquier SPEC —de este módulo o de otro—
que necesite validar en qué estado está una habitación antes de ejecutar su lógica debe
citar este documento en su sección de precondiciones.

## Estados vigentes (8)

El nombre en inglés es el canónico (el que debe usarse en todo SPEC, PLAN y código).
El nombre en español entre paréntesis es el que usan los diagramas del equipo y la interfaz (regla 12 del plan base).

1. **Available** (Disponible)
2. **Reserved** (Reservada)
3. **Occupied** (Ocupada)
4. **PendingCleaning** (Pendiente de limpieza)
5. **InCleaning** (En limpieza)
6. **DisabledForRepairs** (Inhabilitada por reparaciones)
7. **TechnicalBlock** (Bloqueo técnico)
8. **Inactive** (Inactiva)

## Tabla de transiciones

Estas son las transiciones confirmadas contra el diagrama de casos de uso y la arquitectura actualizada (Módulo 1 es el dueño absoluto de `Room.status`):

| Desde | Hacia | Disparador | Actor / Componente |
| --- | --- | --- | --- |
| Available | Reserved | Ingesta de lista diaria (00:00) con `startDate = hoy`, actualización `ADDED` con `startDate = hoy`, o confirmación del fin de limpieza de una habitación con llegada pendiente hoy (después de `InCleaning → Available`, en la misma transacción) (asigna `reservedByReservationRef`) | Módulo 1 (Autónomo) |
| Reserved | Available | Actualización `REMOVED` de esa reserva (incluye el no-show, que decide Módulo 2), habitación quitada de la reserva (`UPDATED`) o ausencia de la reserva en la lista diaria de las 00:00, solo si `reservedByReservationRef` coincide (limpia `reservedByReservationRef`) | Módulo 1 (Autónomo) |
| Reserved | Occupied | Check-in confirmado | Recepcionista |
| Occupied | PendingCleaning | Check-out confirmado | Recepcionista |
| Available | InCleaning | Marcar en limpieza (aseo preventivo) | Personal de limpieza |
| PendingCleaning | InCleaning | Marcar en limpieza | Personal de limpieza |
| InCleaning | Available | Confirmar fin de limpieza; si la habitación tiene una llegada pendiente hoy y no se reportó un daño, en la misma transacción continúa a `Reserved` | Personal de limpieza |
| InCleaning | PendingCleaning | Liberar tarea de limpieza (la habitación vuelve a la cola de limpieza) | Personal de limpieza |
| Available | DisabledForRepairs | Marcar inhabilitada por reparaciones (directamente o al confirmar el fin de limpieza con un daño reportado, después de `InCleaning → Available` en la misma transacción) | Personal de limpieza / Personal de mantenimiento |
| DisabledForRepairs | PendingCleaning | Confirmar fin de reparación de habitación | Personal de mantenimiento |
| Available | TechnicalBlock | Programar bloqueo técnico: lo aplica el trabajo autónomo de las 00:00 (después de la ingesta de la lista diaria de reservas) al llegar su fecha de inicio, o de inmediato al programarlo si inicia hoy. Si la habitación no está en `Available`, no se aplica y se reevalúa cada 00:00 hasta su fecha estimada de finalización | Módulo 1 (Autónomo, trabajo de las 00:00) / Personal de mantenimiento (aplicación inmediata) |
| TechnicalBlock | PendingCleaning | Confirmar fin de reparación de habitación | Personal de mantenimiento |
| Available | Inactive | Dar de baja habitación | Administrador |
| Inactive | Available | Marcar disponible / reactivar | Administrador |

> **Transiciones ELIMINADAS** (no existen en la arquitectura vigente):
>
> - `Available → Occupied` (el Check-In exige la habitación en `Reserved`).
> - `InCleaning → Reserved` (no es directa: la habitación pasa primero a `Available` y, en la misma transacción, *Confirmar fin de limpieza* la aparta si tiene una llegada pendiente hoy).
> - Cualquier otra transición no listada en la tabla anterior es inválida y produce HTTP 409.

## Reglas de conflicto y salvaguarda

1. **Habitación no apartable**: Si a las 00:00 (o al recibir un `ADDED`) una habitación con llegada hoy está en `DisabledForRepairs`, `TechnicalBlock` o `Inactive`, Módulo 1 **no** transiciona la habitación a `Reserved` y el Panel de Recepción muestra una alerta operativa en la fila correspondiente ("No disponible: [Estado]").

2. **Habitación ocupada o en limpieza**: Si a las 00:00 (o al recibir un `ADDED`) una habitación con llegada hoy está en `Occupied`, `PendingCleaning` o `InCleaning` (por ejemplo, el huésped anterior sale ese mismo día), Módulo 1 **no** la transiciona a `Reserved` en ese momento: la aparta al confirmarse el fin de su limpieza (`InCleaning → Available → Reserved`, en la misma transacción). Si al confirmar el fin de limpieza se reporta un daño, la habitación queda en `DisabledForRepairs` y aplica la alerta de la regla 1. Mientras tanto, el Panel de Recepción muestra en la fila un indicador informativo ("[Estado]: se apartará al terminar la limpieza").

3. **`RoomStateRequest`, confirmaciones/rechazos externos y mensajes de órdenes de bloqueo eliminados**: Módulo 2 ya **no** ordena transiciones de estado en Módulo 1. `RoomStateRequest`, los acuerdos B7 y B8 (orden de `Reserved`/`Available` externa), `ROOM_OCCUPIED`, `OBSOLETE` y el mecanismo de idempotencia por `requestId` están definitivamente eliminados.

4. **Salvaguarda `reservedByReservationRef`**: La transición `Reserved → Available` autónoma solo se ejecuta si el `reservedByReservationRef` de la habitación coincide con el `reservationRef` del mensaje `REMOVED` o del `UPDATED` que quita la habitación. Al liberar, se limpia `reservedByReservationRef`.

5. **Registro de las transiciones autónomas en el historial de estados** (`SourceFlow`):
   - Apartado automático (`Available → Reserved`): `MARK_RESERVED`.
   - Liberación automática (`Reserved → Available`): `MARK_AVAILABLE`.

## Cómo referenciar este documento desde un SPEC

En la sección de precondiciones o requisitos funcionales, usar:
`Precondición: la habitación debe estar en el estado [EstadoEnInglés] según
documentos/SPEC/referencias/maquina-estados-habitacion.md`.

Usa siempre el nombre en inglés (`Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`,
`DisabledForRepairs`, `TechnicalBlock`, `Inactive`) al citar un estado dentro de un
Functional Requirement, Acceptance Scenario o Key Entity. El español se usa en el título
del caso de uso y en la prosa narrativa de las User Stories.
