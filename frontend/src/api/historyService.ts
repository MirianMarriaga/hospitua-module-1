import { apiRequest } from '@/api/apiClient';
import type { PagedResult, RoomCategory, RoomStatus } from '@/api/types';

export interface RoomStatePeriod {
  roomId: string;
  roomNumber: string;
  categoryRoom: RoomCategory;
  status: RoomStatus;
  startDateTime: string;
  endDateTime?: string;
  actorName?: string;
  sourceFlow: string;
  sourceFlowName: string;
}

/**
 * Sentido del orden por fecha de inicio (FR-009): `asc`, de la más antigua a la más reciente (por
 * defecto); `desc`, al revés. La vista no envía `sort`, así que el backend ordena por inicio.
 */
export type HistoryDirection = 'asc' | 'desc';

export interface HistoryFilters {
  roomId?: string;
  roomNumber?: string;
  categoryRoom?: RoomCategory;
  status?: RoomStatus;
  from?: string;
  to?: string;
  direction?: HistoryDirection;
}

export function getRoomStateHistory(
  filters: HistoryFilters,
  page: number,
): Promise<PagedResult<RoomStatePeriod>> {
  return apiRequest<PagedResult<RoomStatePeriod>>('/api/room-state-history', {
    params: { ...filters, page },
  });
}
