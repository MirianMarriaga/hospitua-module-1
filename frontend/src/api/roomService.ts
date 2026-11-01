import { apiRequest } from '@/api/apiClient';
import type { PagedResult, RoomCategory, RoomStatus } from '@/api/types';

export interface Room {
  roomId: string;
  status: RoomStatus;
  roomNumber: string;
  floor: number;
  categoryRoom: RoomCategory;
  maxCapacity: number;
  baseRate: number;
  version: number;
}

export type InventorySort =
  'roomNumber' | 'floor' | 'categoryRoom' | 'maxCapacity' | 'baseRate' | 'status';

export interface InventoryFilters {
  roomNumber?: string;
  categoryRoom?: RoomCategory;
  floor?: number;
  status?: RoomStatus;
  sort?: InventorySort;
  direction?: 'asc' | 'desc';
}

export const INVENTORY_PAGE_SIZE = 10;

export function getInventory(filters: InventoryFilters, page: number): Promise<PagedResult<Room>> {
  return apiRequest<PagedResult<Room>>('/api/rooms/inventory', {
    params: { ...filters, page, size: INVENTORY_PAGE_SIZE },
  });
}

export function getRoom(roomId: string): Promise<Room> {
  return apiRequest<Room>(`/api/rooms/inventory/${encodeURIComponent(roomId)}`);
}

export interface RegisterRoomRequest {
  roomNumber: string;
  floor: number;
  categoryRoom: RoomCategory;
  maxCapacity: number;
  baseRate: number;
}

export function registerRoom(request: RegisterRoomRequest): Promise<Room> {
  return apiRequest<Room>('/api/rooms', { method: 'POST', body: request });
}
