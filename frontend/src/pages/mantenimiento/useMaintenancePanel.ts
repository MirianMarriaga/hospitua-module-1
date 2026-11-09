import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  getMaintenancePanel,
  startRepair,
  type ActiveRepairTask,
  type MaintenancePanel,
  type MaintenanceRoom,
} from '@/api/maintenanceService';
import { usePanelQuery } from '@/hooks/usePanelQuery';
import { MAINTENANCE_KEY } from '@/lib/queryKeys';

/** Lo que muestra el panel: la tarea activa o la lista, con los conteos de FR-009. */
export interface MaintenancePanelView {
  activeTask?: ActiveRepairTask;
  rooms: MaintenanceRoom[];
  disabledCount: number;
  technicalBlockCount: number;
  overdueCount: number;
  /** Solo los `Scheduled`: con `maintenance` y sin estar en bloqueo técnico (FR-009). */
  scheduledCount: number;
}

/** Habitaciones que requieren intervención: las inhabilitadas y las de bloqueo técnico (FR-006). */
export function needsIntervention(room: MaintenanceRoom): boolean {
  return room.status === 'DisabledForRepairs' || room.status === 'TechnicalBlock';
}

export function toMaintenancePanelView(panel: MaintenancePanel): MaintenancePanelView {
  const blocked = panel.rooms.filter((room) => room.status === 'TechnicalBlock');
  return {
    activeTask: panel.activeTask,
    rooms: panel.rooms,
    disabledCount: panel.rooms.filter((room) => room.status === 'DisabledForRepairs').length,
    technicalBlockCount: blocked.length,
    overdueCount: blocked.filter((room) => room.overdue).length,
    scheduledCount: panel.rooms.filter(
      (room) => room.maintenance !== undefined && room.status !== 'TechnicalBlock',
    ).length,
  };
}

/** Panel de mantenimiento, que se vuelve a consultar cada 60 s y al volver a él (FR-010). */
export function useMaintenancePanel() {
  return usePanelQuery(MAINTENANCE_KEY, getMaintenancePanel, toMaintenancePanelView);
}

/** "Iniciar reparaciones": al terminar, bien o mal, el panel se vuelve a consultar (T011). */
export function useStartRepair() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (roomId: string) => startRepair(roomId),
    onSettled: () => queryClient.invalidateQueries({ queryKey: MAINTENANCE_KEY }),
  });
}
