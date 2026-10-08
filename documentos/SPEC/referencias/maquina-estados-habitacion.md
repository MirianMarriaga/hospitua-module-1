# Referencia: Máquina de estados de Room

**Módulo propietario**: Módulo 1 — Gestión de Habitaciones e Inventario
**Tipo de documento**: Referencia compartida (no es un SPEC de feature, no sigue spec-template.md)
**Actualizado**: 2026-09-07

## Propósito

Este documento es la fuente única de verdad sobre los estados posibles de una habitación
(`Room`) y las transiciones válidas entre ellos. Cualquier SPEC —de este módulo o de otro—
que necesite validar en qué estado está una habitación antes de ejecutar su lógica debe
citar este documento en su sección de precondiciones.

## Estados vigentes (8)

El nombre en inglés es el canónico (el que debe usarse en todo SPEC, PLAN y código).
El nombre en español entre paréntesis es de referencia para los diagramas del equipo.

1. **Available** (Disponible)
2. **Reserved** (Reservada)
3. **Occupied** (Ocupada)
4. **PendingCleaning** (Pendiente a limpieza)
5. **InCleaning** (En limpieza)
6. **DisabledForRepairs** (Inhabilitada por reparaciones)
7. **TechnicalBlock** (Bloqueo técnico)
8. **Inactive** (Inactiva)

## Tabla de transiciones

Estas son las transiciones confirmadas contra el diagrama de casos de uso y la arquitectura actualizada (Módulo 1 es el dueño absoluto de `Room.status`):

| Desde | Hacia | Disparador | Actor / Componente |
|---|---|---|---|
| Available | Reserved | Ingesta de lista diaria (00:00) o actualización `ADDED` con `startDate = hoy` (asigna `reservedByReservationRef`) | Módulo 1 (Autónomo) |
| Reserved | Available | Actualización `REMOVED` de esa reserva o corte de las 23:59 (desmarque automático por no-show) | Módulo 1 (Autónomo) |
| Available | Occupied | Check-in (unidad recién liberada de limpieza o contingencia) | Recepcionista |
| Reserved | Occupied | Check-in confirmado | Recepcionista |
| Occupied | PendingCleaning | Check-out confirmado | Recepcionista |
| Available | InCleaning | Marcar en limpieza (aseo preventivo) | Personal de limpieza |
| PendingCleaning | InCleaning | Marcar en limpieza | Personal de limpieza |
| InCleaning | Available | Confirmar fin de limpieza (sin llegada programada hoy) | Personal de limpieza |
| InCleaning | Reserved | Confirmar fin de limpieza (con llegada pendiente hoy, transición encadenada) | Módulo 1 (Autónomo) |
| Available | DisabledForRepairs | Marcar inhabilitada por reparaciones | Personal de mantenimiento |
| DisabledForRepairs | PendingCleaning | Confirmar reparación finalizada | Personal de mantenimiento |
| Available | TechnicalBlock | Programar bloqueo técnico | Personal de mantenimiento |
| TechnicalBlock | PendingCleaning | Confirmar reparación finalizada | Personal de mantenimiento |
| Available | Inactive | Dar de baja habitación | Administrador |
| Inactive | Available | Marcar disponible / reactivar | Administrador |

> **Reglas de conflicto y salvaguarda**:
> 1. Si a las 00:00 una habitación con llegada hoy está en `Occupied` o en aseo (`PendingCleaning` / `InCleaning`), Módulo 1 no sobrescribe su estado; la aparta a `Reserved` en el momento exacto en que complete la limpieza y pase a `Available`.
> 2. Si a las 00:00 la habitación asignada está en mantenimiento o inactiva (`DisabledForRepairs`, `TechnicalBlock`, `Inactive`), Módulo 1 no transiciona la habitación a `Reserved` y emite una alerta operativa a Recepción.
> 3. Se eliminan definitivamente los mensajes externos `RoomStateRequest` y las confirmaciones/rechazos hacia Módulo 2 (acuerdos B7 y B8 eliminados).

## Cómo referenciar este documento desde un SPEC

En la sección de precondiciones o requisitos funcionales, usar:
`Precondición: la habitación debe estar en el estado [EstadoEnInglés] según 
documentos/SPEC/referencias/maquina-estados-habitacion.md`.

Usa siempre el nombre en inglés (`Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`,
`DisabledForRepairs`, `TechnicalBlock`, `Inactive`) al citar un estado dentro de un
Functional Requirement, Acceptance Scenario o Key Entity. El español se usa en el título
del caso de uso y en la prosa narrativa de las User Stories.
