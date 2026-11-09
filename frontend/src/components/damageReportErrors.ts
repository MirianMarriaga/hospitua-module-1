import { ApiRequestError } from '@/api/apiClient';
import { errorMessage } from '@/lib/errorMessages';
import { isRoomStatus, ROOM_STATUS_LABELS } from '@/lib/labels';

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
  if (error instanceof ApiRequestError && error.errorCode === 'ROOM_INVALID_STATE') {
    const current = error.details.currentStatus;
    const label =
      typeof current === 'string' && isRoomStatus(current)
        ? ROOM_STATUS_LABELS[current].toLowerCase()
        : 'en otro estado';
    return `La habitación está ${label}; ahora no se puede reportar un daño en ella.`;
  }
  return errorMessage(error, REPORT_DAMAGE_ERRORS);
}

export function viewDamageErrorMessage(error: unknown): string {
  return errorMessage(error, VIEW_DAMAGE_ERRORS);
}
