/** Rutas del área de mantenimiento, en un módulo aparte para que las páginas se enlacen sin ciclos. */
export const MAINTENANCE_PANEL_PATH = '/mantenimiento';
export const ACTIVE_REPAIR_TASK_PATH = '/mantenimiento/tarea';

/** Estado con el que se vuelve al panel: el aviso de lo que pasó. */
export interface MaintenancePanelState {
  notice?: string;
}
