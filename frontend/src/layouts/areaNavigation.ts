import type { StaffArea } from '@/api/types';

export interface NavItem {
  to: string;
  label: string;
}

/** Menú del Sidebar y pantalla de inicio de cada área (la primera entrada). */
export const AREA_NAVIGATION: Record<StaffArea, NavItem[]> = {
  RECEPCION: [{ to: '/recepcion', label: 'Panel de recepción' }],
  ADMINISTRACION: [
    { to: '/administracion/inventario', label: 'Inventario' },
    { to: '/administracion/habitaciones/nueva', label: 'Registrar habitación' },
  ],
  GERENCIA: [
    { to: '/gerencia/inventario', label: 'Inventario' },
    { to: '/gerencia/historial', label: 'Historial de estados' },
  ],
  LIMPIEZA: [{ to: '/limpieza', label: 'Panel de limpieza' }],
  MANTENIMIENTO: [{ to: '/mantenimiento', label: 'Panel de mantenimiento' }],
};

export function homeOf(area: StaffArea): string {
  return AREA_NAVIGATION[area][0]!.to;
}
