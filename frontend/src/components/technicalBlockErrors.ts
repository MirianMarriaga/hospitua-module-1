import { ApiRequestError } from '@/api/apiClient';
import {
  MAINTENANCE_LIMITS,
  TECHNICAL_BLOCK_REASON_MAX,
  type TechnicalBlockCreated,
} from '@/api/technicalBlockService';
import { errorMessage } from '@/lib/errorMessages';
import { formatDate } from '@/lib/format';
import { isRoomStatus } from '@/lib/labels';
import type { RoomStatus } from '@/api/types';

// Textos del bloqueo técnico: el aviso de éxito y las tablas de errores de `plan-programar-bloqueo-tecnico-para-habitacion.md` y de los
// "Mensajes por regla" de `plan-consultar-informacion-mantenimientos.md`.

export const REASON_REQUIRED = `Escribe el motivo del mantenimiento (máximo ${TECHNICAL_BLOCK_REASON_MAX} caracteres).`;
export const INVALID_DATE = 'Elige una fecha válida (DD-MM-YYYY).';
export const RESERVATION_CONFLICT_TEXT =
  'La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas.';
export const MODULE_2_UNAVAILABLE_TEXT =
  'Servicio de reservas temporalmente no disponible. No se programó el mantenimiento; inténtalo de nuevo.';

export const RULE_MESSAGES = {
  END_BEFORE_START: 'La fecha de fin no puede ser anterior a la fecha de inicio.',
  START_IN_PAST: 'La fecha de inicio no puede ser anterior a hoy.',
  RANGE_TOO_LONG: `El mantenimiento no puede durar más de ${MAINTENANCE_LIMITS.maxRangeDays} días.`,
  START_TOO_FAR: `Solo se puede programar con hasta ${MAINTENANCE_LIMITS.maxAdvanceDays} días de anticipación.`,
} as const;

type DateRule = keyof typeof RULE_MESSAGES;

/** Campos del formulario de "Programar mantenimiento". */
export type ScheduleField = 'reason' | 'startDate' | 'estimatedEndDate';

/** Campo en el que se marca cada regla de fechas (regla de UI 2). */
const RULE_FIELDS: Record<DateRule, ScheduleField> = {
  END_BEFORE_START: 'estimatedEndDate',
  START_IN_PAST: 'startDate',
  RANGE_TOO_LONG: 'estimatedEndDate',
  START_TOO_FAR: 'startDate',
};

function isDateRule(rule: unknown): rule is DateRule {
  return typeof rule === 'string' && rule in RULE_MESSAGES;
}

function isScheduleField(field: unknown): field is ScheduleField {
  return field === 'reason' || field === 'startDate' || field === 'estimatedEndDate';
}

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
 * Cada estado escrito para ir después de "Habitación" en el rechazo por estado (F20). No usa
 * `ROOM_STATUS_LABELS`: esas etiquetas van con mayúscula y "Bloqueo técnico" no encaja en la frase.
 */
const STATUS_IN_SENTENCE: Record<Exclude<RoomStatus, 'Inactive'>, string> = {
  Available: 'disponible',
  Reserved: 'reservada',
  Occupied: 'ocupada',
  PendingCleaning: 'pendiente de limpieza',
  InCleaning: 'en limpieza',
  DisabledForRepairs: 'inhabilitada por reparaciones',
  TechnicalBlock: 'en bloqueo técnico',
};

/**
 * Rechazo por estado: dada de baja, o un inicio hoy en una habitación que no está disponible
 * (FR-001, esc. 10: informa el estado actual). Solo se rechaza el inicio hoy, así que el aviso
 * habla de hoy y pide otra fecha de inicio.
 */
export function invalidStateMessage(status: RoomStatus): string {
  if (status === 'Inactive') {
    return 'La habitación está dada de baja; no se puede programar mantenimiento.';
  }
  return `Habitación ${STATUS_IN_SENTENCE[status]} hoy. Elige otra fecha de inicio.`;
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
      return isDateRule(details.rule) ? RULE_MESSAGES[details.rule] : errorMessage(error);
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

/**
 * Rechazo de "Programar" que corresponde a un campo: `VALIDATION_ERROR` por `details.field` e
 * `INVALID_DATE_RANGE` por `details.rule` (regla de UI 2). Los demás van en el aviso del diálogo.
 */
export function scheduleFieldError(
  error: unknown,
): { field: ScheduleField; message: string } | null {
  if (!(error instanceof ApiRequestError)) return null;
  const { errorCode, details } = error;
  if (errorCode === 'VALIDATION_ERROR' && isScheduleField(details.field)) {
    return {
      field: details.field,
      message: details.field === 'reason' ? REASON_REQUIRED : INVALID_DATE,
    };
  }
  if (errorCode === 'INVALID_DATE_RANGE' && isDateRule(details.rule)) {
    return { field: RULE_FIELDS[details.rule], message: RULE_MESSAGES[details.rule] };
  }
  return null;
}

export function viewTechnicalBlockErrorMessage(error: unknown): string {
  return errorMessage(error, VIEW_ERRORS);
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
