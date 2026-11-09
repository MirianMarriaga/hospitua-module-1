import { errorMessage, invalidRoomStateLabel } from '@/lib/errorMessages';

// Textos de la tabla de errores de `plan-marcar-habitacion-inhabilitada-por-reparaciones.md`.
const REPORT_DAMAGE_ERRORS = {
  VALIDATION_ERROR: 'La descripción del daño es obligatoria (máximo 500 caracteres).',
  ROOM_NOT_FOUND: 'No se encontró la habitación.',
};

const VIEW_DAMAGE_ERRORS = {
  ROOM_NOT_FOUND: 'No se encontró la habitación.',
  DAMAGE_REPORT_NOT_FOUND: 'Esta habitación no tiene un daño reportado.',
};

/** El rechazo por estado dice el estado actual de la habitación (FR-002, FR-005). */
export function reportDamageErrorMessage(error: unknown): string {
  const state = invalidRoomStateLabel(error);
  if (state) return `La habitación está ${state}; ahora no se puede reportar un daño en ella.`;
  return errorMessage(error, REPORT_DAMAGE_ERRORS);
}

export function viewDamageErrorMessage(error: unknown): string {
  return errorMessage(error, VIEW_DAMAGE_ERRORS);
}
