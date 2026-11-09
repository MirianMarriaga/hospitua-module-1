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
