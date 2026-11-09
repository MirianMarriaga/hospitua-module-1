import { ApiRequestError } from '@/api/apiClient';
import {
  MAINTENANCE_LIMITS,
  TECHNICAL_BLOCK_REASON_MAX,
  type TechnicalBlockCreated,
} from '@/api/technicalBlockService';
import { errorMessage } from '@/lib/errorMessages';
import { formatDate } from '@/lib/format';
import { isRoomStatus, ROOM_STATUS_LABELS } from '@/lib/labels';
import type { RoomStatus } from '@/api/types';

// Textos del bloqueo técnico: el aviso de éxito y las tablas de errores de `plan-programar-bloqueo-tecnico-para-habitacion.md` y de los
// "Mensajes por regla" de `plan-consultar-informacion-mantenimientos.md`.

export const REASON_REQUIRED = `Escribe el motivo del mantenimiento (máximo ${TECHNICAL_BLOCK_REASON_MAX} caracteres).`;
const INVALID_DATE = 'Elige una fecha válida (DD-MM-YYYY).';
export const RESERVATION_CONFLICT_TEXT =
  'La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas.';
export const MODULE_2_UNAVAILABLE_TEXT =
  'Servicio de reservas temporalmente no disponible. No se programó el mantenimiento; inténtalo de nuevo.';

const RULE_MESSAGES: Record<string, string> = {
  END_BEFORE_START: 'La fecha de fin no puede ser anterior a la fecha de inicio.',
  START_IN_PAST: 'La fecha de inicio no puede ser anterior a hoy.',
  RANGE_TOO_LONG: `El mantenimiento no puede durar más de ${MAINTENANCE_LIMITS.maxRangeDays} días.`,
  START_TOO_FAR: `Solo se puede programar con hasta ${MAINTENANCE_LIMITS.maxAdvanceDays} días de anticipación.`,
};

const SCHEDULE_ERRORS = {
  ROOM_NOT_FOUND: 'No se encontró la habitación.',
  RESERVATION_CONFLICT: RESERVATION_CONFLICT_TEXT,
  MODULE_2_UNAVAILABLE: MODULE_2_UNAVAILABLE_TEXT,
};

const VIEW_ERRORS = {
  ROOM_NOT_FOUND: 'No se encontró la habitación.',
  TECHNICAL_BLOCK_NOT_FOUND: 'Esta habitación no tiene un mantenimiento programado ni en curso.',
};

/**
 * Rechazo por estado: dada de baja, o un inicio hoy en una habitación que no está disponible. El
 * estado va con su nombre de la interfaz, como `invalidRoomStateLabel` (regla 12 del plan base).
 */
export function invalidStateMessage(status: RoomStatus): string {
  if (status === 'Inactive') {
    return 'La habitación está dada de baja; no se puede programar mantenimiento.';
  }
  return `Hoy la habitación está ${ROOM_STATUS_LABELS[status]}. Para empezar hoy debe estar disponible; elige una fecha de inicio posterior.`;
}

function maintenanceConflictMessage(details: Record<string, unknown>): string | undefined {
  const conflicts = details.conflicts;
  if (!Array.isArray(conflicts) || conflicts.length === 0) return undefined;
  const first: unknown = conflicts[0];
  if (typeof first !== 'object' || first === null) return undefined;
  const { maintenanceStart, maintenanceEnd } = first as Record<string, unknown>;
  if (typeof maintenanceStart !== 'string' || typeof maintenanceEnd !== 'string') return undefined;
  return `Ya hay un mantenimiento programado o en curso del ${formatDate(maintenanceStart)} al ${formatDate(maintenanceEnd)}. Elige otras fechas.`;
}

/** Texto de un rechazo de "Programar" según el `errorCode` y sus `details` (T024). */
export function scheduleErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    const { errorCode, details } = error;
    if (errorCode === 'VALIDATION_ERROR') {
      return details.field === 'reason' ? REASON_REQUIRED : INVALID_DATE;
    }
    if (errorCode === 'INVALID_DATE_RANGE') {
      const rule = details.rule;
      return (typeof rule === 'string' && RULE_MESSAGES[rule]) || errorMessage(error);
    }
    if (errorCode === 'ROOM_INVALID_STATE') {
      const current = details.currentStatus;
      return typeof current === 'string' && isRoomStatus(current)
        ? invalidStateMessage(current)
        : 'La habitación cambió de estado. Revisa el panel e intenta de nuevo.';
    }
    if (errorCode === 'MAINTENANCE_CONFLICT') {
      return (
        maintenanceConflictMessage(details) ??
        'Ya hay un mantenimiento programado o en curso en esas fechas. Elige otras fechas.'
      );
    }
  }
  return errorMessage(error, SCHEDULE_ERRORS);
}

export function viewTechnicalBlockErrorMessage(error: unknown): string {
  return errorMessage(error, VIEW_ERRORS);
}

/** Días entre dos fechas `YYYY-MM-DD`, sin pasar por la zona del navegador. */
function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000);
}

/**
 * Validación previa en el cliente, en el orden del backend (`MaintenanceDateRange.forScheduling` y
 * después el estado), para no gastar una petición; la decisión final es del backend (FR-011 a
 * FR-015, FR-006). Devuelve el texto de la primera regla incumplida o `null`.
 */
export function validateSchedule(input: {
  reason: string;
  startDate: string;
  estimatedEndDate: string;
  today: string;
  status: RoomStatus;
}): string | null {
  const { reason, startDate, estimatedEndDate, today, status } = input;
  const text = reason.trim();
  if (text.length === 0 || text.length > TECHNICAL_BLOCK_REASON_MAX) return REASON_REQUIRED;
  if (!startDate || !estimatedEndDate) return INVALID_DATE;
  if (estimatedEndDate < startDate) return RULE_MESSAGES.END_BEFORE_START;
  if (startDate < today) return RULE_MESSAGES.START_IN_PAST;
  if (daysBetween(startDate, estimatedEndDate) > MAINTENANCE_LIMITS.maxRangeDays) {
    return RULE_MESSAGES.RANGE_TOO_LONG;
  }
  if (daysBetween(today, startDate) > MAINTENANCE_LIMITS.maxAdvanceDays) {
    return RULE_MESSAGES.START_TOO_FAR;
  }
  if (startDate === today && status !== 'Available') return invalidStateMessage(status);
  return null;
}

/** Aviso de éxito de `mantenimiento/01-panel.html` según el `status` de la respuesta (T024). */
export function scheduledNotice(
  roomNumber: string,
  result: TechnicalBlockCreated,
  startDate: string,
  estimatedEndDate: string,
): string {
  return result.status === 'Applied'
    ? `Mantenimiento iniciado. La habitación ${roomNumber} queda fuera de servicio hasta el ${formatDate(estimatedEndDate)} (fecha estimada).`
    : `Mantenimiento programado del ${formatDate(startDate)} al ${formatDate(estimatedEndDate)} para la habitación ${roomNumber}.`;
}
