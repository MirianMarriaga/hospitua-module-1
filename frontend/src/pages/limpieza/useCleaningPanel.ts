import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  completeCleaning,
  getCleaningPanel,
  releaseCleaning,
  startCleaning,
  type ActiveCleaningTask,
  type CleaningPanel,
  type CleaningRoom,
} from '@/api/cleaningService';
import { usePanelQuery } from '@/hooks/usePanelQuery';
import { CLEANING_KEY } from '@/lib/queryKeys';

/** Lo que muestra el panel: la tarea activa o la lista, con el conteo por estado (FR-010). */
export interface CleaningPanelView {
  activeTask?: ActiveCleaningTask;
  rooms: CleaningRoom[];
  pendingCount: number;
  availableCount: number;
}

export function toCleaningPanelView(panel: CleaningPanel): CleaningPanelView {
  return {
    activeTask: panel.activeTask,
    rooms: panel.rooms,
    pendingCount: panel.rooms.filter((room) => room.status === 'PendingCleaning').length,
    availableCount: panel.rooms.filter((room) => room.status === 'Available').length,
  };
}

/** Panel de limpieza, que se vuelve a consultar cada 60 s y al volver a él (FR-009). */
export function useCleaningPanel() {
  return usePanelQuery(CLEANING_KEY, getCleaningPanel, toCleaningPanelView);
}

/** "Iniciar limpieza": al terminar, bien o mal, el panel se vuelve a consultar (FR-009). */
export function useStartCleaning() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: startCleaning,
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLEANING_KEY }),
  });
}

/** "Confirmar fin", con la descripción del daño si se escribió (FR-018). */
export function useCompleteCleaning() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, damageDescription }: { taskId: string; damageDescription?: string }) =>
      completeCleaning(taskId, damageDescription),
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLEANING_KEY }),
  });
}

/** "Liberar tarea": la habitación vuelve a la lista como pendiente de limpieza (FR-013). */
export function useReleaseCleaning() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: string) => releaseCleaning(taskId),
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLEANING_KEY }),
  });
}
