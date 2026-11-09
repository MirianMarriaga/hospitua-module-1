import { z } from 'zod';

import type { RegisterRoomRequest } from '@/api/roomService';
import type { RoomCategory } from '@/api/types';
import { isRoomCategory } from '@/lib/labels';

const POSITIVE_INTEGER = /^\d+$/;
const MONEY = /^\d+(\.\d{1,2})?$/;
/** Mayor `int` de Java: el backend rechaza un piso o una capacidad fuera de ese rango. */
const MAX_INT = 2_147_483_647;
/** `NUMERIC(12,2)`: como máximo diez dígitos enteros. */
const MAX_RATE_EXCLUSIVE = 10_000_000_000;

function isPositiveInt(value: string): boolean {
  return POSITIVE_INTEGER.test(value) && Number(value) > 0 && Number(value) <= MAX_INT;
}

function normalizeDecimal(value: string): string {
  return value.trim().replace(',', '.');
}

// Mismas reglas que `Room` del backend (número, piso, tipo, capacidad y tarifa) y mismos textos que la
// tabla de errores de plan-registrar-habitacion.md; plan-editar-habitacion.md usa los mismos.
export const registerRoomSchema = z.object({
  roomNumber: z
    .string()
    .trim()
    .min(1, 'Ingresa el número de habitación.')
    .max(10, 'El número de habitación debe tener máximo 10 caracteres.'),
  floor: z
    .string()
    .trim()
    .min(1, 'Ingresa el piso.')
    .refine(isPositiveInt, 'El piso debe ser un número entero mayor a 0.'),
  categoryRoom: z
    .string()
    .min(1, 'Selecciona el tipo de habitación.')
    .refine(
      (value): boolean => isRoomCategory(value),
      'Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique.',
    ),
  maxCapacity: z
    .string()
    .trim()
    .min(1, 'Ingresa la capacidad máxima.')
    .refine(isPositiveInt, 'La capacidad máxima debe ser un número entero mayor a 0.'),
  baseRate: z
    .string()
    .trim()
    .min(1, 'Ingresa la tarifa base.')
    .refine((value) => {
      const normalized = normalizeDecimal(value);
      return (
        MONEY.test(normalized) && Number(normalized) > 0 && Number(normalized) < MAX_RATE_EXCLUSIVE
      );
    }, 'La tarifa base debe ser mayor a 0 y tener máximo dos decimales.'),
});

export type RegisterRoomValues = z.infer<typeof registerRoomSchema>;

export const EMPTY_ROOM: RegisterRoomValues = {
  roomNumber: '',
  floor: '',
  categoryRoom: '',
  maxCapacity: '',
  baseRate: '',
};

export function toRegisterRoomRequest(values: RegisterRoomValues): RegisterRoomRequest {
  return {
    roomNumber: values.roomNumber.trim(),
    floor: Number(values.floor),
    categoryRoom: values.categoryRoom as RoomCategory,
    maxCapacity: Number(values.maxCapacity),
    baseRate: Number(normalizeDecimal(values.baseRate)),
  };
}
