import { z } from 'zod';

import type { DecommissionReason, DecommissionRoomRequest } from '@/api/roomService';

/** Las categorías de `DecommissionRoomRequest` (plan-dar-de-baja-habitacion.md, FR-008). */
const REASONS = [
  'PERMANENT_REMODELING',
  'FLOOR_CLOSURE',
  'ADMINISTRATIVE_DECISION',
  'OTHER',
] as const satisfies readonly DecommissionReason[];

export const DETAIL_MAX = 500;

// Mismas reglas que `DecommissionRoomRequest` y `RoomDecommission` del backend: categoría del
// catálogo, detalle de máximo 500 caracteres y obligatorio (no solo espacios) con "Otro". Los textos
// son los de la vista `06-baja-motivo.html` y la tabla de errores del plan.
export const decommissionSchema = z
  .object({
    reasonCategory: z
      .string()
      .pipe(z.enum(REASONS, { error: 'Selecciona un motivo para continuar.' })),
    reasonDetail: z.string().max(DETAIL_MAX, 'El detalle admite máximo 500 caracteres.'),
  })
  .superRefine((values, context) => {
    if (values.reasonCategory === 'OTHER' && values.reasonDetail.trim() === '') {
      context.addIssue({
        code: 'custom',
        path: ['reasonDetail'],
        message: 'Escribe el motivo de la baja.',
      });
    }
  });

export type DecommissionInput = z.input<typeof decommissionSchema>;
export type DecommissionValues = z.output<typeof decommissionSchema>;

export const EMPTY_REASON: DecommissionInput = { reasonCategory: '', reasonDetail: '' };

export function toDecommissionRequest(values: DecommissionValues): DecommissionRoomRequest {
  const detail = values.reasonDetail.trim();
  return { reasonCategory: values.reasonCategory, reasonDetail: detail || undefined };
}
