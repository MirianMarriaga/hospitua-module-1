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

/** Cuerpo de `POST /api/staff`; el área es una de las cinco del catálogo. */
export type RegisterStaffRequest = Omit<Schemas['RegisterStaffRequest'], 'area'> & {
  area: StaffArea;
};

/** Registra una cuenta activa; la respuesta nunca trae la contraseña. */
export function registerStaff(request: RegisterStaffRequest): Promise<StaffAccount> {
  return apiRequest<StaffAccount>('/api/staff', { method: 'POST', body: request });
}

/** Cuerpo de `PATCH /api/staff/{username}`: sin `password`, la contraseña actual se conserva. */
export type EditStaffRequest = Omit<Schemas['EditStaffRequest'], 'area'> & { area: StaffArea };

/** Edita el nombre, el área y, si se envía, la contraseña; responde la cuenta con su `version` nueva. */
export function editStaff(
  username: string,
  request: EditStaffRequest,
): Promise<StaffAccountDetail> {
  return apiRequest<StaffAccountDetail>(`/api/staff/${encodeURIComponent(username)}`, {
    method: 'PATCH',
    body: request,
  });
}

/** Desactiva la cuenta sin borrarla; responde la cuenta con `active` en `false`. */
export function deactivateStaff(username: string): Promise<StaffAccount> {
  return apiRequest<StaffAccount>(`/api/staff/${encodeURIComponent(username)}/deactivation`, {
    method: 'POST',
  });
}
