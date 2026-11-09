import { z } from 'zod';

import type { EditRoomRequest, Room } from '@/api/roomService';
import {
  registerRoomSchema,
  toRegisterRoomRequest,
} from '@/pages/administracion/registerRoomSchema';

export const ROOM_NUMBER_REQUIRED = 'El número de habitación es obligatorio.';
const ROOM_NUMBER_MAX = 'El número de habitación debe tener máximo 10 caracteres.';

// Las reglas de registrar (las aplica `Room` del backend en los dos casos). Solo el número vacío tiene
// otro texto en la tabla de errores de plan-editar-habitacion.md.
export const editRoomSchema = registerRoomSchema.extend({
  roomNumber: z.string().trim().min(1, ROOM_NUMBER_REQUIRED).max(10, ROOM_NUMBER_MAX),
});

export type EditRoomValues = z.infer<typeof editRoomSchema>;

export type EditRoomField = keyof EditRoomValues;

export function toEditRoomValues(room: Room): EditRoomValues {
  return {
    roomNumber: room.roomNumber,
    floor: String(room.floor),
    categoryRoom: room.categoryRoom,
    maxCapacity: String(room.maxCapacity),
    baseRate: String(room.baseRate),
  };
}

export function toEditRoomRequest(room: Room, values: EditRoomValues): EditRoomRequest {
  return { version: room.version, ...toRegisterRoomRequest(values) };
}

/** El formulario siempre envía todos los datos; si ninguno cambió, no se llama al backend. */
export function hasChanges(room: Room, request: EditRoomRequest): boolean {
  return (
    request.roomNumber !== room.roomNumber ||
    request.floor !== room.floor ||
    request.categoryRoom !== room.categoryRoom ||
    request.maxCapacity !== room.maxCapacity ||
    request.baseRate !== room.baseRate
  );
}

/**
 * Texto de un `VALIDATION_ERROR` del backend para el campo de `details.field`, tomado de la tabla de
 * errores del plan (la interfaz no muestra el `message` técnico).
 */
export function fieldErrorText(field: EditRoomField, values: EditRoomValues): string {
  switch (field) {
    case 'roomNumber':
      return values.roomNumber.trim() === '' ? ROOM_NUMBER_REQUIRED : ROOM_NUMBER_MAX;
    case 'floor':
      return 'El piso debe ser un número entero mayor a 0.';
    case 'categoryRoom':
      return 'Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique.';
    case 'maxCapacity':
      return 'La capacidad máxima debe ser un número entero mayor a 0.';
    case 'baseRate':
      return 'La tarifa base debe ser mayor a 0 y tener máximo dos decimales.';
  }
}

export function isEditRoomField(value: unknown): value is EditRoomField {
  return typeof value === 'string' && value in editRoomSchema.shape;
}
