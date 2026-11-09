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

export type DecommissionReason =
  'PERMANENT_REMODELING' | 'FLOOR_CLOSURE' | 'ADMINISTRATIVE_DECISION' | 'OTHER';

export interface DecommissionRoomRequest {
  reasonCategory: DecommissionReason;
  reasonDetail?: string;
}

export interface RoomDecommissionDto {
  decommissionId: string;
  roomId: string;
  roomNumber: string;
  floor: number;
  categoryRoom: RoomCategory;
  status: RoomStatus;
  reasonCategory: DecommissionReason;
  reasonDetail: string | null;
  decommissionDateTime: string;
}

export interface RoomDecommissionInfoDto {
  roomId: string;
  roomNumber: string;
  floor: number;
  categoryRoom: RoomCategory;
  status: RoomStatus;
  decommissionId: string;
  decommissionDateTime: string;
  reasonCategory: DecommissionReason;
  reasonDetail: string | null;
}

export interface ReactivateRoomRequest {
  decommissionId: string;
}

export interface ReactivationResultDto {
  roomId: string;
  roomNumber: string;
  floor: number;
  categoryRoom: RoomCategory;
  status: RoomStatus;
  alreadyReactivated: boolean;
}

export interface EditRoomRequest {
  version: number;
  roomNumber?: string;
  floor?: number;
  categoryRoom?: string;
  maxCapacity?: number;
  baseRate?: number;
}

export interface RoomAttributeChange {
  field: string;
  previousValue: unknown;
  newValue: unknown;
}

export interface EditRoomResultDto {
  changed: boolean;
  changes: RoomAttributeChange[];
  room: Room;
}

export interface ConflictingReservation {
  reservationRef: string;
  startDate: string;
  endDate: string;
  status: string;
}

function isConflictingReservation(value: unknown): value is ConflictingReservation {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const item: Record<string, unknown> = { ...value };
  return ['reservationRef', 'startDate', 'endDate', 'status'].every(
    (key) => typeof item[key] === 'string',
  );
}

/**
 * Reservas en conflicto de un `409 RESERVATION_CONFLICT` (`details.reservations`). Lo que no tenga
 * la forma esperada se descarta, en lugar de suponerla con un `as`.
 */
export function readConflictingReservations(
  details: Record<string, unknown>,
): ConflictingReservation[] {
  const reservations = details.reservations;
  return Array.isArray(reservations) ? reservations.filter(isConflictingReservation) : [];
}

export function decommissionRoom(
  roomId: string,
  request: DecommissionRoomRequest,
): Promise<RoomDecommissionDto> {
  return apiRequest<RoomDecommissionDto>(`/api/rooms/${encodeURIComponent(roomId)}/decommission`, {
    method: 'POST',
    body: request,
  });
}

export function getRoomDecommission(roomId: string): Promise<RoomDecommissionInfoDto> {
  return apiRequest<RoomDecommissionInfoDto>(
    `/api/rooms/${encodeURIComponent(roomId)}/decommission`,
  );
}

export function reactivateRoom(
  roomId: string,
  request: ReactivateRoomRequest,
): Promise<ReactivationResultDto> {
  return apiRequest<ReactivationResultDto>(
    `/api/rooms/${encodeURIComponent(roomId)}/reactivation`,
    {
      method: 'POST',
      body: request,
    },
  );
}

export function editRoom(roomId: string, request: EditRoomRequest): Promise<EditRoomResultDto> {
  return apiRequest<EditRoomResultDto>(`/api/rooms/${encodeURIComponent(roomId)}`, {
    method: 'PATCH',
    body: request,
  });
}
