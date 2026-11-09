import { ApiRequestError } from '@/api/apiClient';
import { errorMessage } from '@/lib/errorMessages';
import { isRoomStatus, ROOM_STATUS_LABELS } from '@/lib/labels';

// Textos de "Iniciar limpieza" según el errorCode: tabla de errores de
// `plan-marcar-habitacion-en-limpieza.md` (POST /api/cleaning/tasks).
const START_CLEANING_ERRORS = {
  VALIDATION_ERROR: 'Selecciona una habitación válida.',
  ROOM_NOT_FOUND: 'No se encontró la habitación.',
  ACTIVE_TASK_EXISTS:
    'Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra.',
  ROOM_TASK_EXISTS: 'Otro compañero ya está limpiando esta habitación.',
};

/** El rechazo por estado dice el estado actual de la habitación (FR-009 del panel). */
export function startCleaningErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError && error.errorCode === 'ROOM_INVALID_STATE') {
    const current = error.details.currentStatus;
    const label =
      typeof current === 'string' && isRoomStatus(current)
        ? ROOM_STATUS_LABELS[current].toLowerCase()
        : 'en otro estado';
    return `La habitación está ${label}; ahora no se puede limpiar.`;
  }
  return errorMessage(error, START_CLEANING_ERRORS);
}
