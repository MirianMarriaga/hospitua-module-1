import type { Room } from '@/api/roomService';
import type { PagedResult } from '@/api/types';

export function room(overrides: Partial<Room> = {}): Room {
  return {
    roomId: '11111111-1111-4111-a111-111111111111',
    status: 'Available',
    roomNumber: '101',
    floor: 1,
    categoryRoom: 'SENCILLA',
    maxCapacity: 1,
    baseRate: 180000,
    version: 0,
    ...overrides,
  };
}

export function page<T>(items: T[], overrides: Partial<PagedResult<T>> = {}): PagedResult<T> {
  return {
    items,
    page: 1,
    size: 10,
    totalElements: items.length,
    totalPages: items.length === 0 ? 0 : 1,
    ...overrides,
  };
}
