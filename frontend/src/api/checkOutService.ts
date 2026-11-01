import { apiRequest } from '@/api/apiClient';
import type { DocumentType, RoomStatus } from '@/api/types';

export interface ActiveStayGuest {
  firstName: string;
  lastName: string;
  documentType: DocumentType;
  documentNumber: string;
  nationality: string;
}

export interface ActiveStay {
  stayId: string;
  reservationRef: string;
  roomId: string;
  roomNumber: string;
  categoryRoom: string;
  roomStatus: RoomStatus;
  titularFirstName: string;
  titularLastName: string;
  checkInDate: string;
  expectedCheckoutDate: string;
  source: string;
  guests: ActiveStayGuest[];
}

export interface CheckOutRequest {
  stayId: string;
  confirmed: boolean;
}

export interface CheckOutResult {
  stayId: string;
  roomId: string;
  roomNumber: string;
  roomStatus: RoomStatus;
  checkOutDate: string;
}

export function getActiveStay(reservationRef: string, roomId: string): Promise<ActiveStay> {
  return apiRequest<ActiveStay>('/api/check-out/active-stay', {
    params: { reservationRef, roomId },
  });
}

export function registerCheckOut(request: CheckOutRequest): Promise<CheckOutResult> {
  return apiRequest<CheckOutResult>('/api/check-out', { method: 'POST', body: request });
}
