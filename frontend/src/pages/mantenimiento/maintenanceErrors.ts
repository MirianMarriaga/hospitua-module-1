import { errorMessage, invalidRoomStateLabel } from '@/lib/errorMessages';

// Textos de "Iniciar reparaciones" según el errorCode: tabla de errores de
// `plan-confirmar-reparacion-finalizada.md` (POST /api/maintenance/tasks).
const START_REPAIR_ERRORS = {
  VALIDATION_ERROR: 'Selecciona una habitación válida.',
  ROOM_NOT_FOUND: 'No se encontró la habitación.',
  ROOM_TASK_EXISTS: 'Otro compañero ya está reparando esta habitación.',
  ACTIVE_TASK_EXISTS:
    'Ya estás reparando otra habitación. Termínala o déjala antes de empezar otra.',
};

/** El rechazo por estado dice el estado actual de la habitación. */
export function startRepairErrorMessage(error: unknown): string {
  const state = invalidRoomStateLabel(error);
  if (state) return `La habitación está ${state}; no necesita reparación.`;
  return errorMessage(error, START_REPAIR_ERRORS);
}
