import { apiRequest } from '@/api/apiClient';
import type { components } from '@/api/schema';

type Schemas = components['schemas'];

/**
 * Habitación del panel de limpieza (`CleaningRoomDto`, `plan-consultar-panel-limpieza.md`). El
 * contrato publica todos sus campos como opcionales; el backend siempre envía todos menos
 * `lastCleaningDateTime`, que se omite si la habitación nunca se limpió ("Sin registro", FR-004).
 */
export type CleaningRoom = Required<Omit<Schemas['CleaningRoomDto'], 'lastCleaningDateTime'>> &
  Pick<Schemas['CleaningRoomDto'], 'lastCleaningDateTime'>;

/**
 * Tarea abierta del miembro (`CleaningPanelDto.ActiveTaskDto` del backend). Se escribe a mano: el
 * contrato publica un único `ActiveTaskDto`, el del panel de mantenimiento, porque los dos records
 * se llaman igual, y le faltan `startDateTime` y la fila con `lastCleaningDateTime`.
 */
export interface ActiveCleaningTask {
  taskId: string;
  startDateTime: string;
  room: CleaningRoom;
}

/** `GET /api/cleaning/panel`: con tarea activa, `rooms` llega vacío (FR-001). */
export interface CleaningPanel {
  activeTask?: ActiveCleaningTask;
  rooms: CleaningRoom[];
}

export type CleaningTaskStarted = Required<Schemas['CleaningTaskStartedDto']>;

export function getCleaningPanel(): Promise<CleaningPanel> {
  return apiRequest<CleaningPanel>('/api/cleaning/panel');
}

/** "Iniciar limpieza" (`plan-marcar-habitacion-en-limpieza.md`): la fecha la pone el servidor. */
export function startCleaning(roomId: string): Promise<CleaningTaskStarted> {
  return apiRequest<CleaningTaskStarted>('/api/cleaning/tasks', {
    method: 'POST',
    body: { roomId },
  });
}

/**
 * "Confirmar fin" (`plan-confirmar-fin-de-limpieza.md`): sin descripción la habitación queda
 * disponible o reservada; con ella, fuera de servicio por el daño. Responde 204.
 */
export function completeCleaning(taskId: string, damageDescription?: string): Promise<void> {
  return apiRequest<void>(`/api/cleaning/tasks/${encodeURIComponent(taskId)}/complete`, {
    method: 'POST',
    body: damageDescription ? { damageDescription } : {},
  });
}

/** "Liberar tarea": la habitación vuelve a pendiente de limpieza. Responde 204. */
export function releaseCleaning(taskId: string): Promise<void> {
  return apiRequest<void>(`/api/cleaning/tasks/${encodeURIComponent(taskId)}/release`, {
    method: 'POST',
  });
}
