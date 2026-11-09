/** Rutas del área de limpieza, en un módulo aparte para que las dos páginas se enlacen sin ciclos. */
export const CLEANING_PANEL_PATH = '/limpieza';
export const ACTIVE_CLEANING_TASK_PATH = '/limpieza/tarea';

/** Estado con el que la tarea activa vuelve al panel: el aviso de lo que pasó (vista 03). */
export interface CleaningPanelState {
  notice?: string;
}
