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

// Textos de "Confirmar fin" y "Liberar tarea" según el errorCode: tablas de errores de
// `plan-confirmar-fin-de-limpieza.md` (T020). La red usa el texto común, que sugiere reintentar.
const CLOSE_TASK_ERRORS = {
  TASK_NOT_OWNED: 'Esta habitación la está limpiando otro compañero.',
  TASK_NOT_FOUND: 'No encontramos esta limpieza. Vuelve al panel.',
  TASK_ALREADY_CLOSED: 'Esta tarea ya fue cerrada.',
};

export function completeCleaningErrorMessage(error: unknown): string {
  return errorMessage(error, {
    ...CLOSE_TASK_ERRORS,
    VALIDATION_ERROR: 'La descripción del daño admite máximo 500 caracteres.',
  });
}

export function releaseCleaningErrorMessage(error: unknown): string {
  return errorMessage(error, CLOSE_TASK_ERRORS);
}
