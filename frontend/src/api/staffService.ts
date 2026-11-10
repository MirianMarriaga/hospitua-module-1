import { apiRequest } from '@/api/apiClient';
import type { components } from '@/api/schema';
import type { PagedResult, StaffArea } from '@/api/types';

type Schemas = components['schemas'];

/** Cuenta del personal en la lista (`StaffAccountDto` sin `version`, que solo trae el detalle). */
export type StaffAccount = Required<Omit<Schemas['StaffAccountDto'], 'version'>>;

/** Cuenta con su `version`, la que devuelve `GET /api/staff/{username}` para editar. */
export type StaffAccountDetail = Required<Schemas['StaffAccountDto']>;

/** Filtros de `GET /api/staff`; todos opcionales y combinables. */
export interface StaffFilters {
  area?: StaffArea;
  active?: boolean;
  query?: string;
}

/** Página de cuentas, ordenadas por nombre; el backend fija 10 por página. */
export function listStaff(filters: StaffFilters, page: number): Promise<PagedResult<StaffAccount>> {
  return apiRequest<PagedResult<StaffAccount>>('/api/staff', { params: { ...filters, page } });
}

export function getStaffAccount(username: string): Promise<StaffAccountDetail> {
  return apiRequest<StaffAccountDetail>(`/api/staff/${encodeURIComponent(username)}`);
}
