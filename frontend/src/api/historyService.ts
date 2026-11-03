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

export interface HistoryFilters {
  roomId?: string;
  roomNumber?: string;
  categoryRoom?: RoomCategory;
  status?: RoomStatus;
  from?: string;
  to?: string;
}

export function getRoomStateHistory(
  filters: HistoryFilters,
  page: number,
): Promise<PagedResult<RoomStatePeriod>> {
  return apiRequest<PagedResult<RoomStatePeriod>>('/api/room-state-history', {
    params: { ...filters, page },
  });
}
