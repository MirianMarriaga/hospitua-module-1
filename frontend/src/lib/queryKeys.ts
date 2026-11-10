// Claves de TanStack Query que usan varias pantallas: una para leer y otras para invalidar.

/** Panel de recepción (KPI, llegadas y salidas); Check-In y Check-Out lo invalidan. */
export const RECEPTION_KEY = ['reception'] as const;

/** Inventario de habitaciones; registrar, editar, dar de baja y reactivar lo invalidan. */
export const INVENTORY_KEY = ['inventory'] as const;

/** Una habitación del inventario (`GET /api/rooms/inventory/{roomId}`). */
export function roomKey(roomId: string | undefined) {
  return ['room', roomId] as const;
}

/** Liquidación de una estadía; el Check-Out la descarta al registrar la salida. */
export function settlementKey(stayId: string) {
  return ['settlement', stayId] as const;
}

/** Panel de limpieza; iniciar, terminar o dejar una limpieza lo invalidan. */
export const CLEANING_KEY = ['cleaning'] as const;

/** Panel de mantenimiento; iniciar, terminar o dejar una reparación y reportar un daño lo invalidan. */
export const MAINTENANCE_KEY = ['maintenance'] as const;

/** Lista del personal; registrar, editar y desactivar una cuenta la invalidan. */
export const STAFF_KEY = ['staff'] as const;

/** Una cuenta del personal (`GET /api/staff/{username}`). */
export function staffAccountKey(username: string | undefined) {
  return ['staff-account', username] as const;
}
