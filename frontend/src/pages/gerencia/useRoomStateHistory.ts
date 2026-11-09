import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getRoomStateHistory, type HistoryFilters } from '@/api/historyService';

/**
 * Página del historial; mientras llega la siguiente se sigue viendo la anterior. Con un rango de
 * fechas inválido no se consulta (`enabled`).
 */
export function useRoomStateHistory(filters: HistoryFilters, page: number, enabled: boolean) {
  return useQuery({
    queryKey: ['roomStateHistory', filters, page],
    queryFn: () => getRoomStateHistory(filters, page),
    placeholderData: keepPreviousData,
    enabled,
  });
}
