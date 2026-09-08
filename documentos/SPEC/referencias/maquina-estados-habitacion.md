# Referencia: Máquina de estados de Room

**Módulo propietario**: Módulo 1 — Gestión de Habitaciones e Inventario
**Tipo de documento**: Referencia compartida (no es un SPEC de feature, no sigue spec-template.md)
**Actualizado**: 2026-09-07

## Propósito

Este documento es la fuente única de verdad sobre los estados posibles de una habitación
(`Room`) y las transiciones válidas entre ellos. Cualquier SPEC —de este módulo o de otro—
que necesite validar en qué estado está una habitación antes de ejecutar su lógica debe
citar este documento en su sección de precondiciones.

## Estados vigentes (7)

El nombre en inglés es el canónico (el que debe usarse en todo SPEC, PLAN y código).
El nombre en español entre paréntesis es de referencia para los diagramas del equipo.

1. **Available** (Disponible)
2. **Occupied** (Ocupada)
3. **PendingCleaning** (Pendiente a limpieza)
4. **InCleaning** (En limpieza)
5. **DisabledForRepairs** (Inhabilitada por reparaciones)
6. **TechnicalBlock** (Bloqueo técnico)
7. **Inactive** (Inactiva)

## Tabla de transiciones

Estas son, exactamente, las 11 transiciones confirmadas contra el diagrama — ninguna otra.

| Desde | Hacia | Disparador |
|---|---|---|
| Available | InCleaning | Marcar en limpieza |
| Available | Inactive | Dar de baja |
| Inactive | Available | Marcar disponible |
| Available | DisabledForRepairs | Marcar inhabilitada |
| Available | Occupied | Check-in |
| Available | TechnicalBlock | Marcar bloqueo técnico |
| DisabledForRepairs | PendingCleaning | Reparación finalizada |
| Occupied | PendingCleaning | Check-out |
| TechnicalBlock | PendingCleaning | Reparación finalizada |
| PendingCleaning | InCleaning | Marcar en limpieza |
| InCleaning | Available | Confirmar fin de limpieza |

## Cómo referenciar este documento desde un SPEC

En la sección de precondiciones o requisitos funcionales, usar:
`Precondición: la habitación debe estar en el estado [EstadoEnInglés] según 
documentos/SPEC/referencias/maquina-estados-habitacion.md`.

Usa siempre el nombre en inglés (`Available`, `Occupied`, `PendingCleaning`, `InCleaning`,
`DisabledForRepairs`, `TechnicalBlock`, `Inactive`) al citar un estado dentro de un
Functional Requirement, Acceptance Scenario o Key Entity. El español se usa en el título
del caso de uso y en la prosa narrativa de las User Stories.
