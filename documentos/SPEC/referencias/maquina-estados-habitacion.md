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

1. **Disponible** — lista para check-in o para ser bloqueada por mantenimiento.
2. **Ocupada** — huésped en sitio (check-in realizado).
3. **Pendiente de limpieza (sin asignar)** — liberada, esperando que el Personal de 
   limpieza la tome. Nadie la marca manualmente; el sistema la deja en este estado.
4. **En limpieza** — el Personal de limpieza la tomó y está realizando el aseo.
5. **Inhabilitada por reparaciones** — con daño físico reportado.
6. **Bloqueo técnico** — reservada para mantenimiento preventivo (no confundir con 
   ocupación por huésped ni con convenios; el concepto de convenio fue eliminado).
7. **Inactiva** — dada de baja por el Administrador; excluida del inventario operativo.

## Tabla de transiciones

| Desde | Hacia | Caso de uso disparador | Automático / Manual | Actor |
|---|---|---|---|---|
| Disponible | Ocupada | Registrar check-in | Manual | Módulo 2 / Recepción |
| Ocupada | Pendiente de limpieza | Registrar check-out | **Automático** | Módulo 2 (ver SPEC de check-out) |
| Pendiente de limpieza | En limpieza | Marcar habitación en limpieza | Manual | Personal de limpieza |
| En limpieza | Disponible | Confirmar fin de aseo | Manual | Personal de limpieza |
| Disponible / Ocupada | Inhabilitada por reparaciones | Marcar habitación inhabilitada por reparaciones | Manual | Personal de mantenimiento |
| Inhabilitada por reparaciones | Pendiente de limpieza | Confirmar reparación finalizada | Manual | Personal de mantenimiento |
| Disponible | Bloqueo técnico | Marcar habitación en bloqueo técnico | Manual | Personal de mantenimiento |
| Bloqueo técnico | Disponible | Desbloquear habitación | Manual | Personal de mantenimiento |
| Cualquier estado ≠ Inactiva | Inactiva | Dar de baja habitación | Manual | Administrador |
| Inactiva | Disponible | Marcar habitación como disponible | Manual | Administrador (revierte una baja, sin `<<extend>>`) |

## Notas de diseño

- **"Marcar habitación como disponible"** es el único punto de entrada al estado 
  `Disponible` desde un estado no operativo. Lo incluyen tres flujos (fin de aseo, 
  reparación finalizada, desbloqueo) y además lo dispara directamente el Administrador 
  para revertir una baja.
- La transición automática **Ocupada → Pendiente de limpieza** se documenta como 
  postcondición dentro del SPEC de "Registrar check-out" (Módulo 2), no aquí ni en el 
  SPEC de "Marcar habitación en limpieza". Este documento solo declara que esa transición 
  existe y hacia dónde va.
- El estado `Inactiva` no tiene atributo booleano `activo` asociado; es un valor más del 
  campo `status` de `Room`.
- Ya no existe el estado "Reservada": la reserva es un dato de negocio manejado en el 
  Módulo 2, independiente del estado físico de la habitación.

## Cómo referenciar este documento desde un SPEC

En la sección de precondiciones o requisitos funcionales, en vez de: 
`El sistema DEBE ejecutar <<include>> "Verificar disponibilidad de habitación"`, 
usar: 
`Precondición: la habitación debe estar en el estado [X] según 
documentos/SPEC/referencias/maquina-estados-habitacion.md`.
