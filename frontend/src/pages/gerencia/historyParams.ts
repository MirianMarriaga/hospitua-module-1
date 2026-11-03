import type { HistoryFilters } from '@/api/historyService';
import { isRoomCategory, isRoomStatus } from '@/lib/labels';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export const HISTORY_FILTER_PARAMS = [
  'roomId',
  'roomNumber',
  'categoryRoom',
  'status',
  'from',
  'to',
] as const;

export const INVALID_DATE_RANGE_TEXT = 'La fecha "Desde" no puede ser posterior a "Hasta".';

export function readHistoryFilters(params: URLSearchParams): HistoryFilters {
  const filters: HistoryFilters = {};
  const roomId = params.get('roomId');
  if (roomId) filters.roomId = roomId;
  const roomNumber = params.get('roomNumber');
  if (roomNumber) filters.roomNumber = roomNumber;
  const category = params.get('categoryRoom');
  if (isRoomCategory(category)) filters.categoryRoom = category;
  const status = params.get('status');
  if (isRoomStatus(status)) filters.status = status;
  const from = params.get('from');
  if (from && ISO_DATE.test(from)) filters.from = from;
  const to = params.get('to');
  if (to && ISO_DATE.test(to)) filters.to = to;
  return filters;
}

/** Las fechas ISO se comparan como texto; el backend responde lo mismo con `INVALID_DATE_RANGE`. */
export function hasInvalidRange(filters: HistoryFilters): boolean {
  return filters.from !== undefined && filters.to !== undefined && filters.from > filters.to;
}
