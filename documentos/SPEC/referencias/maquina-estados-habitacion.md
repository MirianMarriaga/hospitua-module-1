# Referencia: Máquina de estados de Room

**Módulo propietario**: Módulo 1 — Gestión de Habitaciones e Inventario
**Tipo de documento**: Referencia compartida (no es un SPEC de feature, no sigue spec-template.md)
**Actualizado**: 2026-09-05

## Propósito

Este documento es la fuente única de verdad sobre los estados posibles de una habitación
(`Room`) y las transiciones válidas entre ellos. Reemplaza al antiguo caso de uso
"Verificar disponibilidad de habitación", que fue retirado del diagrama: la validación de
estado ya no es una acción con actor propio, es una condición que cada SPEC consulta
directamente contra esta referencia.

Cualquier SPEC —de este módulo o de otro— que necesite validar en qué estado está una
habitación antes de ejecutar su lógica debe citar este documento en su sección de
precondiciones, en vez de repetir la tabla de estados o inventar su propia versión.

## Estados vigentes (7)

El nombre en **inglés es el canónico** (el que debe usarse en todo SPEC, PLAN y código).
El nombre en español entre paréntesis es solo de referencia, para mapear contra los
diagramas visuales del equipo (draw.io), que se mantienen en español.

1. **Available** (Disponible) — lista para check-in o para ser bloqueada por mantenimiento.
2. **Occupied** (Ocupada) — huésped en sitio (check-in realizado).
3. **PendingCleaning** (Pendiente de limpieza) — liberada, esperando que el Personal de
   limpieza la tome. Nadie la marca manualmente; el sistema la deja en este estado.
4. **InCleaning** (En limpieza) — el Personal de limpieza la tomó y está realizando el aseo.
5. **DisabledForRepairs** (Inhabilitada por reparaciones) — con daño físico reportado.
6. **TechnicalBlock** (Bloqueo técnico) — reservada para mantenimiento preventivo (no
   confundir con ocupación por huésped ni con convenios; el concepto de convenio fue eliminado).
7. **Inactive** (Inactiva) — dada de baja por el Administrador; excluida del inventario operativo.

## Tabla de transiciones

| Desde | Hacia | Caso de uso disparador | Automático / Manual | Actor |
|---|---|---|---|---|
| Available | Occupied | Registrar check-in | Manual | Módulo 2 / Recepción |
| Occupied | PendingCleaning | Registrar check-out | **Automático** | Módulo 2 (ver SPEC de check-out) |
| PendingCleaning | InCleaning | Marcar habitación en limpieza | Manual | Personal de limpieza |
| InCleaning | Available | Confirmar fin de aseo | Manual | Personal de limpieza |
| Available / Occupied | DisabledForRepairs | Marcar habitación inhabilitada por reparaciones | Manual | Personal de mantenimiento |
| DisabledForRepairs | PendingCleaning | Confirmar reparación finalizada | Manual | Personal de mantenimiento |
| Available | TechnicalBlock | Marcar habitación en bloqueo técnico | Manual | Personal de mantenimiento |
| TechnicalBlock | PendingCleaning | Confirmar reparación finalizada | Manual | Personal de mantenimiento |
| Cualquier estado ≠ Inactive | Inactive | Dar de baja habitación | Manual | Administrador |
| Inactive | Available | Marcar habitación como disponible | Manual | Administrador (revierte una baja, sin `<<extend>>`) |

## Notas de diseño

- **"Marcar habitación como disponible"** es el único punto de entrada al estado
  `Available` desde un estado no operativo. Lo incluye únicamente "Confirmar fin de
  aseo" y además lo dispara directamente el Administrador para revertir una baja.
- **"Confirmar reparación finalizada"** es el único disparador de salida tanto de
  `DisabledForRepairs` como de `TechnicalBlock`, y ambos casos llevan al mismo destino:
  `PendingCleaning`. Se decidió que toda intervención de mantenimiento —sea una
  reparación real o una tarea preventiva— ensucia la habitación, por lo que ninguna
  pasa a `Available` de forma directa. Esto evita que `TechnicalBlock` quede como un
  estado sin salida y mantiene el caso de uso con un único destino, sin ramas según el
  estado de origen.
- **"Marcar pendiente a limpieza"** es el caso de uso que centraliza la transición
  hacia `PendingCleaning`, ya que es disparada por más de un flujo (Registrar check-out
  del Módulo 2, y Confirmar reparación finalizada). Se documenta una sola vez en vez de
  repetirse en cada flujo que la dispara, siguiendo el mismo criterio usado para
  "Marcar habitación como disponible".
- La transición automática **Occupied → PendingCleaning** se documenta como
  postcondición dentro del SPEC de "Registrar check-out" (Módulo 2), no aquí ni en el
  SPEC de "Marcar habitación en limpieza". Este documento solo declara que esa transición
  existe y hacia dónde va.
- El estado `Inactive` no tiene atributo booleano `activo` asociado; es un valor más del
  campo `status` de `Room`.
- Ya no existe el estado "Reservada"/`Reserved`: la reserva es un dato de negocio
  manejado en el Módulo 2, independiente del estado físico de la habitación.

## Cómo referenciar este documento desde un SPEC

En la sección de precondiciones o requisitos funcionales, en vez de:
`El sistema DEBE ejecutar <<include>> "Verificar disponibilidad de habitación"`,
usar:
`Precondición: la habitación debe estar en el estado [EstadoEnInglés] según 
documentos/SPEC/referencias/maquina-estados-habitacion.md`.

Usa siempre el nombre en inglés (`Available`, `Occupied`, `PendingCleaning`, `InCleaning`,
`DisabledForRepairs`, `TechnicalBlock`, `Inactive`) al citar un estado dentro de un
Functional Requirement, Acceptance Scenario o Key Entity — es el nombre canónico. El
español solo se usa en el título del caso de uso y en la prosa narrativa de las User
Stories, no al nombrar el valor del estado en sí.
