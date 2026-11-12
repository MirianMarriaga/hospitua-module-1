import { apiRequest } from '@/api/apiClient';
import type { components } from '@/api/schema';

type Schemas = components['schemas'];

/** Rango del mantenimiento `Applied` o, si no hay, del `Scheduled` más próximo (FR-004). */
export type MaintenanceRange = Required<Schemas['MaintenanceRangeDto']>;

/**
 * Habitación del panel de mantenimiento (`MaintenanceRoomDto`, `plan-consultar-panel-mantenimiento.md`).
 * El contrato publica todos los campos como opcionales; el backend siempre envía todos menos
 * `maintenance`, que se omite si la habitación no tiene ninguno ("Sin programar").
 */
export type MaintenanceRoom = Required<Omit<Schemas['MaintenanceRoomDto'], 'maintenance'>> & {
  maintenance?: MaintenanceRange;
};

/**
 * Tarea de reparación abierta del miembro (`MaintenanceActiveTaskDto`), con su inicio y la fila de
 * su habitación. El contrato publica sus campos como opcionales; el backend siempre los envía.
 */
export type ActiveRepairTask = Required<Omit<Schemas['MaintenanceActiveTaskDto'], 'room'>> & {
  room: MaintenanceRoom;
};

/** `GET /api/maintenance/panel`: con tarea activa, `rooms` llega vacío (FR-001). */
export interface MaintenancePanel {
  activeTask?: ActiveRepairTask;
  rooms: MaintenanceRoom[];
}

export type RepairTaskStarted = Required<Schemas['RepairTaskStartedDto']>;

export function getMaintenancePanel(): Promise<MaintenancePanel> {
  return apiRequest<MaintenancePanel>('/api/maintenance/panel');
}

/** "Iniciar reparaciones" (`plan-confirmar-reparacion-finalizada.md`): la fecha la pone el servidor. */
export function startRepair(roomId: string): Promise<RepairTaskStarted> {
  return apiRequest<RepairTaskStarted>('/api/maintenance/tasks', {
    method: 'POST',
    body: { roomId },
  });
}

/**
 * "Confirmar fin" (`plan-confirmar-reparacion-finalizada.md`): la habitación pasa a pendiente de
 * limpieza y, si estaba en bloqueo técnico, su mantenimiento queda terminado. Responde 204.
 */
export function completeRepair(taskId: string): Promise<void> {
  return apiRequest<void>(`/api/maintenance/tasks/${encodeURIComponent(taskId)}/complete`, {
    method: 'POST',
  });
}

/** "Liberar tarea": la habitación sigue fuera de servicio para otro compañero. Responde 204. */
export function releaseRepair(taskId: string): Promise<void> {
  return apiRequest<void>(`/api/maintenance/tasks/${encodeURIComponent(taskId)}/release`, {
    method: 'POST',
  });
}
