import { ApiRequestError } from '@/api/apiClient';
import { errorMessage } from '@/lib/errorMessages';
import { isRoomStatus, ROOM_STATUS_LABELS } from '@/lib/labels';

const CHECK_OUT_ERRORS = {
  ACTIVE_STAY_NOT_FOUND: 'No encontramos una estadía activa para esta habitación.',
  CONFIRMATION_REQUIRED: 'Marca la casilla para confirmar la salida del huésped.',
  ROOM_INVALID_STATE: 'La habitación no está ocupada.',
};

export function checkOutErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError && error.errorCode === 'ROOM_INVALID_STATE') {
    const current = error.details.currentStatus;
    if (typeof current === 'string' && isRoomStatus(current)) {
      return `La habitación está ${ROOM_STATUS_LABELS[current]}; solo se puede hacer el check-out de una habitación Ocupada.`;
    }
  }
  return errorMessage(error, CHECK_OUT_ERRORS);
}
