import { BedDouble, ConciergeBell, History, type LucideIcon, SprayCan, Wrench } from 'lucide-react';

import type { StaffArea } from '@/api/types';

export interface NavItem {
  to: string;
  label: string;
  /** Ícono lucide junto a la opción del menú, como en el prototipo. */
  icon: LucideIcon;
}

/** Menú del Sidebar y pantalla de inicio de cada área (la primera entrada). */
export const AREA_NAVIGATION: Record<StaffArea, NavItem[]> = {
  RECEPCION: [{ to: '/recepcion', label: 'Panel de recepción', icon: ConciergeBell }],
  ADMINISTRACION: [{ to: '/administracion/inventario', label: 'Inventario', icon: BedDouble }],
  GERENCIA: [
    { to: '/gerencia/inventario', label: 'Inventario', icon: BedDouble },
    { to: '/gerencia/historial', label: 'Historial de estados', icon: History },
  ],
  LIMPIEZA: [{ to: '/limpieza', label: 'Panel de limpieza', icon: SprayCan }],
  MANTENIMIENTO: [{ to: '/mantenimiento', label: 'Panel de mantenimiento', icon: Wrench }],
};

export function homeOf(area: StaffArea): string {
  return AREA_NAVIGATION[area][0]!.to;
}
