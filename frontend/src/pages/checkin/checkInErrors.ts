import { ApiRequestError } from '@/api/apiClient';
import { errorMessage } from '@/lib/errorMessages';
import { isRoomStatus, ROOM_STATUS_LABELS } from '@/lib/labels';

// Textos para el recepcionista, sin jerga técnica ni menciones de módulos o colas (T025).
const CHECK_IN_ERRORS = {
  RESERVATION_NOT_FOUND: 'No encontramos esta reserva entre las llegadas de hoy.',
  ROOM_NOT_FOUND: 'La habitación de esta reserva no está en el inventario.',
  INVALID_CHECK_IN_DATE: 'Esta reserva no llega hoy. El check-in solo se hace el día de llegada.',
  ROOM_INVALID_STATE: 'La habitación no está reservada para esta reserva.',
  STAY_ALREADY_EXISTS: 'Esta habitación ya tiene el check-in registrado.',
  GUEST_COUNT_MISMATCH: 'La cantidad de huéspedes no coincide con la reserva.',
  ROOM_CAPACITY_EXCEEDED: 'La cantidad de huéspedes supera la capacidad de la habitación.',
  DUPLICATE_GUEST_DOCUMENT: 'Dos huéspedes de la habitación tienen el mismo documento.',
  VALIDATION_ERROR: 'Revisa los datos de los huéspedes.',
};

export function checkInErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError && error.errorCode === 'ROOM_INVALID_STATE') {
    const current = error.details.currentStatus;
    if (typeof current === 'string' && isRoomStatus(current)) {
      return `La habitación está ${ROOM_STATUS_LABELS[current]}; solo se puede hacer el check-in de una habitación Reservada para esta reserva.`;
    }
  }
  return errorMessage(error, CHECK_IN_ERRORS);
}
