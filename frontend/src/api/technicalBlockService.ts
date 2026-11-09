import { apiRequest } from '@/api/apiClient';
import type { components } from '@/api/schema';

type Schemas = components['schemas'];

/** Límite de la justificación (`ScheduleTechnicalBlockRequest`: `@NotBlank`, `@Size(max = 500)`). */
export const TECHNICAL_BLOCK_REASON_MAX = 500;

/** `max-range-days` y `max-advance-days` de `application.yml` (`MaintenanceLimits`, plan base T019). */
export const MAINTENANCE_LIMITS = { maxRangeDays: 90, maxAdvanceDays: 365 } as const;

export type ScheduleTechnicalBlockRequest = Schemas['ScheduleTechnicalBlockRequest'];

/** `Scheduled`, o `Applied` si inicia hoy (la habitación queda en bloqueo técnico). */
export type TechnicalBlockCreated = Required<Schemas['TechnicalBlockCreatedDto']>;

/** "Ver informe" del bloqueo (`TechnicalBlockViewDto`): el backend siempre envía todos los campos. */
export type TechnicalBlockView = Required<Schemas['TechnicalBlockViewDto']>;

/** "Programar mantenimiento" (`plan-programar-bloqueo-tecnico-para-habitacion.md`). */
export function scheduleTechnicalBlock(
  roomId: string,
  request: ScheduleTechnicalBlockRequest,
): Promise<TechnicalBlockCreated> {
  return apiRequest<TechnicalBlockCreated>(`/api/rooms/${roomId}/technical-blocks`, {
    method: 'POST',
    body: request,
  });
}

/** El informe `Applied` de la habitación o, si no hay, su `Scheduled` más próximo. */
export function getCurrentTechnicalBlock(roomId: string): Promise<TechnicalBlockView> {
  return apiRequest<TechnicalBlockView>(`/api/rooms/${roomId}/technical-blocks/current`);
}
