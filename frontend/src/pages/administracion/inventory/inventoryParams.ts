import type { InventoryFilters, InventorySort } from '@/api/roomService';
import { isRoomCategory, isRoomStatus } from '@/lib/labels';

/** Opciones de "Ordenar por": columna y dirección de `GET /api/rooms/inventory`. */
export const SORT_OPTIONS: {
  value: string;
  label: string;
  sort: InventorySort;
  direction: 'asc' | 'desc';
}[] = [
  { value: 'roomNumber', label: 'Número de habitación', sort: 'roomNumber', direction: 'asc' },
  { value: 'floor', label: 'Piso', sort: 'floor', direction: 'asc' },
  { value: 'categoryRoom', label: 'Tipo', sort: 'categoryRoom', direction: 'asc' },
  { value: 'maxCapacity', label: 'Capacidad', sort: 'maxCapacity', direction: 'asc' },
  { value: 'baseRate-asc', label: 'Tarifa (menor a mayor)', sort: 'baseRate', direction: 'asc' },
  { value: 'baseRate-desc', label: 'Tarifa (mayor a menor)', sort: 'baseRate', direction: 'desc' },
  { value: 'status', label: 'Estado', sort: 'status', direction: 'asc' },
];

export const FILTER_PARAMS = ['roomNumber', 'categoryRoom', 'floor', 'status'] as const;

export function readInventoryFilters(params: URLSearchParams): InventoryFilters {
  const filters: InventoryFilters = {};
  const roomNumber = params.get('roomNumber');
  if (roomNumber) filters.roomNumber = roomNumber;
  const category = params.get('categoryRoom');
  if (isRoomCategory(category)) filters.categoryRoom = category;
  const floor = Number(params.get('floor'));
  if (Number.isInteger(floor) && floor > 0) filters.floor = floor;
  const status = params.get('status');
  if (isRoomStatus(status)) filters.status = status;
  const option = SORT_OPTIONS.find((candidate) => candidate.value === params.get('sort'));
  if (option) {
    filters.sort = option.sort;
    filters.direction = option.direction;
  }
  return filters;
}

export function hasActiveFilters(params: URLSearchParams): boolean {
  return FILTER_PARAMS.some((name) => params.has(name));
}
