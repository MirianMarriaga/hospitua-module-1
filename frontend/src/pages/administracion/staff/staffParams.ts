import type { StaffFilters } from '@/api/staffService';
import { isStaffArea } from '@/lib/labels';

export const FILTER_PARAMS = ['query', 'area', 'active'] as const;

/** Opciones del filtro "Estado"; el valor es el del parámetro `active`. */
export const ACTIVE_OPTIONS = [
  { value: 'true', label: 'Activa' },
  { value: 'false', label: 'Desactivada' },
];

/** Filtros de la URL; un valor que el backend rechazaría se ignora. */
export function readStaffFilters(params: URLSearchParams): StaffFilters {
  const filters: StaffFilters = {};
  const query = params.get('query')?.trim();
  if (query) filters.query = query;
  const area = params.get('area');
  if (isStaffArea(area)) filters.area = area;
  const active = params.get('active');
  if (active === 'true' || active === 'false') filters.active = active === 'true';
  return filters;
}

export function hasActiveFilters(params: URLSearchParams): boolean {
  return FILTER_PARAMS.some((name) => params.has(name));
}
