import { z } from 'zod';

import { MAINTENANCE_LIMITS, TECHNICAL_BLOCK_REASON_MAX } from '@/api/technicalBlockService';
import type { RoomStatus } from '@/api/types';
import {
  INVALID_DATE,
  invalidStateMessage,
  REASON_REQUIRED,
  RULE_MESSAGES,
} from '@/components/technicalBlockErrors';

export interface ScheduleValues {
  reason: string;
  startDate: string;
  estimatedEndDate: string;
}

export const EMPTY_SCHEDULE: ScheduleValues = { reason: '', startDate: '', estimatedEndDate: '' };

/** Días entre dos fechas `YYYY-MM-DD`, sin pasar por la zona del navegador. */
function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000);
}

/**
 * Validación previa de "Programar mantenimiento", con cada regla en su campo (regla de UI 2): las
 * de `MaintenanceDateRange.forScheduling` y el estado de la habitación si inicia hoy (FR-011 a
 * FR-015, FR-006). Depende de hoy y del estado, por eso se arma por habitación. La decisión final es
 * del backend.
 */
export function scheduleTechnicalBlockSchema(today: string, status: RoomStatus) {
  return z
    .object({ reason: z.string(), startDate: z.string(), estimatedEndDate: z.string() })
    .superRefine(({ reason, startDate, estimatedEndDate }, context) => {
      const issue = (path: keyof ScheduleValues, message: string) =>
        context.addIssue({ code: 'custom', path: [path], message });

      const text = reason.trim();
      if (text.length === 0 || text.length > TECHNICAL_BLOCK_REASON_MAX) {
        issue('reason', REASON_REQUIRED);
      }

      if (!startDate) {
        issue('startDate', INVALID_DATE);
      } else if (startDate < today) {
        issue('startDate', RULE_MESSAGES.START_IN_PAST);
      } else if (daysBetween(today, startDate) > MAINTENANCE_LIMITS.maxAdvanceDays) {
        issue('startDate', RULE_MESSAGES.START_TOO_FAR);
      } else if (startDate === today && status !== 'Available') {
        issue('startDate', invalidStateMessage(status));
      }

      if (!estimatedEndDate) {
        issue('estimatedEndDate', INVALID_DATE);
      } else if (startDate && estimatedEndDate < startDate) {
        issue('estimatedEndDate', RULE_MESSAGES.END_BEFORE_START);
      } else if (
        startDate &&
        daysBetween(startDate, estimatedEndDate) > MAINTENANCE_LIMITS.maxRangeDays
      ) {
        issue('estimatedEndDate', RULE_MESSAGES.RANGE_TOO_LONG);
      }
    });
}
