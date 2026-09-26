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

Estas son las transiciones confirmadas contra el diagrama de casos de uso y la arquitectura actualizada:

| Desde | Hacia | Disparador |
|---|---|---|
| Available | Reserved | Evento de Módulo 2 (Marcar habitación como reservada) |
| Reserved | Available | Cancelación o liberación de reserva (Evento de Módulo 2) |
| Available | Occupied | Check-in |
| Reserved | Occupied | Check-in |
| Occupied | PendingCleaning | Check-out |
| Available | InCleaning | Marcar en limpieza |
| PendingCleaning | InCleaning | Marcar en limpieza |
| InCleaning | Available | Confirmar fin de limpieza |
| Available | DisabledForRepairs | Marcar inhabilitada por reparaciones |
| DisabledForRepairs | PendingCleaning | Confirmar reparación finalizada |
| Available | TechnicalBlock | Programar bloqueo técnico |
| TechnicalBlock | PendingCleaning | Confirmar reparación finalizada |
| Available | Inactive | Dar de baja habitación |
| Inactive | Available | Marcar disponible / reactivar |

## Cómo referenciar este documento desde un SPEC

En la sección de precondiciones o requisitos funcionales, usar:
`Precondición: la habitación debe estar en el estado [EstadoEnInglés] según 
documentos/SPEC/referencias/maquina-estados-habitacion.md`.

Usa siempre el nombre en inglés (`Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`,
`DisabledForRepairs`, `TechnicalBlock`, `Inactive`) al citar un estado dentro de un
Functional Requirement, Acceptance Scenario o Key Entity. El español se usa en el título
del caso de uso y en la prosa narrativa de las User Stories.
