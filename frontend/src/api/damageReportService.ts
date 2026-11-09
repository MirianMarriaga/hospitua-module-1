import { apiRequest } from '@/api/apiClient';
import type { components } from '@/api/schema';

type Schemas = components['schemas'];

/** Límite de la descripción del daño (`ReportDamageRequest`: `@NotBlank`, `@Size(max = 500)`). */
export const DAMAGE_DESCRIPTION_MAX = 500;

export type DamageReportCreated = Required<Schemas['DamageReportCreatedDto']>;

/** "Ver informe" (`DamageReportViewDto`): el backend siempre envía los cuatro campos. */
export type DamageReportView = Required<Schemas['DamageReportViewDto']>;

/** Reportar daño (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md`): Available → DisabledForRepairs. */
export function reportDamage(
  roomId: string,
  damageDescription: string,
): Promise<DamageReportCreated> {
  return apiRequest<DamageReportCreated>(`/api/rooms/${roomId}/damage-reports`, {
    method: 'POST',
    body: { damageDescription },
  });
}

/** El reporte que dejó la habitación en DisabledForRepairs; 404 si ya no está en ese estado. */
export function getLatestDamageReport(roomId: string): Promise<DamageReportView> {
  return apiRequest<DamageReportView>(`/api/rooms/${roomId}/damage-reports/latest`);
}
