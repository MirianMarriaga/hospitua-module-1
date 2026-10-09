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
| Available | Reserved | Ingesta de lista diaria (00:00) con `startDate = hoy`, o actualización `ADDED` con `startDate = hoy` (asigna `reservedByReservationRef`) | Módulo 1 (Autónomo) |
| Reserved | Available | Actualización `REMOVED` de esa reserva, habitación quitada de la reserva (`UPDATED`), o corte de las 23:59 (desmarque automático por no-show), solo si `reservedByReservationRef` coincide (limpia `reservedByReservationRef`) | Módulo 1 (Autónomo) |
| Reserved | Occupied | Check-in confirmado | Recepcionista |
| Occupied | PendingCleaning | Check-out confirmado | Recepcionista |
| Available | InCleaning | Marcar en limpieza (aseo preventivo) | Personal de limpieza |
| PendingCleaning | InCleaning | Marcar en limpieza | Personal de limpieza |
| InCleaning | Available | Confirmar fin de limpieza (sin llegada pendiente hoy para esa habitación) | Personal de limpieza |
| Available | DisabledForRepairs | Marcar inhabilitada por reparaciones | Personal de mantenimiento |
| DisabledForRepairs | PendingCleaning | Confirmar reparación finalizada | Personal de mantenimiento |
| Available | TechnicalBlock | Programar bloqueo técnico | Personal de mantenimiento |
| TechnicalBlock | PendingCleaning | Confirmar reparación finalizada | Personal de mantenimiento |
| Available | Inactive | Dar de baja habitación | Administrador |
| Inactive | Available | Marcar disponible / reactivar | Administrador |

> **Transiciones ELIMINADAS** (no existen en la arquitectura vigente):
> - `Available → Occupied` (el Check-In exige la habitación en `Reserved`).
> - `InCleaning → Reserved` (eliminada; la habitación pasa a `Available` y el consumer de reservas la aparta si corresponde).
> - Cualquier otra transición no listada en la tabla anterior es inválida y produce HTTP 409.

## Reglas de conflicto y salvaguarda

1. **Habitación no apartable**: Si a las 00:00 (o al recibir un `ADDED`) una habitación con llegada hoy está en `DisabledForRepairs`, `TechnicalBlock` o `Inactive`, Módulo 1 **no** transiciona la habitación a `Reserved` y el Panel de Recepción muestra una alerta operativa en la fila correspondiente ("No apartada: [Estado]").

2. **Caso fuera de alcance (sin transición)**: Si a las 00:00 una habitación con llegada hoy está en `Occupied`, `PendingCleaning` o `InCleaning`, se asume que no ocurre y queda fuera del flujo normal. No se define ninguna transición para este caso.

3. **`RoomStateRequest`, confirmaciones/rechazos externos y mensajes de órdenes de bloqueo eliminados**: Módulo 2 ya **no** ordena transiciones de estado en Módulo 1. `RoomStateRequest`, los acuerdos B7 y B8 (orden de `Reserved`/`Available` externa), `ROOM_OCCUPIED`, `OBSOLETE` y el mecanismo de idempotencia por `requestId` están definitivamente eliminados.

4. **Salvaguarda `reservedByReservationRef`**: La transición `Reserved → Available` autónoma solo se ejecuta si el `reservedByReservationRef` de la habitación coincide con el `reservationRef` del mensaje `REMOVED` o del `UPDATED` que quita la habitación. Al liberar, se limpia `reservedByReservationRef`. Motivo de auditoría: `LIBERACION_AUTOMATICA`.

5. **Motivos de auditoría canónicos** para las transiciones autónomas:
   - Apartado automático (`Available → Reserved`): `APARTADO_AUTOMATICO`.
   - Liberación automática (`Reserved → Available`): `LIBERACION_AUTOMATICA`.

## Cómo referenciar este documento desde un SPEC

En la sección de precondiciones o requisitos funcionales, usar:
`Precondición: la habitación debe estar en el estado [EstadoEnInglés] según
documentos/SPEC/referencias/maquina-estados-habitacion.md`.

Usa siempre el nombre en inglés (`Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`,
`DisabledForRepairs`, `TechnicalBlock`, `Inactive`) al citar un estado dentro de un
Functional Requirement, Acceptance Scenario o Key Entity. El español se usa en el título
del caso de uso y en la prosa narrativa de las User Stories.
